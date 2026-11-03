package com.example.myapplication.presentation.ui.chat

/**
 * 思考胶囊折叠态的几何常量。
 *
 * 折叠态是一条**细标签**：高度只有正文行高的一倍半左右，四角全圆（= 高度的一半），
 * 字号比正文小一档。数值集中在这里，才能让"够不够扁"被单测钉住，
 * 而不是散落在 Composable 的修改器链里随手改大 —— 之前 `heightIn(min = 44dp)`
 * 是点击区尺寸，却把视觉高度一起顶到了 44dp，胶囊就此立成了一块卡片。
 */
object ThoughtPillMetrics {
    /** 折叠态视觉高度（dp）：取 28~32 区间的中值，正好是正文一行的 1.5 倍。 */
    const val CollapsedHeightDp = 30

    /** 折叠态左右内边距（dp）：贴着 Wear.Space.Md，落在 10~12 区间。 */
    const val HorizontalPaddingDp = 12

    /** 折叠态字号（sp）：细标签档，比正文 bodyMedium 小一档。 */
    const val LabelFontSp = 12

    /** 全圆角：圆角恒等于高度的一半，高度变了圆角跟着变。 */
    fun cornerRadiusDp(heightDp: Int = CollapsedHeightDp): Float = heightDp / 2f
}
