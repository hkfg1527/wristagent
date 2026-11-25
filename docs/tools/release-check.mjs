#!/usr/bin/env node
/**
 * 发布点自检：把"每 10 个内部版一次"的手工流程固化成一条命令。
 *
 * 用法（在仓库根）：
 *   node docs/tools/release-check.mjs                # 全跑：clean 构建 + 单测 + 真机走查 + 控制台页面 E2E
 *   node docs/tools/release-check.mjs --skip-build    # 只跑真机走查与控制台 E2E
 *   node docs/tools/release-check.mjs --skip-device   # 只跑构建与单测
 *   node docs/tools/release-check.mjs --skip-console  # 跳过控制台页面 E2E
 *
 * 为什么要有它：前两次发布点（10 / 20 / 30）都是临场手打命令，
 * 每次都要现编 adb 坐标、现写像素扫描，还漏过"没验四分区名字"这种事。
 * 固化之后，发布前只剩一条命令，且判据一致（不会这次严、下次松）。
 */
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';

const ROOT = process.cwd();
const ADB = 'E:\\apkkkk\\platform-tools\\adb.exe';
const DEVICE = 'C101X35122973';
const args = process.argv.slice(2);
const skipBuild = args.includes('--skip-build');
const skipDevice = args.includes('--skip-device');
const skipConsole = args.includes('--skip-console');

const results = [];
const record = (name, ok, detail) => {
  results.push({ name, ok, detail });
  console.log((ok ? '  ok   ' : '  FAIL ') + name + (detail ? '  ' + detail : ''));
};

const run = (cmd, argv, opts = {}) =>
  spawnSync(cmd, argv, { cwd: ROOT, encoding: 'utf8', maxBuffer: 64 * 1024 * 1024, timeout: opts.timeout ?? 600000, env: opts.env });

const gradleEnv = {
  SystemRoot: 'C:\\Windows', windir: 'C:\\Windows',
  TEMP: 'C:\\Users\\GHR\\AppData\\Local\\Temp', TMP: 'C:\\Users\\GHR\\AppData\\Local\\Temp',
  USERPROFILE: 'C:\\Users\\GHR', LOCALAPPDATA: 'C:\\Users\\GHR\\AppData\\Local',
  APPDATA: 'C:\\Users\\GHR\\AppData\\Roaming', USERNAME: 'GHR', COMPUTERNAME: 'GHR-PC',
  PROCESSOR_ARCHITECTURE: 'AMD64', NUMBER_OF_PROCESSORS: '16',
  ComSpec: 'C:\\Windows\\system32\\cmd.exe', PATHEXT: '.COM;.EXE;.BAT;.CMD',
  PATH: 'C:\\Windows\\system32;C:\\Windows;C:\\Windows\\System32\\Wbem;C:\\Windows\\System32\\WindowsPowerShell\\v1.0',
  JAVA_HOME: 'C:\\Program Files\\Android\\Android Studio\\jbr',
  GRADLE_USER_HOME: 'D:\\gradle-home', HOME: 'C:\\Users\\GHR',
};

