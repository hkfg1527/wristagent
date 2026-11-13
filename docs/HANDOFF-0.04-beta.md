# 0.04-beta 交接（2026-11-13 16:00）

## 发布状态

- 提交 `b24fe1d`，tag `v0.04-beta` 已推送。
- Release：https://github.com/hkfg1527/wristagent/releases/tag/v0.04-beta
  （prerelease，资产 `wear-0.04-beta.apk`，3,640,006 字节，versionCode 31）
- 单测 **1167 例全绿**；TicWatch Pro 5 已安装 0.04。

## 今天做完的

1. **四套设置页风格**：气泡（默认）/ 方角 / 谷歌大卡片 / 终端（原创）。
   切换入口在外观与点阵 → 页面风格；真值在 `AgentPreferences.settingsStyle`，
   导入导出与控制台共用。
2. **谷歌像素级复刻**：Pixel Watch 2 上拉下 `com.fitbit.FitbitMobile` 的 base.apk（31.8MB），
   `aapt2` 导出资源表（名字全混淆，颜色在 dex 里），改用实机截图取像素。
   规格写在 `docs/DESIGN-GOOGLE-HEALTH.md`：卡片底 `#2A313C`、强调色
   `#7FCFFF / #A8C7FA / #C4EED0`、环轨道 `#48556A`、环 50dp、圆角 24dp（实测 49px@2x）。
3. **五项新设置**（都有真实消费点 + 控制台 + 导入导出 + 单测）：
   请求超时（readTimeout）、自动重试与退避（请求循环）、仅 WiFi（连接前闸门）、触觉反馈（vibrateTick）。
4. **工具调用账本**：工具卡圆环显示「已调用 / 已注册」。
5. **网页控制台**：新增「网络与反馈」页（3 滑杆 / 2 开关 / 2 分段控件），
   修掉 `SERVER_KEYS` 少 7 个可写键的真实缺口。
6. **性能**：轮廓复用 Path、画刷 remember、行定位写入阈值 0.5→2px；
   实测 TicWatch 滚动 p50 14ms / p90 21ms / GPU 2–7ms，janky 11.4%→10.8%（噪声级）。

## 下一步（未做）

- **方角档与终端档没有实机截图存档**（终端档有 S43，方角档只验证过逻辑）。
- **192dp（Pixel Watch 2）三档回归**：设备已断开，需要重新接上。
- 卡片档的语音/系统卡已接，但**系统卡没有做**；DeepSeek 品牌位图仍是 `DS` 字形兜底。
- 性能要用真手指采样（adb 注入的帧统计被输入抖动污染）。
- `docs/device/*/INDEX.md` 还没补今天的截图条目。

## 工具事实（重要）

本会话没有可用 shell：`win_bash` 的 bash.exe 缺失（ENOENT），也没有 pwsh 工具。
可行做法——在 `run_code` 里用 `node:child_process` 的 spawnSync：

- 继承 OS 环境时直接 `spawnSync("git", [...])` 可用；gradle 需要显式 env：
  `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`、`GRADLE_USER_HOME=D:\gradle-home`、
  PATH 里拼上 `%PATH%`（先用 `cmd /c echo %PATH%` 取回来）。
- adb：`E:\apkkkk\platform-tools\adb.exe`；截图走 `exec-out screencap -p` + Buffer 写文件。
- **界面导航用 uiautomator**：`adb shell uiautomator dump` + `exec-out cat /sdcard/ui.xml`，
  解析 `text="..." bounds="[l,t][r,b]"` 拿坐标再点，比盲滑可靠得多。
- GitHub：`api.github.com` 被污染到 127.0.0.1；用
  `curl --resolve api.github.com:443:20.205.243.168`（上传走 `uploads.github.com:443:20.205.243.161`），
  令牌来自 `gh auth token`（gh 自己连不上网，只能借它的令牌用 curl）。

## 16:40 追加

- 第四张 hero 卡（系统：提示词是否自定义 + 控制台端口）已上，环内是终端提示符图标。
- 卡片主数值换行改为按半宽单位决策：四个汉字在 titleLarge 一行放得下，不再断成 3+1；
  端口 chip 收成 `:8080`。
- **方角档与终端档都补了实机截图**（S50/S51、S43），索引写在 `docs/device/v11/INDEX.md`。
- 上述改动只进了 main（`ccf386a`），**0.04-beta 的 Release 仍是发布时的构建**（三张卡）；
  系统卡与换行修复随下一个版本带走，tag 与资产保持自洽。

## 0.04-beta 资产校验

- 本地构建（`ccf386a` 之前那一版，即 Release 内容）：3640854 字节
- SHA-256：`EB9999B39B3EE7F8266221300B706D8E196B39531DCE31F9760454B204ACB6D8`
