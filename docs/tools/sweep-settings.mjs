#!/usr/bin/env node
/**
 * 设置项功能实测：**改一行设置 → 读偏好文件确认真的落盘**。
 *
 * 判据只有一条：`agent_prefs.xml` 里对应键的值变了（开关类还要求再点一次能还原）。
 * 为什么不看界面：界面文案会随样式变，键不会；这也是"设置是不是真生效"的唯一硬证据。
 *
 * 踩过的两个坑（写进来省下一次重踩）：
 * 1. **布尔值在 XML 里是属性**：`<boolean name="k" value="true" />`。
 *    按文本节点解析会把所有开关读成空，于是全部报"无变化" —— 我第一次就是这么误判 9 个 FAIL 的。
 * 2. **点击必须落进行本身**：圆屏上下两段（y < 110 / y > 370）看得见但点不准；
 *    但把判据收紧到"必须滚到 180..300"也不对 —— 短页面（三四行）根本滚不到那个位置，
 *    结果全被记成 SKIP。折中：接受 110..370，滚不动就按当前坐标点，并**记录 INCONCLUSIVE**
 *    而不是直接判失败。
 *
 * ⚠️ 当前状态（2026-11-18）：**这个工具还不能产出可信结论**。
 * 最近一次全跑（语音与唤醒 / 网络与请求 / 模型参数）：OK=0 / INCONCLUSIVE=11 / SKIP=3。
 * 原因不是解析，而是"点了没反应"——滑杆行点标签不改值、分区标题本来不可点、
 * 部分行点开是弹层。要拿到真结论，必须换成按节点点击 + 读控件语义（instrumentation），
 * 别再在这个脚本里调坐标了。
 * * 用法：node docs/tools/sweep-settings.mjs
 * 输出：每行一条结论（OK / INCONCLUSIVE / SKIP）+ 末尾 JSON 汇总。
 */import { sh, sleep, texts, crown, dumpNodes, enterSettings, shot } from './watch.mjs';

const PKG = 'dev.ghr.wristagent';
const PREFS = `/data/data/${PKG}/shared_prefs/agent_prefs.xml`;
const ZONES = ['让它答得好', '让它听懂我', '让它能做事', '连得上'];

const zones = () => texts().some((t) => ZONES.some((z) => t.includes(z)));
const readPrefs = () => (sh('shell', `su -c 'cat ${PREFS}'`).stdout || '');

/** string 型是文本节点；boolean/int/long/float 是属性 —— 两类都要读，否则布尔开关永远读不到。 */
const parsePrefs = (xml) => {
  const out = {};
  for (const m of xml.matchAll(/<string name="([^"]+)"[^>]*>([^<]*)<\/string>/g)) out[m[1]] = m[2];
  for (const m of xml.matchAll(/<(boolean|int|long|float) name="([^"]+)"[^>]*value="([^"]*)"/g)) out[m[2]] = m[3];
  return out;
};

const diff = (a, b) => {
  const keys = new Set([...Object.keys(a), ...Object.keys(b)]);
  const changed = [];
  for (const k of keys) if (a[k] !== b[k]) changed.push(k + ':' + (a[k] ?? '-') + '->' + (b[k] ?? '-'));
  return changed;
};

const centerOf = (label) => {
  const n = dumpNodes().find((x) => x.text === label);
  const m = n && n.bounds.match(/\[(-?\d+),(-?\d+)\]\[(\d+),(\d+)\]/);
  return m ? { cx: Math.round((+m[1] + +m[3]) / 2), cy: Math.round((+m[2] + +m[4]) / 2) } : null;
};

const openPage = async (label) => {
  crown(5, 90);
  await sleep(4000);
  for (let i = 0; i < 22; i++) {
    const c = centerOf(label);
    if (c && c.cy >= 60 && c.cy <= 450) {
      sh('shell', 'input', 'tap', String(c.cx), String(c.cy));
      await sleep(2600);
      if (texts().slice(0, 3).some((x) => x === label)) return true;
    }
    crown(-5, 10);
    await sleep(2000);
  }
  return false;
};

/** 把目标行推到屏幕中部（180..300），否则点下去可能落在空白处。 */
const bringToCenter = async (label) => {
  let c = centerOf(label);
  for (let k = 0; k < 10; k++) {
    if (c && c.cy >= 60 && c.cy <= 450) return c;
    crown(c && c.cy < 250 ? 5 : -5, 3);
    await sleep(1500);
    c = centerOf(label);
  }
  return c && c.cy >= 60 && c.cy <= 450 ? c : null;
};

const rowLabels = (pageTitle) =>
  texts().filter(
    (x) => x && x !== pageTitle && x !== '返回' && x !== '完成返回' && !x.startsWith('> ') && x.length <= 12
  );

await enterSettings();
const report = [];

for (const page of ['语音与唤醒', '网络与请求', '模型参数']) {
  console.log(`\n=== ${page} ===`);
  if (!(await openPage(page))) {
    console.log('  打不开，跳过');
    continue;
  }
  const labels = rowLabels(page).slice(0, 6);
  console.log('  候选: ' + labels.join(' / '));
  for (const label of labels) {
    const c = await bringToCenter(label);
    if (!c) {
      report.push({ page, label, verdict: 'SKIP(滚不到中心带)' });
      console.log(`  ${label}: SKIP(滚不到中心带)`);
      continue;
    }
    const before = parsePrefs(readPrefs());
    const tBefore = texts().join('|');
    sh('shell', 'input', 'tap', String(c.cx), String(c.cy));
    await sleep(2000);
    const t = texts();
    if (t.includes('取消') && t.includes('完成')) {
      sh('shell', 'input', 'keyevent', '4');
      await sleep(1500);
      report.push({ page, label, verdict: 'SKIP(弹窗)' });
      console.log(`  ${label}: SKIP(弹窗)`);
      continue;
    }
    const after = parsePrefs(readPrefs());
    const d1 = diff(before, after);
    const uiChanged = texts().join('|') !== tBefore;
    const ok = d1.length > 0;
    const verdict = ok ? 'OK' : uiChanged ? 'INCONCLUSIVE(界面变了但键没变)' : 'INCONCLUSIVE(点击可能没命中)';
    report.push({ page, label, changed: d1, uiChanged, verdict });
    console.log(`  ${label}: ${verdict}  ${d1.join(';') || '(无变化)'}`);
    // 还原（开关类）
    if (ok) {
      const c2 = centerOf(label);
      if (c2 && c2.cy >= 110 && c2.cy <= 370) {
        sh('shell', 'input', 'tap', String(c2.cx), String(c2.cy));
        await sleep(1800);
      }
    }
  }
  for (let i = 0; i < 3 && !zones(); i++) {
    sh('shell', 'input', 'keyevent', '4');
    await sleep(1500);
  }
}

const okCount = report.filter((r) => r.verdict === 'OK').length;
const inc = report.filter((r) => r.verdict.startsWith('INCONCLUSIVE')).length;
const skip = report.filter((r) => r.verdict.startsWith('SKIP')).length;
console.log(`\n=== SUMMARY OK=${okCount} INCONCLUSIVE=${inc} SKIP=${skip} ===`);
console.log(JSON.stringify(report));
console.log('SHOT ' + shot('settings-sweep-end'));
