package com.example.myapplication.presentation.ui.settings.stylekit

/**
 * 终端档的文本形态（`docs/UI-REBUILD-PLAN.md` §3.6）。
 *
 * 放在 stylekit 而不是渲染文件里，是为了让"前缀怎么加、加几层"能被单测钉住 ——
 * 终端档最容易出的事故就是前缀被重复拼（`> > 标题`）或把用户自己输入的 `>` 也当成前缀。
 */
object TerminalText {

    /** 普通行前缀。 */
    const val ROW_PREFIX = "> "

    /** 选中 / 当前行前缀（用两个尖括号表示"这条在焦点上"）。 */
    const val ACTIVE_PREFIX = ">> "

    /**
     * 给一行标题加终端前缀。
     *
     * 已经是前缀形态（`> ` / `>> `）就不再叠加：终端档在子页与根页共用同一套行渲染，
     * 一处拼错就会变成 `> > 标题`。
     */
    fun decorate(title: String, isTerminal: Boolean, active: Boolean = false): String {
        if (!isTerminal) return title
        val prefix = if (active) ACTIVE_PREFIX else ROW_PREFIX
        if (title.startsWith(ACTIVE_PREFIX) || title.startsWith(ROW_PREFIX)) return title
        return prefix + title
    }

    /**
     * 终端档的数值条：`[■■■■□□□□] 40%`。
     *
     * 终端里没有滑杆，进度用实心/空心方块表示（与命令行进度条同构）。
     * 区间非法（下界 >= 上界）时按 0% 处理，不抛异常 —— 设置项里确实出现过 range 配错。
     */
    fun valueBar(
        value: Float,
        rangeStart: Float,
        rangeEnd: Float,
        cells: Int = BAR_CELLS
    ): String {
        val safeCells = cells.coerceIn(1, 24)
        val span = rangeEnd - rangeStart
        val ratio = if (span <= 0f) 0f else ((value - rangeStart) / span).coerceIn(0f, 1f)
        val filled = kotlin.math.round(ratio * safeCells).toInt().coerceIn(0, safeCells)
        val bar = StringBuilder(safeCells + 8)
        bar.append('[')
        repeat(filled) { bar.append('■') }
        repeat(safeCells - filled) { bar.append('□') }
        bar.append(']')
        val percent = (ratio * 100f).toInt()
        return bar.toString() + " " + percent + "%"
    }

    /** 数值条的格子数：8 格在 466px 屏上正好不换行。 */
    const val BAR_CELLS = 8
}
