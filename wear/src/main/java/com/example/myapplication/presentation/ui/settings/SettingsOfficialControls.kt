package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text

// ============================================================================
// 1. API 核对结果（真实源码，不是凭记忆）
//
// 核对对象：androidx.wear.compose:compose-material3
//   抄签名原文用（唯一有源码的版本）：
//     D:\gradle-home\caches\modules-2\files-2.1\androidx.wear.compose\compose-material3\1.5.6\
//       fbd248218eaa3afb772653b259eb995e9f815a1a\compose-material3-1.5.6-sources.jar
//   读法：PowerShell + System.IO.Compression.ZipFile 打开 jar，取
//     androidx/wear/compose/material3/{SwitchButton,Slider,RadioButton}.kt 三个条目。
//   控件绘制内核在另一个 artifact：
//     ...\compose-material-core\1.5.6\51a14d39049dfc3004e0482c0136e9f5b352490f\
//       compose-material-core-1.5.6-sources.jar  → materialcore/{SelectionControls,ToggleButton}.kt
//   token 表：compose-material3-1.5.6-sources.jar → material3/tokens/{SwitchButton,SplitSwitchButton,
//     RadioButton,SplitRadioButton,Slider,Shape}Tokens.kt
//
// 交叉验证（项目实际编译目标是 1.7.1，不能只看 1.5.6）：
//     ...\compose-material3\1.7.1\73052badc7c855dc1e87501a944633e3f5c868cf\compose-material3-1.7.1.aar
//   取 aar 内 classes.jar，javap -p 对比 1.5.6 与 1.7.1 的 SwitchButtonKt / SliderKt / RadioButtonKt：
//     · public Slider(float, Function1, int, Modifier, ...)、public SwitchButton(...)、
//       public RadioButton(...) 的 JVM 描述符 **1.5.6 与 1.7.1 完全一致**（差异只有编译器生成的
//       SwitchButton$lambda$N / SplitRadioButton$lambda$N 这类私有名字，以及 access$ 桥接方法）。
//     · 几何常量在 1.7.1 仍同名存在：SWITCH_WIDTH / SWITCH_OUTER_HEIGHT / SWITCH_INNER_HEIGHT /
//       SWITCH_TRACK_WIDTH / THUMB_RADIUS_UNCHECKED / THUMB_RADIUS_CHECKED / MIN_HEIGHT /
//       SELECTION_CONTROL_WIDTH / CONTROL_WIDTH / SLIDER_HEIGHT / CONTROL_SIZE / BAR_HEIGHT /
//       SELECTED_BAR_HEIGHT / UNSELECTED_BAR_HEIGHT / BAR_SEPARATOR_RADIUS / SEGMENT_BAR_PADDING。
//   → 下面的签名与尺寸对 1.7.1 直接适用。
//
// ----------------------------------------------------------------------------
// 1.1 官方签名原文（SwitchButton.kt:124，逐字）
//
// public fun SwitchButton(
//     checked: Boolean,
//     onCheckedChange: (Boolean) -> Unit,
//     modifier: Modifier = Modifier,
//     enabled: Boolean = true,
//     shape: Shape = SwitchButtonDefaults.switchButtonShape,
//     colors: SwitchButtonColors = SwitchButtonDefaults.switchButtonColors(),
//     contentPadding: PaddingValues = SwitchButtonDefaults.ContentPadding,
//     interactionSource: MutableInteractionSource? = null,
//     transformation: SurfaceTransformation? = null,
//     icon: @Composable (BoxScope.() -> Unit)? = null,
//     secondaryLabel: @Composable (RowScope.() -> Unit)? = null,
//     label: @Composable RowScope.() -> Unit,
// )
//
// 1.2 SplitSwitchButton（SwitchButton.kt:279）——本文件**不用**，因为它要求两个回调：
//
// public fun SplitSwitchButton(
//     checked: Boolean,
//     onCheckedChange: (Boolean) -> Unit,
//     toggleContentDescription: String?,
//     onContainerClick: () -> Unit,
//     modifier: Modifier = Modifier,
//     enabled: Boolean = true,
//     shape: Shape = SwitchButtonDefaults.splitSwitchButtonShape,
//     colors: SplitSwitchButtonColors = SwitchButtonDefaults.splitSwitchButtonColors(),
//     toggleInteractionSource: MutableInteractionSource? = null,
//     containerInteractionSource: MutableInteractionSource? = null,
//     transformation: SurfaceTransformation? = null,
//     containerClickLabel: String? = null,
//     contentPadding: PaddingValues = SwitchButtonDefaults.ContentPadding,
//     secondaryLabel: @Composable (RowScope.() -> Unit)? = null,
//     label: @Composable RowScope.() -> Unit,
// )
//   不用的理由：SplitSwitchButton 把胶囊劈成两块点击区（标签区走 onContainerClick，开关区走
//   onCheckedChange）。OfficialSwitchRow 的签名只有 onCheckedChange 一个回调，没有第二个回调可接；
//   toggleContentDescription 还是必填项（无默认值）。反过来用 SplitSwitchButton 就得凭空长出一个参数，
//   不是"换成官方组件"，是"改接口"。故用单点击区的 SwitchButton。
//
// 1.3 官方签名原文（Slider.kt:115，逐字）
//
// public fun Slider(
//     value: Float,
//     onValueChange: (Float) -> Unit,
//     steps: Int,
//     modifier: Modifier = Modifier,
//     decreaseIcon: @Composable () -> Unit = { SliderDefaults.DecreaseIcon() },
//     increaseIcon: @Composable () -> Unit = { SliderDefaults.IncreaseIcon() },
//     enabled: Boolean = true,
//     valueRange: ClosedFloatingPointRange<Float> = 0f..(steps + 1).toFloat(),
//     segmented: Boolean = steps <= MaxSegmentSteps,
//     shape: Shape = SliderDefaults.shape,
//     colors: SliderColors = SliderDefaults.sliderColors(),
// )
//   注意：`steps` 是"首末之间的离散档数"，档位 = steps + 1 段；库内 `require(steps >= 0)`。
//   本项目 SettingsSliderMath 的台阶换算（intervals = steps + 1）与之同义，可以直接对拷。
//
// 1.4 官方签名原文（RadioButton.kt:118，逐字）
//
// public fun RadioButton(
//     selected: Boolean,
//     onSelect: () -> Unit,
//     modifier: Modifier = Modifier,
//     enabled: Boolean = true,
//     shape: Shape = RadioButtonDefaults.radioButtonShape,
//     colors: RadioButtonColors = RadioButtonDefaults.radioButtonColors(),
//     contentPadding: PaddingValues = RadioButtonDefaults.ContentPadding,
//     interactionSource: MutableInteractionSource? = null,
//     transformation: SurfaceTransformation? = null,
//     icon: @Composable (BoxScope.() -> Unit)? = null,
//     secondaryLabel: @Composable (RowScope.() -> Unit)? = null,
//     label: @Composable RowScope.() -> Unit,
// )
//
// 1.5 SplitRadioButton（RadioButton.kt:279）——同样**不用**，理由同上：
//
// public fun SplitRadioButton(
//     selected: Boolean,
//     onSelectionClick: () -> Unit,
//     selectionContentDescription: String?,
//     onContainerClick: () -> Unit,
//     modifier: Modifier = Modifier,
//     enabled: Boolean = true,
//     shape: Shape = RadioButtonDefaults.splitRadioButtonShape,
//     colors: SplitRadioButtonColors = RadioButtonDefaults.splitRadioButtonColors(),
//     selectionInteractionSource: MutableInteractionSource? = null,
//     containerInteractionSource: MutableInteractionSource? = null,
//     transformation: SurfaceTransformation? = null,
//     containerClickLabel: String? = null,
//     contentPadding: PaddingValues = RadioButtonDefaults.ContentPadding,
//     secondaryLabel: @Composable (RowScope.() -> Unit)? = null,
//     label: @Composable RowScope.() -> Unit,
// )
//
// 1.6 官方几何常量原文（同一份源码里的私有常量，逐字）
//   SwitchButton.kt:682-695 / 1965-1973：
//     internal val LabelSpacerSize = 1.dp
//     private val HorizontalPadding = 14.dp
//     private val VerticalPadding = 8.dp
//     public val ContentPadding: PaddingValues =
//         PaddingValues(start = HorizontalPadding, top = VerticalPadding,
//                       end = HorizontalPadding, bottom = VerticalPadding)
//     private val SWITCH_WIDTH = 32.dp
//     private val SWITCH_OUTER_HEIGHT = 24.dp
//     private val SWITCH_INNER_HEIGHT = 22.dp
//     private val SWITCH_TRACK_WIDTH = 2.dp
//     private val THUMB_RADIUS_UNCHECKED = 6.dp
//     private val THUMB_RADIUS_CHECKED = 9.dp
//     private val TOGGLE_CONTROL_SPACING = 6.dp
//     private val ICON_SPACING = 6.dp
//     private val MIN_HEIGHT = 52.dp
//   RadioButton.kt:606-617 / 1474-1482：
//     private val HorizontalPadding = 14.dp
//     private val VerticalPadding = 8.dp
//     private val SELECTION_CONTROL_WIDTH = 32.dp
//     private val SELECTION_CONTROL_HEIGHT = 24.dp
//     private val SELECTION_CONTROL_SPACING = 6.dp
//     private val ICON_SPACING = 6.dp
//     private val MIN_HEIGHT = 52.dp
//     private val CONTROL_WIDTH = 24.dp
//     private val CONTROL_HEIGHT = 24.dp
//   Slider.kt:315-322 / 829-835：
//     public val IconSize: Dp = 24.dp
//     public val MaxSegmentSteps: Int = 8
//     private val SLIDER_HEIGHT = 52.dp
//     private val CONTROL_SIZE = 48.dp
//     private val BAR_HEIGHT = 12.dp
//     private val SELECTED_BAR_HEIGHT = 12.dp
//     private val UNSELECTED_BAR_HEIGHT = 4.dp
//     private val BAR_SEPARATOR_RADIUS = 2.dp
//     private val SEGMENT_BAR_PADDING = 4.dp
//   materialcore/SelectionControls.kt:696-698（单选圆点的真正落笔处）：
//     private val RADIO_CIRCLE_RADIUS = 9.dp
//     private val RADIO_CIRCLE_STROKE = 2.dp
//     private val RADIO_DOT_RADIUS = 5.dp
//
// ----------------------------------------------------------------------------
// 2. 官方组件的 token → 本项目实际颜色（WearMaterialTheme 映射后）
//   MaterialTheme.colorScheme 由 WearMaterialColorScheme 注入（primary 还会被 withAccent 换成
//   运行时强调色），所以 token 落地值如下（默认强调色 #4FC3F7）：
//     Primary                    → Wear.Palette.Accent（运行时强调色，默认 #4FC3F7）
//     PrimaryContainer           → accentSurfaceOf(accent) = lerp(Bg, accent, 0.425) ≈ #123A4C
//     OnPrimaryContainer         → Wear.Palette.Accent（同一枚强调色）
//     SurfaceContainer           → Wear.Palette.Surface   #0D1014
//     SurfaceContainerHigh       → Wear.Palette.SurfaceAlt #1B212A
//     OnSurface                  → Wear.Palette.TextPrimary  #F5F7FA
//     OnSurfaceVariant           → Wear.Palette.TextSecondary #A3ADBD
//     Outline                    → Wear.Palette.Border       #32363E
//     Background                 → Wear.Palette.Bg           #000000
//   Shape：SwitchButtonTokens.ContainerShape / RadioButtonTokens.Shape / SliderTokens.ContainerShape
//   都是 ShapeKeyTokens.CornerLarge = RoundedCornerShape(26.dp)，但本项目 WearShapes.large 覆盖成
//   Wear.Radius.Card = 24.dp → **实际渲染是 24dp 圆角，不是库默认的 26dp**。
//   Typography：SwitchButton/RadioButton 的标签走 LabelMedium = 15sp / 行高 18sp / weight 500 /
//   字距 +0.4sp；副标题走 LabelSmall = 13sp / 16sp / 500 / +0.4sp。本项目 WearTypography 没有覆盖
//   这两个槽位，所以就是官方值。
//
// ----------------------------------------------------------------------------
// 3. 换官方后的可见差异（真机对比图用，逐条对上）
//
// 3.1 OfficialSwitchRow（官方 SwitchButton） vs SettingsCompactSwitch（SettingsSwitchRow.kt:57-88）
//   行容器：无 → 整个控件是一枚**可点的胶囊**：minHeight 52dp（MIN_HEIGHT）、宽度 fillMaxWidth、
//          圆角 24dp（shapes.large）、底色 token UncheckedContainerColor = SurfaceContainer #0D1014。
//          行底色、行内 14dp 左右槽线（SettingsRowSidePadding）由原来的 SettingsCardRow 给，
//          现在由官方 contentPadding 给（HorizontalPadding=14dp，两者数值相同 → 左槽线不跳位）。
//          卡片底色是 SettingsCardSurface #2A313C，所以胶囊读起来是"更深的一层内嵌"，而不是原来的
//          "卡片本身就是行"。组内 1dp 发丝线（SettingsGroupHairline）没有对应物：官方一行一枚胶囊。
//   开关尺寸：44×26dp 手搓胶囊 → 32×24dp（SWITCH_WIDTH × SWITCH_OUTER_HEIGHT），内高 22dp，
//          轨边框 2dp（SWITCH_TRACK_WIDTH）。
//   滑块：18dp 圆点、左边距 4dp 起、动画位移 → 未选中半径 6dp（12dp 点）、选中半径 9dp（18dp 点），
//          位置由轨道长度与选中态共同决定；**选中时轨道上多画一个勾**（animateTick，4dp/8dp 折线，
//          颜色 CheckedThumbIconColor = Primary），手搓开关没有这个元素。
//          轨边框：官方只在 trackColor != trackBorderColor 时描边（未选中 → 2dp Outline #32363E；
//          选中时两者同为 Primary → 边框透明，不再有"1dp accent 描边"那一圈）。
//   选中配色：轨道 Wear.Accent.copy(alpha=0.82f) + 1dp accent 边框、滑块 Wear.Palette.Orb #CDD3DC
//          → 轨道 Primary（= accent 实心、无描边）、胶囊底 PrimaryContainer ≈#123A4C、
//          滑块 PrimaryContainer、勾 Primary、**整行标签文字 OnPrimaryContainer = accent**。
//          即"选中时文字也变强调色"，这是最容易被用户一眼看出来的变化。
//   未选中配色：轨道 SurfaceAlt #1B212A + 1dp Border #32363E、滑块 TextSecondary #A3ADBD
//          → 轨道 SurfaceContainer #0D1014 + 2dp Outline #32363E、滑块 Outline #32363E
//          （未选中的滑块从"浅灰"变成"描边灰"，对比度明显下降）、标签 OnSurface #F5F7FA。
//   文字：15sp / Normal / 无字距（Wear.TextBody 走 bodyMedium + withWearFontScale）
//          → 15sp / weight 500 / 字距 +0.4sp，且**不跟随 WearAppearance.fontScale**
//          （官方组件用 token 字号，不经过 Wear.Text* 的 withWearFontScale）。
//   触感：无 → HapticFeedbackType.ToggleOn / ToggleOff（SwitchButton.kt:148-152）。
//   按下反馈：手搓行 indication = null（只有行底色变化）→ 官方 ripple()。
//   副标题：15sp TextSecondary、可换行画在行下（subtitleBelowRow）
//          → LabelSmall 13sp、OnSurfaceVariant #A3ADBD、选中时 OnPrimaryContainer @0.9 alpha、
//          最多 2 行、永远在标签正下方（官方 Labels() 固定排法，没有"画到行下"的那套规则）。
//
// 3.2 OfficialSliderRow（官方 Slider） vs SettingsThinSlider（SettingsSliderRow.kt:118-193）
//   高度：24dp 触控带（轨道 6dp 居中）→ 固定 52dp（SLIDER_HEIGHT），且库内强制
//          `modifier.fillMaxWidth()`（Slider.kt:136），调用方塞宽度 modifier 也会被它盖掉。
//   轨道：6dp 单条、已选 Accent、未选 SurfaceAlt #1B212A
//          → 已选 12dp（SELECTED_BAR_HEIGHT）、未选 4dp（UNSELECTED_BAR_HEIGHT）；
//          已选 SelectedBarColor = Primary；未选 UnselectedBarColor = Background #000000 @0.3 alpha
//          → 压在 #0D1014 上几乎看不见，**这是必须上真机确认的一项**。
//   分段：自绘台阶、无分隔标记
//          → segmented = true 时画 steps 个 4dp 圆点（BAR_SEPARATOR_RADIUS=2dp）：已选段
//          PrimaryContainer ≈#123A4C、未选段 Primary @0.5；分段宽度 = steps + 1。
//   滑块指示：18dp 圆点 + 2dp 外圈，跟随手指
//          → **没有圆点**；位置只由已选段长度表达。
//   输入方式：轨道上任意点按 + 横向拖动（detectTapGestures / detectHorizontalDragGestures）
//          → 轨道**不可点、不可拖**（Slider.kt 里没有任何 pointerInput/draggable）；只能点两端
//          各 48dp（CONTROL_SIZE）的 ± 按钮，`repeatableClickable` 支持长按连发。
//          在 176dp 卡内（减去 2×14dp = 148dp 可用宽）两个 48dp 按钮吃掉 96dp，
//          **轨道只剩 ~52dp**，9 段分隔点会挤在 52dp 里 —— 真机必看项。
//   图标：无 → 24dp 的减/加图标（SliderDefaults.IconSize），色 OnSurface #F5F7FA。
//   容器：无（行内一条轨）→ 胶囊底 SurfaceContainer #0D1014、圆角 24dp。
//   数值文本：右槽 15sp TextSecondary（valueText 参数）
//          → **官方 Slider 没有任何文本槽**，官方签名里没有 value/label 参数；
//          所以本封装把 label/secondary 排在 Slider 上方（labelMedium / labelSmall），
//          数值必须由调用方另画 —— 接线的第一件事就是补这个数值文本。
//   触感：无 → ± 每步 SegmentFrequentTick，到端点 GestureEnd（Slider.kt:147-149）。
//   无障碍：手搓只挂了 pointerInput（无 role、无 RangeInfo）
//          → rangeSemantics(ProgressBarRangeInfo) + 两个 role=Button 的 ± 按钮
//          （文案取 SliderDefaults.increase/decreaseIconContentDescription 本地化字符串）。
//
// 3.3 OfficialRadioRow（官方 RadioButton） vs SettingsRadioDot（SettingsRadioRow.kt:72-99）
//   圆点几何：18dp 盒子 + 2dp 描边画在盒内（Modifier.border）+ 选中点直径 9dp（SettingsRadioDotSize/2）
//          → 环半径 9dp（RADIO_CIRCLE_RADIUS = 9.dp）、描边 2dp 沿半径居中（Stroke(2.dp)）→ 可见外径
//          20dp / 内径 16dp；选中点半径 5dp（RADIO_DOT_RADIUS = 5.dp）→ 直径 10dp。
//          **环比手搓大 2dp（外径 20 vs 18），实心点多 1dp（直径 10 vs 9）** —— 对比图看这一点。
//   圆点颜色：选中 Wear.Accent（危险项 Danger #FF6B6B）、未选 TextMuted #7C8798
//          → 环与点同色：选中 Primary（= accent）、未选 Outline #32363E
//          （未选中从"最暗一档可读文字色"变成"发丝线色"，环会更淡）。
//   圆点槽位：直接是行尾（18dp + 间距）
//          → 选择控件占 32×24dp 槽（SELECTION_CONTROL_WIDTH/HEIGHT），24×24dp 的绘制区在槽内右对齐，
//          与文字之间固定 6dp（SELECTION_CONTROL_SPACING）。
//   行容器：无 → 胶囊：minHeight 52dp、圆角 24dp、选中底 PrimaryContainer ≈#123A4C、
//          未选底 SurfaceContainer #0D1014（卡片 #2A313C 上的更深内嵌）。
//   选中文字：永远是 TextPrimary → 选中时 OnPrimaryContainer = accent（整行文字变色）。
//   副标题：15sp TextSecondary / 可带节奏色块（subtitleStatus）→ LabelSmall 13sp、
//          OnSurfaceVariant #A3ADBD、选中时 OnPrimaryContainer @0.9、最多 2 行。
//   危险语义：dangerSelected 只染那颗点 → 官方只有 colors 一个通道，本封装签名里没有 colors，
//          所以**危险选项的红色语义在这一版封装里会丢**（要保留就得给 OfficialRadioRow 加
//          `colors: RadioButtonColors` 参数，那是改接口，不在本次范围）。
//   预设色预览：未选中时留一颗 dotColor @0.75 小点 → 官方无此能力（同上）。
//   触感：无 → HapticFeedbackType.ContextClick（RadioButton.kt:143）。
//
// 4. 本文件只提供组件，**不接线**（设置页仍在用手搓三件）。
// ============================================================================

