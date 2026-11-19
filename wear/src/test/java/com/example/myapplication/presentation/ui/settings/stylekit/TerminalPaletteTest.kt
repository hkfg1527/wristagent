package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.ui.graphics.Color
import com.example.myapplication.appearance.SettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终端配色的唯一真源（`docs/UI-REBUILD-PLAN.md` §3.6）。
 *
 * 钉住三件事，避免以后"绿色到处写一遍、深浅不一"：
 * 1. 三个色值就是规格里那三个（改色必须同步改计划文档）；
 * 2. 只有 `TERMINAL` 被判定为终端档；
 * 3. 前景与底色对比足够大（亮度差），否则终端在表上会糊成一团。
 */
class TerminalPaletteTest {

    @Test
    fun theThreeColoursMatchTheWrittenSpec() {
        assertEquals("前景绿", Color(0xFF3BFF9A), TerminalPalette.Foreground)
        assertEquals("近黑底", Color(0xFF0B0F0C), TerminalPalette.Background)
        assertEquals("低饱和分隔绿", Color(0xFF123322), TerminalPalette.Divider)
        assertEquals("光标与前景同色", TerminalPalette.Foreground, TerminalPalette.Cursor)
    }

    @Test
    fun onlyTheTerminalStyleIsTreatedAsTerminal() {
        SettingsStyle.values().forEach { style ->
            if (style == SettingsStyle.TERMINAL) {
                assertTrue("终端档必须被判定为终端", TerminalPalette.isTerminal(style))
            } else {
                assertFalse("$style 不该吃终端配色", TerminalPalette.isTerminal(style))
            }
        }
    }

    @Test
    fun theForegroundIsMuchBrighterThanTheBackground() {
        // 相对亮度（sRGB 线性化后的近似），保证对比度足够
        fun luminance(c: Color): Float {
            fun channel(v: Float): Float =
                if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
            return 0.2126f * channel(c.red) + 0.7152f * channel(c.green) + 0.0722f * channel(c.blue)
        }
        val fg = luminance(TerminalPalette.Foreground)
        val bg = luminance(TerminalPalette.Background)
        val contrast = (fg + 0.05f) / (bg + 0.05f)
        assertTrue("前景/底色对比度必须 ≥ 7（实际 $contrast）", contrast >= 7f)
    }
}
