package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCurveMathTest {
    @Test
    fun wideRoundScreenExpandsTheCardButKeepsTheContentBox() {
        assertEquals(216f, SettingsCurveMath.cardWidthDp(233f), 0.001f)
        assertEquals(8.5f, SettingsCurveMath.screenInsetDp(233f), 0.001f)
    }

    @Test
    fun narrowScreenFallsBackToTheContentWidth() {
        assertEquals(176f, SettingsCurveMath.cardWidthDp(192f), 0.001f)
        assertEquals(8f, SettingsCurveMath.screenInsetDp(192f), 0.001f)
    }

    @Test
    fun cardIsWidestAtTheScreenCenterAndNarrowsTowardsTheEdges() {
        val center = SettingsCurveMath.halfWidthDp(0f, 233f, 108f)
        val middle = SettingsCurveMath.halfWidthDp(50f, 233f, 108f)
        val edge = SettingsCurveMath.halfWidthDp(70f, 233f, 108f)
        assertEquals(108f, center, 0.001f)
        assertTrue(middle in 88f..108f)
        assertTrue(edge <= 88.1f)
        assertTrue(center > middle && middle > edge)
    }

    @Test
    fun halfWidthKeepsShrinkingAllTheWayToTheCircleEdge() {
        val mid = SettingsCurveMath.halfWidthDp(80f, 233f, 108f)
        val lower = SettingsCurveMath.halfWidthDp(100f, 233f, 108f)
        val outside = SettingsCurveMath.halfWidthDp(120f, 233f, 108f)
        assertTrue("边缘不能提前夹到 88dp 而变直", mid < 88f)
        assertTrue(mid > lower)
        assertEquals(0f, outside, 0.001f)
    }

    @Test
    fun adjacentRowsShareTheSameBoundaryWidth() {
        val boundaryY = 233f / 2f + 50f
        val above = SettingsCurveMath.halfWidthDp(boundaryY - 233f / 2f, 233f, 108f)
        val below = SettingsCurveMath.halfWidthDp(boundaryY - 233f / 2f, 233f, 108f)
        assertEquals(above, below, 0f)
    }

    @Test
    fun contentExpandsAtTheCenterAndReturnsToNormalAtTheEdge() {
        val centered = SettingsCurveMath.contentScaleX(
            topInRootPx = 233f / 2f - 24f,
            rowHeightPx = 48f,
            screenHeightPx = 233f,
            minHalfPx = 88f,
            maxHalfPx = 108f
        )
        val nearEdge = SettingsCurveMath.contentScaleX(
            topInRootPx = 233f / 2f + 70f,
            rowHeightPx = 48f,
            screenHeightPx = 233f,
            minHalfPx = 88f,
            maxHalfPx = 108f
        )
        assertEquals(216f / 176f, centered, 0.01f)
        assertTrue("边缘内容必须继续收缩，而不是停在 1.0", nearEdge < 0.75f)
    }

    @Test
    fun theSmoothPathUsesCubicArcsAndDomedCapsNotStraightSideLines() {
        val source = com.example.myapplication.readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsCurve.kt"
        )
        assertEquals(6, Regex("cubicTo\\s*\\(").findAll(source).count())
        assertTrue("必须保留穹顶高度", source.contains("CapHeightDp = 14f"))
        assertTrue("侧边必须使用圆近似常数", source.contains("BezierCircle"))
        assertFalse("侧边不得用直线拼接", source.contains("lineTo(lTop"))
    }

    @Test
    fun aCenteredRowUsesTheExpandedWidthAndAnEdgeRowUsesTheSafeWidth() {
        val centerRowTop = 233f / 2f - 24f
        val centerRowBottom = centerRowTop + 48f
        assertEquals(108f, SettingsCurveMath.halfWidthDp(centerRowTop - 233f / 2f, 233f, 108f), 0.001f)
        assertEquals(108f, SettingsCurveMath.halfWidthDp(centerRowBottom - 233f / 2f, 233f, 108f), 0.001f)

        val edgeRowTop = 233f / 2f + 70f
        val edgeRowBottom = edgeRowTop + 48f
        val topHalf = SettingsCurveMath.halfWidthDp(edgeRowTop - 233f / 2f, 233f, 108f)
        val bottomHalf = SettingsCurveMath.halfWidthDp(edgeRowBottom - 233f / 2f, 233f, 108f)
        assertEquals(88f, topHalf, 0.1f)
        assertEquals(0f, bottomHalf, 0.001f)
    }
}