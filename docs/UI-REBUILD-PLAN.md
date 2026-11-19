# 设置页 UI 全面重构计划（六套风格各自独立实现）

> 授权：用户 2026-11-20「全部都按各自风格重新做」「每一套 UI 逻辑不一样」「用谷歌官方说明书和谷歌那些东西来做」
> 「别把东西都放到一起，有些单独的可以做成小方块卡」「终端可以更极端，绿字、加载像命令行」。
> 目标优先级：**UI 先做完，再回头做功能欠账。**

## 0. 硬约束（每次提交前自查）

1. 圆屏优先：内容一律在 `ScreenScaffold` / `AppScaffold` 的 `contentPadding` 内，禁止 `fillMaxWidth` 贴边。
2. 组件只用 `androidx.wear.compose.material3`（1.7.1）与 `androidx.wear.compose.foundation.lazy`（1.7.1）里的真实组件；
   不许手搓仿真件（自绘开关 / 自绘滑杆 / 自绘单选 **全部要替换成官方件**）。
3. 卡片同宽 176dp、互不重叠；**禁止横向形变**（`scaleX == scaleY`）。
4. 每套风格必须有：`TimeText` + `ScrollIndicator`（官方滚动指示器）+ 边缘淡出。
5. 入场 + 退场都要有，且**参数按风格各自定义**（见 §2 表）。
6. 磁滑：松手吸附到最近一行（表冠与手势共用同一 fling/snap 行为）。
7. 每套风格完成 = 真机截图 + 像素/几何数字（宽度、间隙、色值）+ 六页巡检（能开能回）。

## 1. 目录与所有权（一套风格一个包，互不 import 私有实现）

```
presentation/ui/settings/stylekit/
  StyleMotion.kt            // 动效参数表（唯一真源，渲染侧只读）
  SettingsStyleRenderer.kt  // 每套风格的渲染契约：PageFrame / GroupHeader / RowFrame / BackControl
  balloon/BalloonStyle.kt       // BALLOON
  square/SquareStyle.kt         // SQUARE
  card/CardStyle.kt             // GOOGLE_CARD
  terminal/TerminalStyle.kt     // TERMINAL
  official/OfficialListStyle.kt // M3_LIST
  official/TransformStyle.kt    // M3_TRANSFORM（必须接 SurfaceTransformation）
```

共享层只保留「数据 + 注册表 + 几何常量」，**不放任何一套风格的视觉实现**。
现有 `SettingsShared.kt` 逐步瘦身为：行数据登记、分页骨架调度、公共工具（几何/文本测量）。

## 2. 六套风格的设计定稿

| | BALLOON | SQUARE | GOOGLE_CARD | TERMINAL | M3_LIST | M3_TRANSFORM |
|---|---|---|---|---|---|---|
| 视觉语言 | 气泡：整组一张连续曲面，32dp 大圆角，柔和留白 | 方角：直角 + 1dp 发丝线 + 居中标题，工业仪表 | 大卡片：一行一张独立卡，卡间 8dp，可与小方块卡混排 | 终端：近黑底、等宽绿字、`> ` 提示符、光标闪烁、加载＝命令行输出 | 官方列表：全部官方控件，官方节奏（ListHeader/SubHeader） | 官方转场：官方列表 + 每行 SurfaceTransformation（缩放 + 高度变化） |
| 行外壳 | `Card` 整组一张（组内行无分隔线） | `OutlinedCard` 直角 + 行间 `HorizontalDivider` | 一行一张 `Card`（`AppCard`/`TitleCard`/小方块 `Card` 混排） | 无卡：`Text` 行 + 前导 `>`；选中态 = 反显 | `Card(onClick)` + 官方 `SwitchButton`/`Slider`/`RadioButton` | 同 M3_LIST + `transformedHeight` |
| 开关 | 官方 `SwitchButton` | 官方 `SwitchButton` | 官方 `SwitchButton`（独立成小方块卡时用 `SplitSwitchButton`） | 官方 `TextToggleButton`（`[on]/[off]` 文案） | 官方 `SwitchButton` | 官方 `SwitchButton` |
| 滑杆 | 官方 `Slider`（非分段） | 官方 `Slider`（分段） | 官方 `Slider`（分段） | 自绘 `LinearProgressIndicator` 风格数值条 + `+/-` 官方 `IconButton` | 官方 `Slider(segmented)` | 官方 `Slider(segmented)` |
| 单选 | 官方 `RadioButton`（整行胶囊） | 官方 `RadioButton`（行尾） | 官方 `RadioButton`（小方块卡网格） | `> [x] 选项` 文本行 | 官方 `RadioButton` | 官方 `RadioButton` |
| 分组标题 | `ListHeader` | `ListSubHeader` + 居中 | `ListHeader` | `# 标题` | `ListHeader` | `ListHeader` |
| 返回 | 官方 `EdgeButton` | 40dp 直角 `IconButton` | 列表首项 `Card` | 末行 `> 返回` 文本行 | 列表首项 | 列表首项 |
| **入场** | 上移 8dp + 淡入，错峰 40ms | 左移 12dp + 淡入，错峰 30ms（硬切感） | 缩放 0.92→1.0 + 淡入，错峰 50ms | 逐字符打字 + 光标闪烁，错峰 60ms | 官方默认（无位移，仅淡入） | 官方 `SurfaceTransformation` 自带 |
| **退场** | 下移 16dp + 淡出，反向错峰 | 右移 16dp + 淡出 | 缩放 1.0→0.94 + 淡出 | 清屏式（整块淡出） | 官方默认 | 官方默认 |
| **磁滑** | 吸附（行高 64dp，间隙 8dp） | 吸附（行高 56dp） | 吸附（卡片高度各自不同，取最近卡中心） | **不吸附**（命令行连续滚动，符合终端语义） | 吸附 | 吸附 |
| **边缘缩放** | 0.94 + 淡出到 0.5 | 0.96 + 淡出到 0.5 | 0.90 + 淡出到 0.4（真做，当前是空操作） | 无缩放，仅淡出 | 0.95（官方默认） | 0.90（官方默认） |

