#!/usr/bin/env node
/**
 * 门禁工具自检：验证"设备不在时不会假绿"。
 *
 * 事故：手表 adb 掉线后，release-check 里 device()/geometry() 每步拿空结果，
 * 却能凑出"8/8 通过"；style-shots 打印 INVALID 却返回 0。
 * 这条自检锁住两条：①设备缺失时 release-check 必须判红且退出码非 0；
 * ②style-shots-v2 在 distinct<5 时必须退出码非 0。
 *
 * 只在设备**确实不在**时跑；设备在线时会跳过（避免把真机走查当成断言对象）。
 */
import { spawnSync } from 'node:child_process';

const ADB = process.env.ADB || 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = process.env.DEVICE || 'C101X35122973';
const ROOT = process.cwd();

const adbOut = (spawnSync(ADB, ['devices'], { encoding: 'utf8' }).stdout || '');
const online = new RegExp(DEVICE + '\\s+device').test(adbOut);
if (online) {
  console.log('GATE_SELFTEST_SKIP device online（跳过自检，避免干扰真机走查）');
  process.exit(0);
}

const run = (script, args = []) => spawnSync('node', [script, ...args], {
  cwd: ROOT, encoding: 'utf8', timeout: 900000,
  env: { ...process.env },
});

let failures = 0;
const check = (name, ok, detail) => {
  console.log(`${ok ? '  ok  ' : ' FAIL '} ${name}${detail ? '  ' + detail : ''}`);
  if (!ok) failures++;
};

// --skip-build --skip-console：只验"设备缺失时判红"，不重跑 4 分钟 clean 构建。
const rel = run('docs/tools/release-check.mjs', ['--skip-build', '--skip-console']);
const relOut = (rel.stdout || '') + (rel.stderr || '');
check(
  'release-check 在设备缺失时判红',
  rel.status !== 0 && /FAIL\s+设备在线/.test(relOut),
  `exit=${rel.status}`,
);

const shots = run('docs/tools/style-shots-v2.mjs');
const shotsOut = (shots.stdout || '') + (shots.stderr || '');
// 只查"非零退出"是空洞断言：设备缺失时脚本在更早的 STOP 分支就 exit=2 了，
// 根本走不到末尾 distinct<5 的判红。必须同时看到 distinct 判据本身。
const reportedInvalid = /distinct=\d\/5\s+INVALID/.test(shotsOut);
const stoppedEarly = /STOP:/.test(shotsOut);
check(
  'style-shots-v2 在设备缺失时必须判红而非静默退出',
  shots.status === 1 && reportedInvalid,
  `exit=${shots.status} invalid=${reportedInvalid} stoppedEarly=${stoppedEarly}`,
);

console.log(failures === 0 ? '\nGATE_SELFTEST_OK' : `\nGATE_SELFTEST_BROKEN (${failures})`);
process.exit(failures === 0 ? 0 : 3);
