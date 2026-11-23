package com.example.myapplication.presentation.ui.settingsv2.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 圆屏收边：让内容**吃满圆屏横向面积**，同时不被圆弧切掉。
 *
 * 为什么需要它：圆屏在竖直方向不同高度的可用弦长不同 —— 中心处等于直径，越靠近上下两端越窄。
 * 之前两种做法都不对：
 * ①定宽矩形 —— 两侧大片死黑，用户原话"裁成一个正方形了，我拿它干什么"；
 * ②满宽矩形 —— 顶部/底部被圆弧吃掉，真机截图里 banner 被切成 `1stagent`、底栏 `[风格]` 被切一半。
 *
 * 做法：每项自己上报竖直中心位置，按圆方程算出该高度处的可用弦长，把差值作为左右内缩。
 * 滚动时位置变化 → 自动收放，形成"中间宽、上下窄"的领结形。
 */
@Composable
fun CircleFit(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val cfg = LocalConfiguration.current
    val density = LocalDensity.current
    val screenW = with(density) { cfg.screenWidthDp.dp.toPx() }
    val screenH = with(density) { cfg.screenHeightDp.dp.toPx() }

    // 本项竖直中心（根坐标）；初始给屏幕中心，避免首帧算出极端内缩
    var centerY by remember { mutableFloatStateOf(screenH / 2f) }

    // 收边**必须有上限**：纯按圆方程算，最顶上那一行会缩到几乎为 0 宽
    // （Robolectric 实测：首屏 `连得上` 宽度不足、直接显示不出来）。
    // 真手表应用的做法是"保证最小可读宽度，允许轻微被圆弧压边"，
    // 所以这里最多收到屏宽的 18%：中间满宽、两端收但永远有 64% 可用。
    val maxInset = screenW * 0.18f
    val insetPx = circleInsetPx(centerY, screenW, screenH).coerceAtMost(maxInset)

    Box(
        modifier = modifier
            .onGloballyPositioned { coords ->
                centerY = coords.positionInRoot().y + coords.size.height / 2f
            }
            .padding(horizontal = with(density) { insetPx.toDp() }),
    ) {
        content()
    }
}

/**
 * 纯函数版：给定竖直中心与屏幕尺寸，返回该高度应有的左右内缩（像素）。
 * 单独拿出来是为了能直接单测圆几何，不必渲染界面。
 */
fun circleInsetPx(centerY: Float, screenW: Float, screenH: Float): Float {
    val radius = min(screenW, screenH) / 2f
    val dy = abs(centerY - screenH / 2f)
    val halfChord = if (dy < radius) sqrt(radius * radius - dy * dy) else 0f
    return (radius - halfChord).coerceAtLeast(0f)
}
