
import fs from 'node:fs';
import http from 'node:http';
const PORT = Number(process.env.CDP_PORT || 9334);
const TOKEN = process.env.CONSOLE_TOKEN || "";
const OUT = process.env.OUT_DIR || './';
const get = (p) => new Promise((res, rej) => { http.get({ host:'127.0.0.1', port:PORT, path:p }, r => { let d=''; r.on('data',c=>d+=c); r.on('end',()=>res(d)); }).on('error', rej); });
const pages = JSON.parse(await get('/json/list'));
const target = pages.find(p => p.type === 'page');
const ws = new WebSocket(target.webSocketDebuggerUrl);
let id = 0; const pending = new Map();
const send = (m, p) => new Promise(res => { const i = ++id; pending.set(i, res); ws.send(JSON.stringify({ id: i, method: m, params: p })); });
await new Promise((r, j) => { ws.onopen = r; ws.onerror = j; });
ws.onmessage = (ev) => { const m = JSON.parse(ev.data); if (m.id && pending.has(m.id)) { pending.get(m.id)(m.result); pending.delete(m.id); } };
if (!TOKEN) { console.error('CONSOLE_TOKEN missing'); process.exit(2); }
await send('Page.enable', {}); await send('Runtime.enable', {});
await send('Page.navigate', { url: 'http://127.0.0.1:8080/' });
await new Promise(r => setTimeout(r, 2500));
await send('Runtime.evaluate', { expression: 'localStorage.setItem("console_token", ' + JSON.stringify(TOKEN) + ')' });
await send('Page.navigate', { url: 'http://127.0.0.1:8080/' });
await new Promise(r => setTimeout(r, 4000));
for (const [w, h, name] of [[400, 780, 'watch'], [700, 900, 'phone'], [1280, 900, 'desktop']]) {
  await send('Emulation.setDeviceMetricsOverride', { width: w, height: h, deviceScaleFactor: 1, mobile: w <= 420 });
  await new Promise(r => setTimeout(r, 1400));
  const shot = await send('Page.captureScreenshot', { format: 'png' });
  fs.writeFileSync(OUT + 'console-' + name + '.png', Buffer.from(shot.data, 'base64'));
  const info = await send('Runtime.evaluate', { expression: 'document.title + " | body=" + document.body.scrollWidth + "x" + document.body.scrollHeight + " | tabs=" + document.querySelectorAll("[data-page]").length', returnByValue: true });
  console.log(name + ' ' + w + 'x' + h + ' -> ' + (info.result && info.result.value));
}
ws.close(); process.exit(0);
