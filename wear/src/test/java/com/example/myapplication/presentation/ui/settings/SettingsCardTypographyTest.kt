package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCardTypographyTest {

    @Test
    fun shortNumbersKeepTheDisplayStyle() {
        listOf("2,278", "0 / 12", "—", "100%", "0.7").forEach { value ->
            assertTrue(value + " 应该用大字号", SettingsCardTypography.usesDisplayStyle(value))
            assertEquals(1, SettingsCardTypography.maxLinesFor(value))
        }
    }

    @Test
    fun longTextFallsBackOneTierAndOnlyWrapsWhenItStillDoesNotFit() {
        listOf("deepseek-v4-pro", "https://api.deepseek.com").forEach { value ->
            assertFalse(value + " 不该用 30sp（会被截成省略号）", SettingsCardTypography.usesDisplayStyle(value))
            assertEquals(value + " 退档后一行放不下，应该给两行", 2, SettingsCardTypography.maxLinesFor(value))
        }
        // 四个汉字 = 8 个半宽单位：退档到 titleLarge 后一行放得下，不该断成 3+1
        assertEquals(8, SettingsCardTypography.widthUnits("预置默认"))
        assertEquals(1, SettingsCardTypography.maxLinesFor("预置默认"))
        assertEquals(1, SettingsCardTypography.maxLinesFor("密钥未填写"))
    }

    @Test
    fun theBoundaryIsExactlySevenHalfWidthUnits() {
        assertTrue(SettingsCardTypography.usesDisplayStyle("1234567"))
        assertFalse(SettingsCardTypography.usesDisplayStyle("12345678"))
        // 三个 CJK 字 = 6 个单位，仍在大字档；四个 CJK 字 = 8 个单位，退档
        assertTrue(SettingsCardTypography.usesDisplayStyle("温度值"))
        assertFalse(SettingsCardTypography.usesDisplayStyle("温度数值"))
    }

    @Test
    fun widthUnitsCountCjkDouble() {
        assertEquals(5, SettingsCardTypography.widthUnits("2,278"))
        assertEquals(10, SettingsCardTypography.widthUnits("密钥未填写"))
        // 半角化的破折号（U+2014）算一个单位：它比 CJK 窄
        assertEquals(1, SettingsCardTypography.widthUnits("—"))
    }

    @Test
    fun surroundingWhitespaceDoesNotChangeTheTier() {
        assertTrue(SettingsCardTypography.usesDisplayStyle("  2,278  "))
        assertFalse(SettingsCardTypography.usesDisplayStyle("  deepseek-chat  "))
    }
}
