package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.ui.graphics.Color
import com.example.myapplication.appearance.SettingsStyle

/**
 * 终端档的配色与"是不是终端档"的唯一判定。
 *
 * 设计依据 `docs/UI-REBUILD-PLAN.md` §3.6：终端只用一色（绿），底色压到近黑，
 * 不再叠主题强调色。渲染侧只读这里，避免绿色散落在多个文件里各写一遍。
 */
object TerminalPalette {

    /** 前景（标签 / 数值 / 前缀）：终端绿。 */
    val Foreground = Color(0xFF3BFF9A)

    /** 底色：比全局背景更冷更暗，保证绿色高对比但整体低亮度。 */
    val Background = Color(0xFF0B0F0C)

    /** 分隔线：低饱和绿，避免与前景争夺注意力。 */
    val Divider = Color(0xFF123322)

    /** 光标方块颜色（与前景同色，靠闪烁区分）。 */
    val Cursor = Foreground

    /** 只有终端档吃这套配色。 */
    fun isTerminal(style: SettingsStyle): Boolean = style == SettingsStyle.TERMINAL
}
