#!/usr/bin/env node
/**
 * 设置页 15 页巡检：每页判据只有两条 —— **能打开**、**能返回根页**。
 *
 * 这一版把前三版的坑都收进去了：
 * 1. 别要求"连续两次坐标一致"再落指 —— 列表带边缘缩放动画时坐标一直在微动，
 *    那个门槛会让点击**永远不会发生**，于是整轮全红（第 3 版就这么废的）。
 *    正确做法：滚动后睡够，单次读坐标，点下去，**用结果验证**。
 * 2. 步长自适应：找不到就 6 格大步，看见了但贴边就 3 格小步把它推进安全带。
 * 3. 贴底（cy > 390）时不能继续往下滚 —— 目标在列表最后，再滚就滚没了。
 * 4. 打开与否**不能**用"页面标题 == 行名"判断：实测有 4 页标题和行名不一致
 *    （模型与服务→模型与端点、模型参数→推理参数、语音与唤醒→语音输入、底栏副键→手势与侧键）。
 *    判据改成"根页分区标题消失 + 屏上有内容"，标题单独记进结果当证据。
 * 5. 返回要**轮询等**：子页退场是逐行错峰动画，1.5s 就再按一次会把应用退出到表盘。
 */
import fs from 'node:fs';
import path from 'node:path';
import { sh, sleep, texts, crown, shot, dumpNodes, enterSettings } from './watch.mjs';

const ROOT = process.cwd();
const OUT = path.join(ROOT, 'build-logs/sweep-0.0.0.47.jsonl');

/** 根页行标题，顺序 = 根页从上到下的真实顺序（滚动只朝一个方向，顺序错了就找不到）。 */
const PAGES = [
  '模型与服务',
  '供应商',
  '模型参数',
  '提示词与记忆',
  '语音与唤醒',
  '底栏副键',
  '外观与动效',
  '工具与 MCP',
  '安全策略',
  '特权执行',
  'Lux 终端',
  '网络与请求',
  '网页控制台',
  '配置导入导出',
  '关于',
];

const ZONES = ['让它答得好', '让它听懂我', '让它能做事', '连得上'];
const zonesVisible = () => {
  const t = texts();
  return ZONES.some((z) => t.some((line) => line.includes(z)));
};

const centerOf = (label) => {
  const n = dumpNodes().find((x) => x.text === label);
  const m = n && n.bounds.match(/\[(-?\d+),(-?\d+)\]\[(\d+),(\d+)\]/);
  if (!m) return null;
  return { cx: Math.round((+m[1] + +m[3]) / 2), cy: Math.round((+m[2] + +m[4]) / 2) };
};

/**
 * 点中目标行。
 *
 * 实测出来的两条硬事实（都是被真机打脸打出来的）：
 * - **一格表冠 ≈ 1/8 行**：`crown(-5, 8)` 大约走一行，所以"每步 2 格"的写法等于原地踏步；
 * - **回顶再找**：访问过后面几页之后列表停在下方，目标行可能在视野上方，
 *   一路往下滚永远看不到它（「Lux 终端」因此连红三版）。
 *
 * 所以：先回顶，再用 20 格一步往下扫，扫到就点。每页最多 12 步，够覆盖整页。
 */
/**
 * 等页面标题出现。
 *
 * 为什么需要：点完 2.6s 就 dump，会读到**动画中间帧** —— 行已经排好、标题还没上屏，
 * 于是 `texts()[0]` 是某一行而不是页标题（「语音与唤醒」被读成「启用语音识别」、
 * 「特权执行」被读成「Root」）。判据本身没错，是取样太早。
 */
const waitForTitle = async (label, tries = 6) => {
  for (let i = 0; i < tries; i++) {
    const t = texts();
    if (t[0] === label) return true;
    await sleep(700);
  }
  return false;
};

