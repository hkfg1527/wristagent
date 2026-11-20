#!/usr/bin/env node
// 连拍：补 P 轮要求的"滚动中 / 贴边态"证据。
//
// 背景：单帧截图只能证明静止状态；磁滑、边缘淡出、边缘缩放都发生在滚动过程中。
// 做法：先在后台用 su+sendevent 连续转表冠（制造滚动），同时前台连拍 N 帧。
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(import.meta.dirname, '../..');
const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const OUT_DIR = process.env.OUT_DIR || 'docs/device/p1';
const PREFIX = process.env.PREFIX || 'scroll';
const FRAMES = Number(process.env.FRAMES || 3);
const NOTCHES = Number(process.env.NOTCHES || 60);
const DIRECTION = process.env.DIRECTION === 'up' ? 5 : -5;

const adb = (...args) => spawnSync(ADB, ['-s', DEVICE, ...args], { cwd: ROOT, encoding: 'utf8', timeout: 120000 });
const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', `Start-Sleep -Milliseconds ${ms}`]);

const outDir = path.join(ROOT, OUT_DIR);
fs.mkdirSync(outDir, { recursive: true });

adb('shell', 'input', 'keyevent', '224');
sleep(800);

// 后台转表冠：不等待它结束，让前台能拍到滚动中的画面
const crown = spawn(ADB, [
  '-s', DEVICE, 'shell', 'su', '-c',
  `for i in $(seq 1 ${NOTCHES}); do sendevent /dev/input/event0 2 8 ${DIRECTION}; sendevent /dev/input/event0 0 0 0; done`
], { cwd: ROOT, stdio: 'ignore' });

for (let i = 1; i <= FRAMES; i++) {
  const file = path.join(outDir, `${PREFIX}-${i}.png`);
  const png = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], {
    cwd: ROOT, encoding: 'buffer', maxBuffer: 64 * 1024 * 1024, timeout: 120000
  });
  fs.writeFileSync(file, png.stdout || Buffer.alloc(0));
  console.log(`${PREFIX}-${i}.png ${fs.statSync(file).size}B`);
  sleep(220);
}
crown.kill();
