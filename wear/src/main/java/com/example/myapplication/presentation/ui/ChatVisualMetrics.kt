package com.example.myapplication.presentation.ui

/**
 * 聊天页的几何常量与纯运算：气泡与主键图形的尺寸都从这里取，
 * 让"气泡不许贴圆周""主键图形不小于圆的 40%"这类约束可以被单测钉死，而不是靠截图目测。
 */
object ChatVisualMetrics {
    /** 气泡与助手卡片的最大宽度（dp）：233dp 屏左右各留 24.5dp（旧值 200dp 只剩 3.4dp）。 */
    const val BubbleMaxWidthDp = 184f

    /** 气泡内边距（dp）：四边对称，不再上下 16.5 / 14.5。 */
    const val BubblePaddingDp = 14f

    /** 主键图形盒（dp）：52dp 圆的 42%（旧值 16×11.5dp ≈ 31%）。 */
    const val OrbGlyphDp = 22f

    /** 主键圆形直径（dp）：与 Wear.Size.Orb 同值。 */
    const val OrbDiameterDp = 52f

    /** 气泡到屏幕左右边缘的余量（dp）。 */
    fun sideGutterDp(
        screenWidthDp: Float,
        bubbleWidthDp: Float = BubbleMaxWidthDp
    ): Float = ((screenWidthDp - bubbleWidthDp) / 2f).coerceAtLeast(0f)

    /** 气泡内文字可用宽度（dp）。 */
    fun bubbleTextWidthDp(
        bubbleWidthDp: Float = BubbleMaxWidthDp,
        paddingDp: Float = BubblePaddingDp
    ): Float = (bubbleWidthDp - paddingDp * 2f).coerceAtLeast(0f)

    /** 主键图形占圆的直径比例。 */
    fun orbGlyphRatio(
        orbDiameterDp: Float = OrbDiameterDp,
        glyphDp: Float = OrbGlyphDp
    ): Float = if (orbDiameterDp <= 0f) 0f else (glyphDp / orbDiameterDp).coerceAtMost(1f)
}
