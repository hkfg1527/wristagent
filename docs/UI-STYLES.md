# 设置页 6 套风格说明书

> 写入时间 2026-11-18 01:56。代码基线 `0.0.0.56`（versionCode 94）。
> 槽位取值唯一依据：`SettingsStylePolicy.specOf()`（`wear/src/main/java/com/example/myapplication/presentation/ui/settings/SettingsStylePolicy.kt`）。
> 文件与符号名以写入时的源码为准；行号只在会漂的地方省略，改代码后以符号名回查。
> 判定只用三个词：**一致** / **部分一致** / **代码与计划不符**。

## 共用分流点（读后面 6 节前先看这张表）

`SettingsStyle` 枚举现在只有 **4** 个值：`BALLOON` / `SQUARE` / `GOOGLE_CARD` / `TERMINAL`（`appearance/SettingsStyle.kt`）。计划里的第 5、6 套（`M3_LIST` / `M3_TRANSFORM`）**没有枚举值**，`specOf()` 只有 4 个分支；第 5 套已有一份**未接线**的官方实现 `SettingsStyleM3List.kt`（`SettingsM3ListPage` / `SettingsM3ListSwitchRow` / `SettingsM3ListRadioRow`，全项目 0 个调用点），第 6 套的行转场接法也写在同一个文件里。

| 槽位 | 分流位置 | 现状 |
|---|---|---|
| 1 骨架 | 子页 `SettingsShared.kt` `SettingsSubPage`；根页 `SettingsScreen.kt` | `ScreenScaffold` 在子页**无条件**套上；根页不用它。`shell` 槽位**没有消费者**。`timeText`/`scrollIndicator` 一律传 `null`，时钟与指示器改在 Box 里叠层手放（两页各一处） |
| 2 组标题 | `SettingsShared.kt` `SettingsSectionHeader` | 读 `header` 槽位：`CHEVRON_PREFIX` → `"> "`；`CENTERED` → `TextAlign.Center`；其余 → 强调色短横 |
| 3 行外壳 | `SettingsShared.kt` `SettingsCardRow` | 读 `rowShell` 槽位：`CARD_EACH` → 每行一张官方 Card + 8dp 间隙；`HAIRLINE_FLAT` → 无卡面 + 1dp 发丝线；其余走 `groupChrome`（官方 Card 内 + 组内发丝线）。`PLAIN` 值**没有分支** |
| 4/5/6 控件 | `SettingsSwitchRow.kt` / `SettingsSliderRow.kt` / `SettingsRadioRow.kt`；官方封装在 `SettingsOfficialControls.kt` | 现网各只有**一份**手搓实现，`toggle`/`slider`/`radio` 三个槽位在 `src/main` **零消费者**；官方三件封装（`OfficialSwitchRow` / `OfficialSliderRow` / `OfficialRadioRow`）已写好但**0 个调用点** |
| 7 返回 | 子页 `SettingsShared.kt`（`backInList` → 列表首项，否则 `SettingsEdgeBackButton`，其中 `SQUARE_EDGE_BUTTON` 再转 `SettingsSquareBackButton`）；根页 `SettingsScreen.kt` | `EDGE_BUTTON` / `SQUARE_EDGE_BUTTON` / `LIST_ROW` 有消费者；`TOP_BAR` 无消费者。**根页返回不在表里**：恒为列表末尾「完成返回」官方 `Button` 行 |
| 8 字体 | `SettingsRowText.kt` | 只接了一条 `usesMonospace`（组标题 + 行标签 + 行取值三档）。`SettingsFocusStyle`（18sp 焦点档）不换等宽 |

全 `src/main` 现在只剩 **1 处** `WearAppearance.style ==`，在 `SettingsStylePolicy.isActive` 自己身上（外观页问"哪套在生效"）；渲染侧 0 处，迁移前的 11 处已搬进槽位表，源码里只留下"原来是 …，现在读表"的注释。外观页读 `labelRes`/`summaryRes` 属于显示，不算分流。

---

## 1. 气泡 BALLOON

**1）定位**：连续的同一组行拼一张 176dp 官方 Card，组标题左侧沟里一根 8dp×2dp 强调色短横。

**2）8 个槽位**（`specOf(SettingsStyle.BALLOON)`）

