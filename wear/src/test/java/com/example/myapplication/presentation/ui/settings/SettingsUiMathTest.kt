package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设置页几何：内容宽度 168dp、徽章旁的副标题恒有 >= 96dp 可用宽度。 */
class SettingsLayoutMathTest {

    @Test
    fun roundScreenContentWidthIsOneHundredSixtyEight() {
        assertEquals(168f, SettingsLayoutMath.contentWidthDp(192f), 0.001f)
    }

    @Test
    fun widerScreensAreCappedAtTheSameWidth() {
        assertEquals(168f, SettingsLayoutMath.contentWidthDp(454f), 0.001f)
    }

    @Test
    fun narrowerScreensKeepBothSideInsets() {
        assertEquals(152f, SettingsLayoutMath.contentWidthDp(176f), 0.001f)
    }

    @Test
    fun nonPositiveScreenWidthYieldsZero() {
        assertEquals(0f, SettingsLayoutMath.contentWidthDp(0f), 0.001f)
        assertEquals(0f, SettingsLayoutMath.contentWidthDp(-10f), 0.001f)
    }

    @Test
    fun subtitleKeepsNinetySixDpBesideAFullWidthBadge() {
        assertEquals(96f, SettingsLayoutMath.subtitleWidthDp(168f), 0.001f)
    }

    @Test
    fun everyBadgeWidthUpToTheCapKeepsSubtitleUsable() {
        for (badge in 0..SettingsLayoutMath.BadgeMaxWidthDp.toInt()) {
            val width = SettingsLayoutMath.subtitleWidthDp(168f, badge.toFloat())
            assertTrue("badge=" + badge + " subtitle=" + width, width >= SettingsLayoutMath.SubtitleMinWidthDp)
        }
    }

    @Test
    fun narrowerBadgesGiveTheSubtitleMoreRoom() {
        assertTrue(SettingsLayoutMath.subtitleWidthDp(168f, 8f) > SettingsLayoutMath.subtitleWidthDp(168f, 40f))
    }

    @Test
    fun subtitleWidthNeverGoesNegative() {
        assertEquals(0f, SettingsLayoutMath.subtitleWidthDp(100f, 40f), 0.001f)
    }
}

/** 取值换算：温度量化与旧 ± 按钮逐位一致，Token 档位映射可往返。 */
class SettingsValueMathTest {

    @Test
    fun temperatureQuantizationMatchesLegacyArithmetic() {
        assertEquals(0.7f, SettingsValueMath.quantizeTemperature(0.74f), 0.0001f)
        assertEquals(0.8f, SettingsValueMath.quantizeTemperature(0.75f), 0.0001f)
        assertEquals(1.2f, SettingsValueMath.quantizeTemperature(1.24f), 0.0001f)
    }

    @Test
    fun temperatureIsClampedToTheLegacyRange() {
        assertEquals(1.5f, SettingsValueMath.quantizeTemperature(9f), 0.0001f)
        assertEquals(0f, SettingsValueMath.quantizeTemperature(-9f), 0.0001f)
    }

    @Test
    fun temperatureStepCountCoversSixteenStops() {
        assertEquals(14, SettingsValueMath.TemperatureSteps)
    }

    @Test
    fun temperatureIndexAndValueRoundTrip() {
        for (index in 0..15) {
            val value = SettingsValueMath.temperatureAt(index)
            assertEquals(index / 10f, value, 0.0001f)
            assertEquals(index, SettingsValueMath.temperatureIndex(value))
        }
    }

    @Test
    fun tokenLadderKeepsTheLegacyPresets() {
        val ladder = listOf(150, 300, 600, 1200, 2048)
        ladder.forEachIndexed { index, tokens ->
            assertEquals(index, SettingsValueMath.tokenIndex(tokens))
            assertEquals(tokens, SettingsValueMath.tokenAt(index))
        }
    }

    @Test
    fun tokenStepsAreTheGapsBetweenStops() {
        assertEquals(3, SettingsValueMath.TokenSteps)
    }

    @Test
    fun nonPresetTokenValuesSnapToTheNearestStop() {
        assertEquals(4, SettingsValueMath.tokenIndex(4096))
        assertEquals(0, SettingsValueMath.tokenIndex(100))
        assertEquals(2, SettingsValueMath.tokenIndex(500))
    }

    @Test
    fun tokenIndexIsClampedAtBothEnds() {
        assertEquals(150, SettingsValueMath.tokenAt(-3))
        assertEquals(2048, SettingsValueMath.tokenAt(99))
    }

    @Test
    fun blankSecretStaysBlank() {
        assertEquals("", SettingsValueMath.maskSecret(""))
        assertEquals("", SettingsValueMath.maskSecret("   "))
    }

    @Test
    fun shortSecretIsFullyMasked() {
        assertEquals("••••", SettingsValueMath.maskSecret("abcd"))
    }

    @Test
    fun longSecretKeepsOnlyItsLastFourCharacters() {
        assertEquals("••••efgh", SettingsValueMath.maskSecret("sk-abcdefgh"))
    }
}
