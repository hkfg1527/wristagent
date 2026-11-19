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
}
