# 全代码审查 · 0.0.0.49（5 个子智能体并行 · 只读）

> 用户 2026-11-17 新规：**每 10 个内部版派 ≥5 个子智能体做全代码审查**；同时定了**简洁优先**——
> 同一个控件被交接写成多份就融成一份、只留一份。这是第一次执行。

## 0. 基线数字（本机实测）

| 指标 | 值 |
|---|---|
| 主源码 | **153 个文件 / ~30876 行 / 17 个包** |
| 测试 | **1295 条 @Test / 189 套件 / 170 个测试文件 / 17420 行** |
| 单测耗时 | `--rerun-tasks` **50s**（其中测试执行约 10s，编译约 40s） |
| 最大包 | `presentation.ui.settings` **~9038 行（29%）** |
| 上帝文件（>800 行） | **8 个，合计约占全项目 30%** |

上帝文件清单：

```
SettingsShared.kt            2081 行 / 41 个 @Composable / 5 类职责
AgentDispatcher.kt           1422 行 / 7 类职责（提示词+工具循环+HTTP/SSE+审批+记忆+子代理+模型拉取）
BottomInteractiveBlock.kt    1112 行 / 布局+风险渲染+动画编排
SettingsScreen.kt             980 行 / 焦点导航+20 份偏好镜像+引擎生命周期
AgentPreferences.kt           942 行 / 全量偏好 + 反向 import presentation
ChatScreen.kt                 921 行 / 布局+折叠计时+组合期读偏好
AgentToolRegistry.kt          842 行 / 注册表 + 30 个工具实现
NetworkDiagnostics.kt         826 行 / 诊断+报告格式+修复计划
```

## 1. 本轮已修（P0，真炸弹）

**`isMinifyEnabled = true` 但从未调用 `proguardFiles(...)`** —— 仓库里那份 1377 字节的
`proguard-rules.pro`（coroutines ServiceLoader / Shizuku / AIDL keep）**完全没生效**，
release 只吃 AGP 默认规则。本轮已接上：

```kotlin
isMinifyEnabled = true
proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
```

**驳回一条审查结论**：审查说 `libs.compose.ui.tooling` 与 `libs.androidx.compose.ui.tooling`
重复。查版本目录后确认是两件东西（`androidx.wear.compose:compose-ui-tooling` 与
`androidx.compose.ui:ui-tooling`），**不动**。八荣八耻第 1 条：认真查询，不瞎猜。

**实测后撤回一条“优化”**：`maxParallelForks = 4` 跑 **50.3s**，1 fork **51.3s**，差 2%（噪声内）。
1295 条真跑只用约 10s，瓶颈是编译。按“简洁优先”不引入这条配置，测试文件里留了注释记这个数。

## 2. 重复控件（用户最在意的那条）

| 重复对象 | 份数 | 位置 | 合并后省 |
|---|---|---|---|
| 行组件（开关行/分段开关行、单选行/子项单选行） | 4 | `SettingsShared.kt:1413/1473/1665/1736` | ~70 行 |
| 手搓开关 `SettingsCompactSwitch` vs 官方 `SwitchButton`（import 了但 0 调用） | 2 | `:1433` | ~35 行 |
| 手搓滑轨 `SettingsThinSlider` vs `ApprovalTrack` | 2 | `:1574` / `BottomInteractiveBlock.kt:743` | ~150 行 |
| 滑杆 px↔档位换算 | 2 | `ApprovalSliderState.kt:29` / `SettingsUiMath.kt:557` | ~45 行 |
| 卡壳 | 3 | `SettingsBalloonGroup.kt:34` / `SettingsStyleCards.kt:55` / `WearComponents.kt:45` | 统一 1 个月 |
| 分隔线 | 3 | `SettingsShared.kt:937/1062` / `WearComponents.kt:234` | ~25 行 |
| 返回键 + 箭头 | 3 + 6 | `:702/758`、`SettingsStyleCards.kt:266`… | ~90 行 |
| 输入对话框 | 2 | `TextInputDialog.kt:55` / `SettingsShared.kt:1954` | ~140 行 |
| 动作按钮 | 4 | `TextInputDialog:184` / `SettingsShared:1856` / `SessionDrawer:468` / `Onboarding:369` | ~110 行 |
| 状态点 | 4 | `SettingsShared:1388/1771` / `SessionDrawer:416` / `FrostedGlass:293` | ~30 行 |
| 曲面时钟 | 2 | 自绘 `TopClockBar.kt:43` vs 官方 `TimeText` | ~85 行 |
| 可折叠胶囊 | 2 | `FrostedGlassComponents.kt:145/281` | ~90 行 |

