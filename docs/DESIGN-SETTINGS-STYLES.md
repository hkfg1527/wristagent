# 设置页风格预设（0.04-beta 设计稿）

## 目标

设置页外观可自切换，预留三套预设。切换入口挂在「外观与点阵」子页最上方，
默认档保持现状，老用户升级后像素不变。

## 三套预设

### A · 方角 M3E（`SQUARE`）

- 文字居中，行间用 1 物理像素横线分隔，不分卡片、不画曲面。
- 一切方形：返回键是方形轮廓键，行背景、选中态、开关底座都是直角。
- 滚动仍是 M3E 的 `ScalingLazyColumn`（edgeScale/edgeAlpha 保持现状）。
- 视觉职责：给"只想看字"的人一套最安静的界面。

### B · 谷歌大卡片（`GOOGLE_CARD`）

参照实机截图 `docs/device/pixelwatch2/H04_health_app.png`、`H05_health_cards.png`
（Google Health / Fitbit 表上首页）与 `H00_health_home.png`（Wear OS 系统设置）：

- 黑底，卡片是**大圆角矩形**（半径 32dp，接近系统设置的药丸感，但不做全圆）。
- 每张卡一个独立强调色：卡片标签用该色的低饱和版，大字与环用高饱和版。
- 卡片内部结构（自上而下）：标签（14sp）→ 主数值（30sp 粗体）→ 可选药丸 chip。
- **右侧圆形指示器**：44dp 环，4dp 轨道 + 6dp 进度，环内放图标（矢量/位图），
  状态只用环的填充比例 + 图标表达，不写"已启用/未配置"这类文字。
- 卡片间距 8dp，页首第一张即大卡片，不另加标题行。

卡片到数据的映射（首版）：

| 卡片 | 主数值 | 环 | 环内图标 | chip |
| --- | --- | --- | --- | --- |
| 模型与服务 | 当前模型名（如 `deepseek-v4-pro`） | 配置完成度（Key 已填 100%，未填 0%） | DeepSeek 图标位图 | 未配置时提示"去填 Key" |
| 工具与 MCP | 已启用工具数 | 已调用 / 已注册工具数 | 扳手 | 调用为 0 时提示 |
| 语音与唤醒 | 识别引擎名 | 就绪度（引擎 + Key 两段） | 麦克风 | 缺 Key 时提示 |
| 系统 / 提示词 | 预置名 | 自定义比例 | 文本图标 | — |

### C · 气泡（`BALLOON`，默认）

当前实现原样保留：整页一个气球、屏幕圆弦轮廓、内容等比缩小。

## 共用管道

1. `WearAppearance.style`：Compose 快照状态，全站唯一真值。
2. `AppearanceStore`：新键 `settings_style`，落 `wear_appearance` 偏好文件，跟着
   `AppearanceBootstrap` 一次灌入。
3. 配置导入导出：`SettingsTransferCodec` 增加 `settingsStyle` 条目。
4. 控制台：`ConsoleFields` / `ConsoleBridge` 增加 `settingsStyle` 枚举字段。
5. 设置页渲染按风格分支：气球组 → C；卡片列表 → B；横线列表 → A。
   子页同样分三种外壳，返回键随风格切换（M3E EdgeButton / 方形键）。

## 验收

- 三套风格都能在实机（TicWatch 233dp、Pixel Watch 2 192dp）与模拟器（240dp）切换，切换即时生效。
- 默认档截图像素与 0.03-beta 一致。
- 单测：风格枚举存取、非法值回落默认、导入导出往返、渲染分支选择。
- 截图存 `docs/device/v11/`、`docs/device/pixelwatch2/`、`docs/device/emulator/`。
