# 0.05 工作交接（2026-11-13 24:30，硬截止 11-14 05:00）

## 环境（最重要，先看这段）

- **没有 shell 工具**：`win_bash` 的 bash.exe 本体正常（直接 spawn 能跑出 5.3.15），
  是插件的握手逻辑判它「启动即退出」，别再修它。所有命令走
  `run_code` 里的 `node:child_process` `spawnSync`。
- gradle 需要显式 env：`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`、
  `GRADLE_USER_HOME=D:\gradle-home`、PATH 里拼回 `cmd /c echo %PATH%` 的结果。
- adb：`E:\apkkkk\platform-tools\adb.exe`；截图走 `exec-out screencap -p` + Buffer 写文件。
- 界面导航用 `uiautomator dump` + `exec-out cat /sdcard/ui.xml` 解析 `text/bounds`，
  比盲滑可靠；设置页可从根页按文本找入口。
- **GUI 控制**：`docs/tools/gui.ps1`（move / click / doubleclick / type / key / shot / size）已实测可用，
  下一步用它开浏览器验证网页控制台。
- git push：`http.curloptResolve=github.com:443:140.82.114.4`（112.4 和 121.3 连不通）。

## 已完成（都已推送，main 最新 3da094a）

- **0.04-beta 已发布**：tag `v0.04-beta`，资产 3,640,006 字节（三张 hero 卡的构建）。
- **四套风格**：气泡 / 方角 / 谷歌大卡片 / 终端；切换入口在外观与点阵，真值在 `AgentPreferences.settingsStyle`。
- **大卡片档已改用官方 M3E**：`androidx.wear.compose.material3.Card`，
  并且开回官方滚动行为（`edgeScale=0.84 / edgeAlpha=0.55` + `AutoCenteringParams`）。
  环状指示器已删除（它压在文字上），改成 标签 / 大数值 / 信息行 + 左侧 DS 缩写徽标。
- **子页 hero 卡**：已接 4 个（模型与服务、供应商、模型参数、语音）。
- **五项设置**：请求超时 / 自动重试 / 重试退避 / 仅 WiFi / 触觉反馈（都有真实消费点）。
- **网页控制台**：新增「网络与反馈」页，修掉 `SERVER_KEYS` 少 7 个可写键的缺口。
- 单测 **1171 例全绿**。

## 下一步（按顺序）

1. **网页控制台实机验证**：用 gui.ps1 开浏览器 → 打开 `http://127.0.0.1:8080/?t=<token>`，
   点一遍新页（三个滑杆 + 两个开关 + 两个分段控件），截图存 `docs/device/console/`。
   控制台 token 在表端「网页控制台」页显示。
2. **剩余 7 个子页 hero 卡**：工具与 MCP、安全策略、提示词、外观与点阵、远程控制台、网络体检、配置导入导出。
3. **聊天页流畅度 + 真性能测量**（Perfetto / 重组计数），替换掉不可信的 adb 帧统计。
4. **DeepSeek 品牌位图**（现在是 DS 缩写徽标，等素材或授权自绘）。
5. **发 0.05-beta**：versionCode 32 / versionName 0.05-beta，CHANGELOG 补 M3E 改造，出包 + Release。

## 风格规则（别破坏）

- 默认档 BALLOON 的像素不许变。
- 只有 GOOGLE_CARD 档开官方缩放与居中；其余三套必须 `edgeScale=1` + `autoCentering=null`。
- 行内文字仍是左右两槽（`SettingsVisualGuardTest` 钉死）；方角档只居中组标题。
- 强调色令牌只许 `Wear.Accent`/`Wear.Card*`；调色板上限 14 个 token。
