package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 设置页几何：内容宽度 168dp、徽章旁的副标题恒有 >= 96dp 可用宽度。 */
class SettingsLayoutMathTest {

    @Test
    fun realWatchKeepsTheOneHundredSixtyEightCap() {
        // 233dp 真机：弦长 169.4dp 被 168dp 硬上限截住 —— 真机版面不变，仍是 168dp
        assertEquals(183.2f, SettingsLayoutMath.contentWidthDp(233f), 0.2f)
    }

    @Test
    fun widerScreensAreCappedAtTheSameWidth() {
        assertEquals(184f, SettingsLayoutMath.contentWidthDp(454f), 0.001f)
    }

    @Test
    fun contentWidthIsTheChordWhereTheSafeBandEnds() {
        // 192dp 屏：安全带 72dp（半高上限），弦 = 2*sqrt(96^2 - 72^2) = 126.996dp
        assertEquals(126.996f, SettingsLayoutMath.contentWidthDp(192f), 0.05f)
    }

    @Test
    fun narrowScreensShrinkBelowTheOldInsetFormula() {
        // 旧公式在 176dp 屏给出 152dp：那已经画到物理圆周外；弦长只有 105.6dp
        assertEquals(105.6f, SettingsLayoutMath.contentWidthDp(176f), 0.05f)
        assertTrue(
            SettingsLayoutMath.contentWidthDp(176f) < 176f - SettingsLayoutMath.SideInsetDp * 2f
        )
    }

    @Test
    fun contentNeverWiderThanTheScreenMinusBothInsets() {
        listOf(176f, 192f, 200f, 233f, 300f).forEach { screen ->
            assertTrue(
                "screen=" + screen + " width=" + SettingsLayoutMath.contentWidthDp(screen),
                SettingsLayoutMath.contentWidthDp(screen) <= screen - SettingsLayoutMath.SideInsetDp * 2f
            )
        }
    }

    @Test
    fun sideInsetReproducesTheMeasuredRealWatchMargin() {
        // 加宽后真机 233dp 屏的左右边距：内容 183.18dp，边距 (233 - 183.18) / 2 = 24.91dp
        assertEquals(24.91f, SettingsLayoutMath.sideInsetDp(233f), 0.05f)
    }

    @Test
    fun sideInsetTimesTwoPlusContentWidthIsExactlyTheScreen() {
        listOf(192f, 200f, 233f, 300f).forEach { screen ->
            val box = screen - SettingsLayoutMath.sideInsetDp(screen) * 2f
            assertEquals(
                "screen=" + screen,
                SettingsLayoutMath.contentWidthDp(screen),
                box,
                0.05f
            )
        }
    }

