#!/usr/bin/env node
// S7 十套风格批量取证。
//
// 两次教训：
// ①0.0.0.141：切风格 → 唤醒 → 直接 screencap，十张同帧（截图时屏幕不是设置页）。
// ②0.0.0.142：用"字节数相同"判断同帧是**错的** —— 深色页面压缩后大小本来就接近。
//   现在用内容哈希（sha256 前 12 位）判断，并把哈希写进报告。
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '../..');
const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const TOKEN = process.env.CONSOLE_TOKEN || '4659';
const BASE = process.env.CONSOLE_BASE || 'http://127.0.0.1:18080';
const OUT = path.join(ROOT, 'docs/device/s7');
const STYLES = ['BALLOON', 'SQUARE', 'GOOGLE_CARD', 'TERMINAL', 'M3_LIST', 'M3_TRANSFORM', 'NEON', 'PAPER', 'GLASS', 'ARC'];
const SETTINGS_TEXT = /模型与服务|全部启用|语音与唤醒|配置导入导出/;

const adb = (...args) => spawnSync(ADB, ['-s', DEVICE, ...args], { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

function dump() {
  // 必须先删旧文件：dump 失败时旧 XML 会留着，看起来"在设置页"其实是上一轮残留。
  adb('shell', 'rm', '-f', '/sdcard/s7.xml');
  const r = adb('shell', 'uiautomator', 'dump', '/sdcard/s7.xml');
  if (!/dumped to/i.test((r.stdout || '') + (r.stderr || ''))) return '';
  return adb('shell', 'cat', '/sdcard/s7.xml').stdout || '';
}

const inSettings = () => SETTINGS_TEXT.test(dump());

function enterSettings() {
  for (let i = 0; i < 6; i++) {
    adb('shell', 'input', 'swipe', '233', '380', '233', '180', '500');
    sleep(900);
    if (inSettings()) return true;
  }
  return false;
}

function shot(file) {
  const png = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], {
    cwd: ROOT, encoding: 'buffer', maxBuffer: 64 * 1024 * 1024, timeout: 120000
  });
  fs.writeFileSync(file, png.stdout || Buffer.alloc(0));
  return fs.statSync(file).size;
}

const hashOf = (file) => crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 12);

async function setStyle(style) {
  const res = await fetch(`${BASE}/api/settings`, {
    method: 'POST',
    headers: { 'X-Console-Token': TOKEN, 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ settingsStyle: style })
  });
  return res.status;
}

fs.mkdirSync(OUT, { recursive: true });
const rows = [];
let prevHash = '';
for (const style of STYLES) {
  adb('shell', 'input', 'keyevent', '224');
  sleep(600);
  if (!inSettings() && !enterSettings()) {
    rows.push([style, 'SKIP', '未进入设置页']);
    console.log(`${style.padEnd(13)} SKIP 未进入设置页`);
    continue;
  }
  const code = await setStyle(style);
  sleep(1200);
  if (!inSettings() && !enterSettings()) {
    rows.push([style, 'SKIP', '切风格后掉出设置页']);
    console.log(`${style.padEnd(13)} SKIP 掉出设置页`);
    continue;
  }
  const file = path.join(OUT, `${style}.png`);
  let size = shot(file);
  let hash = hashOf(file);
  let note = '';
  if (hash === prevHash) {
    sleep(1200);
    if (inSettings()) {
      size = shot(file);
      hash = hashOf(file);
    }
    if (hash === prevHash) note = '与上一套同一帧（无效）';
  }
  prevHash = hash;
  rows.push([style, `http=${code}`, `${size}B ${hash} ${note}`]);
  console.log(`${style.padEnd(13)} http=${code} ${size}B ${hash} ${note}`);
}
fs.writeFileSync(path.join(OUT, 'report.txt'), rows.map((r) => r.join('  ')).join('\n') + '\n');