**顺带发现死代码**：`SettingsShared.kt:1103` 的“自绘超椭圆外壳分支”**不可达**
（`shape == null` 已先 return），可连同 `SettingsCurve.kt:80 cardPath` 一起删 ~290 行；
`SettingsScreen.kt:895 RootSectionRow` 声明后零调用。

## 3. 性能热点（按严重度）

| 级别 | 位置 | 问题 | 改法 |
|---|---|---|---|
| P0 | `BottomInteractiveBlock.kt:583` | 高光 Canvas 每帧 new `Brush.sweepGradient` | `drawWithCache` |
| P0 | `ChatScreen.kt:146/802` | 组合期读动画值 → 一次激荡重组整屏约 22 帧 | 改读绘制相位 |
| P0 | `SettingsShared.kt:1113` | 每行每帧 `Path()` 重建（`SettingsCurve.kt:362`） | 复用 `buildGroupPath` |
| P1 | `SettingsScreen.kt:466` | 整页分区塞进**一个** lazy item（3000+px，零虚拟化 + 每帧栅格化整页） | 按分区拆 item |
| P1 | `SettingsShared.kt:1106` | `onGloballyPositioned` 滚动中写状态（2px 节流仍每帧） | 阈值 8–12px 或列表滚动量共享 |
| P1 | `PromptViewerSection.kt:217` | 组合期读 scroll + 每帧重建 Brush + 每帧重跑 `promptParagraphs`（内含 Regex） | `derivedStateOf` + `remember(text)` |
| P1 | `ConversationList.kt:235` | 向 `AgentSquareBlurBox` 传 5 个**函数体里从未读**的参数 → 每帧整列重组 | 删参数恢复 skip |
| P2 | `ChatViewModel.kt:145` → `SessionStore.kt:16` | `init` 里同步 `readText()`，冷启动主线程读盘 | 挪 IO |

## 4. 测试体系

- **1295 条里约 250 条是“扫源码字符串”的守卫**（44 个文件、86 次 `readProjectFile`、
  287 处 `.contains("…")`、71 处 `Regex`）。这些确实只能扫文本，但**重复率很高**：

| 规则 | 被几条钉 | 建议 |
|---|---|---|
| `drawsGroupHairline(slot)` 真值 | 4 | 合并成参数化 1 条 |
| 发丝线“全目录只许一处声明” | 5 | 留 1 条 |
| 强调色必须走 `Wear.Accent` | 4 | 留最严的 `AccentRuntimeSourceGuardTest` |
| `CardEdgeZoom.MinScale = 1.0` | 4 | 留 2 条（值 + 源码） |
| Console 映射（PAGES/Fields/存储键） | 5 文件 ≈30 条 | 合并夹具，读 `app.js` 共用解析 |
| 正文对比度 ≥4.5:1 | 4 | 合并，删自带第二份 luminance 公式 |

## 5. 下一步优先级（按“省得最多 / 风险最低”排）

1. **死分支与死文件**（零风险，~290 行）：删不可达的超椭圆外壳 + `cardPath` + `RootSectionRow`。
2. **行组件四份合一**（低风险，~70 行）：所有人都要改的那一层，一漂移就是全站槽线不齐。
3. **卡壳/分隔线/返回键三合**（低风险，~115 行 + 可见一致性收益）。
4. **性能 P0 三处**（`drawWithCache` / 绘制相位读 / 复用 Path）：用 `gfxinfo framestats` 的
   P90/P99 与 `dumpsys gfxinfo` 的 janky% 对比前后。
5. **测试守卫收成 1 个 `SourceInvariantsTest`**：一次遍历 `src/main` 建文本索引，
   改一行代码跑的是 ~1000 条行为测试。

## 6. 未回的一份

`dead-code`（死代码/未用字符串/未用资源统计）本轮未返回，下轮补进本文。
