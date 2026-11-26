#!/usr/bin/env node
/**
 * 手表导航助手：把"点行、滚表冠、关弹层"这三件事做成可复用、可自检的动作。
 *
 * 为什么会需要它：第 51–52 轮我连续两轮"验证失败"，结论写成"列表丢了焦点、表冠失效"。
 * 真相是 **我的点击落在了「输入命令…」这一行上，弹出了一个对话框盖住整页** ——
 * dump 里全是对话框的节点，看起来就像"页面卡住不动"。
 *
 * 教训写成代码：
 * 1. 每次点击前后都要判断"是不是弹出了弹层"（出现 取消/完成 就是弹层）；
 * 2. 点完必须**验证效果**，不能"点了就算成功"；
 * 3. 滚动用一次 su 批量注入（避免每条 sendevent 都弹 Magisk toast）。
 */
import { spawnSync } from 'node:child_process';

const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';

export const sh = (...args) =>
  spawnSync(ADB, ['-s', DEVICE, ...args], { encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, timeout: 120000 });

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export const texts = () => {
  sh('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  const xml = sh('shell', 'cat', '/sdcard/ui.xml').stdout || '';
  return [...xml.matchAll(/text="([^"]+)"/g)].map((m) => m[1]).filter(Boolean);
};

export const boundsOf = (label) => {
  sh('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  const xml = sh('shell', 'cat', '/sdcard/ui.xml').stdout || '';
  const esc = label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const m = xml.match(new RegExp('<node[^>]*text="' + esc + '"[^>]*bounds="\\[(-?\\d+),(-?\\d+)\\]\\[(\\d+),(\\d+)\\]"'));
  return m ? { cx: Math.round((+m[1] + +m[3]) / 2), cy: Math.round((+m[2] + +m[4]) / 2), raw: m[0] } : null;
};

/**
 * 滚动：**用手指滑动实现**（2026-11-26 改）。
 *
 * 原实现是 `sendevent /dev/input/event0 … REL_WHEEL`，但那个通道是本机的 `qpnp_pon`（电源键），
 * **到不了应用** —— 0.0.0.261 查明，项目自己的 `RotaryBus` 也记着"注入 REL_WHEEL 后
 * onRotaryScrollEvent 一行日志都没有"。也就是说建立在它上面的
 * `sweep-pages.mjs` / `sweep-settings.mjs` / `watch.mjs` 的滚动**一直是空操作**。
 *
 * 现在的换算：一格表冠 ≈ 1/8 行 ≈ 19px（行高约 148px），
 * `delta > 0` 朝列表开头（手指往下滑），`delta < 0` 朝末尾（手指往上滑）。
 * 单次手势限制在 60–350px，超额分多次。
 *
 * **未在设备上验证**（改这段时 handoff 期间 adb 一直离线）。上机后请先用
 * `crown(-5, 8)`（≈ 一行）核对位移，再信任任何 sweep 结果。
 */
export const crown = (delta, n) => {
  const px = Math.max(60, Math.min(350, Math.round(Math.abs(delta) * n * 3.8)));
  const times = Math.max(1, Math.ceil(px / 350));
  const step = Math.round(px / times);
  const fromY = delta > 0 ? 140 : 400;
  const toY = delta > 0 ? fromY + step : fromY - step;
  for (let i = 0; i < times; i++) {
    sh('shell', 'input', 'swipe', '233', String(fromY), '233', String(Math.max(60, Math.min(430, toY))), '260');
  }
};

/** 有没有弹层：取消/完成 同时出现基本就是文本对话框。 */
export const overlayOpen = () => {
  const t = texts();
  return t.includes('取消') && t.includes('完成');
};

/** 关掉任何弹层（最多三次返回）。 */
export const closeOverlay = async () => {
  for (let i = 0; i < 3; i++) {
    if (!overlayOpen()) return true;
    sh('shell', 'input', 'keyevent', '4');
    await sleep(1200);
  }
  return false;
};

/**
 * 点一行，并**验证它没有打开弹层**（打开了就关掉并返回 false）。
 * 这就是第 51 轮那个坑的通用解药。
 */
export const tapRow = async (label) => {
  const b = boundsOf(label);
  if (!b || b.cy < 55 || b.cy > 405) return false;
  sh('shell', 'input', 'tap', String(b.cx), String(b.cy));
  await sleep(2200);
  if (overlayOpen()) {
    await closeOverlay();
    return false;
  }
  return true;
};

/** 朝一个方向滚，直到出现某个文案（或到步数上限）。 */
export const crownTo = async (label, { delta = -5, step = 5, maxSteps = 10 } = {}) => {
  for (let i = 0; i < maxSteps; i++) {
    if (texts().includes(label)) return true;
    crown(delta, step);
    await sleep(2100);
  }
  return texts().includes(label);
};
