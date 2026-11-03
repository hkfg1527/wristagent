package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

/**
 * 行与行之间的"独立块"约束：块级留白只允许 4 的倍数、行高不低于 44dp、
 * 分隔线永远是 1dp 发丝线、行内微间距按"旧值 + 指定增量"收敛。
 *
 * 这里有两条不同的规则，不要混：
 * - 块级留白（行间、组标题下方）走 [SettingsSpacingMath.GridDp] 网格；
 * - 行内微间距（标题↔取值 +1dp、取值↔箭头 +2dp）是视觉修正，允许离开网格，
 *   否则用户要求的 +2dp 会被 4dp 网格直接吃掉。
 */
class SettingsSpacingMathTest {

    @Test
    fun everyBlockGapSitsOnTheFourDpGrid() {
        SettingsSpacingMath.BlockGapsDp.forEach { gap ->
            assertTrue("块级留白必须是 4dp 的倍数: " + gap, SettingsSpacingMath.isOnGrid(gap))
        }
    }

    @Test
    fun nonPositiveOrOffGridGapsAreRejected() {
        assertFalse(SettingsSpacingMath.isOnGrid(0f))
        assertFalse(SettingsSpacingMath.isOnGrid(-4f))
        assertFalse(SettingsSpacingMath.isOnGrid(10f))
    }

    @Test
    fun itemGapGrowsByFourToSixDpOverTheOldToken() {
        val delta = SettingsSpacingMath.ItemGapDp - Wear.Space.Sm.value
        assertTrue("项与项之间的留白增量必须落在 4~6dp，实际 " + delta, delta in 4f..6f)
        assertTrue(SettingsSpacingMath.isOnGrid(SettingsSpacingMath.ItemGapDp))
    }

    @Test
    fun itemGapStaysWiderThanTheBaselineToken() {
        assertTrue(SettingsSpacingMath.ItemGapDp > Wear.Space.Sm.value)
    }

    @Test
    fun sectionHeaderKeepsTwelveDpAboveTheFirstRowOfItsGroup() {
        assertEquals(12f, SettingsSpacingMath.SectionHeaderBottomDp, 0.001f)
        assertTrue(SettingsSpacingMath.isOnGrid(SettingsSpacingMath.SectionHeaderBottomDp))
    }

    @Test
    fun valueToTailGapWidensByTwoDp() {
        assertEquals(
            Wear.Space.Sm.value + 2f,
            SettingsSpacingMath.ValueToTailDp,
            0.001f
        )
    }

    @Test
    fun titleToValueKeepsOnlyAHairlineOfBreath() {
        assertEquals(
            Wear.Space.Sm.value + SettingsSpacingMath.HairlineDp,
            SettingsSpacingMath.TitleToValueDp,
            0.001f
        )
    }

    @Test
    fun theDividerStaysAHairline() {
        assertEquals(1f, SettingsSpacingMath.HairlineDp, 0.001f)
        assertEquals(Wear.Space.Xs.value, SettingsSpacingMath.HairlineDp * 4f, 0.001f)
    }

    @Test
    fun rowsKeepAFortyFourDpTouchTarget() {
        assertEquals(44f, SettingsSpacingMath.MinRowHeightDp, 0.001f)
        assertEquals(SettingsSpacingMath.MinRowHeightDp, Wear.Size.Touch.value, 0.001f)
        assertTrue(SettingsSpacingMath.meetsTouchTarget(Wear.Size.Touch.value))
        assertFalse(SettingsSpacingMath.meetsTouchTarget(SettingsSpacingMath.MinRowHeightDp - 1f))
    }
}