/**
 * 开关行：官方 [SwitchButton]（整行一枚可点胶囊，单点击区）。
 *
 * 为什么不是 `SplitSwitchButton`：它要求 `onContainerClick` + 必填 `toggleContentDescription`，
 * 与本行的"一个布尔值一个回调"不匹配（签名原文见文件头 1.2）。
 *
 * @param label 主标签，走官方 `labelMedium`（15sp/18sp、500、+0.4sp），最多 3 行、尾部省略。
 * @param secondary 副标题，走官方 `labelSmall`（13sp），最多 2 行；null 或空串时不占槽。
 * @param modifier 追加在官方控件之前；内部再补 `fillMaxWidth()`，否则官方 `width(IntrinsicSize.Max)`
 *   会按标签不换行的固有宽度铺开，长标签在 176dp 卡里会溢出。
 */
@Composable
fun OfficialSwitchRow(
    label: String,
    secondary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val secondaryLabel: (@Composable RowScope.() -> Unit)? =
        if (secondary.isNullOrEmpty()) {
            null
        } else {
            { Text(text = secondary) }
        }
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth(),
        secondaryLabel = secondaryLabel,
        label = { Text(text = label) }
    )
}

/**
 * 数值行：官方 [Slider]（`segmented = true`，两端 48dp 的 ± 按钮）。
 *
 * 官方 Slider 没有文本槽，所以标签与说明按官方字号排在滑轨上方；**数值本身必须由调用方另画**
 * （官方签名里没有 value 文本参数）。
 *
 * @param steps 首末之间的离散档数：档位 = steps + 1 段；`segmented` 时画 steps 个分隔点。
 * @param valueRange 取值区间，`value` 越界会被官方库夹取。
 */
