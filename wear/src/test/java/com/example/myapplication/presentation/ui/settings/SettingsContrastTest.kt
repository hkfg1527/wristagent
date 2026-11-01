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
    fun dangerTextPassesOnItsOwnContainer() {
        assertTrue(contrastRatio(Wear.Palette.Danger, Wear.Palette.DangerSurface) >= 4.5f)
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
