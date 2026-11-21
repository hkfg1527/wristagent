#!/usr/bin/env node
// 磁滑吸附（snapFlingBehavior）的真机量化取证。
//
// 为什么不用录屏：Wear 上 screenrecord 拿不到有效帧；screencap 单次往返 469~555ms，
// 短于任何一次滚动动画，逐帧采样物理上做不到。所以换一个能"判决"而不是"目测"的实验。
//
// 原理：慢拖（900ms，末速度≈0.04px/ms，低于 fling 阈值）松手后
//   - 吸附生效  -> 内容被重新对齐到锚点，**多个不同拖动距离塌陷成同一个静止态**
//   - 无吸附    -> 内容停在松手位置，每个距离留下一个独立静止态（连续）
// 两种仪器互相校验：
//   ①截图 SHA256（整屏像素）：任何差异都算不同态，但看不到"差多少"；
//   ②uiautomator dump 里首区标题 `连得上` 的像素 bounds：给出坐标，能看出阶梯还是连续。
//
// 判据（R4，写死）：
//   M3_CARD (snapScroll=true)  : 12 个拖动距离(12~400px) -> 哈希静止态 <= 4 种，
//                                且存在一个态被 >= 3 个不同距离命中（抗"连续"），
//                                且 marker 可见（未被顶出屏幕）的那些距离，dump 与哈希给出**同一划分**。
//   TERMINAL (snapScroll=false) : 8 个拖动距离(12~96px) -> 哈希静止态 >= 4 种（对照组）。
//   TERMINAL 的 y 读数会在内容顶到屏幕上方后被裁切（实测 h 从 32 掉到 4、top 卡在 0），
//   属仪器上限，故对照组只认哈希种类。
//
// 历史判据的两次失败（保留在案，避免又走一遍）：
//   R2 要求"静止态步长 == 行距 86px"，实测 25px。前提是"滚动=刚性平移"，
//      但 `SurfaceTransformation` 会把非锚点项朝中心平移（实测卡内行距 86 与文字高 47 都不变，
//      所以是**平移**不是缩放，位移被 morph 曲线吃掉一部分），前提不成立。
//   R3 直接用"两仪器同一划分"判对照组，被裁切读数判成 FAIL —— 同样是仪器上限，不是缺陷。
import { spawnSync } from 'node:child_process';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '../..');
const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const TOKEN = process.env.CONSOLE_TOKEN || '4659';
const BASE = process.env.CONSOLE_BASE || 'http://127.0.0.1:18080';
const OUT = path.join(ROOT, 'docs/device/snap');

const CHAT_OR_SETTINGS = /连得上|让它能做事|让它听懂我|让它答得好/;
const MARKER = '连得上';
const SMALL = [12, 24, 36, 48, 60, 72, 84, 96];
const WIDE = [...SMALL, 160, 240, 320, 400];
const DRAGS = process.env.DRAGS ? process.env.DRAGS.split(',').map(Number) : null;

const CASES = [
  // 吸附组：宽拖动范围，判"塌陷"（静止态少、且有多距离共用同一态）
  { legacy: 'BALLOON', name: 'M3_CARD', drags: WIDE, maxStates: 4, minGroup: 3, requireSamePartition: true },
  // 对照组：不吸附，判"连续"（每个距离一个态）
  { legacy: 'TERMINAL', name: 'TERMINAL', drags: SMALL, minHashKinds: 4 },
].map((c) => ({ ...c, drags: DRAGS || c.drags }))
  .filter((c) => !process.env.ONLY || process.env.ONLY.split(',').includes(c.name));