| # | 槽位 | 表声明 | 代码落点与取值 | 判定 |
|---|---|---|---|---|
| 1 | 骨架 | `PLAIN_LIST` | 根页：无 `ScreenScaffold`，`ScalingLazyColumn` 直挂 + 时钟/指示器叠层；子页：`ScreenScaffold`（无条件），时钟/指示器同样叠层 | 代码与计划不符（表说 PLAIN_LIST，子页实际是 SCREEN_SCAFFOLD） |
| 2 | 组标题 | `ACCENT_MARK` | `SettingsSectionHeader` 默认支：12sp / TextMuted / Medium / 字距 +0.5sp，左起 14dp，沟内 8dp×2dp 强调色短横 | 一致 |
| 3 | 行外壳 | `CARD_GROUP` | `SettingsGroupCard`：官方 `Card`，宽 176dp，底 `#2A313C`，`contentPadding` 竖 4dp / 横 0dp；组内行间 1dp（`SettingsGroupHairline`） | 一致 |
| 4 | 开关 | `COMPACT_SWITCH` | `SettingsCompactSwitch`：44×26dp 胶囊，拇指 18dp，位移 4→22dp，选中底色 Accent 0.82 | 一致（非官方件） |
| 5 | 滑杆 | `THIN_TRACK` | `SettingsThinSlider`：轨 6dp、滑块 18dp、外圈 2dp、触控带 24dp | 一致（非官方件） |
| 6 | 单选 | `DOT` | `SettingsRadioDot`：环 18dp / 描边 2dp / 选中实心 9dp | 一致（非官方件） |
| 7 | 返回 | `EDGE_BUTTON` | 子页 `SettingsEdgeBackButton`：官方 `EdgeButton` + `EdgeButtonSize.ExtraSmall` + `fillMaxWidth`，接 `Modifier.scrollable(reverseDirection = true)`，图标是自绘 Canvas 折角 | 部分一致（子页对；根页是列表末尾「完成返回」`Button`，表里没有这一项） |
| 8 | 字体 | `DEFAULT` | 12 / 15 / 18sp，行高 16 / 20 / 24sp，`FontFamily.Default` | 一致 |

附加 7 条逐像素开关：`cardScaling=false` / `rootFocusBlock=true` / `rootHeroCards=false` / `subPageHero=false` / `animateRootSections=true` / `rowEnterSlide=true` / `cardEdgeZoom=false`。

**3）官方组件 vs 手搓件**

官方（`androidx.wear.compose.*`）：
- `Card` + `CardDefaults.cardColors` — `SettingsBalloonGroup.kt` `SettingsGroupCard`
- `ScreenScaffold` — `SettingsShared.kt` `SettingsSubPage`
- `EdgeButton` / `EdgeButtonDefaults` / `EdgeButtonSize` — `SettingsShared.kt` `SettingsEdgeBackButton`
- `TimeText` / `ScrollIndicator` — `SettingsScreen.kt`、`SettingsShared.kt`
- `ScalingLazyColumn` + `ScalingLazyColumnDefaults.scalingParams` — `SettingsScaling.kt`
- `CircularProgressIndicator` — `SettingsHeroRing.kt`（28dp / 描边 3dp / 轨道 `#48556A`）
- `Button` — `SettingsShared.kt` `SettingsActionButton`（根页「完成返回」）

手搓件：
- `SettingsCompactSwitch` — `SettingsSwitchRow.kt`
- `SettingsThinSlider`（Canvas 画轨 + 两个 `pointerInput`）— `SettingsSliderRow.kt`
- `SettingsRadioDot` — `SettingsRadioRow.kt`
- EdgeButton 内的折角箭头 `Canvas` — `SettingsShared.kt` `SettingsEdgeBackButton`
- 组标题短横 `headerAccentMark`（`drawRect`）— `SettingsShared.kt`
- `SettingsEdgeScrim` 上下渐变遮罩（`drawWithContent`）— `SettingsShared.kt`

**4）已知不一致 / 待收口**

- 槽位 1 无消费者：`ScreenScaffold` 在子页无条件套；同一套风格里根页/子页两种骨架（计划 §2 明确不允许）。
- 槽位 7 只管子页；根页返回是列表末尾 `Button` 行，表里没有对应值。
- 界面文案「整页一个气球，宽度随圆屏收缩」（`values-zh/strings_settings.xml`）与 0.0.0.46 之后的实现不符：整页超椭圆已删除，现在是每组一张固定 176dp 官方 Card。
- 分组壳与 `rowShell` 槽位解耦：`SettingsSubPage` 对每个 `grouped` 分段无条件套 `SettingsGroupCard`（官方 Card），行外壳只决定"行自己画什么"。BALLOON 正好要这层卡，所以一致；换 `rowShell` 值的风格会同时得到这层卡（见 §2 §4）。

**5）验收要点**

改这套风格要同时看：
- `SettingsBalloonGroup.kt`：卡宽 176dp、`contentPadding` 竖 4dp / 横 0dp；
- `SettingsShared.kt` `SettingsCardRow` 的 `groupChrome` 分支（发丝线、14dp 槽线）；
- `SettingsSectionHeader` 默认支（12sp / 短横 / 24dp 组间距）；
- 根页四处 `SettingsGroupCard`（`SettingsScreen.kt`）+ 子页 `SettingsPageChunking` 分段规则；
- `SettingsStylePolicyTest`（数量 4、四套互异、骨架唯一、等宽唯一）与 `SettingsStyleConsistencyGuardTest`；
- 真机截图：`docs/device/v11/style-balloon.png` 已存在（2026-11-17 02:21，35.8 KB，单张）；计划 §5 要求的"一张槽位表"图仍缺，命名也不是 `style-<name>-*`。