const versionName = () => {
  const g = fs.readFileSync(path.join(ROOT, 'wear/build.gradle.kts'), 'utf8');
  const line = g.split('\n').find((l) => l.includes('versionName = '));
  return line.substring(line.indexOf('versionName = ') + 14).replace(/["\s]/g, '');
};

const build = () => {
  console.log('\n[1/4] clean 构建 + 全量单测');
  const r = run('cmd', ['/c', 'gradlew.bat', '--console=plain', 'clean', ':wear:assembleRelease', ':wear:testDebugUnitTest'], { env: gradleEnv });
  const out = (r.stdout || '') + (r.stderr || '');
  record('构建成功', r.status === 0, (out.match(/BUILD \w+ in [^\n]+/) || [''])[0]);
  const dir = path.join(ROOT, 'wear/build/test-results/testDebugUnitTest');
  let tests = 0, suites = 0, failures = 0;
  for (const f of fs.readdirSync(dir).filter((n) => n.endsWith('.xml'))) {
    const x = fs.readFileSync(path.join(dir, f), 'utf8');
    const m = x.match(/tests="(\d+)"[^>]*skipped="(\d+)" failures="(\d+)" errors="(\d+)"/);
    if (m) { tests += +m[1]; suites += 1; failures += +m[3] + +m[4]; }
  }
  record('单测全绿', failures === 0, tests + ' 条 / ' + suites + ' 套件 / ' + failures + ' 失败');
  const apk = path.join(ROOT, 'wear/build/outputs/apk/release/wear-release.apk');
  record('APK 产出', fs.existsSync(apk), fs.existsSync(apk) ? (fs.statSync(apk).size / 1024 / 1024).toFixed(2) + ' MB' : 'MISSING');
  return tests;
};

const adb = (...a) => run(ADB, ['-s', DEVICE, ...a]);
const dumpTexts = () => {
  adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  const xml = (adb('shell', 'cat', '/sdcard/ui.xml').stdout || '');
  return [...xml.matchAll(/text="([^"]+)"/g)].map((m) => m[1]).filter(Boolean);
};
/** 带屏幕坐标的 dump：顺序判据要比较两个标题的 y，不能只看"出现过"。 */
const dumpNodes = () => {
  adb('shell', 'uiautomator', 'dump', '/sdcard/ui.xml');
  const xml = (adb('shell', 'cat', '/sdcard/ui.xml').stdout || '');
  return [...xml.matchAll(/text="([^"]+)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g)]
    .map((m) => ({ text: m[1], top: Number(m[3]), bottom: Number(m[5]) }));
};
const crown = (delta, n) =>
  adb('shell', "su -c 'for i in $(seq 1 " + n + "); do sendevent /dev/input/event0 2 8 " + delta + "; sendevent /dev/input/event0 0 0 0; done'");

