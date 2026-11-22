#!/usr/bin/env node
// 仪器自检：在信任任何测量之前先跑这一条。
//
// 三个坑都是我真机上踩过的，且每次踩都导致过**错误结论**：
//   1. dump 取不到内容（无限动画让界面永不 idle → uiautomator 报 could not get idle state）
//      却拿旧文件/空结果当"页面没变"，于是误判功能没生效；
//   2. `>` 在 dump 里被转义成 `&gt;`，用未转义的字符串匹配 → 明明有内容却判定"不存在"；
//   3. 用"顶部标题"（如 `连得上`）判断"是否还在某页面"，但执行命令后列表跟到底、标题滚出视野
//      → 把"滚到底"误判成"页面被关掉"（这个错我犯了两次）。
//
// 这个脚本不测产品功能，只测**仪器是否还可靠**：
//   A. 能取到 dump，且能取到已知标记；
//   B. `>` 的转义形式能匹配到内容，未转义形式匹配不到（证明"必须用转义"这条不是玄学）；
//   C. 存在一个**与滚动位置无关**的页面身份标记（滚到底后仍在），同时顶部标记会消失（证明为何不能用它）。
import { spawnSync } from 'node:child_process';

const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const TOKEN = process.env.CONSOLE_TOKEN || '4659';
const BASE = process.env.CONSOLE_BASE || 'http://127.0.0.1:18080';

const adb = (...args) => spawnSync(ADB, ['-s', DEVICE, ...args], { encoding: 'utf8', timeout: 120000 });
const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

let failures = 0;
const check = (name, ok, detail = '') => {
  console.log(`${ok ? '  ok  ' : ' FAIL '} ${name}${detail ? '  ' + detail : ''}`);
  if (!ok) failures++;
};

function dump(file) {
  adb('shell', 'rm', '-f', `/sdcard/${file}`);
  const r = adb('shell', 'uiautomator', 'dump', `/sdcard/${file}`);
  const out = (r.stdout || '') + (r.stderr || '');
  if (!/dumped to/i.test(out)) return { xml: '', dumped: false, why: out.trim().split('\n')[0] };
  return { xml: adb('shell', 'cat', `/sdcard/${file}`).stdout || '', dumped: true, why: '' };
}

async function setStyle(legacy) {
  const res = await fetch(`${BASE}/api/settings`, {
    method: 'POST',
    headers: { 'X-Console-Token': TOKEN, 'Content-Type': 'application/json; charset=utf-8' },
    body: JSON.stringify({ settingsStyle: legacy }),
  });
  return res.status;
}

/** 滚动无关的页面身份标记：底栏 `[ 风格 ]`（终端）或状态栏 `[n/4] NL`。 */
const SCROLL_INDEPENDENT = /\[ 风格 \]|\[\d\/\d\] \d+L/;
/** 顶部标记：滚到底就会消失，**不能**用来判断页面身份。 */
const TOP_ONLY = /连得上/;

(async () => {
  console.log('=== 仪器自检（不改产品代码，只验测量链）===');

  const code = await setStyle('TERMINAL');
  check('控制台可下发设置', code === 200, `POST status=${code}`);
  sleep(1600);

  // 进设置页
  let xml = '';
  for (let i = 0; i < 8; i++) {
    const d = dump('inst.xml');
    if (d.dumped && SCROLL_INDEPENDENT.test(d.xml)) { xml = d.xml; break; }
    adb('shell', 'input', 'swipe', '233', '380', '233', '180', '500');
    sleep(900);
  }

  // A. dump 可用 + 已知标记可读
  const a = dump('inst.xml');
  check('A1 dump 能取到内容', a.dumped && a.xml.length > 1000, `len=${a.xml.length} ${a.why}`);
  check('A2 能读到已知标记（状态栏或底栏）', SCROLL_INDEPENDENT.test(a.xml));
  xml = a.xml;

  // B. `>` 的转义形式
  const escaped = xml.includes('&gt; ');
  const raw = /text="> /.test(xml);
  check('B1 dump 里 `>` 是转义形式 `&gt;`', escaped);
  check('B2 未转义形式匹配不到（所以必须用转义）', !raw);

  // C. 页面身份标记必须与滚动位置无关
  //    先把列表滚回**顶部**再断言 C1 —— 否则上一轮跑完停在底部，C1 会假红（这一版就踩过）
  for (let k = 0; k < 26; k++) {
    adb('shell', 'input', 'keyevent', '24'); // 音量上 = 往回滚一行
    sleep(80);
  }
  sleep(1000);
  const top = dump('inst-top.xml');
  check('C1 滚回顶部后顶部标记存在', top.dumped && TOP_ONLY.test(top.xml));
  check('C1b 顶部态下滚动无关标记同样存在', SCROLL_INDEPENDENT.test(top.xml));

  for (let k = 0; k < 26; k++) {
    adb('shell', 'input', 'keyevent', '25'); // 音量下 = 往下滚一行（已接线）
    sleep(90);
  }
  sleep(1200);
  const b = dump('inst2.xml');
  check('C2 滚到底后 dump 仍可用', b.dumped && b.xml.length > 500, `len=${b.xml.length} ${b.why}`);
  check('C3 滚动无关标记**仍在**（这才是页面身份判据）', SCROLL_INDEPENDENT.test(b.xml));
  check('C4 顶部标记已消失（证明不能用它判页面）', !TOP_ONLY.test(b.xml));

  console.log(failures === 0 ? '\nINSTRUMENTS_OK' : `\nINSTRUMENTS_BROKEN (${failures} 项失败)`);
  process.exit(failures === 0 ? 0 : 3);
})();