---

## 2. 方角 SQUARE

**1）定位**：标题居中、行间 1dp 发丝线、返回键是 40dp 直角方框。

**2）8 个槽位**（`specOf(SettingsStyle.SQUARE)`）

| # | 槽位 | 表声明 | 代码落点与取值 | 判定 |
|---|---|---|---|---|
| 1 | 骨架 | `PLAIN_LIST` | 同 §1 第 1 槽：根页无 Scaffold，子页恒有 | 代码与计划不符 |
| 2 | 组标题 | `CENTERED` | `SettingsSectionHeader` 的 `CENTERED` 支：`TextAlign.Center`，无左内边距，宽仍 176dp，无短横 | 一致 |
| 3 | 行外壳 | `HAIRLINE_FLAT` | `SettingsCardRow` 的 `HAIRLINE_FLAT` 支：行自身无卡面、无缩放，行后 1dp `Wear.Palette.Border`；**但**分组块仍被 `SettingsGroupCard` 包成 24dp 圆角官方 Card | 代码与计划不符（"无卡面"只在行级成立，块级仍有圆角卡） |
| 4 | 开关 | `COMPACT_SWITCH` | 同 §1：44×26dp 手搓胶囊 | 一致（非官方件） |
| 5 | 滑杆 | `THIN_TRACK` | 同 §1：6dp 轨 + 18dp 滑块 | 一致（非官方件） |
| 6 | 单选 | `DOT` | 同 §1：18dp 环 | 一致（非官方件） |
| 7 | 返回 | `SQUARE_EDGE_BUTTON` | `SettingsEdgeBackButton` 转 `SettingsSquareBackButton`：40dp 方框 + 1dp 描边 + 2dp 折角，贴底 8dp，接同一套 `scrollable` + 退场位移 | 一致（`SQUARE_EDGE_BUTTON` 是计划 §2 之外的第 4 个返回取值） |
| 8 | 字体 | `DEFAULT` | 同 §1 三档 | 一致 |

附加 7 条：`cardScaling=false` / `rootFocusBlock=true` / `rootHeroCards=false` / `subPageHero=false` / `animateRootSections=true` / `rowEnterSlide=true` / `cardEdgeZoom=false`。

**3）官方组件 vs 手搓件**

官方：
- `Card`（分组块外壳，24dp 圆角）— `SettingsBalloonGroup.kt`
- `ScreenScaffold`、`TimeText`、`ScrollIndicator`、`ScalingLazyColumn` — `SettingsShared.kt` / `SettingsScreen.kt` / `SettingsScaling.kt`
- `CircularProgressIndicator` — `SettingsHeroRing.kt`
- `Button`（根页「完成返回」）— `SettingsShared.kt`

手搓件：
- `SettingsSquareBackButton`（整个返回控件：`drawRect` 描边 + `Path` 折角）— `SettingsShared.kt`
- `SettingsCompactSwitch` — `SettingsSwitchRow.kt`
- `SettingsThinSlider` — `SettingsSliderRow.kt`
- `SettingsRadioDot` — `SettingsRadioRow.kt`
- 行间发丝线 `Box(1.dp)` — `SettingsShared.kt` `SettingsCardRow`
- `SettingsEdgeScrim` — `SettingsShared.kt`

**4）已知不一致 / 待收口**

- 槽位 1 无消费者（同 §1）。
- 槽位 3 与 `SettingsGroupCard` 冲突：分组行仍是"圆角卡里的直角行"。
- `HAIRLINE_FLAT` 分支每行后无条件画线，不判 `slot` → 组尾多一根 1dp。
- 槽位 7 的 `SQUARE_EDGE_BUTTON` 未写进 `docs/UI-STYLE-PLAN.md` §2 的返回选项（计划只列 列表行 / EdgeButton / 顶栏）。
- 根页返回是列表末尾 `Button` 行（方形语言在这里不成立）。

**5）验收要点**

- `SettingsShared.kt` `SettingsEdgeBackButton` 的 `SQUARE_EDGE_BUTTON` 分流点与 `SettingsSquareBackButton`（40dp / 1dp / 2dp）；
- `SettingsCardRow` 的 `HAIRLINE_FLAT` 支与 `SettingsGroupCard` 的圆角；
- `SettingsSectionHeader` 的 `CENTERED` 支；
- 根页返回行 `SettingsActionButton` 与 `SettingsExitController`（退场 140ms）；
- `SettingsStylePolicyTest` + `SettingsStyleConsistencyGuardTest`；
- 真机截图：`docs/device/v11/style-square.png` 已存在（2026-11-17 02:21，29.4 KB，单张）；槽位表图缺。

---

## 3. 大卡片 GOOGLE_CARD

**1）定位**：一行一张 176dp 官方 Card，卡内 label/value/meta，右侧 28dp 官方圆环，滚到边缘等比缩小淡出。

