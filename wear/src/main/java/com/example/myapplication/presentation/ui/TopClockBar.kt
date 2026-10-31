package com.example.myapplication.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.curvedText
import com.example.myapplication.presentation.ui.theme.Wear

/** 弧心角度：0° 为 3 点钟方向、角度顺时针递增，270° 即屏幕正上方。 */
private const val CLOCK_ANCHOR_DEGREES = 270f

/** 时钟可用弧长上限：正上方 ±60°（合计 120°），保证文字绝不铺到圆屏两侧被裁。 */
private const val CLOCK_MAX_SWEEP_DEGREES = 120f

/**
 * 顶部时间：官方 Wear 曲面文字 (CurvedLayout + M3 curvedText)，沿屏幕圆形外缘贴在正上方。
 * 字号/字重/颜色全部取自设计令牌，不做任何硬编码。
 */
@Composable
fun TopClockBar(
    timeText: String,
    modifier: Modifier = Modifier
) {
    CurvedLayout(
        modifier = modifier,
        anchor = CLOCK_ANCHOR_DEGREES,
        radialAlignment = CurvedAlignment.Radial.Outer
    ) {
        curvedText(
            text = timeText,
            modifier = CurvedModifier.padding(
                outer = Wear.Space.Sm,
                inner = 0.dp,
                before = 0.dp,
                after = 0.dp
            ),
            maxSweepAngle = CLOCK_MAX_SWEEP_DEGREES,
            // 默认角向（Normal）即为贴合顶缘的 ∩ 弧：两端下垂、中间最高，且字形正立
            color = Wear.Palette.TextPrimary,
            fontSize = Wear.TextDisplay.fontSize,
            fontWeight = Wear.TextDisplay.fontWeight
        )
    }
}
