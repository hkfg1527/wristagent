package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.ui.graphics.Color
import com.example.myapplication.appearance.SettingsStyle

/**
 * 纸感档的配色（`docs/UI-REBUILD-PLAN.md` §2 PAPER）。
 *
 * 为什么现在才建这份文件：P8 的实测（0.0.0.143）发现这套风格"根页背景 `#000000`、卡片 `#0D1014`"，
 * 与方角档 **root 哈希完全相同** —— 也就是说纸色底、墨字、去卡片这些招牌特征一个都没实现。
 * 这份文件是它们的唯一真源。
 */
object PaperPalette {

    /** 纸色底：米白，比纯白暖一点，像旧纸。 */
    val Background = Color(0xFFF2EDE4)

    /** 墨色文字：深褐黑，不用纯黑（纯黑在纸上太硬）。 */
    val Ink = Color(0xFF2A2622)

    /** 次要文字（副标题、数值）：墨色的 70%。 */
    val InkSecondary = Color(0xFF5C554C)

    /** 分隔细线：墨色 20% 透明，替代卡片。 */
    val Rule = Color(0x332A2622)

    /** 只有纸感档吃这套配色。 */
    fun isPaper(style: SettingsStyle): Boolean = style == SettingsStyle.PAPER
}