**2）8 个槽位**（`specOf(SettingsStyle.GOOGLE_CARD)`）

| # | 槽位 | 表声明 | 代码落点与取值 | 判定 |
|---|---|---|---|---|
| 1 | 骨架 | `SCREEN_SCAFFOLD` | 子页 `ScreenScaffold`（一致）；根页无 `ScreenScaffold`（手写 `ScalingLazyColumn` + 时钟/指示器叠层） | 部分一致（同一风格两种骨架） |
| 2 | 组标题 | `ACCENT_MARK` | 与 §1 同一支；但 `SettingsTextLayout.shouldRenderPageHeader` 在"页标题 == hero 标签"时抑制标题行 | 一致 |
| 3 | 行外壳 | `CARD_EACH` | `SettingsCardRow` 的 `CARD_EACH` 支：每行一张 `SettingsM3Card`（176dp，底 `#2A313C`），行后 8dp（`SettingsCardStyle.Gap`） | 一致 |
| 4 | 开关 | `COMPACT_SWITCH` | 仍是 44×26dp 手搓胶囊 | 代码与计划不符（计划 §3 要求 M3E 档 100% 官方 `SwitchButton`） |
| 5 | 滑杆 | `THIN_TRACK` | 仍是自绘 6dp 轨 | 代码与计划不符（要求官方 `Slider(segmented)`） |
| 6 | 单选 | `DOT` | 仍是行尾手绘圆点 | 代码与计划不符（要求官方 `RadioButton`） |
| 7 | 返回 | `LIST_ROW` | 子页列表首项 `SettingsBackCardRow`：官方 Card + 12dp Canvas 箭头 + `labelLarge`，宽 176dp，下留 8dp | 一致 |
| 8 | 字体 | `DEFAULT` | 行内走 12/15/18sp 三档；卡内另有一套官方 typography：`labelMedium` / `titleMedium`(18sp) / `titleLarge`(24sp) / `bodySmall` | 部分一致（槽位 8 管不到卡内字号） |

附加 7 条：`cardScaling=true` / `rootFocusBlock=false` / `rootHeroCards=true` / `subPageHero=true` / `animateRootSections=false` / `rowEnterSlide=false` / `cardEdgeZoom=true`。

**3）官方组件 vs 手搓件**

官方：
- `Card` / `CardDefaults.cardColors` / `Icon` / `Text` / `MaterialTheme.typography` — `SettingsStyleCards.kt`（`SettingsM3Card`、`SettingsHeroCard`、`SettingsBrandCard`、`SettingsBackCardRow`）
- `CircularProgressIndicator` + `ProgressIndicatorDefaults.colors` — `SettingsHeroRing.kt`
- `ScreenScaffold`、`TimeText`、`ScrollIndicator`、`ScalingLazyColumn`、`Button` — 同 §1

手搓件：
- 返回卡里的箭头 `Path` + `drawPath` — `SettingsStyleCards.kt` `SettingsBackCardRow`
- `SettingsCompactSwitch` / `SettingsThinSlider` / `SettingsRadioDot` — 三个行文件
- `Modifier.cardEdgeZoom()`（`graphicsLayer` 等比缩放 + 透明度）— `SettingsCardEdgeZoom.kt`
- `SettingsEdgeScrim`、`headerAccentMark` — `SettingsShared.kt`

**4）已知不一致 / 待收口**

- 槽位 4/5/6 与计划 §3 冲突：M3E 基准档仍是三件手搓控件，表里已声明 `COMPACT_SWITCH`/`THIN_TRACK`/`DOT`（表与计划不符，代码与表一致）。官方件已备但未接线：`SettingsOfficialControls.kt` 的 `OfficialSwitchRow` / `OfficialSliderRow` / `OfficialRadioRow`（0 个调用点；`OfficialRadioRow` 的注释写明它丢掉了 `dangerSelected` / `dotColor` 两个语义）。
- 槽位 1：根页与子页两种骨架并存。
- 卡套卡：分组块由 `SettingsGroupCard` 无条件套一层官方 Card（`SettingsShared.kt` `SettingsSubPage`），行内又由 `CARD_EACH` 各画一张 `SettingsM3Card`；两层底色同为 `SettingsCardSurface = #2A313C`。根页四处分区卡同理。
- 槽位 8 有两条来源（行三档 vs 卡内官方 typography），改字号要改两处。
- 边缘缩放常量：`MinScale = 1.00f`（即不缩小）、`MinAlpha = 0.45f`、`StartFraction = 0.5`、缓动 `CubicBezierEasing(0.3, 0, 0.7, 1)`；与 `docs/CONTEXT.md` §3 表里写的"贴边 0.70 / 0.50"不符，与 §0.5「恒 1.0、只淡出 0.45」一致。

**5）验收要点**

