package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大卡片字号决策：阈值必须由卡片宽度算出来，不能写死。
 *
 * 176dp 卡上文字列只有 82dp：displayMedium 一行放 5 个单位、titleLarge 一行放 10 个。
 * 这两个数字一旦漂走，卡片里的模型名要么被截成 dee…，要么无谓地退成小字。
 */
class SettingsCardTypographyTest {

    private val cardDp = SettingsContentWidth.value

    @Test
    fun theTextColumnMatchesTheCardGeometry() {
        assertEquals(82f, SettingsCardMetrics.textColumnDp(cardDp), 0.01f)
        assertEquals(5, SettingsCardMetrics.displayUnitsFor(cardDp))
        assertEquals(10, SettingsCardMetrics.titleUnitsFor(cardDp))
    }

    @Test
    fun shortNumbersKeepTheDisplayStyle() {
        listOf("2,278", "100%", "0.7", "12").forEach { value ->
            assertTrue(value + " 应该用大字号", SettingsCardTypography.usesDisplayStyle(value, cardDp))
            assertEquals(1, SettingsCardTypography.maxLinesFor(value, cardDp))
        }
    }

    @Test
    fun sixUnitsNoLongerFitsAtDisplaySize() {
        // 82dp / 15dp = 5 个单位的预算：6 个字符在 30sp 下会被裁掉，必须退档
        assertEquals(6, SettingsCardTypography.widthUnits("0 / 12"))
        assertFalse(SettingsCardTypography.usesDisplayStyle("0 / 12", cardDp))
        assertEquals(1, SettingsCardTypography.maxLinesFor("0 / 12", cardDp))
    }

    @Test
    fun fourChineseCharactersStayOnOneLineAfterFallingBack() {
        assertEquals(8, SettingsCardTypography.widthUnits("预置默认"))
        assertFalse(SettingsCardTypography.usesDisplayStyle("预置默认", cardDp))
        assertEquals("退档后一行放得下，不该断成 3+1", 1, SettingsCardTypography.maxLinesFor("预置默认", cardDp))
    }

    @Test
    fun onlyTrulyLongValuesGetTwoLines() {
        listOf("deepseek-v4-pro", "https://api.deepseek.com").forEach { value ->
            assertFalse(value + " 不该用 30sp", SettingsCardTypography.usesDisplayStyle(value, cardDp))
            assertEquals(value + " 退档后仍放不下，给两行", 2, SettingsCardTypography.maxLinesFor(value, cardDp))
        }
    }

    @Test
    fun widerCardsAffordMoreUnits() {
        // 同一句话在更宽的卡上应该允许更大的字号：说明阈值真的是从宽度来的
        assertFalse(SettingsCardTypography.usesDisplayStyle("0 / 12", 176f))
        assertEquals(7, SettingsCardMetrics.displayUnitsFor(206f))
    }

    @Test
    fun surroundingWhitespaceDoesNotChangeTheTier() {
        assertTrue(SettingsCardTypography.usesDisplayStyle("  2,278  ", cardDp))
        assertFalse(SettingsCardTypography.usesDisplayStyle("  deepseek-chat  ", cardDp))
    }
}
