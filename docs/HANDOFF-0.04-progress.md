# 0.04-beta 进度（2026-11-13 01:05）

目标 `goal-41e6a420-ce55-410c-b03d-23fbeeba1774`，截止 05:00 持续工作。
设计稿 `docs/DESIGN-SETTINGS-STYLES.md`，交接 `docs/HANDOFF-0.04.md`。

## 已完成

1. 主气球两肩犄角修复（`2c53503`）：解析轮廓 + 穹顶按半宽反解 + 内缩 56dp。
2. 风格管道（`SettingsStyle` 三档、`WearAppearance.style`、`AppearanceStore` 新键、
   外观子页顶部三档单选）。
3. **方角档**：气球不画、组标题居中无短横、行变透明居中 + 1px 发丝线、返回键换 40dp 方框。
4. **大卡片档**：`SettingsStyleCards.kt` 的 `SettingsHeroCard`（大圆角卡 + displayMedium
   主数值 + 药丸 chip + 44dp 圆环 + 环内图标）、行变独立圆角卡、根页首项为模型 hero 卡。
5. 卡片强调色**从运行时强调色派生**（`Wear.CardModel/CardTools/CardVoice/CardSystem` +
   `shiftHue`），不动 14 个调色板 token，不破坏两条强调色源码守卫。

单测：**1150 例全绿**（含 `SettingsStyleTest` 4 例、`SettingsBalloonOutlineTest` 4 例）。

## 下一轮（按优先级）

1. **实机三档截图**（必须）：TicWatch `C101X35122973`（233dp）、Pixel Watch 2
   `42021RTJWWP97H`（192dp）。模拟器已掉线。
   路径：聊天页麦克位上滑 → 设置 → 滚到底部「系统」段 → 「外观与点阵」→ 页面风格 →
   三档各截一张 → 返回检查根页与子页。
2. **工具与 MCP 卡的圆环数据**：现在没有调用计数。在工具分发处加计数器
   （`AgentPreferences` 持久化 + 一处自增），工具卡用「已调用 / 已注册」当 fraction。
3. **DeepSeek 品牌位图**：`res/drawable` 无素材，现用 `SettingsRingGlyph("DS")` 兜底；
   加一张 vector 后换成 `ringIcon` 里的 `Image`。
4. **风格真值收敛**：把 `settings_style` 挪进 `AgentPreferences`（console 与
   `SettingsTransferCodec` 自动覆盖），`WearAppearance.style` 只做运行时镜像。
5. 语音卡、系统卡接入 hero 卡；子页在卡片档也要用卡片外壳。
6. 无活时的自找活清单：聊天页气泡与卡片风格的细节打磨、长列表滚动性能
   （trace 一下 `ScalingLazyColumn` 的帧时间）、`docs/device` 索引更新。