- `SettingsStyleCards.kt`：四张卡的宽 176dp、卡内 `contentPadding` 只用官方默认（再补一层会把文字挤成两行）、8dp 卡间距写在卡自身 `padding` 上；
- `SettingsCardRow` 的 `CARD_EACH` 支与 `SettingsGroupCard` 的双层卡；
- `SettingsScreen.kt` 四个 `cardScaling` / `rootFocusBlock` / `rootHeroCards` / `animateRootSections` 读表点；
- `SettingsCardEdgeZoom.kt`（`cardEdgeZoom` 槽位）、`SettingsRowStagger.kt`（`rowEnterSlide` 槽位）、`SettingsScaling.kt`（`cardScaling` 槽位两个函数当前实现相同）；
- `SettingsBackCardRow` 与 `backInList`；
- `SettingsStylePolicyTest`（骨架唯一 + 返回必须 LIST_ROW）+ `SettingsStyleConsistencyGuardTest`；
- 真机截图：`docs/device/v11/style-google_card.png` 已存在（2026-11-17 02:22，27.5 KB，单张；文件名是 `google_card`，不是计划里的 `style-card-*`）；槽位表图缺。

---

## 4. 终端 TERMINAL

**1）定位**：等宽字体、`> ` 组标题前缀、行间 1dp 发丝线；返回仍是官方贴底 `EdgeButton`，不是控制台键。

**2）8 个槽位**（`specOf(SettingsStyle.TERMINAL)`）

| # | 槽位 | 表声明 | 代码落点与取值 | 判定 |
|---|---|---|---|---|
| 1 | 骨架 | `PLAIN_LIST` | 同 §1 第 1 槽 | 代码与计划不符 |
| 2 | 组标题 | `CHEVRON_PREFIX` | `SettingsSectionHeader` 的 `CHEVRON_PREFIX` 支：`"> " + text`，宽 176dp，左起 14dp，等宽（`usesMonospace`） | 一致 |
| 3 | 行外壳 | `HAIRLINE_FLAT` | 与 §2 同一支：行级无卡面 + 1dp 线；块级仍被 `SettingsGroupCard` 包成圆角卡 | 代码与计划不符（同 §2） |
| 4 | 开关 | `COMPACT_SWITCH` | 44×26dp 手搓胶囊 | 一致（非官方件） |
| 5 | 滑杆 | `THIN_TRACK` | 自绘 6dp 轨 | 一致（非官方件） |
| 6 | 单选 | `DOT` | 18dp 手绘环 | 一致（非官方件） |
| 7 | 返回 | `EDGE_BUTTON` | `SettingsEdgeBackButton`：官方 `EdgeButton` ExtraSmall（圆角胶囊，不是方键） | 一致 |
| 8 | 字体 | `MONOSPACE` | 组标题 / 行标签 / 行取值三档换 `FontFamily.Monospace`；焦点档 `SettingsFocusStyle`（18sp）**未**换 | 部分一致 |

附加 7 条：`cardScaling=false` / `rootFocusBlock=true` / `rootHeroCards=false` / `subPageHero=false` / `animateRootSections=true` / `rowEnterSlide=true` / `cardEdgeZoom=false`。

**3）官方组件 vs 手搓件**

官方：
- `Card`（分组块外壳）、`ScreenScaffold`、`EdgeButton`、`TimeText`、`ScrollIndicator`、`ScalingLazyColumn`、`CircularProgressIndicator`、`Button` — 同 §1 文件
手搓件：
- `SettingsCompactSwitch` / `SettingsThinSlider` / `SettingsRadioDot` — 三个行文件
- 发丝线 `Box(1.dp)`、`headerAccentMark`（本档不画）、`SettingsEdgeScrim` — `SettingsShared.kt`
- 等宽换族是样式拷贝（`copy(fontFamily = …)`），不是新组件 — `SettingsRowText.kt`

**4）已知不一致 / 待收口**

- 槽位 1、3 同 §1/§2。
- 槽位 8 只覆盖三档正文：子页标题与根页模型名（`SettingsFocusStyle`）仍是默认字族。
- 界面文案「按键像控制台」（`values-zh/strings_settings.xml`）与实际不符：返回键是官方圆角 `EdgeButton`。
- `SettingsRowText.kt` 仍 `import com.example.myapplication.appearance.SettingsStyle`，本文件已无 `SettingsStyle.` 引用（拆表后的残留 import）。
- 根页返回是列表末尾 `Button` 行（同所有风格）。

**5）验收要点**

- `SettingsRowText.kt`：`terminalMono` 覆盖范围（三档 vs 焦点档）；
- `SettingsSectionHeader` 的 `CHEVRON_PREFIX` 支（`"> "` 前缀与 14dp 左内边距）；
- `SettingsCardRow` 的 `HAIRLINE_FLAT` 支（与方角共用，改一处两套一起变）；
- `SettingsStylePolicyTest` 的 `terminalIsTheOnlyMonospaceStyle`（再加一套等宽必红）；
- `SettingsStyleConsistencyGuardTest`；
- 真机截图：`docs/device/v11/style-terminal.png` 已存在（2026-11-17 02:22，29.7 KB，单张）；槽位表图缺。

