/**
 * 控制台「页面级」E2E：真浏览器（Edge headless）+ CDP，断 DOM 而不是只看接口。
 *
 * 为什么需要它：0.0.0.86 之前只验过接口层（curl /api/*）和手点截图，
 * 导航切换 / 搜索跳转 / 非法输入拦截 / 控件写回 / 三档皮肤 这些动作没有可复跑的判据。
 * 这个脚本把它们变成断言，退出码 0 = 全绿。
 *
 * 关键手段：Page.addScriptToEvaluateOnNewDocument 先把 window.fetch 包一层，
 * 记录每一笔 /api/settings POST。这样「没提交」「提交了什么」都是抓到的证据，不是推断。
 *
 * 前置：
 *   adb forward tcp:18080 tcp:8080
 *   $env:CONSOLE_TOKEN = '4659'   # 手表端 console_prefs.xml 里的 console_token
 * 运行：
 *   node docs/tools/console-e2e.mjs
 * 可选环境变量：CONSOLE_URL（默认 http://127.0.0.1:18080/）、OUT_DIR（默认 docs/device/v12）、
 *              CDP_PORT（默认 9336）、BROWSER（默认 Edge 路径）
 */
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';

const CDP_PORT = Number(process.env.CDP_PORT || 9336);
const BASE = process.env.CONSOLE_URL || 'http://127.0.0.1:18080/';
const TOKEN = process.env.CONSOLE_TOKEN || '';
const OUT = process.env.OUT_DIR || 'docs/device/v12';
const BROWSER = process.env.BROWSER || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe';