const adb = (...args) =>
  spawnSync(ADB, ['-s', DEVICE, ...args], { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
const sleep = (ms) =>
  spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

function dump() {
  adb('shell', 'rm', '-f', '/sdcard/snap.xml');
  const r = adb('shell', 'uiautomator', 'dump', '/sdcard/snap.xml');
  if (!/dumped to/i.test((r.stdout || '') + (r.stderr || ''))) return '';
  return adb('shell', 'cat', '/sdcard/snap.xml').stdout || '';
}

/** 读 `连得上` 节点的 bounds；读不到返回 null（绝不当 0 用）。前缀容忍：TERMINAL 是 `# 连得上`。 */
function markerBox(xml) {
  const m = xml.match(new RegExp(`text="[^"]*${MARKER}"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]`));
  if (!m) return null;
  const [x1, y1, x2, y2] = m.slice(1).map(Number);
  return { x1, y1, x2, y2, w: x2 - x1, h: y2 - y1 };
}

/** 读同名标签的 top y（用于量卡内行距，判断"非锚点项被缩放"）。 */
function textTop(xml, label) {
  const m = xml.match(new RegExp(`text="[^"]*${label}"[^>]*bounds="\\[\\d+,(\\d+)\\]`));
  return m ? Number(m[1]) : -1;
}

function wake() {
  adb('shell', 'su', '-c', 'svc power stayon true');
  adb('shell', 'input', 'keyevent', '224');
  sleep(600);
}

/** 每次试验都走同一条路径复位：force-stop -> 启动（聊天页）-> 上滑进设置页。 */
function resetToSettings() {
  adb('shell', 'am', 'force-stop', 'dev.ghr.wristagent');
  sleep(400);
  adb('shell', 'am', 'start', '-n',
    'dev.ghr.wristagent/com.example.myapplication.presentation.MainActivity');
  sleep(2600);
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

function shotHash(file) {
  const png = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], {
    cwd: ROOT, encoding: 'buffer', maxBuffer: 64 * 1024 * 1024, timeout: 120000,
  });
  fs.writeFileSync(file, png.stdout || Buffer.alloc(0));
  return crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex').slice(0, 12);
}

/** 卡内「模型 -> 温度」的行距（判断非锚点项是否被缩放：缩放会改变行距，平移不会）。 */
function filePitch(xml) {
  const a = textTop(xml, '模型');
  const b = textTop(xml, '温度');
  return a >= 0 && b >= 0 ? b - a : -1;
}

/** 慢拖：900ms 走 dy 像素（向上 = 内容上移）。 */
function slowDrag(dy) {
  const y0 = 300;
  adb('shell', 'input', 'swipe', '233', String(y0), '233', String(y0 - dy), '900');
}

fs.mkdirSync(OUT, { recursive: true });
wake();

/** 由"读数集合"给出的分组：同值即同组。用于交叉校验两种仪器是否给出同一划分。 */
const partition = (pairs) => {
  const groups = new Map();
  for (const [dy, v] of pairs) {
    const key = String(v);
    if (!groups.has(key)) groups.set(key, []);
    groups.get(key).push(dy);
  }
  return [...groups.values()].map((g) => g.sort((a, b) => a - b).join(','))
    .sort().join(' | ');
};

let allOk = true;
const report = [];
for (const c of CASES) {
  const { legacy, name, drags, maxStates, minGroup, minHashKinds, requireSamePartition } = c;
  const code = await setStyle(legacy);
  sleep(1100);
  const rows = [];
  for (const dy of drags) {
    if (!resetToSettings()) {
      console.log(`STOP: ${name} 进不去设置页`);
      process.exit(2);
    }
    const baseBox = markerBox(dump()); // 复位后的基准位（同一条路径，用于自检）
    slowDrag(dy);
    sleep(1300); // 远长于吸附回弹（spring 约 300~400ms）
    const xml = dump();
    const box = markerBox(xml);
    // 卡内行距：非锚点项被 SurfaceTransformation 朝中心平移 -> 视觉位移 != 布局位移
    const inCardPitch = filePitch(xml);
    const hash = shotHash(path.join(OUT, `${name}-${dy}.png`));
    rows.push({
      dy, baseY: baseBox ? baseBox.y1 : -1, y: box ? box.y1 : -1,
      h: box ? box.h : -1, inCardPitch, hash,
    });
    const r = rows.at(-1);
    console.log(`${name.padEnd(9)} post=${code} drag=${String(dy).padStart(3)}px `
      + `baseY=${r.baseY} restY=${r.y} h=${r.h} 卡内行距=${inCardPitch} hash=${hash}`);
  }
  const baseYs = new Set(rows.map((r) => r.baseY));
  const hashKinds = new Set(rows.map((r) => r.hash)).size;
  const hashGroups = partition(rows.map((r) => [r.dy, r.hash]));
  const hashSizes = [...new Set(rows.map((r) => r.hash))]
    .map((h) => rows.filter((r) => r.hash === h).length);
  const maxGroupSize = Math.max(...hashSizes);
  // y 只在 marker 未被滚出视野/未顶到屏幕外时可信：要求两次 reset 的 h 一致
  const visibleRows = rows.filter((r) => r.y >= 0);
  const yGroups = partition(visibleRows.map((r) => [r.dy, r.y]));
  const hashGroupsVisible = partition(visibleRows.map((r) => [r.dy, r.hash]));
  const samePartition = visibleRows.length === 0 ? null : yGroups === hashGroupsVisible;

  let ok = baseYs.size === 1; // 仪器自检：每次复位的画面/坐标必须一致
  if (minHashKinds != null) ok = ok && hashKinds >= minHashKinds;
  if (maxStates != null) ok = ok && hashKinds <= maxStates;
  if (minGroup != null) ok = ok && maxGroupSize >= minGroup;
  if (requireSamePartition) ok = ok && samePartition === true;

  allOk = allOk && ok;
  report.push(`${name.padEnd(9)} 拖动=[${drags.join(',')}] 复位基准Y=${[...baseYs].join('/')}`
    + ` 卡内行距=[${[...new Set(rows.map((r) => r.inCardPitch))].join(', ')}]`);
  report.push(`${name.padEnd(9)} 哈希静止态 ${hashKinds} 种 (${hashGroups})`
    + ` 最大共用距离数=${maxGroupSize}`);
  report.push(`${name.padEnd(9)} marker 可见 ${visibleRows.length}/${rows.length} 次`
    + ` dump分组=${yGroups} hash分组=${hashGroupsVisible} 同一划分=${samePartition}`);
  report.push(`${name.padEnd(9)} 判据 ${JSON.stringify({ maxStates, minGroup, minHashKinds, requireSamePartition })}`
    + ` => ${ok ? 'PASS' : 'FAIL'}`);
  report.slice(-4).forEach((l) => console.log(l));
}

fs.writeFileSync(path.join(OUT, 'report.txt'), report.join('\n') + '\n');
console.log(allOk ? 'SNAP_PROBE_OK' : 'SNAP_PROBE_FAIL');
process.exit(allOk ? 0 : 3);