---

## 5. 官方列表 M3_LIST（计划第 5 套）

**1）定位**：官方 M3E 静态胶囊列表——`TransformingLazyColumn` + 整行胶囊控件（计划原文写 `Button` / `SplitToggleButton` / `Slider`；`SplitToggleButton` 官方库不存在，见本条第 4 项），行高固定，只有滚动。

**2）8 个槽位**

| # | 槽位 | 表声明 | 代码实际（`SettingsStyleM3List.kt`，未接线） |
|---|---|---|---|
| 1 | 骨架 | 无 | `SettingsM3ListPage`：`ScreenScaffold`（`TransformingLazyColumnState` 重载，`edgeButton` 是第二个位置参数）套 `TransformingLazyColumn`；`TimeText` 自己放 TopCenter；`contentPadding` 顶部 `RootContentTopPadding`、左右 `settingsCardScreenInset()` |
| 2 | 组标题 | 无 | 官方 `ListHeader`（次项，带 heading 语义），不是 `SettingsSectionHeader` |
| 3 | 行外壳 | 无 | 无壳：文件头写明"不 clip、不 background、不自拼滑杆开关"；项间距 4dp（`SettingsM3ListItemGap`） |
| 4 | 开关 | 无 | `SettingsM3ListSwitchRow` → 官方 `SplitSwitchButton`（`toggleContentDescription = null`、`onContainerClick` 行体也切换） |
| 5 | 滑杆 | 无 | **无实现**：文件里没有滑杆行（计划要求官方 `Slider`，尚缺） |
| 6 | 单选 | 无 | `SettingsM3ListRadioRow` → 官方 `SplitRadioButton`（`selectionContentDescription = null`、`onContainerClick` 同一回调） |
| 7 | 返回 | 无 | 列表首项 = 官方 `Button`（带 `transformedHeight` + `SurfaceTransformation`）；底部 `EdgeButton` 兜底，自己接 `Modifier.scrollable(reverseDirection = true)` |
| 8 | 字体 | 无 | 官方组件自带 typography，未改字族、未改字号 |

判定：**代码与计划不符**——组件有一份，风格没有：`SettingsStyle` 无 `M3_LIST`、`specOf()` 无分支、三个 API 全项目 0 个调用点。

**3）官方组件 vs 手搓件**

- 官方（已写进 `SettingsStyleM3List.kt`）：`TransformingLazyColumn`、`rememberTransformingLazyColumnState`、`TransformingLazyColumnState` 版 `ScreenScaffold`、`Button`、`ListHeader`、`SplitSwitchButton`、`SplitRadioButton`、`EdgeButton`、`TimeText`、`SurfaceTransformation`、`rememberTransformationSpec`、`Modifier.transformedHeight`。
- 手搓件（这份文件）：**0 件**。但现网共用的三件手搓控件（`SettingsCompactSwitch` / `SettingsThinSlider` / `SettingsRadioDot`）仍在其余四套的渲染路径上；这套风格要真正上线，得让行组件走 `SettingsM3List*` 而不是那三个文件。

**4）已知不一致 / 待收口**

- 组件未接线：`SettingsM3ListPage` / `SettingsM3ListSwitchRow` / `SettingsM3ListRadioRow` 全项目 0 个调用点；滑杆行（槽位 5）尚未写。
- 计划 §3 写的 `SplitToggleButton` 在官方库里**不存在**（1.5.6 源码、1.7.1 字节码一致）：官方拆分布局只有 `SplitSwitchButton` / `SplitCheckboxButton` / `SplitRadioButton`。实现用的是 `SplitSwitchButton`。
- 枚举 4 值、`specOf` 4 行、外观页 `SettingsStyle.values().forEach` 只列 4 项。
- `SettingsStylePolicyTest` 两处会直接红：`assertEquals("风格数量变了就要同步 UI-STYLE-PLAN.md", 4, all.size)`；`theCardStyleIsTheOnlyOneOnTheOfficialScaffold`（断言只有 `GOOGLE_CARD` 走 `SCREEN_SCAFFOLD`）。
- `SettingsStyleConsistencyGuardTest` 的 `StyleComparisonLimit = 1` 是单向棘轮（只许往下调）；新风格若在渲染侧加 `style ==` 会红。
- 外观页（`WatchSettingsSections.kt`）用 `SettingsSubRadioRow` + `SettingsSwitchRow`，这套风格落地后会成为第一个"手搓件混进官方档"的页面。

**5）验收要点**