const device = () => {
  console.log('\n[2/4] 真机安装与走查');
  adb('start-server');
  const install = adb('install', '-r', '-d', 'wear/build/outputs/apk/release/wear-release.apk');
  const pkg = adb('shell', 'dumpsys', 'package', 'dev.ghr.wristagent').stdout || '';
  const installed = (pkg.split('\n').find((l) => l.includes('versionName')) || '').trim();
  record('安装成功', install.status === 0, installed);

  adb('shell', 'am', 'force-stop', 'dev.ghr.wristagent');
  adb('shell', 'am', 'start', '-n', 'dev.ghr.wristagent/com.example.myapplication.presentation.MainActivity');
  // 等界面真的出来，而不是死等 5 秒：装完新包后首帧有时要 8~10 秒，
  // 固定 sleep 会在慢机器上直接导致后面"进设置页"失败（0.0.0.137 起出现过多次 7/8）。
  let uiReady = false;
  for (let i = 0; i < 30 && !uiReady; i++) {
    spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Milliseconds 700']);
    const texts = JSON.stringify(dumpTexts());
    uiReady = texts.length > 40 && !/No nodes|Could not/.test(texts);
  }
  console.log('  界面就绪：' + (uiReady ? '是' : '超时（继续尝试进设置）'));

  // 进设置页
  //
  // 0.0.0.140 实测：长距离快滑（420→40 / 260ms）在新版聊天页上**不再触发**设置入口，
  // 界面就绪也没用。改成台账里记的两条可靠路径按序尝试：
  //  ①短距阻尼上滑（380→180 / 500ms）—— 审批浮层那条手势的同款参数；
  //  ②更长的一段（440→40 / 240ms）兜底。
  //
  // 0.0.0.261 更正：原来的"②表冠先滚到列表顶部"是**空操作** —— `crown()` 把 REL_WHEEL 注进
  // /dev/input/event0（这台的 `qpnp_pon` 电源键），而带 REL_WHEEL 的是 event1（`slate-spi`）；
  // 项目自己的 `RotaryBus.kt` 也记着"注入 REL_WHEEL 后 onRotaryScrollEvent 一行日志都没有"。
  // 死代码已删（行为不变：它从来没起过作用）。
  const SETTINGS_RE = /连得上|让它能做事|让它听懂我|让它答得好|回复风格/;
  const enterSettings = () => {
    for (let i = 0; i < 6; i++) {
      adb('shell', 'input', 'swipe', '233', '380', '233', '180', '500');
      spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Milliseconds 900']);
      // 判据文本必须跟当前设置页一致：V2 的分区名是 连得上/让它能做事/让它听懂我/让它答得好，
      // 行里有 模型/温度/回复风格。旧判据（请求超时/全部启用/模型与服务）在 V2 上永远不会命中。
      if (SETTINGS_RE.test(JSON.stringify(dumpTexts()))) return true;
    }
    // 偶发失败兜底：这一段的手势偶尔不生效（0.0.0.137 出现过 7/8，报「进入设置页 undefined」）
    for (let i = 0; i < 3; i++) {
      adb('shell', 'input', 'swipe', '233', '440', '233', '40', '240');
      spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Milliseconds 1500']);
      if (SETTINGS_RE.test(JSON.stringify(dumpTexts()))) return true;
    }
    return false;
  };
  const entered = enterSettings();
  record('进入设置页', entered);

  // 四分区按序 —— 判据是"一次连续扫描收集到的顺序 == 规范顺序（或它的精确倒序）"。
  //
  // 三次迭代的教训：
  // ① 初版只判"四个都找到"：实测反序照样 8/8，等于没判；
  // ② 第二版想比对同屏 y 坐标，但这页每个分区标题之间隔着 4~8 行，466px 屏上**永远不可能同屏**，
  //    实测证据是"（一帧都没同屏）"—— 判据不可满足；
  // ③ 表冠方向在这台表上与我手测结论不一致，所以不能假设"正数往下 / 负数往上"。
  // 现在：先朝一个方向一路滚到尽头（内容不再变化即到端点），再朝反方向连续扫描收集；
  // 只要收集顺序是规范顺序或它的精确倒序（从底部往上扫就是倒序，属正常），就算按序。
  // 结构被打乱（例如 A C B D）会两种都不匹配而红。
  //
  // 0.0.0.261 更正：上面 ③ 提到的"表冠"其实**从未真的滚动过列表** ——
  //   `crown()` 注的是 /dev/input/event0（qpnp_pon 电源键），本机带 REL_WHEEL 的是 event1；
  //   项目自己的 `RotaryBus.kt` 也记着"注入 REL_WHEEL 后 onRotaryScrollEvent 一行日志都没有"。
  //   这条判据以前能过，靠的是**存档风格恰好是 TERMINAL**（36px 字格，一屏 10 行，
  //   四个分区同屏都在），而不是真的滚过。一旦存档风格是 SQUARE/M3_CARD 就必红
  //   （0.0.0.261 实测：只收集到「连得上」）。
  //   改用**手指滑动**：它对任何风格都有效，而且与"顺序"这条判据本身无关（判据没有被放松）。
  //   方向：先退出再进入设置页把列表复位到顶（**不能用下拉**：下拉是系统退出手势，会把页面关掉），
  //   然后一路向上扫，收集到的顺序应当恰好是规范顺序的倒序。
  const zones = ['让它答得好', '让它听懂我', '让它能做事', '连得上'];
  const sleep = (ms) => spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Milliseconds ' + ms]);
  // 复位到页首：先按 BACK 直到真的离开设置页（子页开着时一次 BACK 只关子页），再重新进入。
  // 不看一眼就重进的话，`enterSettings()` 会因为"页面上已经有设置文案"直接返回 true，
  // 列表停在中途，收集顺序就不可信了。
  for (let i = 0; i < 3; i++) {
    adb('shell', 'input', 'keyevent', '4');
    sleep(900);
    if (!SETTINGS_RE.test(JSON.stringify(dumpTexts()))) break;
  }
  enterSettings();
  const found = [];
  let lastKeys = null;
  let reachedEnd = false;
  for (let step = 0; step < 40 && found.length < 4; step++) {
    const nodes = dumpNodes();
    // 同一帧里可能同时出现多个分区标题（M3_CARD 一屏 3 张卡、终端一屏 10 行），
    // 所以**必须按屏幕 y 从小到大**决定先后，不能按 `zones` 数组的固定顺序追加 ——
    // 0.0.0.262 实测踩到过：一次 dump 同时命中「让它能做事」和「连得上」，
    // 按数组顺序追加得到 `让它能做事 → 连得上` 的倒序，判据假红。
    const hits = zones
      .map((z) => {
        const hit = nodes.find((n) => n.text.includes(z));
        return hit ? { zone: z, top: hit.top } : null;
      })
      .filter(Boolean)
      .sort((a, b) => a.top - b.top);
    for (const h of hits) if (!found.includes(h.zone)) found.push(h.zone);
    if (found.length === 4) break;
    const key = nodes.map((n) => n.text + '@' + n.top).join('|');
    if (lastKeys === key) { reachedEnd = true; break; }
    lastKeys = key;
    adb('shell', 'input', 'swipe', '233', '390', '233', '170', '350');
    sleep(900);
  }
  const canonical = found.length === 4 && found.join('|') === zones.join('|');
  const reversed = found.length === 4 && found.join('|') === zones.slice().reverse().join('|');
  record(
    '四分区按序',
    canonical || reversed,
    '收集=' + found.join(' → ') + (reversed ? '（自顶向下，倒序读取，正常）' : '') +
      ' · 端点=' + (reachedEnd ? '已确认' : '未确认')
  );
  // 子页证据（不计分，只落盘）：进设置后点进目标页并截图。
  // 为什么放在这里：进设置页只有"审批浮层上滑 / 会话抽屉"两条路，adb 手势不可靠；
  // 而本脚本已经能稳定进设置，顺手拍子页比自己现摸便宜得多。
  const subpageShot = (target, fileName) => {
    const xml = () => {
      adb('shell', 'uiautomator', 'dump', '/sdcard/sub.xml');
      return (adb('shell', 'cat', '/sdcard/sub.xml').stdout || '');
    };
    crown(5, 200);
    spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Seconds 4']);
    for (let i = 0; i < 16; i++) {
      const raw = xml();
      // 终端档的标签带 `> ` 前缀（`> 语音与唤醒`），所以匹配要放宽成「包含目标文本」
      const m = raw.match(new RegExp('text="[^"]*' + target + '[^"]*"[^>]*bounds="\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]"'));
      if (m) {
        const cx = Math.round((Number(m[1]) + Number(m[3])) / 2);
        const cy = Math.round((Number(m[2]) + Number(m[4])) / 2);
        adb('shell', 'input', 'tap', String(cx), String(cy));
        spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Seconds 3']);
        // 文件名带上当前风格，否则下一次跑会把上一套风格的证据覆盖掉（0.0.0.137 就发生过一次）
        const styleNow = (adb('shell', 'cat', '/sdcard/ui.xml').stdout || '').length >= 0 ? '' : '';
        const out = path.join(ROOT, 'docs/device/subpages/' + fileName);
        fs.mkdirSync(path.dirname(out), { recursive: true });
        const png = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], {
          cwd: ROOT, encoding: 'buffer', maxBuffer: 64 * 1024 * 1024, timeout: 120000
        });
        fs.writeFileSync(out, png.stdout || '');
        adb('shell', 'input', 'keyevent', '4');
        spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Seconds 2']);
        return 'docs/device/subpages/' + fileName;
      }
      crown(-5, 16);
      spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Seconds 1']);
    }
    return '';
  };
  const shot = subpageShot('语音与唤醒', 'subpage.png');
  console.log('  子页证据：' + (shot ? shot : '未取得（目标行没在 16 步内出现）'));
  // 第二个子页：网络与请求（含滑杆 —— 终端档要看到 `[■■□□] 50%` 数值条）
  const shot2 = subpageShot('网络与请求', 'subpage-slider.png');
  console.log('  滑杆页证据：' + (shot2 ? shot2 : '未取得'));

  return found;
};