if (!TOKEN) { console.error('CONSOLE_TOKEN 缺失（手表端 console_prefs.xml 里的 console_token）'); process.exit(2); }
if (!fs.existsSync(BROWSER)) { console.error('找不到浏览器：' + BROWSER); process.exit(2); }
fs.mkdirSync(OUT, { recursive: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const checks = [];
let pageErrors = [];

function check(name, ok, detail) {
  checks.push({ name, ok: !!ok, detail: detail === undefined ? '' : String(detail).slice(0, 300) });
  console.log((ok ? 'PASS ' : 'FAIL ') + name + (detail === undefined ? '' : ' — ' + String(detail).slice(0, 160)));
}

function cdpHttp(pathname, method = 'GET') {
  return new Promise((resolve, reject) => {
    const req = http.request({ host: '127.0.0.1', port: CDP_PORT, path: pathname, method }, (res) => {
      let d = '';
      res.setEncoding('utf8');
      res.on('data', (c) => { d += c; });
      res.on('end', () => { try { resolve(JSON.parse(d)); } catch { resolve({ raw: d }); } });
    });
    req.on('error', reject);
    req.end();
  });
}

// 读路径只有 /api/state（/api/settings 只接受 POST，GET 会 405）
async function apiSettings() {
  const res = await fetch(new URL('/api/state', BASE), { headers: { 'X-Console-Token': TOKEN } });
  if (!res.ok) throw new Error('GET /api/state -> ' + res.status);
  const body = await res.json();
  return body && body.settings ? body.settings : body;
}

const profile = fs.mkdtempSync(path.join(os.tmpdir(), 'wa-console-e2e-'));
const child = spawn(BROWSER, [
  '--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  '--disable-extensions', '--hide-scrollbars',
  '--user-data-dir=' + profile,
  '--remote-debugging-port=' + CDP_PORT,
  '--window-size=1280,900',
  'about:blank'
], { stdio: 'ignore', windowsHide: true });

let ws = null;
let exitCode = 0;

function cleanup() {
  try { ws?.close(); } catch { /* 忽略 */ }
  try { child.kill(); } catch { /* 忽略 */ }
  setTimeout(() => { try { fs.rmSync(profile, { recursive: true, force: true }); } catch { /* 忽略 */ } }, 800);
}

try {
  // 1) 等 CDP 端点
  let version = null;
  const t0 = Date.now();
  while (Date.now() - t0 < 25000 && !version) {
    try { version = await cdpHttp('/json/version'); } catch { await sleep(300); }
  }
  if (!version || !version.webSocketDebuggerUrl) throw new Error('CDP 未在 25 秒内就绪');
  console.log('browser: ' + (version.Browser || '?'));

  const target = await cdpHttp('/json/new?about:blank', 'PUT');
  if (!target.webSocketDebuggerUrl) throw new Error('拿不到页面 target');

  ws = new WebSocket(target.webSocketDebuggerUrl);
  await new Promise((res, rej) => {
    ws.addEventListener('open', res, { once: true });
    ws.addEventListener('error', () => rej(new Error('CDP WebSocket 连接失败')), { once: true });
  });

  let msgId = 0;
  const pending = new Map();
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) { pending.get(m.id)(m); pending.delete(m.id); return; }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails || {};
      pageErrors.push('exception: ' + (d.exception?.description || d.text || ''));
    } else if (m.method === 'Runtime.consoleAPICalled' && m.params.type === 'error') {
      pageErrors.push('console.error: ' + (m.params.args || []).map((a) => a.value ?? a.description ?? a.type).join(' '));
    } else if (m.method === 'Log.entryAdded' && m.params.entry.level === 'error') {
      const e = m.params.entry;
      if (!/favicon/.test(e.url || '')) pageErrors.push('log: ' + e.text + ' @ ' + (e.url || ''));
    }
  });

  const send = (method, params = {}) => new Promise((resolve, reject) => {
    const id = ++msgId;
    const timer = setTimeout(() => { pending.delete(id); reject(new Error('CDP 超时: ' + method)); }, 30000);
    pending.set(id, (m) => {
      clearTimeout(timer);
      if (m.error) reject(new Error(method + ' -> ' + m.error.message));
      else resolve(m.result || {});
    });
    ws.send(JSON.stringify({ id, method, params }));
  });

  async function val(expression) {
    const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (r.exceptionDetails) throw new Error('页面 JS 异常: ' + (r.exceptionDetails.exception?.description || r.exceptionDetails.text));
    return r.result ? r.result.value : undefined;
  }
  const run = (body) => val('(async () => { ' + body + ' })()');

  async function waitFor(expression, ms = 10000) {
    const start = Date.now();
    let last;
    while (Date.now() - start < ms) {
      try { last = await val(expression); if (last) return last; } catch (e) { last = e.message; }
      await sleep(150);
    }
    return last;
  }

  async function goto(url, ready = "document.readyState === 'complete' && !!document.querySelector('#content .content-in')") {
    await send('Page.navigate', { url });
    await waitFor(ready);
    await sleep(600);
  }

  async function shot(name) {
    const r = await send('Page.captureScreenshot', { format: 'png' });
    fs.writeFileSync(path.join(OUT, name), Buffer.from(r.data, 'base64'));
  }

  const posts = () => val('window.__e2e ? window.__e2e.posts.length : -1');
  const resetPosts = () => run('window.__e2e.posts.length = 0; return true;');
  const postsJson = () => val('JSON.stringify(window.__e2e ? window.__e2e.posts : [])');

  await send('Page.enable');
  await send('Runtime.enable');
  await send('Log.enable');
  await send('Page.addScriptToEvaluateOnNewDocument', {
    source: `(() => {
      window.__e2e = { posts: [], log: [] };
      const orig = window.fetch;
      window.fetch = async (input, init) => {
        const url = typeof input === 'string' ? input : (input && input.url) || '';
        const method = ((init && init.method) || 'GET').toUpperCase();
        const rec = { url: url, method: method, body: init && init.body ? String(init.body) : '', status: 0, resp: '', t0: Math.round(performance.now()), ms: 0, aborted: false, err: '' };
        if (url.indexOf('/api/settings') >= 0 && method === 'POST') window.__e2e.posts.push(rec);
        if (url.indexOf('/api/') === 0 || url.indexOf('/api/') > 0) window.__e2e.log.push(rec);
        if (init && init.signal) {
          init.signal.addEventListener('abort', () => { rec.aborted = true; rec.abortedAt = Math.round(performance.now()); });
        }
        try {
          const res = await orig(input, init);
          rec.status = res.status;
          rec.ms = Math.round(performance.now()) - rec.t0;
          if (window.__e2e.posts.indexOf(rec) >= 0) { try { rec.resp = (await res.clone().text()).slice(0, 200); } catch (e) { rec.resp = 'clone-failed:' + e; } }
          return res;
        } catch (e) {
          rec.err = String(e);
          rec.ms = Math.round(performance.now()) - rec.t0;
          throw e;
        }
      };
    })();`
  });
  await send('Emulation.setDeviceMetricsOverride', { width: 1280, height: 900, deviceScaleFactor: 1, mobile: false });

  // ---------- A. 认证闸门 ----------
  await goto(BASE);
  await run("localStorage.removeItem('console_token'); return true;");
  await goto(BASE);
  const noTok = await waitFor("/缺失|无效/.test((document.getElementById('tbToken')||{}).textContent||'')");
  check('A1 无令牌：顶栏显示「令牌 缺失/无效」', !!noTok, await val("(document.getElementById('tbToken')||{}).textContent"));
  check('A2 无令牌时后端确实回 401（页面记录的 log 里有）', pageErrors.some((e) => /401/.test(e)), pageErrors.length + ' 条页面错误');
  pageErrors = []; // 上面的 401 是刻意制造的，之后的错误一律算红

  await run('localStorage.setItem("console_token", ' + JSON.stringify(TOKEN) + '); return true;');
  await goto(BASE);
  const okTok = await waitFor("/^令牌 有效$/.test((document.getElementById('tbToken')||{}).textContent||'')");
  check('A3 有令牌：顶栏显示「令牌 有效」', !!okTok, await val("(document.getElementById('tbToken')||{}).textContent"));

  const top = await val(`(() => {
    const g = (id) => (document.getElementById(id) || {}).textContent || '';
    return { device: g('tbDevice'), backend: g('tbBackend'), port: g('tbPort'), state: (document.getElementById('status')||{dataset:{}}).dataset.state, note: g('sidebarNote') };
  })()`);
  check('A4 顶栏读到真实设备名', top.device && top.device !== '当前设备' && top.device.length > 0, top.device);
  check('A5 顶栏读到底层后端', /^(ROOT|SHIZUKU|DIRECT)$/.test(top.backend), top.backend);
  check('A6 顶栏读到端口', /端口 \d+/.test(top.port), top.port);
  check('A7 连接状态灯 = ok', top.state === 'ok', top.state);

  if (!process.env.E2E_SKIP) {
  // ---------- B. 令牌弹层 ----------
  await run("document.getElementById('tbToken').click(); return true;");
  const sheetOpen = await waitFor('document.getElementById("sheet") && !document.getElementById("sheet").hidden');
  check('B1 点「令牌」打开弹层', !!sheetOpen);
  const sheetVal = await val("(document.getElementById('sheetInput')||{}).value");
  check('B2 弹层里令牌已填', !!sheetVal, sheetVal ? '长度 ' + String(sheetVal).length : '空');
  await run("document.getElementById('sheetCancel').click(); return true;");
  const sheetClosed = await waitFor('document.getElementById("sheet") && document.getElementById("sheet").hidden');
  check('B3 取消后弹层收起', !!sheetClosed);

  // ---------- C. 十个页面逐个切换 ----------
  const PAGES = [
    ['model', '模型与连接'], ['provider', '供应商与网络'], ['role', '角色与提示词'],
    ['generate', '生成参数'], ['dialog', '对话与安全'], ['voice', '语音'],
    ['tools', '工具能力'], ['interface', 'MCP 与界面'], ['device', '设备与控制台'],
    ['network', '网络与反馈']
  ];
  for (const [id, title] of PAGES) {
    await run("document.querySelector('.nav-item[data-page=\"" + id + "\"]').click(); return true;");
    const got = await waitFor(`(() => {
      const c = document.getElementById('content');
      if (!c || c.dataset.page !== ${JSON.stringify(id)}) return false;
      const t = c.querySelector('.page-title');
      return t && t.textContent.trim() === ${JSON.stringify(title)} ? {
        page: c.dataset.page, title: t.textContent.trim(), rows: c.querySelectorAll('.row').length,
        groups: c.querySelectorAll('.group').length, docTitle: document.title,
        active: (document.querySelector('.nav-item.is-active')||{}).dataset ? document.querySelector('.nav-item.is-active').dataset.page : ''
      } : false;
    })()`);
    check('C ' + id + ' 打开且标题 = ' + title, !!got && got.rows > 0, got ? ('rows=' + got.rows + ' groups=' + got.groups) : '拿不到页面');
    if (got) {
      check('C ' + id + ' document.title 同步', got.docTitle.indexOf(title) === 0, got.docTitle);
      check('C ' + id + ' 侧栏高亮跟随', got.active === id, got.active);
    }
  }

  // ---------- D. 搜索 ----------
  await run("document.getElementById('searchOpen').click(); return true;");
  const overlay = await waitFor('document.getElementById("overlay") && !document.getElementById("overlay").hidden');
  check('D1 搜索层打开', !!overlay);
  await run(`(() => {
    const i = document.getElementById('searchInput');
    i.value = '温度';
    i.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  const hits = await waitFor("document.querySelectorAll('#searchResults .sr-item').length");
  const firstHit = await val("(() => { const a = document.querySelector('#searchResults .sr-item'); return a ? a.textContent : ''; })()");
  check('D2 搜「温度」有结果且首条命中温度', hits > 0 && /温度/.test(firstHit), 'hits=' + hits + ' / ' + String(firstHit).slice(0, 60));

  await run("document.querySelector('#searchResults .sr-item').click(); return true;");
  const jumped = await waitFor(`(() => {
    const c = document.getElementById('content');
    if (!c || c.dataset.page !== 'generate') return false;
    const row = c.querySelector('.row[data-id="temperature"]');
    return row ? { flash: row.classList.contains('is-flash'), overlayHidden: document.getElementById('overlay').hidden } : false;
  })()`);
  check('D3 点结果跳到「生成参数」并高亮温度行', !!jumped && jumped.flash && jumped.overlayHidden, jumped ? JSON.stringify(jumped) : '未跳转');

  await run("document.getElementById('searchOpen').click(); return true;");
  await run(`(() => {
    const i = document.getElementById('searchInput');
    i.value = 'zzzz-no-hit';
    i.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  const empty = await waitFor("document.querySelectorAll('#searchResults .sr-empty').length");
  check('D4 无命中显示空态', !!empty);
  await run("document.getElementById('searchClose').click(); return true;");
  const closed = await waitFor('document.getElementById("overlay").hidden');
  check('D5 关闭搜索层', !!closed);

  // ---------- E. 非法 JSON 拦截（customHeaders 是本地草稿键，不该发请求） ----------
  await run("document.querySelector('.nav-item[data-page=\"provider\"]').click(); return true;");
  await waitFor("document.querySelector('.row[data-id=\"customHeaders\"] textarea')");
  await resetPosts();
  await run(`(() => {
    const ta = document.querySelector('.row[data-id="customHeaders"] textarea');
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, '{');
    ta.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  await sleep(900);
  const badState = await val(`(() => {
    const s = document.querySelector('.row[data-id="customHeaders"] .json-state');
    return s ? { text: s.textContent, tone: s.dataset.tone || '' } : null;
  })()`);
  check('E1 非法 JSON：页内提示不合法', !!badState && /不合法/.test(badState.text) && badState.tone === 'bad', badState ? badState.text : '没有 .json-state');
  check('E2 非法 JSON：一笔请求都没发', (await posts()) === 0, 'posts=' + (await posts()));

  await run(`(() => {
    const ta = document.querySelector('.row[data-id="customHeaders"] textarea');
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, '{"X-E2E":"1"}');
    ta.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  await sleep(900);
  const goodState = await val(`(() => {
    const s = document.querySelector('.row[data-id="customHeaders"] .json-state');
    const banner = (document.getElementById('banners') || {}).textContent || '';
    return { text: s ? s.textContent : '', tone: s ? (s.dataset.tone || '') : '', banner: banner.slice(0, 120), posts: window.__e2e.posts.length };
  })()`);
  check('E3 合法 JSON：页内回「已记录 1 个请求头」', /已记录 1 个请求头/.test(goodState.text) && goodState.tone === 'ok', goodState.text);
  check('E4 合法 JSON：仍不发请求（本地草稿键）', goodState.posts === 0, 'posts=' + goodState.posts);
  check('E5 合法 JSON：出现「只保存在本地」提示条', /只保存在本地/.test(goodState.banner), goodState.banner);

  await run(`(() => {
    const ta = document.querySelector('.row[data-id="customHeaders"] textarea');
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, '');
    ta.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  await sleep(600);

  }

  // ---------- F. 服务端键的写回（customSystemPrompt 是文本域，安全可还原） ----------
  // 手表侧写一笔要 1~2 秒，固定 sleep 会读到旧值；这里一律轮询到值真的变。
  async function waitSettings(predicate, ms = 9000) {
    const start = Date.now();
    let last = null;
    while (Date.now() - start < ms) {
      try { last = await apiSettings(); if (predicate(last)) return last; } catch { /* 断流重试 */ }
      await sleep(300);
    }
    return last;
  }
  async function waitPostsSettled(ms = 9000) {
    const start = Date.now();
    let list = [];
    while (Date.now() - start < ms) {
      list = JSON.parse(await postsJson());
      if (list.length && list.every((p) => p.status !== 0)) return list;
      await sleep(200);
    }
    return list;
  }

  const before = await apiSettings();
  const originPrompt = typeof before.customSystemPrompt === 'string' ? before.customSystemPrompt : '';
  await run("document.querySelector('.nav-item[data-page=\"generate\"]').click(); return true;");
  await waitFor("document.querySelector('.row[data-id=\"customSystemPrompt\"] textarea')");
  await resetPosts();
  // 中文必须原样往返：charset 丢了就会变成一串 U+FFFD（0.0.0.87 修的真缺陷）
  const probeText = 'E2E 中文探针-' + Date.now().toString(36);
  await run(`(() => {
    const ta = document.querySelector('.row[data-id="customSystemPrompt"] textarea');
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, ${JSON.stringify(probeText)});
    ta.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  const afterPrompt = await waitSettings((s) => s.customSystemPrompt === probeText);
  const posted = await waitPostsSettled();
  const lastPost = posted.length ? posted[posted.length - 1] : null;
  const apiLog = JSON.parse(await val('JSON.stringify((window.__e2e.log || []).slice(-14))'));
  check('F1 文本域改动发出带 customSystemPrompt 的 POST', !!lastPost && /customSystemPrompt/.test(lastPost.body), posted.length + ' 笔 / ' + (lastPost ? lastPost.body.slice(0, 60) : ''));
  check('F2 POST 全部返回 200', posted.length >= 1 && posted.every((p) => p.status === 200), posted.map((p) => p.status + '/' + p.ms + 'ms' + (p.aborted ? '/aborted' : '') + (p.err ? '/' + p.err.slice(0, 40) : '')).join(',') + ' || 最近 API：' + apiLog.map((e) => e.url + ' ' + e.status + ' ' + e.ms + 'ms' + (e.aborted ? '/aborted' : '') + (e.err ? '/' + e.err.slice(0, 30) : '')).join(' | '));
  check('F3 中文原样写回手表（没有 U+FFFD）', !!afterPrompt && afterPrompt.customSystemPrompt === probeText, JSON.stringify(afterPrompt && afterPrompt.customSystemPrompt));
  const toastText = await val("(document.getElementById('toastText')||{}).textContent");
  check('F4 出现「已保存」提示', /已保存/.test(toastText || ''), toastText);

  await run(`(() => {
    const ta = document.querySelector('.row[data-id="customSystemPrompt"] textarea');
    Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set.call(ta, ${JSON.stringify(originPrompt)});
    ta.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  const restoredPrompt = await waitSettings((s) => s.customSystemPrompt === originPrompt);
  check('F5 还原自定义系统提示词', !!restoredPrompt && restoredPrompt.customSystemPrompt === originPrompt, JSON.stringify(restoredPrompt && restoredPrompt.customSystemPrompt));

  // ---------- G. 滑杆写回 ----------
  const beforeTemp = Number((await apiSettings()).temperature);
  await resetPosts();
  const wantedTemp = beforeTemp >= 1.0 ? 0.3 : 1.2;
  // run() 自己会包一层 async 箭头函数，所以这里必须用 val() 直接求值这个同步 IIFE，否则拿到的是 undefined
  const pill = await val(`(() => {
    const row = document.querySelector('.row[data-id="temperature"]');
    const inp = row && row.querySelector('input.slider');
    if (!inp) return { error: 'no slider', rowFound: !!row };
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(inp, '${wantedTemp}');
    inp.dispatchEvent(new Event('input', { bubbles: true }));
    const p = row.querySelector('.value-pill');
    return { value: inp.value, pill: p ? p.textContent : null, pillCount: row.querySelectorAll('.value-pill').length, rowText: row.textContent.slice(0, 120) };
  })()`);
  const afterTemp = await waitSettings((s) => Math.abs(Number(s.temperature) - wantedTemp) < 0.001);
  const sliderPosts = await waitPostsSettled();
  const lastSliderPost = sliderPosts.length ? sliderPosts[sliderPosts.length - 1] : null;
  check('G1 滑杆拖动改了数字胶囊', !!pill && pill.pill === wantedTemp.toFixed(1), 'target=' + wantedTemp + ' ' + JSON.stringify(pill));
  check('G2 滑杆改动发出带 temperature 的 POST', !!lastSliderPost && /"temperature"/.test(lastSliderPost.body), sliderPosts.length + ' 笔 / ' + (lastSliderPost ? lastSliderPost.body : ''));
  check('G3 手表侧读回滑杆值 ' + wantedTemp, !!afterTemp && Math.abs(Number(afterTemp.temperature) - wantedTemp) < 0.001, 'server=' + (afterTemp && afterTemp.temperature));

  await run(`(() => {
    const inp = document.querySelector('.row[data-id="temperature"] input.slider');
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(inp, '${beforeTemp}');
    inp.dispatchEvent(new Event('input', { bubbles: true }));
    return true;
  })()`);
  const tempBack = await waitSettings((s) => Math.abs(Number(s.temperature) - beforeTemp) < 0.001);
  check('G4 还原温度', !!tempBack && Math.abs(Number(tempBack.temperature) - beforeTemp) < 0.001, 'server=' + (tempBack && tempBack.temperature));

  // ---------- H. 三档皮肤（按视口宽度挂 data-skin；侧栏只在 >900px 出现） ----------
  const visible = (id) => `document.getElementById('${id}') && document.getElementById('${id}').getClientRects().length > 0`;
  for (const [w, h, skin, name] of [[400, 780, 'watch', 'console-e2e-watch.png'], [700, 900, 'phone', 'console-e2e-phone.png'], [1280, 900, 'desktop', 'console-e2e-desktop.png']]) {
    await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: w <= 420 });
    await sleep(900);
    const info = await val(`(() => ({
      skin: document.documentElement.getAttribute('data-skin'),
      innerW: window.innerWidth,
      scrollW: document.documentElement.scrollWidth,
      navShown: !!document.getElementById('nav') && document.getElementById('nav').getClientRects().length > 0,
      tabsShown: !!document.getElementById('tabs') && document.getElementById('tabs').getClientRects().length > 0
    }))()`);
    check('H ' + w + 'px → ' + skin + ' 皮肤', info.skin === skin, info.skin + ' innerW=' + info.innerW);
    check('H ' + w + 'px 无横向溢出', info.scrollW <= info.innerW + 1, 'scrollW=' + info.scrollW + ' innerW=' + info.innerW);
    if (skin === 'watch' || skin === 'phone') check('H ' + skin + ' 用顶部标签条，不显示侧栏', info.tabsShown && !info.navShown, 'nav=' + info.navShown + ' tabs=' + info.tabsShown);
    else check('H 电脑皮肤用左侧栏，收起标签条', info.navShown && !info.tabsShown, 'nav=' + info.navShown + ' tabs=' + info.tabsShown);
    await shot(name);
  }

  // ---------- J. 控制台 action 契约（网页按钮 ↔ 服务端 action） ----------
  // 曾经「测试连接」按钮打的是 connection-test，而服务端 ACTION_NAMES 里没有这个名字 → 恒 404。
  // 这类缺陷两边各自自洽，只有真点一次才暴露。
  await run("document.querySelector('.nav-item[data-page=\"provider\"]').click(); return true;");
  const actBtn = await waitFor("!!document.querySelector('button[data-action=\"connection-test\"]')");
  check('J1 供应商页有「测试连接」按钮', !!actBtn);
  await run("document.querySelector('button[data-action=\"connection-test\"]').click(); return true;");
  const act = await waitFor(
    `(() => {
      const btn = document.querySelector('button[data-action="connection-test"]');
      const box = btn && btn.closest('.row-control') && btn.closest('.row-control').querySelector('.action-result');
      if (!box) return false;
      const tone = box.dataset.tone || '';
      if (!tone || tone === 'running' || tone === 'idle') return false;
      return { tone: tone, text: (box.textContent || '').slice(0, 140) };
    })()`,
    45000
  );
  check('J2 「测试连接」不再是 404', !!act && !/not_found/.test(act.text || ''), act ? JSON.stringify(act) : '拿不到结果');
  check('J3 「测试连接」给出明确结论', !!act && /连接成功|连接失败/.test(act.text || ''), act ? act.text : '拿不到结果');

  check('I 页面零 JS 异常', pageErrors.length === 0, pageErrors.slice(0, 3).join(' | '));
} catch (err) {
  check('RUN 执行完成', false, err && err.message ? err.message : String(err));
  exitCode = 1;
} finally {
  const failed = checks.filter((c) => !c.ok);
  if (failed.length) exitCode = 1;
  const pass = checks.length - failed.length;
  console.log('\n' + pass + '/' + checks.length + ' 通过' + (failed.length ? '，红：' + failed.map((c) => c.name).join('；') : ''));
  try {
    fs.mkdirSync('build-logs', { recursive: true });
    fs.writeFileSync('build-logs/console-e2e.json', JSON.stringify({ at: new Date().toISOString(), base: BASE, pass, total: checks.length, checks, pageErrors }, null, 2));
  } catch { /* 忽略 */ }
  cleanup();
  await sleep(300);
  process.exit(exitCode);
}