@Composable
fun OfficialSliderRow(
    label: String,
    secondary: String? = null,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        // 4dp 与官方标签/副标题之间的节奏一致（SwitchButton 的 LabelSpacerSize 只有 1dp，
        // 那是胶囊内部的两行间距；这里是"行文本 + 控件"的块间距，取 4dp 网格）。
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (!secondary.isNullOrEmpty()) {
            Text(
                text = secondary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            steps = steps,
            valueRange = valueRange,
            segmented = true
        )
    }
}

/**
 * 单选行：官方 [RadioButton]（整行一枚可点胶囊，单点击区）。
 *
 * 为什么不是 `SplitRadioButton`：同上，它要 `onSelectionClick` + `onContainerClick` 两个回调
 * 和一个必填的 `selectionContentDescription`（签名原文见文件头 1.5）。
 *
 * 注意：官方单选控件只有 `colors` 一个语义通道，本签名不暴露它 —— 也就是说
 * `SettingsRadioRow` 的 `dangerSelected` / `dotColor` 预览能力**在这一版封装里没有对应物**
 * （见文件头 3.3）。
 */
@Composable
fun OfficialRadioRow(
    label: String,
    secondary: String? = null,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val secondaryLabel: (@Composable RowScope.() -> Unit)? =
        if (secondary.isNullOrEmpty()) {
            null
        } else {
            { Text(text = secondary) }
        }
    RadioButton(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        secondaryLabel = secondaryLabel,
        label = { Text(text = label) }
    )
}