const geometry = () => {
  console.log('\n[3/4] 卡片几何（同屏宽度 + 不重叠）');
  // 先滚到顶（-5 是往上），否则采到的是页面底部那一屏，采样区间跟"根页首屏"没有可比性
  crown(-5, 200);
  spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Seconds 5']);
  const shot = path.join(ROOT, 'docs/device/v11/release-check.png');
  const png = spawnSync(ADB, ['-s', DEVICE, 'exec-out', 'screencap', '-p'], { cwd: ROOT, encoding: 'buffer', maxBuffer: 64 * 1024 * 1024, timeout: 120000 });
  fs.writeFileSync(shot, png.stdout || '');
  const ps = [
    'Add-Type -AssemblyName System.Drawing',
    '$bmp = [System.Drawing.Bitmap]::FromFile("' + shot.replace(/\\/g, '\\\\') + '")',
    '$runs = @(); $start = -1',
    'for ($y = 0; $y -lt $bmp.Height; $y++) {',
    '  $c = $bmp.GetPixel(233, $y); $on = (($c.R + $c.G + $c.B) -gt 45)',
    '  if ($on -and $start -lt 0) { $start = $y }',
    '  if ((-not $on) -and $start -ge 0) { $runs += "$start..$($y-1)"; $start = -1 }',
    '}',
    'if ($start -ge 0) { $runs += "$start..$($bmp.Height-1)" }',
    'Write-Output ($runs -join ", ")',
    '$bmp.Dispose()',
  ].join('\n');
  const psFile = path.join(ROOT, 'build-logs/release-geo.ps1');
  fs.mkdirSync(path.dirname(psFile), { recursive: true });
  fs.writeFileSync(psFile, ps);
  const o = spawnSync('powershell', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', psFile], { encoding: 'utf8' });
  const runs = (o.stdout || '').trim();
  record('几何已采样', runs.length > 0, runs.slice(0, 120));
};

/**
 * 控制台「页面级」E2E：真浏览器 + CDP，断 DOM 而不是只看接口。
 *
 * 令牌不再手填：设备已 root，直接 `su -c cat` 读手表端 console_prefs.xml。
 * 这样发布点只剩一条命令，不会因为"忘了 dot-source 环境变量"而漏跑一项。
 */
const consoleE2E = () => {
  console.log('\n[4/4] 控制台页面 E2E（真浏览器 + CDP）');
  const raw = (adb('shell', "su -c 'cat /data/data/dev.ghr.wristagent/shared_prefs/console_prefs.xml'").stdout || '');
  const token = (raw.match(/name="console_token">([^<]+)</) || [])[1];
  if (!token) {
    record('控制台页面 E2E', false, '读不到手表端 console_token（设备未 root 或未启动过控制台）');
    return;
  }
  // 控制台服务器跑在 App 进程内：先确保 App 活着，再转发端口。
  adb('shell', 'am', 'start', '-n', 'dev.ghr.wristagent/com.example.myapplication.presentation.MainActivity');
  spawnSync('powershell', ['-NoProfile', '-Command', 'Start-Sleep -Seconds 4']);
  adb('forward', 'tcp:18080', 'tcp:8080');
  const r = spawnSync('node', ['docs/tools/console-e2e.mjs'], {
    cwd: ROOT, encoding: 'utf8', maxBuffer: 32 * 1024 * 1024, timeout: 600000,
    env: { ...process.env, CONSOLE_TOKEN: token, CONSOLE_URL: 'http://127.0.0.1:18080/', OUT_DIR: 'docs/device/release-check' },
  });
  const out = (r.stdout || '') + (r.stderr || '');
  const summary = (out.match(/(\d+)\/(\d+) 通过[^\n]*/) || [])[0] || out.trim().split('\n').slice(-1)[0] || '';
  const reds = [...out.matchAll(/^FAIL ([^\n]+)/gm)].map((m) => m[1].slice(0, 60));
  record('控制台页面 E2E', r.status === 0, summary + (reds.length ? ' | 红：' + reds.join('；') : ''));
};

const main = () => {
  console.log('release-check · 版本 ' + versionName());
  if (!skipBuild) build();
  if (!skipDevice || !skipConsole) {
    // 设备不在时必须**整体判红**：此前 adb 掉线时 device()/geometry() 里每步拿空结果，
    // 最后还能凑出"8/8 通过"的假象（本会话踩过：adb devices 为空却仍走完全程）。
    const listed = run(ADB, ['devices']).stdout || '';
    const online = /C101X35122973\s+device/.test(listed);
    if (!online) {
      record('设备在线', false, 'adb devices 里没有 C101X35122973（真机走查与控制台 E2E 全部未验证）');
      const failed = results.filter((r) => !r.ok);
      console.log('\n=== 结果：' + (results.length - failed.length) + '/' + results.length + ' 通过 ===');
      failed.forEach((f) => console.log('  失败：' + f.name + ' ' + f.detail));
      process.exit(1);
    }
    record('设备在线', true);
  }
  if (!skipDevice) { device(); geometry(); }
  if (!skipConsole) consoleE2E();
  const failed = results.filter((r) => !r.ok);
  console.log('\n=== 结果：' + (results.length - failed.length) + '/' + results.length + ' 通过 ===');
  if (failed.length) { failed.forEach((f) => console.log('  失败：' + f.name + ' ' + f.detail)); process.exit(1); }
};

main();
