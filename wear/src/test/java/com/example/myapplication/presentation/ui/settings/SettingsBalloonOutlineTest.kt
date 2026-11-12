package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

/**
 * 主气球轮廓的几何约束。
 *
 * 全类以 dp 为单位传入（outlineSegments 只关心数值一致），
 * 参数取 233dp 圆屏 + 216dp 内容盒，与实机截图为同一组几何。
 */
class SettingsBalloonOutlineTest {
    private val screenDp = 233f
    private val marginDp = 4f
    private val widthDp = 215.5f
    private val topInRootDp = 74.5f
    private val heightDp = 136f
    private val capDp = 20f
    private val centerX = widthDp / 2f

    private fun outline() = SettingsCurveGeometry.outlineSegments(
        widthPx = widthDp,
        heightPx = heightDp,
        topInRootPx = topInRootDp,
        screenHeightPx = screenDp,
        screenMarginPx = marginDp,
        capHeightPx = capDp
    )

    private fun flatten(steps: Int = 32): List<Pair<Float, Float>> {
        val points = ArrayList<Pair<Float, Float>>()
        for (s in outline()) {
            for (i in 0..steps) {
                val t = i.toFloat() / steps
                val mt = 1f - t
                val a = mt * mt * mt
                val b = 3f * mt * mt * t
                val c = 3f * mt * t * t
                val d = t * t * t
                points += Pair(
                    a * s.x0 + b * s.c1x + c * s.c2x + d * s.x1,
                    a * s.y0 + b * s.c1y + c * s.c2y + d * s.y1
                )
            }
        }
        return points
    }

    @Test
    fun theOutlineNeverPokesPastTheScreenChord() {
        val overshoot = flatten().maxOf { point ->
            val fromCenter = topInRootDp + point.second - screenDp / 2f
            abs(point.first - centerX) - SettingsCurveMath.halfWidthDp(fromCenter, screenDp, centerX)
        }
        assertTrue(
            "轮廓必须收在圆弦之内：肩部凸出的小犄角最多超界 0.25dp，实测超界 " + overshoot + "dp",
            overshoot <= 0.25f
        )
    }

    @Test
    fun theCapIsADomeNotAFlatLid() {
        val cap = outline().first()
        val capHeight = cap.y0
        val shoulderHalf = kotlin.math.abs(cap.x0 - centerX)
        assertTrue(
            "穹顶半宽 " + shoulderHalf + " 与高 " + capHeight + " 必须成拱（不超过 2.4:1），" +
                "否则封口会压成平盖加尖角",
            shoulderHalf <= capHeight * 2.4f
        )
        assertTrue("穹顶必须高过 20dp 下限，肩部才有足够长度过渡", capHeight > 20f)
    }

    @Test
    fun theOutlineStaysWideAtItsWidestPoint() {
        val widest = flatten().maxOf { point -> abs(point.first - centerX) }
        assertEquals("主气球仍要撑满内容盒", centerX, widest, 0.5f)
    }

    @Test
    fun theOutlineIsClosedAndSymmetric() {
        val segments = outline()
        assertTrue("至少要有穹顶与侧壁", segments.size > 8)
        val first = segments.first()
        val last = segments.last()
        assertEquals("首尾必须闭合", first.x0, last.x1, 0.01f)
        assertEquals("首尾必须闭合", first.y0, last.y1, 0.01f)

        val points = flatten()
        for (point in points) {
            val mirror = 2f * centerX - point.first
            val nearest = points.minOf { other -> hypot(other.first - mirror, other.second - point.second) }
            assertTrue(
                "左右必须镜像，(" + point.first + "," + point.second + ") 缺失对侧点，最近距离 " + nearest,
                nearest <= 0.1f
            )
        }
    }
}
