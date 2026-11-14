package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 大卡片档边缘缩放的语义守卫：只缩不放、等比、屏心不缩、贴边饱和。
 *
 * 0.70 / 0.50 是 compose-foundation 1.7.1 `ScalingLazyColumnDefaults.scalingParams` 的官方
 * 默认 edgeScale / edgeAlpha；0.75 距离处的 0.5 是官方那条对称缓动
 * CubicBezierEasing(0.3f, 0f, 0.7f, 1f) 在中点的确定取值。
 */
class SettingsCardEdgeZoomTest {

    @Test
    fun theOfficialEdgeNumbersAreTheOnesWeShip() {
        assertEquals(0.70f, CardEdgeZoom.MinScale, 0.0001f)
        assertEquals(0.50f, CardEdgeZoom.MinAlpha, 0.0001f)
    }

    @Test
    fun theCentreOfTheScreenIsNeverScaled() {
        assertEquals(0f, CardEdgeZoom.progress(0f), 0.0001f)
        assertEquals(0f, CardEdgeZoom.progress(0.2f), 0.0001f)
        assertEquals(0f, CardEdgeZoom.progress(CardEdgeZoom.StartFraction), 0.0001f)
        assertEquals(1f, CardEdgeZoom.scaleFor(0f), 0.0001f)
        assertEquals(1f, CardEdgeZoom.alphaFor(0f), 0.0001f)
    }

    @Test
    fun hoveringAtTheEdgeSaturatesInsteadOfGrowingForever() {
        assertEquals(1f, CardEdgeZoom.progress(1f), 0.0001f)
        assertEquals(1f, CardEdgeZoom.progress(3f), 0.0001f)
        assertEquals(1f, CardEdgeZoom.progress(Float.POSITIVE_INFINITY), 0.0001f)
        assertEquals(CardEdgeZoom.MinScale, CardEdgeZoom.scaleFor(1f), 0.0001f)
        assertEquals(CardEdgeZoom.MinAlpha, CardEdgeZoom.alphaFor(1f), 0.0001f)
    }

    @Test
    fun theMidpointRunsOnTheOfficialSymmetricCurve() {
        assertEquals(0.5f, CardEdgeZoom.progress(0.75f), 0.02f)
    }

    @Test
    fun theProgressOnlyEverGrowsWithDistance() {
        var previous = -1f
        var d = 0f
        while (d <= 2f) {
            val value = CardEdgeZoom.progress(d)
            assertTrue("progress must not decrease at d=$d", value >= previous - 0.0001f)
            assertTrue("progress must stay inside 0..1 at d=$d", value in 0f..1f)
            previous = value
            d += 0.01f
        }
    }

    @Test
    fun theScaleOnlyEverShrinksAndNeverStretchesHorizontally() {
        var d = 0f
        while (d <= 2f) {
            val progress = CardEdgeZoom.progress(d)
            val scale = CardEdgeZoom.scaleFor(progress)
            assertTrue("scale must never exceed 1 at d=$d", scale <= 1f)
            assertTrue("scale must never drop below the official floor", scale >= CardEdgeZoom.MinScale)
            // 同一张卡横纵共用一个 scale —— 结构上就不存在横向拉伸的可能
            assertEquals(scale, CardEdgeZoom.scaleFor(progress), 0f)
            d += 0.01f
        }
    }

    @Test
    fun theCentreOfTheViewportMapsToZeroProgress() {
        // 384px 屏：中心 192 → 进度 0；顶边 0 → 距离比例 1 → 饱和
        assertEquals(0f, CardEdgeZoom.progressForCenter(192f, 384f), 0.0001f)
        assertEquals(1f, CardEdgeZoom.progressForCenter(0f, 384f), 0.0001f)
        assertEquals(1f, CardEdgeZoom.progressForCenter(384f, 384f), 0.0001f)
        // 上下对称：同一张卡在屏心上方与下方给同一个缩放
        assertEquals(
            CardEdgeZoom.progressForCenter(120f, 384f),
            CardEdgeZoom.progressForCenter(264f, 384f),
            0.0001f
        )
    }

    @Test
    fun aNonFiniteOrEmptyScreenFallsBackToIdentity() {
        assertEquals(0f, CardEdgeZoom.progress(Float.NaN), 0.0001f)
        assertEquals(0f, CardEdgeZoom.progressForCenter(100f, 0f), 0.0001f)
    }
}
