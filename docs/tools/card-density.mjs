#!/usr/bin/env node
// 首屏"完整可见的设置项"测量器（真机 466×466 圆屏）。
//
// 为什么需要它：设置页的密度讨论（D6）一直是靠"看图感觉"，没有数字。
// 圆屏上"看得见"不等于"在 466×466 之内" —— 文本的四个角还要落在**圆**里，否则会被圆弧切掉。
// 本工具只做一件事：把当前这一屏里，**完整落在圆内**的文本节点数出来，按风格存成基线。
//
// 判据（几何，不靠眼睛）：
//   对每个文本节点的矩形，取它的四个角与上下边中点，检查每个点是否在半径 233 的圆内。
//   全部在内 → 记为"完整可见"；否则记为"被弧切"。
//
// 用法：
//   node docs/tools/card-density.mjs                 # 量当前风格
//   node docs/tools/card-density.mjs --all           # 五套依次量（会改偏好，量完还原）
//
// 注意：会进入设置页并滚动到顶部；不改任何设置值（--all 会临时改 settingsStyle 再还原）。

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '../..');
const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const BASE = process.env.CONSOLE_BASE || 'http://127.0.0.1:18080';
const OUT = path.join(ROOT, 'build-logs/card-density.json');

const adb = (...args) => spawnSync(ADB, ['-s', DEVICE, ...args], { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

function token() {
  const xml = adb('shell', "su -c 'cat /data/data/dev.ghr.wristagent/shared_prefs/console_prefs.xml'").stdout || '';
  const m = xml.match(/name="console_token">([^<]+)</);
  return m ? m[1] : process.env.CONSOLE_TOKEN || '4659';
}

async function state() {
  const r = await fetch(`${BASE}/api/state`, { headers: { 'X-Console-Token': TOKEN } });
  return r.json();
}

async function setStyle(legacy) {
  await fetch(`${BASE}/api/settings`, {
    method: 'POST',
    headers: { 'X-Console-Token': TOKEN, 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ settingsStyle: legacy }),
  });
  sleep(1500);
}

function dumpTexts() {
  adb('shell', 'rm', '-f', '/sdcard/density.xml');
  adb('shell', 'uiautomator', 'dump', '/sdcard/density.xml');
  const xml = adb('shell', 'cat', '/sdcard/density.xml').stdout || '';
  return [...xml.matchAll(/text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g)]
    .map((m) => ({ text: m[1], l: +m[2], t: +m[3], r: +m[4], b: +m[5] }));
}

function enterSettings() {
  // 先退出设置页再进：否则"已经停在设置页"时会在**滚动中间**直接测量，
  // 量到的就不是首屏（0.0.0.270 实测踩过：量到 y=0 处被切掉半截的「温度」）。
  adb('shell', 'input', 'keyevent', '4');
  sleep(900);
  for (let i = 0; i < 4; i++) {
    adb('shell', 'input', 'swipe', '233', '420', '233', '140', '1200');
    sleep(1800);
    const t = dumpTexts();
    if (t.some((n) => /连得上|让它能做事/.test(n.text))) return true;
  }
  return false;
}

const R = 233;
/** 矩形是否完整落在半径 R 的圆内（四角 + 四边中点都检查）。 */
function fullyInsideCircle(n) {
  const cx = 233, cy = 233;
  const pts = [
    [n.l, n.t], [n.r, n.t], [n.l, n.b], [n.r, n.b],
    [(n.l + n.r) / 2, n.t], [(n.l + n.r) / 2, n.b], [n.l, (n.t + n.b) / 2], [n.r, (n.t + n.b) / 2],
  ];
  return pts.every(([x, y]) => (x - cx) ** 2 + (y - cy) ** 2 <= R * R);
}

function measure(label) {
  const nodes = dumpTexts();
  const items = nodes.filter((n) => n.text.trim().length > 0);
  const inside = items.filter(fullyInsideCircle);
  const clipped = items.filter((n) => !fullyInsideCircle(n));
  console.log(`--- ${label} ---`);
  console.log(`文本节点 ${items.length} 个：完整可见 ${inside.length}，被弧切 ${clipped.length}`);
  inside.forEach((n) => console.log(`  ✓ ${n.text.slice(0, 18).padEnd(20)} y=[${n.t},${n.b}]`));
  clipped.forEach((n) => console.log(`  ✗ ${n.text.slice(0, 18).padEnd(20)} y=[${n.t},${n.b}]`));
  return { style: label, total: items.length, fullyVisible: inside.length, clipped: clipped.length };
}

const TOKEN = token();
const STYLES = [
  ['BALLOON', 'M3_CARD'],
  ['M3_LIST', 'M3_LIST'],
  ['ARC', 'ARC'],
  ['SQUARE', 'SQUARE'],
  ['TERMINAL', 'TERMINAL'],
];

const all = process.argv.includes('--all');
const results = [];
const original = (await state()).settings.settingsStyle;

adb('shell', 'am', 'start', '-n', 'dev.ghr.wristagent/com.example.myapplication.presentation.MainActivity');
adb('forward', 'tcp:18080', 'tcp:8080');

for (const [legacy, name] of all ? STYLES : [[original, original]]) {
  if (all) await setStyle(legacy);
  if (!enterSettings()) {
    console.log(`--- ${name} --- 进不去设置页，跳过`);
    continue;
  }
  results.push(measure(name));
}

if (all) await setStyle(original);
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, JSON.stringify(results, null, 2));
console.log(`\n写入 ${path.relative(ROOT, OUT)}`);
