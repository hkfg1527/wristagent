package com.example.myapplication.presentation.ui.settingsv2.common

import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 圆屏收边的**纯几何**判据（不渲染界面，直接问函数：这一块该缩到多小）。
 *
 * 2026-11-27 改版：从"只收宽度"改成"**等比缩小 + 下限**"（用户原话：
 * 「它可以整体缩小，它为什么一定要形变呢……至少有得有个线度，不能完全挤压吧」）。
 * 这里同时钉住三件事：几何算得对、单调、以及**永远不小于下限**。
 */
class CircleFitMathTest {

    private val w = 466f
    private val h = 466f
    private val half = 24f

    private fun scaleAt(centerY: Float, minScale: Float = 0.76f) =
        circleFitScale(centerY - half, centerY + half, w, h, minScale)

    @Test
    fun theScreenCenterKeepsFullScale() {
        // 零厚度的元素在正中：弦长 = 直径 → 恰好 1.0
        assertEquals("屏幕正中的零厚度元素不该缩小", 1f, circleFitScale(h / 2f, h / 2f, w, h, 0.76f), 1e-4f)
        // 有厚度的元素：它的上下边比中线窄，所以会略小于 1（这里 24dp 高 → 0.9947）——
        // 这是正确的（整块都要在圆内），不是 bug。下限给 0.99 是防止有人把"取最窄处"去掉。
        assertTrue("有厚度的居中元素应接近满缩放：${scaleAt(h / 2f)}", scaleAt(h / 2f) > 0.99f)
    }

    @Test
    fun scaleMatchesTheCircleEquationAtAKnownHeight() {
        // y=100 处 dy=133 → 半弦 sqrt(233²-133²)=191.35 → 整弦 382.7 → 382.7/466
        val chord = 2f * sqrt(233f * 233f - 133f * 133f)
        val expected = chord / w
        // 取上下边与中线三处的最窄值：这里把元素做得很扁，三处几乎相同
        val actual = circleFitScale(100f - 0.5f, 100f + 0.5f, w, h, 0.1f)
        assertEquals("y=100 处的等比系数必须等于圆方程算出的弦长比", expected, actual, 0.01f)
    }

    @Test
    fun scaleShrinksMonotonicallyTowardsTheEdges() {
        // 只在"还没触到下限 0.76"的区间比较单调（实测 y=100 起就已经等于下限）
        val samples = listOf(233f, 200f, 150f).map { scaleAt(it) }
        samples.zipWithNext().forEach { (near, far) ->
            assertTrue("越靠近上下两端，元素必须越小：$samples", far < near)
        }
        // 再往下必然撞到下限（实测 y=120 时因元素有厚度仍是 0.81，y=40 起才到底）
        listOf(40f, 0f, -20f).forEach { y ->
            assertEquals("y=$y 应停在下限", 0.76f, scaleAt(y), 1e-4f)
        }
    }

    @Test
    fun scaleNeverGoesBelowTheFloor() {
        // 贴到最顶端（圆外）时，纯几何会是 0；必须有下限兜住
        val floor = 0.76f
        listOf(0f, -50f, 5f, 10f).forEach { y ->
            val s = circleFitScale(y - half, y + half, w, h, floor)
            assertTrue("y=$y 处缩到 $s，低于下限 $floor —— 用户要求'不能完全挤压'", s >= floor - 1e-3f)
        }
    }

    @Test
    fun scaleIsSymmetricTopAndBottom() {
        listOf(40f, 100f, 160f).forEach { dy ->
            val up = scaleAt(h / 2f - dy)
            val down = scaleAt(h / 2f + dy)
            assertEquals("y 偏移 ±$dy 必须得到相同系数", up, down, 1e-4f)
        }
    }

    @Test
    fun anItemNearTheTopIsSmallerThanOneInTheMiddle() {
        val middle = scaleAt(h / 2f)
        val top = scaleAt(40f)
        assertTrue("靠近顶部必须比中间小（$top vs $middle）", top < middle)
    }
}
