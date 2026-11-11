package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * I1-2 的后半：最大输出 Token 除了那 5 档阶梯，还必须能落到任意值。
 *
 * 只测阶梯的话，"自定义"这条路径等于没测 —— 这里刻意用**不在阶梯上**的值做主线，
 * 阶梯值只做回归对照。
 */
class MaxTokensCustomTest {

    @Test
    fun aValueBetweenTwoStopsIsAcceptedInsteadOfSnappedAway() {
        val custom = 640
        assertFalse("640 必须不在阶梯上，否则这条用例证明不了自定义", SettingsValueMath.TokenLadder.contains(custom))
        assertEquals(custom, SettingsValueMath.parseMaxTokens("640"))

        // 三角验证：同一套解析对另外两组非阶梯值同样成立，排除针对入参特化
        assertEquals(1000, SettingsValueMath.parseMaxTokens(" 1000 "))
        assertEquals(97, SettingsValueMath.parseMaxTokens("97"))
    }

    @Test
    fun ladderStopsStillRoundTripThroughSliderAndParser() {
        SettingsValueMath.TokenLadder.forEach { stop ->
            assertEquals(stop, SettingsValueMath.parseMaxTokens(stop.toString()))
            assertEquals(stop, SettingsValueMath.tokenAt(SettingsValueMath.tokenIndex(stop)))
        }
    }

    @Test
    fun outOfRangeInputIsClampedIntoTheLegalWindow() {
        assertEquals(SettingsValueMath.MaxTokensMax, SettingsValueMath.parseMaxTokens("999999"))
        assertEquals(SettingsValueMath.MaxTokensMin, SettingsValueMath.parseMaxTokens("1"))
        assertEquals(
            SettingsValueMath.MaxTokensMax,
            SettingsValueMath.parseMaxTokens(SettingsValueMath.MaxTokensMax.toString())
        )
        assertEquals(
            SettingsValueMath.MaxTokensMin,
            SettingsValueMath.parseMaxTokens(SettingsValueMath.MaxTokensMin.toString())
        )
    }

    @Test
    fun garbageNeverOverwritesTheStoredValue() {
        // null = 调用方保持原值不动：空串 / 非数字 / 0 / 负数 / 小数都不许落盘
        assertNull(SettingsValueMath.parseMaxTokens(""))
        assertNull(SettingsValueMath.parseMaxTokens("   "))
        assertNull(SettingsValueMath.parseMaxTokens("abc"))
        assertNull(SettingsValueMath.parseMaxTokens("12abc"))
        assertNull(SettingsValueMath.parseMaxTokens("0"))
        assertNull(SettingsValueMath.parseMaxTokens("-5"))
        assertNull(SettingsValueMath.parseMaxTokens("1.5"))
    }
}
