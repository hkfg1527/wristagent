#!/usr/bin/env node
// 判定"物理表冠走哪条通道"，一条命令出结论。
//
// 为什么需要它：真机上 ACTION_SCROLL 无法用 adb 注入（sendevent 的 REL_WHEEL 与 input roll 的
// trackball 事件两层日志都是 0），所以"表冠到底走哪条路"只能靠真人转一次。这个工具把"转一次"
// 变成一条可复现的命令：清日志 → 提示你转 → 读回来 → 判定。
//
// 三条通道（代码里都已接线，见 TerminalStyle / MainActivity）：
//   activity …  走 ACTION_SCROLL（onGenericMotionEvent）
//   key …       走音量键映射（onKeyDown VOLUME_UP/DOWN）
//   activate …  Enter（onKeyDown ENTER/DPAD_CENTER）
import { spawnSync } from 'node:child_process';

const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const TAG = 'TerminalRotary';
const WAIT_SECONDS = Number(process.env.WAIT || 20);

const adb = (...args) =>
  spawnSync(ADB, ['-s', DEVICE, ...args], { encoding: 'utf8', timeout: 120000 });
const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

const focus = () =>
  (adb('shell', 'dumpsys', 'window').stdout || '')
    .split('\n')
    .find((l) => l.includes('mCurrentFocus')) || '';

console.log('=== 表冠通道判定 ===');
console.log('前置条件：手表上打开 Wrist Agent → 进设置页 → 选「终端」风格（底栏应显示 [n/4] NL）。');
console.log(`现在请**转动表冠** ${WAIT_SECONDS} 秒（中途可以停，但别退出设置页）。`);

// 先确认前台是 App，否则转了也白转
const fg = focus();
if (!fg.includes('dev.ghr.wristagent')) {
  console.log(`\n⚠ 当前前台不是 App：${fg.trim() || '(读不到)'}`);
  console.log('  先把 App 切到前台再跑本工具。');
}

adb('logcat', '-c');
sleep(WAIT_SECONDS * 1000);
const log = (adb('logcat', '-d', '-s', TAG).stdout || '')
  .split('\n')
  .filter((l) => l.includes(TAG));

const activity = log.filter((l) => l.includes('activity delta='));
const key = log.filter((l) => l.includes('key delta='));
const activate = log.filter((l) => l.includes('activate'));

console.log(`\n捕获到 ${log.length} 条 TerminalRotary 日志：`);
[['activity', activity], ['key', key], ['activate', activate]].forEach(([name, rows]) => {
  console.log(`  ${name.padEnd(9)} ${rows.length} 条${rows.length ? '  e.g. ' + rows[0].split(TAG).pop().trim() : ''}`);
});

console.log('\n=== 结论 ===');
if (activity.length) {
  console.log('表冠走 **ACTION_SCROLL**（onGenericMotionEvent）。按行滚动已接线，无需改动。');
} else if (key.length) {
  console.log('表冠走 **音量键映射**（VOLUME_UP/DOWN）。按行滚动已接线（已实测一格 = 一行，位移 {-36, 0}）。');
} else if (activate.length) {
  console.log('只收到 Enter：说明你按了侧键/寄存器键，但表冠本身没产生滚动事件。');
} else {
  console.log('⚠ **两条通道都没有事件** —— 表冠在这个机型上可能是第三种映射。下一步：');
  console.log('  1) 看内核事件：  adb -s ' + DEVICE + " shell su -c 'getevent -l /dev/input/event0'");
  console.log('     然后转表冠，看是否出现 REL_WHEEL / ABS_WHEEL / KEY_VOLUME* 行；');
  console.log('  2) 若出现 KEY_VOLUMEUP/DOWN，说明系统把它当音量键，但没到我们这层（检查前台与阀门）；');
  console.log('  3) 若出现其它 KEY_* / ABS_*，把行贴给我，我加第三条通道。');
  process.exit(2);
}
