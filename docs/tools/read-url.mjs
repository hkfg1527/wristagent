// 读取任意网页的可见文本：headless Edge + CDP（Runtime.evaluate）。
// 用法：node docs/tools/read-url.mjs <url> [maxChars]
// 为什么不用 curl：m3.material.io 这类站点是 JS 渲染的，curl 只能拿到 "requires JavaScript"。
import { spawn } from "node:child_process";
import net from "node:net";
import crypto from "node:crypto";

const EDGE = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const url = process.argv[2];
const maxChars = Number(process.argv[3] || 4000);
if (!url) { console.error("usage: node read-url.mjs <url> [maxChars]"); process.exit(2); }
const port = 9300 + Math.floor(Math.random() * 400);
const profile = "C:\\Users\\GHR\\AppData\\Local\\Temp\\readurl-" + port;

const child = spawn(EDGE, ["--headless=new", "--disable-gpu", "--no-first-run", "--remote-debugging-port=" + port,
  "--user-data-dir=" + profile, "--window-size=1280,1600", "about:blank"], { stdio: "ignore", windowsHide: true });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
async function httpJson(path) {
  const res = await fetch("http://127.0.0.1:" + port + path, { method: path.startsWith("/json/new") ? "PUT" : "GET" });
  return res.json();
}
let version = null;
for (let i = 0; i < 40 && !version; i++) {
  await sleep(400);
  try { version = await httpJson("/json/version"); } catch {}
}
if (!version) { child.kill(); console.error("CDP 起不来"); process.exit(1); }

let target;
try { target = await httpJson("/json/new?" + encodeURIComponent(url)); }
catch { const list = await httpJson("/json/list"); target = list.find((t) => t.type === "page"); }
if (!target) { child.kill(); console.error("没有 page target"); process.exit(1); }

const wsUrl = new URL(target.webSocketDebuggerUrl);
const key = crypto.randomBytes(16).toString("base64");
const text = await new Promise((resolve) => {
  const sock = net.connect(Number(wsUrl.port), wsUrl.hostname, () => {
    sock.write("GET " + wsUrl.pathname + " HTTP/1.1\r\nHost: " + wsUrl.host + "\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: " + key + "\r\nSec-WebSocket-Version: 13\r\n\r\n");
  });
  let handshook = false, head = Buffer.alloc(0), accum = "", done = false;
  const send = (obj) => {
    const payload = Buffer.from(JSON.stringify(obj));
    const mask = crypto.randomBytes(4);
    const masked = Buffer.from(payload);
    for (let i = 0; i < masked.length; i++) masked[i] ^= mask[i % 4];
    let header;
    if (payload.length < 126) header = Buffer.from([0x81, 0x80 | payload.length]);
    else if (payload.length < 65536) { header = Buffer.alloc(4); header[0] = 0x81; header[1] = 0xFE; header.writeUInt16BE(payload.length, 2); }
    else { header = Buffer.alloc(10); header[0] = 0x81; header[1] = 0xFF; header.writeBigUInt64BE(BigInt(payload.length), 2); }
    sock.write(Buffer.concat([header, mask, masked]));
  };
  const finish = (v) => { if (!done) { done = true; resolve(v); try { sock.end(); } catch {} } };
  const onPayload = (chunk) => {
    accum += chunk.toString("utf8");
    const i = accum.indexOf('{"id":1');
    if (i < 0) return;
    try {
      const j = JSON.parse(accum.slice(i, accum.lastIndexOf("}") + 1));
      const v = j?.result?.result?.value;
      if (typeof v === "string") finish(v);
    } catch {}
  };
  sock.on("data", (chunk) => {
    if (!handshook) {
      head = Buffer.concat([head, chunk]);
      const idx = head.indexOf("\r\n\r\n");
      if (idx < 0) return;
      handshook = true;
      const rest = head.slice(idx + 4); head = Buffer.alloc(0);
      setTimeout(() => send({ id: 1, method: "Runtime.evaluate", params: { expression: "document.body ? document.body.innerText : ''", returnByValue: true } }), 5000);
      if (rest.length) onPayload(rest);
      return;
    }
    onPayload(chunk);
  });
  sock.on("error", () => finish(""));
  setTimeout(() => finish(""), 25000);
});
try { child.kill(); } catch {}
const clean = (text || "").replace(/\n{3,}/g, "\n\n").trim();
console.log(clean.slice(0, maxChars) || "(no text)");
