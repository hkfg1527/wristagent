package com.example.myapplication.presentation.ui.settingsv2.common

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 圆屏收边：**等比缩小**，绝不改宽度比例。
 *
 * 为什么改（2026-11-27，用户原话）：
 * 「它可以整体缩小，它为什么一定要形变呢？让元素都挤压在一起……至少有得有个线度，不能完全挤压吧。」
 * 之前的实现是**只收宽度**（左右 padding 到弦长，高度不变）—— 于是靠近上下两端的行会被压扁成
 * 宽扁的条，比例失真、观感是"被挤了一下"。现在改成按弦长算一个 0~1 的**等比**系数，
 * 用 `graphicsLayer` 同时缩两轴：元素永远保持自己的长宽比，只是变小。
 *
 * 三道边界，缺一不可：
 * 1. **下限**：缩到 `minScale` 就不再缩（各风格自己的 `edgeScaleMin`，0.76~0.94）——
 *    这就是用户说的"至少得有个限度，不能完全挤压"；
 * 2. **取最窄处**：用元素上下两条边与中线三处弦长的**最小值**，保证整块都落在圆内，
 *    而不是只有中心点刚好贴边；
 * 3. **只做图形层缩放**：不改布局宽高，所以文本排版宽度不变、不会因此多出省略号。
 */
@Composable
fun CircleFit(
    modifier: Modifier = Modifier,
    /** 最小等比系数（= 该风格的 `edgeScaleMin`）。低于它就不再缩。 */
    minScale: Float = 0.76f,
    content: @Composable () -> Unit,
) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = with(density) { cfg.screenWidthDp.dp.toPx() }
    val screenH = with(density) { cfg.screenHeightDp.dp.toPx() }

    // 本项上下边界（根坐标）；初始放进屏幕正中，避免首帧算出极端值
    var top by remember { mutableStateOf(screenH / 2f - 24f) }
    var bottom by remember { mutableStateOf(screenH / 2f + 24f) }

    val scale = circleFitScale(top, bottom, screenW, screenH, minScale)

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val pos = coords.positionInRoot()
                if (coords.size.height > 0) {
                    top = pos.y
                    bottom = pos.y + coords.size.height
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        content()
    }
}

/** 竖直位置 y 处的可用弦长（像素）。圆外返回 0。 */
fun chordWidthAt(y: Float, screenW: Float, screenH: Float): Float {
    val radius = min(screenW, screenH) / 2f
    val dy = abs(y - screenH / 2f)
    return if (dy < radius) 2f * sqrt(radius * radius - dy * dy) else 0f
}

/**
 * 纯函数版：给定元素的上下边界，返回它的等比缩放系数。
 *
 * 取 上边 / 中线 / 下边 三处弦长的**最小值**再除以屏宽，然后夹在 `[minScale, 1]`。
 * 单独拿出来是为了能直接单测几何，不必渲染界面。
 */
fun circleFitScale(
    top: Float,
    bottom: Float,
    screenW: Float,
    screenH: Float,
    minScale: Float = 0.76f,
): Float {
    if (screenW <= 0f || screenH <= 0f) return 1f
    val mid = (top + bottom) / 2f
    val narrowest = minOf(
        chordWidthAt(top, screenW, screenH),
        chordWidthAt(mid, screenW, screenH),
        chordWidthAt(bottom, screenW, screenH),
    )
    return (narrowest / screenW).coerceIn(minScale.coerceIn(0.1f, 1f), 1f)
}