配色：全部走 `MaterialTheme.colorScheme`；TERMINAL 额外用固定绿 `#3BFF9A` 作为前景强调色（等宽字体 `FontFamily.Monospace`）。

## 3. 实施阶段（每阶段一轮，各自交付真机证据）

| 阶段 | 内容 | 验收 |
|---|---|---|
| **S0（本轮）** | `StyleMotion` 参数表 + 渲染契约 + 计划定稿 | 编译通过、单测绿、参数表有测试钉住 |
| S1 | BALLOON 全套（壳体 + 行 + 控件 + 动效 + 磁滑） | 根页/子页截图 + 宽度 352px/间隙数字 |
| S2 | SQUARE | 同上 |
| S3 | GOOGLE_CARD（含小方块卡混排） | 同上 + 单卡/双卡混排截图 |
| S4 | TERMINAL（绿字/命令行加载/光标） | 同上 + 加载态截图 |
| S5 | M3_LIST（全部官方件） | 同上 + 官方控件截图 |
| S6 | M3_TRANSFORM（真接 SurfaceTransformation） | 同上 + 与 M3_LIST 的像素级差异证据 |
| S7 | 六套统一回归：release-check 8/8 + tag | 全绿 |

## 3.5 打磨阶段 P1–P10（用户 2026-11-20 追加，**S7 之后必做，一轮只磨一套**）

用户原话：「风格和细节要打磨到百分之百。S7 轮之后要再留出来全新的十轮，每一轮单独各种截图、各种检查，
把握好每一个风格的细节。这个我给你这 10 套风格的预算是至少 50 轮以上。」

| 轮 | 内容 | 每轮必须交出的东西 |
|---|---|---|
| P1–P10 | 一轮只打磨一套风格（顺序同 S1–S7 的风格清单 + 新增四套） | ①**五种截图**：根页 / 子页 / 交互态（开关或滑杆刚动过）/ 滚动中 / 贴边态；②**几何数字**：卡宽、间隙、行高、圆角、内收；③**色值**：前景/背景/强调三档的实际 RGB；④**问题清单**：本轮发现并修掉的 + 未修的；⑤**离 100% 差什么**：一句话实话 |

规则：
- 打磨轮**只动那一套风格的文件**（`stylekit/<style>/`），共享层改动必须当轮写进报告。
- 每套风格在打磨轮结束时必须满足：入场/退场有、磁滑有（终端除外）、边缘淡出有、贴边缩放**真的不是 1**、
  开关/滑杆/单选在该套里视觉自洽（不与其他套撞脸）。
- 十套里任何一套没走完 P 轮，**不许**宣称"十套完成"。


## 4. 已知要顺带修掉的三件事

0. **官方滑杆丢了无障碍标签（S1 拦路石，2026-11-20 实测）**：把 BALLOON 的滑杆槽位改成
   `OFFICIAL_SEGMENTED` 后，11 条滑杆用例全红 —— 节点上不再同时带 `contentDescription`（"某设置项"）
   与 `SetProgress`（区间）。现状：
   - 官方 `Slider` 自己带 `SetProgress`，但行标签没并进去，读屏只念百分比不念是哪一项；
   - `M3_LIST` / `M3_TRANSFORM` **也是** `OFFICIAL_SEGMENTED`，但现有用例只在**默认风格（BALLOON）**
     下渲染这些页面，所以这两套的滑杆无障碍**从来没被测过**。
   - 修法（下一轮）：在官方滑杆行上建立独立语义边界，把标签与区间并到同一个可调节节点
     （`Modifier.semantics(mergeDescendants = true) { contentDescription = …; stateDescription = … }`
     包住官方 `Slider`），再补一条"在 M3_LIST 风格下渲染并断言同一节点带标签+区间"的用例。
   只有修完这条，BALLOON 才能安全切到官方件，S1 才算真正开始。

1. **边缘缩放是空操作**：`CardEdgeZoom.MinScale = 1.00f` → 缩放恒等于 1（`SettingsCardEdgeZoom.kt:40`）。按 §2 表给每套真实值，并把判据从「宽度 == 176dp」改成「未缩放基准宽度 == 176dp」。
2. **没有磁滑**：全仓无 `snapFlingBehavior`。要加一个共享的吸附 fling（表冠与手势共用）。
3. **M3_TRANSFORM 名不副实**：`transformRows` 从未被使用 → 与 M3_LIST 逐像素相同。

## 5. 上下文纪律

- `SettingsShared.kt` 现有 1950 行，**不再往里加视觉代码**；新实现只进 `stylekit/<风格>/`。
- 每轮只动一套风格 + 共享契约，避免大范围冲突；每轮结束跑全量单测（1405 条）。
- 每个阶段的报告必须给：截图路径、卡宽/间隙像素数、动效参数（时长/位移/错峰）。