const tapScrolled = async (label, maxSteps = 20) => {
  crown(5, 90);
  await sleep(4500); // 回顶后等惯性/回弹彻底停：2.8s 时坐标还会在落指前漂一行
  for (let i = 0; i < maxSteps; i++) {
    const c = centerOf(label);
    if (c && c.cy >= 60 && c.cy <= 450) {
      sh('shell', 'input', 'tap', String(c.cx), String(c.cy));
      await sleep(2600);
      // 标题已经和行名统一，所以这里能**直接验证开的是不是这一页**：
      // 点空 → 还停在根页；点在隔壁行 → 标题是隔壁页的名字。两种都认得出。
      if (await waitForTitle(label)) return true;
      if (zonesVisible()) {
        // 点空（行在动画里挪了位）：同一处再补一次，别立刻放弃去找下一屏
        const again = centerOf(label);
        if (again && again.cy >= 60 && again.cy <= 430) {
          sh('shell', 'input', 'tap', String(again.cx), String(again.cy));
          await sleep(2600);
          if (await waitForTitle(label)) return true;
        }
      } else {
        // 开到了别的页：退回去重来
        sh('shell', 'input', 'keyevent', '4');
        await sleep(2200);
      }
    }
    if (c && c.cy > 450) {
      crown(5, 6); // 贴底：往回抬半行
    } else {
      crown(-5, 10); // ≈ 1.2 行：20 格会从第 3 行头上跳过去（模型参数就栽在这）
    }
    await sleep(2200);
  }
  return false;
};

/**
 * 从子页回到根页。
 *
 * 两个坑：
 * 1. 子页退场是逐行错峰动画，1.5s 就再按一次会把应用退出到表盘 —— 所以"按一次、轮询等"。
 * 2. **根页分区标题不一定在屏上**：从「关于」返回时列表停在底部，屏上只有
 *    「配置导入导出 / 关于 / 完成返回」，按分区标题判断就会误判成"没回到根页"，
 *    于是又按一次返回、把设置页关掉。所以判据改成：按一次返回后**先回顶**再看分区标题。
 */
const backToRoot = async () => {
  for (let attempt = 0; attempt < 2; attempt++) {
    if (!zonesVisible()) {
      sh('shell', 'input', 'keyevent', '4');
    }
    for (let poll = 0; poll < 4; poll++) {
      await sleep(800);
      if (zonesVisible()) return true;
    }
    // 可能是"已经回到根页、但列表停在底部"：回顶再判
    crown(5, 90);
    await sleep(2600);
    if (zonesVisible()) return true;
  }
  return zonesVisible();
};

const args = process.argv.slice(2);
const argOf = (name, def) => {
  const i = args.indexOf('--' + name);
  return i >= 0 ? Number(args[i + 1]) : def;
};
const start = argOf('start', 0);
const count = argOf('count', 15);

if (!(await enterSettings())) {
  console.log('FAIL: 进不了设置根页');
  process.exit(1);
}
crown(5, 90);
await sleep(4000);

const results = [];
for (let i = start; i < Math.min(start + count, PAGES.length); i++) {
  const label = PAGES[i];
  // 每页都冷启重进设置：0.0.0.66 证实失败行是 tapped=false —— 表冠推动不了列表（焦点不在列表上）。
  // 冷启后焦点是干净的，这也是最早两页能过、越往后越容易失败的原因。
  await enterSettings();
  crown(5, 90);
  await sleep(3500);
  // 标题与行名已统一（0.0.0.48），所以"打开"的判据收紧成 **标题必须等于行名**：
  // 点在隔壁行、点空、打开后标题不对，三种情况都会被这条抓住。
  const tapped = await tapScrolled(label);
  const after = texts();
  const title = after[0] || '';
  const opened = tapped && title === label;
  const back = await backToRoot();
  results.push({ index: i, label, title, titleMatchesRow: title === label, opened, back });
  console.log(
    `PAGE ${i} ${label}\topen=${opened ? 'ok' : 'FAIL'}\tback=${back ? 'ok' : 'FAIL'}` +
      (title && title !== label ? `\t实际标题=${title}` : '')
  );
  if (!back) {
    shot('sweep-fail-' + i);
    await enterSettings();
    crown(5, 90);
    await sleep(4000);
  }
}

fs.appendFileSync(OUT, results.map((r) => JSON.stringify(r)).join('\n') + '\n');
console.log(`BATCH DONE ${start}..${start + count - 1}`);
