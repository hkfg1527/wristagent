package com.example.myapplication.presentation.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.pow

/** sRGB 相对亮度，按 WCAG 2.1 定义计算。 */
fun relativeLuminance(color: Color): Float {
    fun linearize(channel: Float): Float =
        if (channel <= 0.03928f) channel / 12.92f else ((channel + 0.055f) / 1.055f).pow(2.4f)

    return 0.2126f * linearize(color.red) +
        0.7152f * linearize(color.green) +
        0.0722f * linearize(color.blue)
}

/**
 * 两色的对比度，取值 1.0 ~ 21.0，与前景/背景顺序无关。
 * 正文文字要求 >= 4.5 才能满足 WCAG AA。
 */
fun contrastRatio(first: Color, second: Color): Float {
    val a = relativeLuminance(first)
    val b = relativeLuminance(second)
    val lighter = if (a >= b) a else b
    val darker = if (a >= b) b else a
    return (lighter + 0.05f) / (darker + 0.05f)
}
