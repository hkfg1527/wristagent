package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.graphics.Color
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.contrastRatio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 深色背景下正文对比度必须 >= 4.5:1（WCAG AA）。 */
class SettingsContrastTest {

    private val surfaces = listOf(
        Wear.Palette.Bg,
        Wear.Palette.Surface,
        Wear.Palette.SurfaceAlt
    )

    @Test
    fun primaryTextPassesOnEverySurface() {
        surfaces.forEach { surface ->
            assertTrue(
                "TextPrimary on " + surface,
                contrastRatio(Wear.Palette.TextPrimary, surface) >= 4.5f
            )
        }
    }

    @Test
    fun secondaryTextPassesOnEverySurface() {
        surfaces.forEach { surface ->
            assertTrue(
                "TextSecondary on " + surface,
                contrastRatio(Wear.Palette.TextSecondary, surface) >= 4.5f
            )
        }
    }

    @Test
    fun accentTextPassesOnItsOwnContainer() {
        assertTrue(contrastRatio(Wear.Palette.Accent, Wear.Palette.AccentDim) >= 4.5f)
    }

    @Test
    fun accentSectionHeaderPassesOnThePageBackground() {
        // 组标题改成主色小字后，它读的就是这一组对比度：必须仍然达到 WCAG AA。
        assertTrue(contrastRatio(Wear.Palette.Accent, Wear.Palette.Bg) >= 4.5f)
    }

    @Test
    fun dangerTextPassesOnItsOwnContainer() {
        assertTrue(contrastRatio(Wear.Palette.Danger, Wear.Palette.DangerSurface) >= 4.5f)
    }

    @Test
    fun dimGlyphColorPassesAAOnThePageBackground() {
        // 行尾箭头 / 胶囊秒数：黑底 5.04:1
        assertTrue(contrastRatio(Wear.Palette.TextDim, Wear.Palette.Bg) >= 4.5f)
    }

    @Test
    fun dimGlyphColorStaysAboveTheIconFloorOnRaisedSurfaces() {
        // 抬升表面（Surface / SurfaceAlt）上它只承载图形，按非文本对比下限 3:1 要求，这里钉 4:1
        listOf(Wear.Palette.Surface, Wear.Palette.SurfaceAlt).forEach { surface ->
            assertTrue(
                "TextDim on " + surface,
                contrastRatio(Wear.Palette.TextDim, surface) >= 4f
            )
        }
    }

    @Test
    fun clockArcTextIsDimmerThanTheRowTitleButStillFarAboveAA() {
        assertTrue(contrastRatio(Wear.Palette.Clock, Wear.Palette.Bg) >= 4.5f)
        assertTrue(
            contrastRatio(Wear.Palette.Clock, Wear.Palette.Bg) <
                contrastRatio(Wear.Palette.TextPrimary, Wear.Palette.Bg)
        )
    }

    @Test
    fun hairlineIsVisibleOnTheOledBackground() {
        // 旧值 #232A35 只有 1.45:1，OLED 上"时有时无"
        assertTrue(contrastRatio(Wear.Palette.Border, Wear.Palette.Bg) >= 1.6f)
    }

    @Test
    fun accentTrackKeepsStaticIndicatorsReadable() {
        assertTrue(contrastRatio(Wear.Palette.AccentTrack, Wear.Palette.Bg) >= 4.5f)
        assertTrue(
            contrastRatio(Wear.Palette.AccentTrack, Wear.Palette.Bg) <
                contrastRatio(Wear.Palette.Accent, Wear.Palette.Bg)
        )
    }

    @Test
    fun orbIsStillTheBrightestSurfaceButNoLongerNearPureWhite() {
        assertTrue(contrastRatio(Wear.Palette.Orb, Wear.Palette.Bg) >= 4.5f)
        assertTrue(
            contrastRatio(Wear.Palette.Orb, Wear.Palette.Bg) <
                contrastRatio(Color(0xFFE8EDF5), Wear.Palette.Bg)
        )
    }

    @Test
    fun blackAndWhiteHitTheKnownExtremes() {
        assertEquals(21f, contrastRatio(Color.White, Color.Black), 0.05f)
        assertEquals(1f, contrastRatio(Color.Black, Color.Black), 0.001f)
    }

    @Test
    fun contrastIsSymmetric() {
        assertEquals(
            contrastRatio(Wear.Palette.TextPrimary, Wear.Palette.Bg),
            contrastRatio(Wear.Palette.Bg, Wear.Palette.TextPrimary),
            0.0001f
        )
    }
}
