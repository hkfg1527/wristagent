package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.theme.Wear

/** 组内行：曲面背景由外层 [SettingsBalloonGroup] 统一绘制，本行不再画自己的卡。 */
internal val LocalSettingsGroupChrome = compositionLocalOf { false }

/** 当前主气球在屏幕上的 y 与高度；背景路径只画一次。 */
@Stable
internal class SettingsGroupGeometryState(
    topPx: Float,
    heightPx: Float
) {
    var topPx by mutableFloatStateOf(topPx)
    var heightPx by mutableFloatStateOf(heightPx)
}

/**
 * 一组卡片：只画一张闭合超椭圆气球。
 *
 * 背景只画一次，宽度沿屏幕圆周的弦长连续变化；行内容只按所在高度等比缩小，绝不放大。
 * 因此不存在行间接缝，也没有“侧边一条曲线 + 封口另一条曲线”的双重轮廓。
 */
@Composable
fun SettingsBalloonGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
    val screenMarginPx = with(density) { SettingsCurveMath.ScreenEdgeMarginDp.dp.toPx() }
    val capHeightPx = with(density) { SettingsCurveGeometry.CapHeightDp.dp.toPx() }
    val state = remember(screenHeightPx) {
        SettingsGroupGeometryState(
            topPx = screenHeightPx / 2f,
            heightPx = 0f
        )
    }
    // 每帧重建轮廓时复用同一个 Path：滚动掉帧主要来自逐帧分配，不是 GPU
    val reusablePath = remember { Path() }
    val brush = remember {
        Brush.verticalGradient(colors = listOf(Wear.Palette.SurfaceAlt, Wear.Palette.Surface))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                state.topPx = coordinates.positionInRoot().y
                state.heightPx = coordinates.size.height.toFloat()
            }
            .drawWithContent {
                if (WearAppearance.style != SettingsStyle.BALLOON) {
                    // 方角与大卡片档自己画外壳，这里只放内容。
                    drawContent()
                    return@drawWithContent
                }
                val path = SettingsCurveGeometry.buildGroupPath(
                    path = reusablePath,
                    size = size,
                    topInRootPx = state.topPx,
                    screenHeightPx = screenHeightPx,
                    screenMarginPx = screenMarginPx,
                    capHeightPx = capHeightPx
                )
                val highlightEnd = if (size.height < 320.dp.toPx()) size.height else 320.dp.toPx()
                drawPath(path = path, brush = brush)
                drawContent()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CompositionLocalProvider(LocalSettingsGroupChrome provides true) {
            content()
        }
    }
}

/** 连续卡片行在列表里的分段：组标题与卡外行各自断开气球。 */
internal data class SettingsPageChunk(
    val indices: List<Int>,
    val grouped: Boolean
)

internal object SettingsPageChunking {
    fun chunks(kinds: List<SettingsRowEntryKind>): List<SettingsPageChunk> {
        // 一页只保留一个主气球：分区标题、卡片行与卡外动作块都装进同一个曲面。
        if (kinds.isEmpty()) return emptyList()
        return listOf(SettingsPageChunk(kinds.indices.toList(), grouped = true))
    }
}
