## 进度（2026-11-13 00:30 更新）

已落地并编译通过：

- `SettingsStyle` 三档 + `WearAppearance.style` + `AppearanceStore` 新键 + 「外观与点阵」
  顶部三档单选行；`SettingsStyleTest`（4 例）覆盖默认档、脏值回落、存取往返、文案唯一。
- **方角档外壳**：`SettingsBalloonGroup` 非气球档不画曲面；`SettingsCardRow` 增 SQUARE 分支
  （透明底 + 内容居中 + 1dp 发丝线）；`SettingsSectionHeader` 方角档居中且去掉强调短横；
  新增 `SettingsSquareBackButton`（40dp 方框 + 1dp 描边 + 折角箭头，跟随列表滚动淡出）。
- **大卡片档**：`SettingsStyleCards.kt` 提供 `SettingsHeroCard`（大圆角卡 + 标签 + displayMedium
  主数值 + 药丸 chip + 右侧 44dp 圆环，环内可放图标/字形）、`SettingsRing`、`SettingsRingGlyph`、
  三个强调色常量（模型/工具/语音）。`SettingsCardRow` 增 GOOGLE_CARD 分支（一行一张独立圆角卡 +
  8dp 间隔）。根页在卡片档下先渲染模型 hero 卡（DeepSeek 用 "DS" 字形兜底，环表示 Key 是否已填）。

## 立即可做（下一轮）

1. **实机三档截图**：模拟器已挂（`emulator-5554` 掉线），改走 TicWatch
   `C101X35122973`（233dp）与 Pixel Watch 2 `42021RTJWWP97H`（192dp）。
   路径：聊天页麦克位上滑 → 设置 → 滚到「系统」段的「外观与点阵」→ 页面风格三档 → 返回截图。
2. **工具调用计数**：目前没有调用次数来源。要在工具分发处加计数器（persist 到
   `AgentPreferences`），工具与 MCP 卡才能用整环显示"已调用 / 已注册"。
3. **DeepSeek 图标位图**：`res/drawable` 里还没有品牌位图，现在用 `SettingsRingGlyph("DS")` 兜底。
4. **风格持久化收敛**：现在风格只落在 `wear_appearance`，控制台与导入导出还没接。
   建议把真值挪进 `AgentPreferences`（`style` 字段），`WearAppearance.style` 只做运行时镜像，
   这样 `ConsoleFields` / `SettingsTransferCodec` 自动覆盖。
5. 测试：`:wear:testDebugUnitTest --offline` 全量（上次全绿 1092 + 新增 4 = 1096 待确认）。