落地这套要同时改：
- `appearance/SettingsStyle.kt`（加枚举值 + 2 个 string 资源，中英成对）；
- `SettingsStylePolicy.specOf()`（加一行，15 个参数全给值）；
- `SettingsStylePolicyTest`（数量断言、骨架断言、"四套互异"改"六套互异"）；
- `SettingsStyleConsistencyGuardTest`（`StyleComparisonLimit`、`renderFilesFor` 若拆新文件）；
- `docs/UI-STYLE-PLAN.md` §3 状态列、本文；
- 自动跟随、不需要手改：`ConsoleFields`（`SettingsStyle.values().map { it.name }`）、`SettingsTransferCodec`（按名读写）、外观页单选列表；
- 真机截图 `docs/device/v11/style-m3-list-*`（目前不存在）。

---

## 6. 官方转场 M3_TRANSFORM（计划第 6 套）

**1）定位**：与第 5 套同一份官方列表，但每行挂 `SurfaceTransformation` + `Modifier.transformedHeight(state, spec)`，滚到边缘按官方曲线缩小淡出。

**2）8 个槽位**

| # | 槽位 | 表声明 | 代码实际 |
|---|---|---|---|
| 1 | 骨架 | 无 | `SettingsStyle` 无 `M3_TRANSFORM`；`specOf()` 无此分支；行转场接法已在 `SettingsStyleM3List.kt`（未接线） |
| 2 | 组标题 | 无 | 同上 |
| 3 | 行外壳 | 无 | 同上 |
| 4 | 开关 | 无 | 同上 |
| 5 | 滑杆 | 无 | 同上 |
| 6 | 单选 | 无 | 同上 |
| 7 | 返回 | 无 | 同上 |
| 8 | 字体 | 无 | 同上 |

判定：**代码与计划不符（整套风格未落地）**。

**3）官方组件 vs 手搓件**

- 官方（已存在，同一文件）：`rememberTransformationSpec()`、`SurfaceTransformation(spec)`、`Modifier.transformedHeight(state, spec)`，挂在返回行、标题行、内容项三处。
- 手搓件：**0 件**。
- 与第 5 套的关系（`docs/UI-STYLE-PLAN.md` §3）：同一份列表 + 一个开关，差别只在是否有行转场。

**4）已知不一致 / 待收口**

- 同 §5 全部条目；补滑杆行（槽位 5）后这套才有完整 8 槽位。
- 第 5 套（静态）与第 6 套（转场）的差别在计划里是"同一份列表 + 一个开关"；现在唯一那份实现**已经**在返回行、标题行、内容项三处挂了 `SurfaceTransformation` + `transformedHeight`（`SettingsStyleM3List.kt`），等于只实现了第 6 套的形态。要拆两套，得先像 `cardEdgeZoom` 那样加一个逐像素开关。
- 额外：本项目现有的边缘缩放是自绘的 `Modifier.cardEdgeZoom()`（`SettingsCardEdgeZoom.kt`），与官方 `TransformingLazyColumn` 路线是两套实现；第 6 套落地时应二选一，不要同时挂（`docs/UI-STYLE-PLAN.md` §3 的"官方曲线"指官方 `rememberTransformationSpec()`）。
- `docs/CONTEXT.md` 已定：库的 `scalingParams` 保持平坦（`edgeScale = 1f`、`edgeAlpha = 1f`，见 `SettingsScaling.kt`），缩放由行/卡自己承担。

**5）验收要点**

- 同 §5 的 7 条；
- 追加：行转场与 `cardEdgeZoom` 不能同时生效；`rowEnterSlide` 与转场同时开会让行位移叠加（表里这一格必须 `false`）；
- 真机截图 `docs/device/v11/style-m3-transform-*`（目前不存在）。

---

## 跨风格共同规则

事实来源：`docs/CONTEXT.md` §1（硬约束）、§3（已定决策）、§0.5（已完成）。条目原文照抄；本文补充一律放在括号里。

**硬约束（§1，违反即返工）**

1. **禁止任何横向拉伸 / 横向形变**；动画**只允许缩小**（等比，`scaleX == scaleY`）。
2. M3E 卡片**不得相互重合**：静态卡间距 8dp，实测每张卡独立、间隙 16px@2x。
3. 默认气泡档的像素要守住：改它之前先问用户（渐变实验已备好，见 `docs/PERF-0.05.md`）。
4. 设置页行文案始终"左值右值两槽"（有守卫测试）；调色板上限 14 token。
5. 所有交付必须**真机验证**，不能用"编译通过"代替交互验收。

**已定决策（§3）**

- 大卡片档：官方 Wear M3 `Card`，一行一张；根页每卡一个 lazy item；不开 autoCentering。
- 边缘缩放：`CardEdgeZoom` 屏心 1.0、贴边 0.70/0.50，官方缓动，只缩不放；库的 `scalingParams` 保持平坦。**（代码现值：`MinScale = 1.00f`、`MinAlpha = 0.45f`，与 §0.5「恒 1.0、只淡出 0.45」一致，与 §3 表里 0.70/0.50 不一致 —— 改前先确认以哪条为准。）**
- 圆环规则（用户 01:15 拍板）：**每张卡都有环**；没数据也画轨道（"就算没有，也得在旁边塞点东西"）。实现：`SettingsHeroRing`（官方 `CircularProgressIndicator`，28dp / 描边 3dp / 轨道 `#48556A`，`progress = null` 时传 0f 只留轨道）。
- 返回控件（用户 01:15 拍板）：选 ②跟着列表滚走（返回钮进列表内容，滚下去就滚出去；下拉退出手势仍常驻）。**现状：只有 `GOOGLE_CARD` 的 `LIST_ROW` 走这条；其余三套仍是贴底固定槽位。**
- 页标题：卡片档里标题与 hero 标签同句时**不画标题**（`SettingsTextLayout.shouldRenderPageHeader`）。

