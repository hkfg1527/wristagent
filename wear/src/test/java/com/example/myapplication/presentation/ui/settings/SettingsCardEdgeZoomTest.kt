package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    /**
     * 用户 02:05 的追加约束是"所有卡片限制在统一宽度里"，所以判据从"照搬官方 0.70"
     * 改成"宽度几乎不变、淡出承担边缘感"：收缩不超过 6%，透明度必须明显下降。
     * 这不是放宽断言：原来钉的是一个具体数字，现在钉的是两条产品约束。
     */
    /**
     * 用户 02:20 的原话是"所有卡片限制在统一宽度里"。等比缩放必然改宽度，所以唯一自洽的解是：
     * 缩放恒为 1.0，边缘只淡出。这条测试钉住这个解，防止以后又把它改回"缩一点"。
     */
    @Test
    fun cardsKeepOneWidthSoTheEdgeEffectIsFadeOnly() {
        assertEquals("缩放必须恒为 1.0：任何等比缩放都会改变卡片宽度", 1f, CardEdgeZoom.MinScale, 0.0001f)
        assertTrue("边缘必须仍然淡出", CardEdgeZoom.MinAlpha <= 0.5f)
        var d = 0f
        while (d <= 2f) {
            assertEquals("任何距离下缩放都必须是 1", 1f, CardEdgeZoom.scaleFor(CardEdgeZoom.progress(d)), 0.0001f)
            d += 0.05f
        }
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

/** 主数值退档判据：按宽度（em）算，不按字符数 —— 中日韩字符是拉丁字符的两倍宽。 */
class SettingsCardTitleWidthTest {

    @Test
    fun latinAndCjkAreWeighedDifferently() {
        assertEquals(8.25f, titleEmUnits("deepseek-v4-pro"), 0.001f)
        assertEquals(7.65f, titleEmUnits("40 项可移植设置"), 0.001f)
        assertEquals(3f, titleEmUnits("已配置"), 0.001f)
    }

    @Test
    fun theModelNameThatUsedToWrapNowDropsAStep() {
        assertTrue("deepseek-v4-pro 必须退档", brandTitleIsCompact("deepseek-v4-pro"))
        assertTrue("40 项可移植设置 必须退档", brandTitleIsCompact("40 项可移植设置"))
    }

    @Test
    fun shortValuesKeepTheLargeStep() {
        assertFalse(brandTitleIsCompact("已配置"))
        assertFalse(brandTitleIsCompact("配置 1"))
        assertFalse(brandTitleIsCompact(""))
    }
}
