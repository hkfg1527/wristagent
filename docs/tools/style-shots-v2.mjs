#!/usr/bin/env node
// 五套风格的真机取证（V2）。
//
// 教训：前几轮"五张截图哈希相同"的真正原因不是界面不重绘，而是**当时屏幕上不是设置页**
// （release-check 的 subpageShot 结尾会按 back 退回聊天页）。所以这里每一步都先校验页面：
// 只有 dump 里出现 V2 的分区文本，才允许截图；风格是否真的换了，用该风格的**特征标记**判定。
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '../..');
const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const TOKEN = process.env.CONSOLE_TOKEN || '4659';
const BASE = process.env.CONSOLE_BASE || 'http://127.0.0.1:18080';
const OUT = path.join(ROOT, 'docs/device/v2five');

const CHAT_OR_SETTINGS = /连得上|让它能做事|让它听懂我|让它答得好/;

/** 旧枚举值 → V2 风格名（与 SettingsV2Style.fromLegacy 一致）。 */
const STYLES = [
  // 接真实偏好后首卡行变成 模型/温度/回复风格，'供应商' 已不在首屏，标记改为 '模型'
  ['BALLOON', 'M3_CARD', [/连得上/, /模型/]],
  ['M3_LIST', 'M3_LIST', [/连得上/]],
  ['ARC', 'ARC', [/连得上/]],
  ['SQUARE', 'SQUARE', [/连得上/]],
  // uiautomator 把 > 转义成 &gt;，校验串必须用转义形式，否则永远 MISS
  ['TERMINAL', 'TERMINAL', ['# ', '&gt; ']],
];

const adb = (...args) => spawnSync(ADB, ['-s', DEVICE, ...args], { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

function dump() {
  adb('shell', 'rm', '-f', '/sdcard/shots.xml');
  const r = adb('shell', 'uiautomator', 'dump', '/sdcard/shots.xml');
  if (!/dumped to/i.test((r.stdout || '') + (r.stderr || ''))) return '';
  return adb('shell', 'cat', '/sdcard/shots.xml').stdout || '';
}

function wake() {
  // 只点亮屏幕：**不要**用 keyevent 26 连按两次，那会把应用按到后台（实测 dump 出来是系统"最近用过"列表）。
  adb('shell', 'su', '-c', 'svc power stayon true');
  adb('shell', 'input', 'keyevent', '224'); // WAKEUP
  sleep(600);
}

function foreground() {
  adb('shell', 'am', 'start', '-n', 'dev.ghr.wristagent/com.example.myapplication.presentation.MainActivity');
  sleep(2500);
}

function enterSettings() {
  for (let i = 0; i < 12; i++) {
    const xml = dump();
    if (CHAT_OR_SETTINGS.test(xml)) return true;
    adb('shell', 'input', 'swipe', '233', '380', '233', '180', '500');
    sleep(850);
  }
  return false;
}

async function setStyle(legacy) {
  const res = await fetch(`${BASE}/api/settings`, {
    method: 'POST',
    headers: { 'X-Console-Token': TOKEN, 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ settingsStyle: legacy }),
  });
  return res.status;
}

function shot(file) {
  const png = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], {
    cwd: ROOT, encoding: 'buffer', maxBuffer: 64 * 1024 * 1024, timeout: 120000,
  });
  fs.writeFileSync(file, png.stdout || Buffer.alloc(0));
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 12);
}

fs.mkdirSync(OUT, { recursive: true });
foreground();
wake();
if (!enterSettings()) {
  console.log('STOP: 进不去设置页（12 次滑动都没扫到 V2 分区文本）');
  process.exit(2);
}

const rows = [];
let pitchText = '' 
const hashes = new Set();
for (const [legacy, name, markers] of STYLES) {
  try {
  const code = await setStyle(legacy);
  sleep(1100);
  let xml = dump();
  // 风格没被应用（页面被切走）时再进一次
  if (!CHAT_OR_SETTINGS.test(xml)) {
    enterSettings();
    xml = dump();
  }
  const markerOk = markers.every((m) => (m instanceof RegExp ? m.test(xml) : xml.includes(m)));
  const file = path.join(OUT, `${name}.png`);
  const hash = shot(file);
  hashes.add(hash);
  // 顺带量该风格的行距（先验证 marker，避免读到旧页面）：模型 -> 温度 的 top 差
  const pitchOf = (xmlText, a, b) => {
    const find = (label) => {
      const re = new RegExp(`text="${label}"[^>]*bounds="\\[\\d+,(\\d+)\\]`);
      const m = xmlText.match(re);
      return m ? Number(m[1]) : -1;
    };
    const ta = find(a); const tb = find(b);
    return (ta >= 0 && tb >= 0) ? (tb - ta) : -1;
  };
  // 终端行文本带 > 前缀（dump 里转义为 &gt;），普通标签匹配不到时再试带前缀的
  const pitchPlain = pitchOf(xml, '模型', '温度');
  const pitch = pitchPlain > 0 ? pitchPlain : pitchOf(xml, '&gt; 模型', '&gt; 温度');
  pitchText = pitch > 0 ? `${pitch}px/${(pitch / 2).toFixed(1)}dp` : 'n/a';
  rows.push(`${name.padEnd(9)} post=${code} marker=${markerOk ? 'ok' : 'MISS'} hash=${hash} pitch=${pitchText}`);
  console.log(rows[rows.length - 1]);
  } catch (e) {
    // 单套失败不应中断整轮（上一轮就是在这里静默中断，导致 report 只到 SQUARE）
    rows.push(`${name.padEnd(9)} ERROR ${String(e && e.message || e)}`);
    console.log(rows[rows.length - 1]);
  }
}

const distinct = hashes.size;
console.log(`distinct=${distinct}/5 ${distinct === 5 ? 'OK' : 'INVALID（少于 5 个不同哈希 = 有截图不是该风格）'}`);
fs.writeFileSync(path.join(OUT, 'report.txt'), rows.join('\n') + `\ndistinct=${distinct}/5\n`);
process.exit(distinct === 5 ? 0 : 3);
