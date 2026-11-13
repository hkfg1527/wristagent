package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大卡片字号决策：阈值必须由卡片宽度算出来，不能写死。
 *
 * 176dp 卡上文字列只有 82dp：displayMedium 的预算算出来是 5 个单位，titleLarge 是 8 个。
 * 退档后的行数上限固定给 3 行 —— 那是**上限**不是目标行数，放得下的短文本仍排一行。
 */
class SettingsCardTypographyTest {

    private val cardDp = SettingsContentWidth.value

    @Test
    fun theTextColumnMatchesTheCardGeometry() {
        // 176dp 卡 − 左右各 10dp 内边距 − 38dp 徽标块 = 118dp 文字列
        assertEquals(118f, SettingsCardMetrics.textColumnDp(cardDp), 0.01f)
        assertEquals(7, SettingsCardMetrics.displayUnitsFor(cardDp))
        assertEquals(12, SettingsCardMetrics.titleUnitsFor(cardDp))
    }

    @Test
    fun shortNumbersKeepTheDisplayStyle() {
        listOf("2,278", "100%", "0.7", "12").forEach { value ->
            assertTrue(value + " 应该用大字号", SettingsCardTypography.usesDisplayStyle(value, cardDp))
            assertEquals(1, SettingsCardTypography.maxLinesFor(value, cardDp))
        }
    }

    @Test
    fun nineUnitsNoLongerFitsAtDisplaySize() {
        // 118dp / 15dp = 7 个单位的预算：8 个字符在 30sp 下会被裁掉，必须退档
        assertEquals(8, SettingsCardTypography.widthUnits("0 / 1234"))
        assertFalse(SettingsCardTypography.usesDisplayStyle("0 / 1234", cardDp))
        assertEquals(3, SettingsCardTypography.maxLinesFor("0 / 1234", cardDp))
    }

    @Test
    fun fourChineseCharactersFallBackButKeepAThreeLineCap() {
        assertEquals(8, SettingsCardTypography.widthUnits("预置默认"))
        assertFalse(SettingsCardTypography.usesDisplayStyle("预置默认", cardDp))
        // 上限是 3 行：这四个字本来就排得下一行，上限不会把文本撑成三行
        assertEquals(3, SettingsCardTypography.maxLinesFor("预置默认", cardDp))
    }

    @Test
    fun theFallbackTierAlwaysGetsThreeLines() {
        // 实机量出的一行只装 6 个拉丁字符：两行会把 deepseek-v4-pro 截成 "deepse / ek-v4…"
        assertEquals(15, SettingsCardTypography.widthUnits("deepseek-v4-pro"))
        assertEquals(3, SettingsCardTypography.maxLinesFor("deepseek-v4-pro", cardDp))
        assertEquals(3, SettingsCardTypography.maxLinesFor("https://api.deepseek.com", cardDp))
    }

    @Test
    fun widerCardsAffordMoreUnits() {
        // 同一句话在更宽的卡上应该允许更大的字号：说明阈值真的是从宽度来的
        assertFalse(SettingsCardTypography.usesDisplayStyle("0 / 1234", 176f))
        assertEquals(9, SettingsCardMetrics.displayUnitsFor(206f))
    }

    @Test
    fun surroundingWhitespaceDoesNotChangeTheTier() {
        assertTrue(SettingsCardTypography.usesDisplayStyle("  2,278  ", cardDp))
        assertFalse(SettingsCardTypography.usesDisplayStyle("  deepseek-chat  ", cardDp))
    }
}
