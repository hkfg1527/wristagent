package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ui.theme.Wear

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

    /**
     * 圆角取令牌 [Wear.Radius.Pill]，不再在页面里算"高度一半"。
     *
     * 令牌宣称两档、代码里却并存四种圆角（12 / 15 / 24 / 999）—— 15dp 这一档的来历没人说得清，
     * 于是把它换成显式的全圆例外，改的人一眼能看出这是"胶囊"而不是又一种圆角档位。
     */
    fun cornerRadiusDp(): Float = Wear.Radius.Pill.value
}
