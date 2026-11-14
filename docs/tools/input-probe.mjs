// 页面输入响应探针：对**当前屏幕**依次注入 触摸滑动 / 表冠旋转，判断页面是否响应。
// 用法：node docs/tools/input-probe.mjs <label>
// 依赖：adb（路径见下）、已开启 adb forward 不需要；需要 root（sendevent 表冠）。
import { spawnSync } from "node:child_process";

const ADB = "E:\\apkkkk\\platform-tools\\adb.exe";
const SERIAL = "C101X35122973";
const label = process.argv[2] || "page";

const adb = (...args) => spawnSync(ADB, ["-s", SERIAL, ...args], { encoding: "utf8", timeout: 60000, maxBuffer: 8 * 1024 * 1024 }).stdout || "";
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const dumpTexts = () => {
  adb("shell", "uiautomator", "dump", "/sdcard/ui.xml");
  const xml = adb("shell", "cat", "/sdcard/ui.xml");
  return [...xml.matchAll(/text="([^"]+)"/g)].map((m) => m[1]).filter(Boolean);
};
const signature = (texts) => texts.slice(0, 6).join("|");
const crown = async (delta, count) => {
  const cmd = "su -c 'for i in $(seq 1 " + count + "); do sendevent /dev/input/event0 2 8 " + delta + "; sendevent /dev/input/event0 0 0 0; done'";
  adb("shell", cmd);
  await sleep(900);
};

const base = dumpTexts();
const rows = [];
// 触摸：向上滑一屏
adb("shell", "input", "swipe", "233", "340", "233", "150", "300");
await sleep(900);
const afterTouch = dumpTexts();
rows.push({ input: "触摸上滑", changed: signature(base) !== signature(afterTouch), after: signature(afterTouch).slice(0, 40) });
// 表冠：向反方向拨回来
await crown(5, 20);
const afterCrown = dumpTexts();
rows.push({ input: "表冠 +5x20", changed: signature(afterTouch) !== signature(afterCrown), after: signature(afterCrown).slice(0, 40) });

console.log("# " + label);
console.log("baseline: " + signature(base).slice(0, 46));
for (const r of rows) console.log("- " + r.input + ": " + (r.changed ? "响应" : "无变化") + " → " + r.after);