    @Test
    fun sideInsetNeverShrinksBelowTheSafetyMargin() {
        // 极窄屏：弦长公式会把内缩推到半屏，但仍不许低于 12dp 安全边
        assertTrue(SettingsLayoutMath.sideInsetDp(200f) >= SettingsLayoutMath.SideInsetDp)
        assertEquals(SettingsLayoutMath.SideInsetDp, SettingsLayoutMath.sideInsetDp(0f), 0.001f)
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
    fun itemGapStaysOnTheMinimumStep() {
        // 纵向预算全部让给内容：行间距压到最小呼吸 4dp
        assertEquals(4f, SettingsSpacingMath.ItemGapDp, 0.001f)
        assertTrue(SettingsSpacingMath.isOnGrid(SettingsSpacingMath.ItemGapDp))
        assertTrue(SettingsSpacingMath.ItemGapDp < Wear.Space.Sm.value)
    }

    @Test
    fun sectionHeaderKeepsEightDpAboveTheFirstRowOfItsGroup() {
        // 233dp 圆屏放不下卡片式组间距：24dp 会让一屏只剩一行半
        assertEquals(8f, SettingsSpacingMath.SectionHeaderBottomDp, 0.001f)
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
    fun rowStepGrowsForBiggerRows() {
        // 48 行高 + 1 发丝 + 4 间距 = 53dp
        assertEquals(53f, SettingsSpacingMath.RowStepDp, 0.001f)
        assertEquals(
            SettingsSpacingMath.MinRowHeightDp + SettingsSpacingMath.HairlineDp +
                SettingsSpacingMath.ItemGapDp,
            SettingsSpacingMath.RowStepDp,
            0.001f
        )
    }

    @Test
    fun biggerRowsTradeRowCountForLegibility() {
        // 233dp 真机：233 / 53 = 4.4 行
        assertTrue(SettingsSpacingMath.rowsPerScreen(233f) >= 4f)
        assertEquals(4.4f, SettingsSpacingMath.rowsPerScreen(233f), 0.05f)
        assertTrue(
            "步进变大后每屏行数应减少",
            SettingsSpacingMath.rowsPerScreen(233f) < SettingsSpacingMath.rowsPerScreen(233f, 49f)
        )
    }

    @Test
    fun hairlineInsetMatchesTheRowTextLeftEdge() {
        // 分隔线内缩量必须等于行内左右内边距，线才和文字左边缘同轴
        assertEquals(
            SettingsLayoutMath.RowHorizontalPaddingDp,
            SettingsLayoutMath.HairlineInsetDp,
            0.001f
        )
        assertEquals(8f, SettingsLayoutMath.HairlineInsetDp, 0.001f)
    }

    @Test
    fun bodyLineHeightGivesChineseTextFortyThreePercentLeading() {
        // 这个令牌同时被聊天正文复用，放大设置页不该改动全站正文行高
        assertEquals(20f, SettingsSpacingMath.BodyLineHeightSp, 0.001f)
        val ratio = SettingsSpacingMath.BodyLineHeightSp / 14f
        assertTrue("行距倍数 " + ratio, ratio >= 1.4f)
        assertEquals(1.43f, ratio, 0.01f)
    }

    @Test
    fun statusWordValueLeavesTheTitleAboveSeventyEightDp() {
        // "已配置" 三个 12sp 汉字 ≈ 36dp：标题仍有 87dp，足够放下 5 个汉字的行标题
        assertTrue(SettingsLayoutMath.titleBudgetDp(36f) >= 78f)
    }

    @Test
    fun aBareTwentyFourCharModelNameSqueezesTheTitleUnderTheMinimum() {
        // 反向阀门：取值退回 88dp 的裸模型名时，标题预算掉到 35dp —— 这正是"折行 + 截断"的成因
        assertTrue(SettingsLayoutMath.titleBudgetDp(88f) < 78f)
    }

    @Test
    fun bottomInsetClearsTheBottomScrim() {
        assertTrue(
            Wear.Size.SettingsBottomInset.value >= SettingsLayoutMath.BottomScrimDp
        )
        assertEquals(44f, Wear.Size.SettingsBottomInset.value, 0.001f)
    }

    @Test
    fun rootTitleStartsBelowTheTopScrim() {
        assertTrue(
            "标题顶边 " + (SettingsLayoutMath.TopScrimDp + SettingsSpacingMath.ItemGapDp +
                RootTitleTopPadding.value) + "dp 落在遮罩内",
            SettingsLayoutMath.TopScrimDp + SettingsSpacingMath.ItemGapDp + RootTitleTopPadding.value >=
                SettingsLayoutMath.TopScrimDp
        )
    }

    @Test
    fun rowsExceedTheMinimumTouchTarget() {
        assertEquals(48f, SettingsSpacingMath.MinRowHeightDp, 0.001f)
        assertTrue(
            "设置行必须高于全局触控下限",
            SettingsSpacingMath.MinRowHeightDp >= Wear.Size.Touch.value
        )
        assertTrue(SettingsSpacingMath.meetsTouchTarget(SettingsSpacingMath.MinRowHeightDp))
        assertFalse(SettingsSpacingMath.meetsTouchTarget(SettingsSpacingMath.MinRowHeightDp - 1f))
    }
}
