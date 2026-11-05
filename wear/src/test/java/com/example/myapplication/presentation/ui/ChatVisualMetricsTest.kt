package com.example.myapplication.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 聊天页几何：气泡不许贴圆周、主键图形必须够大。 */
class ChatVisualMetricsTest {

    @Test
    fun bubbleKeepsMoreThanTwentyFourDpOffEachEdgeOnTheRealWatch() {
        // 233dp 屏、184dp 气泡：左右各 24.5dp（旧值 200dp 只剩 3.4dp 到圆周）
        assertEquals(24.5f, ChatVisualMetrics.sideGutterDp(233f), 0.05f)
        assertTrue(ChatVisualMetrics.sideGutterDp(233f) >= 24f)
    }

    @Test
    fun narrowScreensNeverProduceNegativeGutters() {
        assertEquals(0f, ChatVisualMetrics.sideGutterDp(120f), 0.001f)
    }

    @Test
    fun bubbleTextWidthIsTheBubbleMinusBothPaddings() {
        assertEquals(156f, ChatVisualMetrics.bubbleTextWidthDp(), 0.001f)
        assertEquals(184f - 14f * 2f, ChatVisualMetrics.bubbleTextWidthDp(), 0.001f)
    }

    @Test
    fun orbGlyphTakesAtLeastFortyPercentOfTheKeyDiameter() {
        assertTrue("比例 " + ChatVisualMetrics.orbGlyphRatio(), ChatVisualMetrics.orbGlyphRatio() >= 0.4f)
        assertTrue(ChatVisualMetrics.orbGlyphRatio() < 0.5f)
        assertEquals(22f, ChatVisualMetrics.OrbGlyphDp, 0.001f)
    }

    @Test
    fun orbGlyphRatioIsSafeForDegenerateSizes() {
        assertEquals(0f, ChatVisualMetrics.orbGlyphRatio(0f), 0.001f)
        assertEquals(1f, ChatVisualMetrics.orbGlyphRatio(10f, 20f), 0.001f)
    }
}
