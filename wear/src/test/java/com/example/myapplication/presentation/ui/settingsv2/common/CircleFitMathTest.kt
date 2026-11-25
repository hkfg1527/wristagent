package com.example.myapplication.presentation.ui.settingsv2.common

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆屏收边的**纯几何**判据（不渲染界面，直接问函数：这个高度该缩多少）。
 *
 * 为什么需要它：0.0.0.263 之前 `M3_LIST` / `SQUARE` 没有接 `CircleFit`，
 * 分区标题在靠近上下两端时被圆弧切字（圆图实测：「连得上」左侧缺一块）。
 * 这条判据同时钉住"几何算得对"和"三个列表风格真的接了"（后者在架构守卫里）。
 */
class CircleFitMathTest {

    private val w = 466f
    private val h = 466f

    @Test
    fun theVerticalCenterNeedsNoInset() {
        assertEquals("屏幕正中可用弦长 = 直径，不该内缩", 0f, circleInsetPx(h / 2f, w, h), 1e-3f)
    }

    @Test
    fun insetMatchesTheCircleEquationAtAKnownHeight() {
        // 圆心在 (233,233)，半径 233；y=100 处 dy=133 → 半弦 = sqrt(233²-133²) = 191.35
        val expected = 233f - sqrt(233f * 233f - 133f * 133f)
        val actual = circleInsetPx(100f, w, h)
        assertEquals("y=100 处内缩必须等于圆方程算出的值", expected, actual, 0.5f)
    }

    @Test
    fun insetGrowsMonotonicallyTowardsTheEdges() {
        val samples = listOf(233f, 200f, 150f, 100f, 60f, 20f, 0f).map { circleInsetPx(it, w, h) }
        samples.zipWithNext().forEach { (near, far) ->
            assertTrue("越靠近上下两端，内缩必须越大：$samples", far > near)
        }
    }

    @Test
    fun insetIsSymmetricAboveAndBelowTheCenter() {
        assertEquals(
            "屏幕上下必须对称（同一 dy 的内缩相同）",
            circleInsetPx(233f - 80f, w, h),
            circleInsetPx(233f + 80f, w, h),
            1e-3f,
        )
    }

    @Test
    fun outsideTheCircleTheInsetIsTheWholeHalfWidth() {
        // dy >= 半径时半弦为 0，只剩半径本身（这时内容已完全不可见，收边也到极限）
        assertEquals(233f, circleInsetPx(-40f, w, h), 1e-3f)
        assertEquals(233f, circleInsetPx(506f, w, h), 1e-3f)
    }
}
