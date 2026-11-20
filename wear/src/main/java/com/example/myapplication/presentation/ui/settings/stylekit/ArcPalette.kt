package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.appearance.SettingsStyle

/**
 * 弧档的招牌特征：**弧形分隔线**。
 *
 * 0.0.0.157 体检发现 ARC 此前"没有任何弧线，看起来就是方角档" —— 也就是说这套风格只存在于枚举里。
 * 这里给它一条真正的弧：每行底部一条从两端向下弯的曲线（屏幕是圆的，弧度顺着圆边走）。
 *
 * 为什么选它而不是"弧形卡片"：卡片要沿弧裁切会动到宽度/不重叠这两条硬约束，
 * 而弧形分隔线只改绘制、不动布局 —— 先让风格"看得见"，再谈更激进的排布。
 */
object ArcPalette {

    /** 弧线颜色：比正文暗一档的冷灰，避免抢注意力。 */
    val Rule = Color(0xFF3A4048)

    /** 弧的垂度：中点比两端低多少。 */
    val Sag: Dp = 5.dp

    /** 两端内缩，避免贴到圆屏边缘。 */
    val Inset: Dp = 18.dp

    /** 线宽。 */
    val Width: Dp = 1.dp

    fun isArc(style: SettingsStyle): Boolean = style == SettingsStyle.ARC
}

/**
 * 在内容底部画一条向下弯的弧线（[ArcPalette.Sag] 为垂度）。
 *
 * 注意：线画在自身边界**之内**（`size.height - sag` 到 `size.height`），不越界，
 * 所以不会影响相邻行、也不会触发"卡片不重叠"判据。
 */
fun Modifier.arcRule(): Modifier = this.drawBehind {
    val inset = ArcPalette.Inset.toPx()
    val sag = ArcPalette.Sag.toPx()
    val path = Path().apply {
        moveTo(inset, size.height)
        quadraticBezierTo(size.width / 2f, size.height - sag * 2f, size.width - inset, size.height)
    }
    drawPath(path = path, color = ArcPalette.Rule, style = Stroke(width = ArcPalette.Width.toPx()))
}
