package com.example.myapplication.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material3.Shapes
import androidx.wear.compose.material3.Typography
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 单一排版权威的守门测试。
 *
 * 主题只注入 colorScheme 时，官方组件吃库默认字号、页面又用自造的 Wear.Text*，
 * 同一屏上并存两套排版权威。这里把"字号只来自官方阶梯、圆角只有两档"钉死，
 * 任何人在 [WearTypography] / [WearShapes] 里私自加一档 sp 或一种圆角都会红。
 */
class WearTypographyAuthorityTest {

    @Test
    fun themeTypographyIsTheOfficialLadderUntouched() {
        // 与官方默认逐槽位相等 —— 也就是没有哪一档被本地改过字号。
        assertEquals(Typography(), WearTypography)
    }

    @Test
    fun theLadderPagesRelyOnKeepsTheOfficialSizes() {
        assertEquals(12f, WearTypography.bodySmall.fontSize.value, 0.001f)
        assertEquals(14f, WearTypography.bodyMedium.fontSize.value, 0.001f)
        assertEquals(16f, WearTypography.titleMedium.fontSize.value, 0.001f)
        assertEquals(18f, WearTypography.titleLarge.fontSize.value, 0.001f)
        assertEquals(30f, WearTypography.displayMedium.fontSize.value, 0.001f)
    }

    @Test
    fun theSmallerSlotsAreNotReachedByPageCode() {
        // bodyExtraSmall(10sp) 低于"最小 12sp"的既有下限，页面代码不得引用它。
        assertEquals(10f, WearTypography.bodyExtraSmall.fontSize.value, 0.001f)
        assertTrue(WearTypography.bodySmall.fontSize.value >= 12f)
        assertTrue(WearTypography.bodyMedium.fontSize.value >= 12f)
        assertTrue(WearTypography.titleLarge.fontSize.value >= 12f)
    }

    @Test
    fun curvedClockUsesTheOfficialArcSlot() {
        // 曲面时钟不再对 18sp 做 0.84 的手调缩放，直接取官方弧形字阶 arcMedium(15sp)。
        assertEquals(18f, WearTypography.arcLarge.fontSize.value, 0.001f)
        assertEquals(15f, WearTypography.arcMedium.fontSize.value, 0.001f)
        assertEquals(14f, WearTypography.arcSmall.fontSize.value, 0.001f)
    }
}

/** 圆角只保留两档：控件 12dp / 卡片 24dp，官方五档全部收敛到这两档上。 */
class WearShapesTierTest {

    private val controlCorner = RoundedCornerShape(Wear.Radius.Control)
    private val cardCorner = RoundedCornerShape(Wear.Radius.Card)

    @Test
    fun controlAndCardCornersAreTheOnlyTwoTiers() {
        assertEquals(12f, Wear.Radius.Control.value, 0.001f)
        assertEquals(24f, Wear.Radius.Card.value, 0.001f)
    }

    @Test
    fun allFiveOfficialSlotsCollapseOntoThoseTwoTiers() {
        val distinct = setOf(
            WearShapes.extraSmall,
            WearShapes.small,
            WearShapes.medium,
            WearShapes.large,
            WearShapes.extraLarge
        )
        assertEquals(2, distinct.size)
        assertEquals(setOf<Any>(controlCorner, cardCorner), distinct)
    }

    @Test
    fun controlsTakeTheControlTierAndCardsTakeTheCardTier() {
        assertEquals(controlCorner, WearShapes.extraSmall)
        assertEquals(controlCorner, WearShapes.small)
        assertEquals(controlCorner, WearShapes.medium)
        assertEquals(cardCorner, WearShapes.large)
        assertEquals(cardCorner, WearShapes.extraLarge)
    }

    @Test
    fun theTwoTiersAreActuallyDifferent() {
        assertNotEquals(controlCorner, cardCorner)
    }

    @Test
    fun theRawMaterialDefaultsAreNotWhatWeShip() {
        // 官方默认是 4/8/18/26/36 五档；我们下发的必须是收敛后的两档。
        val official = Shapes()
        assertNotEquals(official.small, WearShapes.small)
        assertNotEquals(official.large, WearShapes.large)
    }
}

/** 间距只允许 4 的倍数，取值集合固定为 4 / 8 / 12 / 16 / 24。 */
class WearSpacingScaleTest {

    private val allowed = setOf(4f, 8f, 12f, 16f, 24f)

    private val spaceValues = mapOf(
        "Xs" to Wear.Space.Xs,
        "Sm" to Wear.Space.Sm,
        "Md" to Wear.Space.Md,
        "Lg" to Wear.Space.Lg,
        "Xl" to Wear.Space.Xl
    )

    @Test
    fun everySpacingTokenIsAnAllowedStep() {
        spaceValues.forEach { (name, dp) ->
            assertTrue(name + "=" + dp.value, allowed.contains(dp.value))
        }
    }

    @Test
    fun everySpacingTokenIsAMultipleOfFour() {
        spaceValues.forEach { (name, dp) ->
            assertEquals(name, 0f, dp.value % 4f, 0.001f)
        }
    }

    @Test
    fun theScaleIsStrictlyIncreasing() {
        val values = listOf(Wear.Space.Xs, Wear.Space.Sm, Wear.Space.Md, Wear.Space.Lg, Wear.Space.Xl)
            .map { it.value }
        assertEquals(values.sorted(), values)
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun rowGlyphsShrinkToTheNewTokens() {
        assertEquals(6f, Wear.Size.StatusBlock.value, 0.001f)
        assertEquals(10f, Wear.Size.ChevronGlyph.value, 0.001f)
        assertEquals(44f, Wear.Size.SettingsBottomInset.value, 0.001f)
        assertEquals(0f, Wear.Size.SettingsBottomInset.value % 2f, 0.001f)
    }

    @Test
    fun pillRadiusIsWrittenIntoTheTokensAsAnExplicitException() {
        assertTrue("Pill=" + Wear.Radius.Pill, Wear.Radius.Pill > Wear.Radius.Card)
        assertTrue(Wear.Radius.Pill.value >= 999f)
    }

    @Test
    fun theSettingsScreenMetricsStayOnTheScale() {
        // 顶部时钟带 34dp、底栏预留 76dp、行间距 8dp、分区间距 16dp。
        assertEquals(34f, Wear.Size.TopContentInset.value, 0.001f)
        assertEquals(76f, Wear.Size.BottomBarReserve.value, 0.001f)
        assertEquals(Wear.Size.BottomBarReserve, Wear.Size.ListCenterReach)
        assertEquals(8f, Wear.Space.Sm.value, 0.001f)
        assertEquals(16f, Wear.Space.Lg.value, 0.001f)
        assertEquals(0f, Wear.Size.TopContentInset.value % 2f, 0.001f)
    }
}
