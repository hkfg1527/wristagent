package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 数值行与它的细滑轨。
 *
 * 从 `SettingsShared.kt`（2000+ 行的上帝文件）拆出来：滑杆是一个完整的小部件 ——
 * 三条几何常量、触摸换算、绘制全在这里，与"设置页的其它行长什么样"无关。
 */
/** 细滑轨的几何常量（轨道 / 滑块 / 外圈）钉在 [SettingsSliderMath]，测试直接钉死防回漂。 */
private val SliderTrackHeight: Dp = SettingsSliderMath.TrackHeightDp.dp

private val SliderThumbSize: Dp = SettingsSliderMath.ThumbSizeDp.dp

private val SliderThumbRing: Dp = SettingsSliderMath.ThumbRingDp.dp

/** 滑轨的触控带高度：轨道只有 6dp，手指需要更高的一块命中区。 */
private val SliderTouchHeight: Dp = 24.dp

/**
 * 数值行：一条 6dp 细轨 + 18dp 圆形滑块，细粒度取值由 [SettingsValueMath] 量化。
 *
 * 标签 / 数值 / 滑轨三件，整体是组卡片里的一行：圆角与 14dp 内边距由 [SettingsCardRow] 统一给。
 */
@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
    /** 可选的第二行说明：滑杆最需要的恰恰是"这个数字到底改变什么"。 */
    secondary: String? = null,
    modifier: Modifier = Modifier
) {
    // 官方分段滑杆：没有文本槽，数值由调用点另画（本函数只负责控件本身）。

    // 终端档：滑杆换成数值条，且画在**标签下方**（belowContent 槽）。
    // 以前塞在 trailing，把标题列压成每行一个字（0.0.0.138 真机截图）。
    if (com.example.myapplication.presentation.ui.settings.stylekit.TerminalPalette.isTerminal(
            com.example.myapplication.presentation.ui.settings.WearAppearance.style
        )
    ) {
        SettingsSlotRow(
            title = label,
            subtitle = secondary,
            modifier = modifier,
            belowContent = {
                androidx.compose.foundation.text.BasicText(
                    text = com.example.myapplication.presentation.ui.settings.stylekit.TerminalText.valueBar(
                        value, valueRange.start, valueRange.endInclusive, cells = 6
                    ),
                    style = androidx.compose.ui.text.TextStyle(
                        color = com.example.myapplication.presentation.ui.settings.stylekit.TerminalPalette.Foreground,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp)
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
        return
    }

    if (SettingsStylePolicy.current().slider == SettingsSliderControl.OFFICIAL_SEGMENTED) {
        OfficialSliderRow(
            label = label,
            secondary = secondary,
            value = value,
            valueRange = valueRange,
            steps = steps,
            onValueChange = onValueChange,
            modifier = modifier
        )
        return
    }
    // 语义边界：卡组外层是 Card(onClick = {})（可点 → 会 mergeDescendants），
    // 滑杆行本身不可点，不设边界就会被整张卡吸进同一个无障碍节点 ——
    // 0.0.0.88 实测：外面那张卡把「底栏自动收起延时」与「表冠滚动增益」两行并成一个节点，
    // 区间取前者的，第二个滑杆的 SetProgress 直接丢失，读屏拨不动它。
    // 加一个空语义合并边界后，本行独立成节点，两行各读各的、各拨各的。
    SettingsCardRow(modifier = modifier.semantics(mergeDescendants = true) {}) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = SettingsRowLabelStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(SettingsLabelValueGap))
                // 数值进右槽：与所有行的取值落在同一条右槽线上，不再用 titleMedium 抢焦点
                BasicText(
                    text = valueText,
                    modifier = Modifier.widthIn(max = SettingsValueMaxWidth),
                    style = SettingsRowValueStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (secondary != null) {
                // 与开关行/导航行同一条副标题渲染路径：位置规则（同行 or 换行）由它统一决定
                SettingsSubtitleLine(
                    subtitle = secondary,
                    subtitleMaxLines = 2,
                    subtitleStatus = null,
                    belowRow = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            SettingsThinSlider(
                label = label,
                value = value,
                valueRange = valueRange,
                steps = steps,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 细滑轨 + 圆形滑块：已选段走运行时强调色，未选段走 SurfaceAlt；
 * 滑块 18dp 填充 TextPrimary，外圈 2dp 用轨道色，视觉上"压在轨道上"而不是融进去。
 *
 * 为什么不用官方 Slider：Wear M3 的 Slider 是"52dp 分段胶囊 + 两端 ± 图标"，整块铺满强调色，
 * 眯眼看是页面上最大的一块实色 —— 强调色在这里是重音，不该铺面积。轨道因此自己画、拖拽自己接。
 *
 * 无障碍：自绘不挂语义的后果是"读屏读不到、也拨不动"，所以这里补三件
 * （[contentDescription] 给名字、[ProgressBarRangeInfo] 给区间、[SemanticsActions.SetProgress] 让读屏能拨），
 * 与官方 Slider 的 rangeSemantics 同义。视觉一分不改。
 */
@Composable
private fun SettingsThinSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBarColor = Wear.Accent
    val unselectedBarColor = Wear.Palette.SurfaceAlt
    val thumbColor = Wear.Palette.TextPrimary
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderTouchHeight)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                setProgress { target ->
                    onValueChange(target)
                    true
                }
            }
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset ->
                    onValueChange(
                        settingsSliderValueAt(
                            xPx = offset.x,
                            widthPx = size.width.toFloat(),
                            thumbRadiusPx = SliderThumbSize.toPx() / 2f,
                            valueRange = valueRange,
                            steps = steps
                        )
                    )
                }
            }
            .pointerInput(valueRange, steps) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange(
                        settingsSliderValueAt(
                            xPx = change.position.x,
                            widthPx = size.width.toFloat(),
                            thumbRadiusPx = SliderThumbSize.toPx() / 2f,
                            valueRange = valueRange,
                            steps = steps
                        )
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeightPx = SliderTrackHeight.toPx()
            val thumbRadiusPx = SliderThumbSize.toPx() / 2f
            val centerY = size.height / 2f
            val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
            val thumbCenterX = thumbRadiusPx + usableWidth * fraction
            val trackTop = centerY - trackHeightPx / 2f
            drawRoundRect(
                color = unselectedBarColor,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )
            drawRoundRect(
                color = selectedBarColor,
                topLeft = Offset(0f, trackTop),
                size = Size(thumbCenterX, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbCenterX, centerY)
            )
            drawCircle(
                color = unselectedBarColor,
                radius = thumbRadiusPx - SliderThumbRing.toPx() / 2f,
                center = Offset(thumbCenterX, centerY),
                style = Stroke(width = SliderThumbRing.toPx())
            )
        }
    }
}
