# 交接（状态更新 2026-11-14 21:12，硬截止 11-15 05:00）

## 已发布（都可下载，APK 均在 GitHub release 里）

| 版本 | versionCode | 内容 |
|---|---|---|
| `v0.05-beta` | 32 | 官方 M3E Card + 边缘缩放 + 品牌识别/角色 + 17/17 二级页 hero |
| `v0.06-beta` | 33 | 卡片档改成一卡一个 lazy item、关掉 autoCentering、去掉重复的焦点块 |
| `v0.07-beta` | 34 | 性能：干掉每帧重画的 432×3113 图层（doFrame p90 52.7→20.8ms） |
| `v0.08-beta` | 35 | 品牌卡上标识与角色同框；中西混排副标题不再折行 |

## 这一段的硬证据

- 单测 **1186 条全绿**（`./gradlew :wear:testDebugUnitTest`）。
- 性能：`docs/PERF-0.05.md` —— 直方图 + `framestats` 相位拆解 + `atrace` 切片 + 修完复测，
  含 4 条**被实验推翻的推断**（谁写谁知道，别重蹈）。
- 视觉：`docs/device/v11/` 下 `percard-*`、`flatcard-zoom`、`walk-1..4`、`mark-02-modelconfig` 等实机截图。

## 下一步最值得做的三件

0. **默认气泡档的那笔竖向渐变**（最高性价比，需用户点头）：实测换成纯色后 70~85% 掉帧 → 39.6%、
   p50 26 → 16ms。改它就是改默认风格的像素，所以只写进 `docs/PERF-0.05.md`（含最小改法与验证步骤），没上线。

1. **预热着色器 / 量化图层 alpha**：剩下 >64ms 的帧被 `shader_compile`(29ms)、`cache_miss`(28ms)、
   `postAndWait`(88ms) 占掉，是图形管线一次性成本。要么启动时预热，要么把每帧变化的 alpha 步进量化。
2. ~~网页控制台鉴权后截图~~ **已解决**，配方记在这里：
   `msedge --headless=new --remote-debugging-port=<port> --user-data-dir=<临时目录> about:blank`
   → `PUT /json/new?<url>` 拿 `webSocketDebuggerUrl` → 自己发 WebSocket 帧
   `{"id":1,"method":"Page.captureScreenshot"}` → 响应里的 base64 存 PNG。
   **关键坑**：不要加 `--virtual-time-budget`（页面有轮询，虚拟时间永远走不完，headless 就不写文件）；
   `--screenshot=<path>` 只在 file:// 页面可靠，http 页面一律不产出。
   打出来的图在 `docs/device/v11/console-authed.png`（顶栏「令牌 有效 / 端口 8080」、网络与反馈真值、
   设置页风格选中"大卡片"）。
3. **brand art 的 48dp 打磨**：`CHARACTERS.md` 第 4 节列了每个角色在 48dp 的风险点与改法
   （deepseek 喷水点太小、openai 叶尖缺口只有 1 格、qwen 问号杆太细、ollama/llama 太像）。
   注意当前点阵渲染器所有点同半径，要做"小点"得先给渲染器加一种符号。

---


## 环境（压缩后必读）

- **没有 shell 工具**：`win_bash` 插件坏（bash 本体正常，其握手判它退出）。所有命令走
  `run_code` 里 `node:child_process` 的 `spawnSync`。
- gradle env：`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`、
  `GRADLE_USER_HOME=D:\gradle-home`、PATH = jbr\bin + `cmd /c echo %PATH%` 的原值。
- adb：`E:\apkkkk\platform-tools\adb.exe`；截图 `exec-out screencap -p` 拿 Buffer 写文件。
- 界面导航：`uiautomator dump` + `exec-out cat /sdcard/ui.xml`，解析 `text/bounds` 再点。
- GUI：`docs/tools/gui.ps1`（move/click/doubleclick/type/key/shot/size）。
  注意：桌面常被别的窗口盖住，`Start-Process <url>` 会把浏览器顶到前面；`WScript.Shell.AppActivate` 对本机会话无效。
- git push：`http.curloptResolve=github.com:443:140.82.114.4`（其它 IP 连不通）。

## 网页控制台（已验证部分）

- 表端「网页控制台」子页显示完整地址：`http://192.168.10.6:8080/?t=4659`（令牌 4659，明文只在该页）。
- `adb forward tcp:18080 tcp:8080` 后本机可访问：
  `/api/ping?t=4659` → `{"ok":true,"version":1,"app":"wristagent"}` ✅
  `/api/state?t=4659` → 返回完整 settings（含 modelName=deepseek-v4-pro、temperature=0.7…）✅
- 页面在浏览器里能渲染：侧栏 10 页（含新增「网络与反馈」）✅；
  **但"已鉴权后的真实数据态"截图还没拿到**（窗口被别的应用盖住，最后一次截图截到的是别的 App）。
  下一步：Start-Process 打开 → 等 6s → 立刻 gui.ps1 shot。

## 代码现状（main，最后一次推送 3da094a，之后只有文档提交 a2bea8f）

- **四套风格**：气泡（默认，像素不变）/ 方角 / 谷歌大卡片 / 终端。
- **大卡片档 = 官方 M3E 组件**：`androidx.wear.compose.material3.Card`；滚动用
  `edgeScale=0.84 / edgeAlpha=0.55` + `AutoCenteringParams()`；其余三套保持
  `edgeScale=1` + `autoCentering=null`（自绘圆弦需要平坦几何）。
- **hero 卡**：标签 / 大数值 / 信息行 + 左侧 DS 徽标，**没有环**（环曾压字，已删）。
  已接根页 4 张 + 子页 4 个（模型与服务、供应商、模型参数、语音）。
- 五项新设置：请求超时 / 自动重试 / 重试退避 / 仅 WiFi / 触觉反馈。
- 单测 **1171 例全绿**；0.04-beta 已发布，0.05 待发（versionCode 32）。

## 用户刚追加的三项产品方向（优先级）

1. **AI 品牌视觉**：按接口 provider/endpoint 识别并展示对应品牌图；自定义接口也要有统一好看的品牌视觉，
   不许出现难看占位图。
2. **拟人角色形象**：随当前品牌在设置页切换（例如 Google → 用户说的"哈吉米"那类）；角色图与品牌 logo
   必须是**两套独立资产**，不许混用。
3. **M3E 列表空间缩放**：按官方 M3 Expressive 语义做上下边缘项的空间缩放与衔接；
   **严禁横向拉伸，动画只允许缩小**。先弄清官方语义再动手。

## 不许破坏的规则

- 默认档 BALLOON 像素不变。
- 只有 GOOGLE_CARD 档开官方缩放与居中吸附。
- 行内文字保持左右两槽（`SettingsVisualGuardTest` 钉死）；方角档只居中组标题。
- 强调色只许 `Wear.Accent` / `Wear.Card*`；调色板上限 14 个 token。
