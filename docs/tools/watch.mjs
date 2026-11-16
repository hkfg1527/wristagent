#!/usr/bin/env node
/**
 * 手表设备的"高频动作"封装：进设置根页、截图、按文案点、按文案取坐标。
 *
 * 为什么单独一个文件：`nav.mjs` 只管"点行/滚表冠/关弹层"这三个易错动作，
 * 而每一轮改 UI 都还要反复做"回到设置根页 → 截图 → 再截一屏"。这两件事混在一起，
 * 脚本就会长成一次性代码。这里只放**与具体页面无关**的动作。
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { sh, sleep, texts, boundsOf, crown } from './nav.mjs';

const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const PKG = 'dev.ghr.wristagent';
const ACTIVITY = PKG + '/com.example.myapplication.presentation.MainActivity';
const ROOT = process.cwd();

/** 四个意图分区标题是"在根页"的唯一可靠判据（行标题可能被滚出屏幕，分区标题不会全消失）。 */
export const ROOT_ZONES = ['让它答得好', '让它听懂我', '让它能做事', '连得上'];

export const onRoot = () => {
  const t = texts();
  // 分区标题在 dump 里带装饰前缀（实测是 "&gt; 连得上"），所以只能做包含匹配，不能做全等匹配。
  return ROOT_ZONES.some((z) => t.some((line) => line.includes(z)));
};

/** 往屏幕上方划（进入设置页 / 下一页）。 */
export const swipeUp = (ms = 260) => {
  sh('shell', 'input', 'swipe', '233', '430', '233', '60', String(ms));
};

export const back = async (wait = 1200) => {
  sh('shell', 'input', 'keyevent', '4');
  await sleep(wait);
};

/**
 * 冷启动并进到设置根页。
 *
 * 踩过的坑：应用会**恢复上次停留的页面**（上次停在「关于」，冷启动后还是「关于」），
 * 所以"启动后直接划上去"会一直划在子页里，永远等不到分区标题。
 * 这里先判断是否停在卡片子页（出现「完成返回」），是就先退出，再重新划进设置。
 */
export const enterSettings = async ({ tries = 12 } = {}) => {
  const launch = () => {
    sh('shell', 'am', 'force-stop', PKG);
    sh('shell', 'am', 'start', '-n', ACTIVITY);
  };
  const walk = async () => {
    await sleep(5000);
    for (let i = 0; i < tries; i++) {
      if (onRoot()) return true;
      // 恢复出来的若是卡片子页，先退出这一层；聊天页没有「完成返回」，直接划上去就是设置页
      if (texts().includes('完成返回')) {
        await back(1500);
        continue;
      }
      swipeUp();
      await sleep(900);
    }
    return onRoot();
  };
  launch();
  if (await walk()) return true;
  // 一次不够：可能恢复到了一个既不是根页、也没有「完成返回」的浮层页 —— 划不动也退不出。
  // 再冷启一次，从聊天页重进，比继续盲划靠得住。
  launch();
  return walk();
};

/** 截屏到 docs/device/<sub>/<name>.png，返回绝对路径。 */
export const shot = (name, sub = 'v11') => {
  const dir = path.join(ROOT, 'docs/device', sub);
  fs.mkdirSync(dir, { recursive: true });
  const file = path.join(dir, name + '.png');
  const r = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], {
    encoding: 'buffer',
    maxBuffer: 64 * 1024 * 1024,
    timeout: 120000,
  });
  fs.writeFileSync(file, r.stdout || Buffer.alloc(0));
  return file;
};

/**
 * 点一条有文案的行；返回是否点到了。
 *
 * 圆屏上下两段（y<55 / y>405）是**看得见但点不准**的区域：行在那里时坐标会落在
 * 系统的圆形触摸裁剪外，`input tap` 会被吃掉。以前的做法是"找不到就当失败"，
 * 于是巡检里出现一堆假的"点击未生效"。现在先把它滚进中间安全带再点，最多试两次。
 */
export const tapText = async (label, { retries = 3 } = {}) => {
  for (let attempt = 0; attempt <= retries; attempt++) {
    const b = boundsOf(label);
    if (!b) {
      // 不在屏上：先朝列表末尾滚一小段再找（巡检时行常常只差一屏）
      crown(-5, 4);
      await sleep(1600);
      continue;
    }
    if (b.cy >= 70 && b.cy <= 400) {
      sh('shell', 'input', 'tap', String(b.cx), String(b.cy));
      await sleep(2200);
      return true;
    }
    // 在安全带外：行偏下就往上滚（正数），偏上就往下滚（负数）
    crown(b.cy > 400 ? 5 : -5, 2);
    await sleep(1500);
  }
  return false;
};

/** 朝前滚（负数）找一条文案，找到就返回 true；到上限就 false。 */
export const scrollTo = async (label, { step = 6, maxSteps = 12, wait = 1700 } = {}) => {
  for (let i = 0; i < maxSteps; i++) {
    if (texts().includes(label)) return true;
    crown(-5, step);
    await sleep(wait);
  }
  return texts().includes(label);
};

export { sh, sleep, texts, boundsOf, crown };

/**
 * 把当前屏的每个节点按 `text @ [x1,y1][x2,y2] clickable` 打出来。
 * 巡检脚本出问题时，第一件事就是看这个 —— 光看文案列表分不清"文案在哪个节点上、
 * 那个节点可不可点、坐标落在圆屏的可触区里没有"。
 */
export const dumpNodes = (onlyText = true) => {
  sh('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  const xml = sh('shell', 'cat', '/sdcard/ui.xml').stdout || '';
  const out = [];
  for (const m of xml.matchAll(/<node[^>]*>/g)) {
    const tag = m[0];
    const text = (tag.match(/text="([^"]*)"/) || [])[1] || '';
    const desc = (tag.match(/content-desc="([^"]*)"/) || [])[1] || '';
    const bounds = (tag.match(/bounds="([^"]*)"/) || [])[1] || '';
    const clickable = /clickable="true"/.test(tag);
    if (onlyText && !text && !desc) continue;
    out.push({ text, desc, bounds, clickable });
  }
  return out;
};
