# 交接（2026-11-14 17:45，硬截止 11-15 05:00）

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
