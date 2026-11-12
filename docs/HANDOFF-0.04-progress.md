# 0.04-beta 进度（2026-11-13 02:55）

- 气球收口改为「弦宽 × 四分之一圆窗口」：宽度在顶点归零且切线水平，窗口终点导数为 0，
  与侧壁切线严格相接；单测新增 `theSilhouetteNeverBitesBackFromItsWidestRow`（肩部不许回落）。
  实机 S14/S15/S18 验证：子页与设置尾部小分组的顶部不再是尖，测量确认宽度剖面单调凹形。
- 工具调用账本：`AgentPreferences.calledToolNames`（逗号串）+ `registeredToolCount`，
  `AgentToolRegistry.executeTool` 记名、`getToolDefinitions` 记数；`ToolCallLedgerTest` 6 例。
  `FakeSharedPreferences` 补上 `getStringSet` 读取。
- 大卡片档实机验证：S20（子页，行变独立圆角卡）、S21（根页）、S25（hero 卡 + 44dp 圆环）。
  已修：环内 "MCP" 被裁成 "MC" → 改单字；注册数为 0 时显示 "—" 而不是 "0 / 0"。
- 全量单测 1103 例全绿 + Release 包构建通过。

## 下一步

1. 风格接入 `SettingsTransferCodec`（导入导出）与 `ConsoleFields` / `ConsoleBridge`（控制台字段）。
2. 语音与系统 hero 卡；卡片档子页也用卡片外壳。
3. 方角档的方形返回键实机验证（`SettingsSquareBackButton`）。
4. DeepSeek 品牌位图：`res/drawable` 仍无素材，现为 "DS" 字形兜底。
5. 三档在 Pixel Watch 2（192dp）各截一套图。