**卡片同宽的实测口径（§0.5 已完成）**

- 卡片宽度：**一律 352px / 176dp**（`SettingsDesignMath.ContentWidthDp = 176f`）；组卡、行卡、返回卡都用 176dp。
- 卡外独立行（动作按钮等）用 `settingsCardWidth()`：233dp 屏 = 216dp（`SettingsCurveMath.MaxCardWidthDp`），它**不是卡片**，不受 176dp 约束。
- 卡边缘缩放恒 1.0、只淡出（0.45）；`SettingsCardEdgeZoomTest` 守"等比 + 只缩不放"。

**每卡一个环 / 不重叠的落地检查点**

- 环：`SettingsStyleCards.kt` 的 `SettingsHeroCard`（右槽）与 `SettingsBrandCard`（右上角）都必须调 `SettingsHeroRing`，`ring = null` 时也要画轨道。
- 不重叠：卡间距写在卡自己的 `padding` 上（`SettingsCardStyle.Gap` = 8dp），**不许用兄弟 `Spacer`** —— `SettingsStaggeredRow`/`RootContentRow` 内层是 `Box`，兄弟 `Spacer` 不产生间距（`docs/CONTEXT.md` §已完成第一条）。
- 行高下限 48dp（`MinRowHeightDp`）、发丝线 1dp（`HairlineDp`）、行左右内边距 14dp（`RowSidePaddingDp`）、子项左移 12dp（`SubItemIndentDp`）、组间距 24dp（`SectionGapDp`）—— 六套风格共用，不随风格变。

---

## 7. 槽位声明 vs 真机实测（2026-11-18，0.0.0.70）

| 风格 | 声明外壳 | 真机表现 | 结论 | 证据 |
|---|---|---|---|---|
| BALLOON | CARD_GROUP | 每组一张圆角卡 + 分区标题带强调色短横 | 一致 | `style-balloon-root.png` |
| SQUARE | HAIRLINE_FLAT | 无卡、行间发丝线、标题居中 | 一致 | `style-square-hairline.png` |
| GOOGLE_CARD | CARD_EACH | 一行一张 hero 卡（强调色标签 + 大数值 + 说明 + 圆环） | 一致 | `style-googlecard-root.png` |
| M3_LIST | PLAIN | 官方胶囊列表 + 曲面时钟 + 右缘指示器 | 一致 | `m3list-tools-open.png` |
| M3_TRANSFORM | PLAIN + 行转场 | 同上，滚动时顶部项被压扁 | 一致 | `m3transform-scrolled.png` |
| TERMINAL | HAIRLINE_FLAT | 无卡、行间发丝线、`> ` 前缀、等宽字 | 一致 | `style-terminal-root.png` |

### 实测发现的缺陷（未修，待定）

**GOOGLE_CARD 根页：曲面时钟与首卡文字重叠。**
截图 `style-googlecard-root.png` 里，时钟「2:25」压在首卡数值「deepseek-v4-pro」的同一段
y≈10..30 区间上，肉眼可辨为两个字叠在一起。

原因：根页的 `TimeText` 是**显式叠层**（本项目没有 `AppScaffold`，官方那套托管用不了），
而列表 `contentPadding.top` 仍是 `RootContentTopPadding`（24dp）——只够让开遮罩，不够让开时钟带。
其余风格首屏是普通行，被压的是空白，所以看不出来；大卡片档首屏就是一块大卡，问题才暴露。

三个改法（都要用户拍板，因为它改的是首屏留白）：
1. 根页 `contentPadding.top` 从 24dp 抬到 34dp（官方钟带高度）——所有风格首屏整体下移 10dp；
2. 只在大卡片档抬到 34dp——风格间首屏位置会不一致；
3. 时钟改画在内容之上并加一层渐隐底——不动布局，但要在时钟下面垫一条渐变。
### 追加：这个重叠不是大卡片档独有

`style-terminal-root.png` 里，时钟「5:28」同样压在首行「底栏副键」上（y≈10..30）。
六套风格的根页都走同一条叠层时钟 + 24dp 上边距，所以**是共性缺陷**，不是某一套的问题。
在气泡/方角/官方两套首屏上不易察觉，只是因为被压的那一行当时是空白或落在渐隐遮罩里。

三个改法同上（抬上边距 / 只抬大卡片档 / 时钟加渐隐底），仍未实施，等用户拍板。