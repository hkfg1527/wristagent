#!/usr/bin/env node
// 五套风格的真机取证（V2）。
//
// 教训：前几轮"五张截图哈希相同"的真正原因不是界面不重绘，而是**当时屏幕上不是设置页**
// （release-check 的 subpageShot 结尾会按 back 退回聊天页）。所以这里每一步都先校验页面：
// 只有 dump 里出现 V2 的分区文本，才允许截图；风格是否真的换了，用该风格的**特征标记**判定。
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { regionHash } from './png-region.mjs';

const ROOT = path.resolve(import.meta.dirname, '../..');
const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const TOKEN = process.env.CONSOLE_TOKEN || '4659';
const BASE = process.env.CONSOLE_BASE || 'http://127.0.0.1:18080';
const OUT = path.join(ROOT, 'docs/device/v2five');

const CHAT_OR_SETTINGS = /连得上|让它能做事|让它听懂我|让它答得好/;

/** 旧枚举值 → V2 风格名（与 SettingsV2Style.fromLegacy 一致）。 */
const STYLES = [
  // 第 3 项是"用于量行距的两个相邻行标签"：必须**都在首屏**才量得到。
  // ARC / SQUARE 的行高是 70.5 / 75dp，首屏只放得下两行多，所以用 供应商 -> 模型（相邻），
  // 而不是 模型 -> 温度（后者会被挤出首屏，读数变成 n/a —— 不是回归，是量的对象不在屏上）。
  ['BALLOON', 'M3_CARD', [/连得上/, /模型/], ['模型', '温度']],
  ['M3_LIST', 'M3_LIST', [/连得上/], ['模型', '温度']],
  ['ARC', 'ARC', [/连得上/], ['供应商', '模型']],
  ['SQUARE', 'SQUARE', [/连得上/], ['供应商', '模型']],
  // uiautomator 把 > 转义成 &gt;，校验串必须用转义形式，否则永远 MISS
  ['TERMINAL', 'TERMINAL', ['# ', '&gt; '], ['模型', '温度']],
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
  // 哈希**排除顶部 60px 钟带**：0.0.0.261 起页面顶部有曲面时钟，整屏哈希会把"当前几点"
  // 算进去 —— 同一份代码在 10:55 与 12:57 得到不同哈希，跨版本对比直接失效
  // （实测：M3_CARD 源码没动，升版后整屏哈希从 480b72e5 变成 6d5ba611）。
  // 其余像素逐字节参与哈希，判据没有被放松；解析失败直接抛错，不退回整屏哈希糊过去。
  return regionHash(fs.readFileSync(file), 60);
}

fs.mkdirSync(OUT, { recursive: true });
foreground();
wake();
const enteredUpFront = enterSettings();
// 进不去设置页时不提前 exit：让五套各自跑并各自失败，末尾统一输出
// `distinct=0/5 INVALID` + exit=1。提前 exit=2 会让"判据是否执行"无法被自检区分。
if (!enteredUpFront) {
  console.log('WARN: 进不去设置页（12 次滑动都没扫到 V2 分区文本）→ 五套将全部判红');
}

const rows = [];
let pitchText = '' 
const hashes = new Set();
for (const [legacy, name, markers, pitchPair] of STYLES) {
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
  // 截图前**复位**：离开设置页再进来，清掉"上一次操作留下的回显"与滚动位置。
  // 不复位时哈希会受残留状态污染（实测 232/233 两轮 TERMINAL 哈希不同，却没有任何视觉改动，
  // 无法区分"真变化"与"残影"—— 这种噪音会污染后续所有哈希对比）。
  adb('shell', 'input', 'keyevent', '4'); // BACK：回聊天页
  sleep(700);
  enterSettings();
  xml = dump();
  if (!CHAT_OR_SETTINGS.test(xml)) {
    adb('shell', 'input', 'keyevent', '4');
    sleep(700);
    enterSettings();
    xml = dump();
  }
  const markerAfterReset = markers.every((m) => (m instanceof RegExp ? m.test(xml) : xml.includes(m)));
  const file = path.join(OUT, `${name}.png`);
  const hash = shot(file);
  hashes.add(hash);
  // 顺带量该风格的行距（先验证 marker，避免读到旧页面）：模型 -> 温度 的 top 差
  const pitchOf = (xmlText, a, b) => {
    const find = (label) => {
      // 前缀容忍：TERMINAL 的只读行现在缩进两格（`  模型`），可执行行仍带 `> `
      const re = new RegExp(`text="[^"]*${label}"[^>]*bounds="\\[\\d+,(\\d+)\\]`);
      const m = xmlText.match(re);
      return m ? Number(m[1]) : -1;
    };
    const ta = find(a); const tb = find(b);
    return (ta >= 0 && tb >= 0) ? (tb - ta) : -1;
  };
  // 终端行文本带 > 前缀（dump 里转义为 &gt;），普通标签匹配不到时再试带前缀的
  const [pa, pb] = pitchPair || ['模型', '温度'];
  const pitchPlain = pitchOf(xml, pa, pb);
  const pitch = pitchPlain > 0 ? pitchPlain : pitchOf(xml, `&gt; ${pa}`, `&gt; ${pb}`);
  pitchText = pitch > 0 ? `${pitch}px/${(pitch / 2).toFixed(1)}dp` : 'n/a';
  rows.push(`${name.padEnd(9)} post=${code} marker=${markerOk ? 'ok' : 'MISS'}/reset=${markerAfterReset ? 'ok' : 'MISS'} hash=${hash} pitch=${pitchText}`);
  console.log(rows[rows.length - 1]);
  } catch (e) {
    // 单套失败不应中断整轮（上一轮就是在这里静默中断，导致 report 只到 SQUARE）
    rows.push(`${name.padEnd(9)} ERROR ${String(e && e.message || e)}`);
    console.log(rows[rows.length - 1]);
  }
}

const distinct = hashes.size;
const ok = distinct === 5;
console.log(`distinct=${distinct}/5 ${ok ? 'OK' : 'INVALID（少于 5 个不同哈希 = 有截图不是该风格/设备不可用）'}`);
fs.writeFileSync(path.join(OUT, 'report.txt'), rows.join('\n') + `\ndistinct=${distinct}/5\n`);
// 设备掉线时每套都会抛错 -> distinct=0/5。以前只打印 INVALID 却返回 0，
// 调用方（含定时任务）会把它当成通过。必须给非零退出码，避免"没验过却报绿"。
if (!ok) process.exit(1);
process.exit(distinct === 5 ? 0 : 3);
