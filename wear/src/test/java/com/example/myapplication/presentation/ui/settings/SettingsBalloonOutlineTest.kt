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

    private fun outline(
        top: Float = topInRootDp,
        height: Float = heightDp
    ) = SettingsCurveGeometry.outlineSegments(
        widthPx = widthDp,
        heightPx = height,
        topInRootPx = top,
        screenHeightPx = screenDp,
        screenMarginPx = marginDp,
        capHeightPx = capDp
    )

    private fun flatten(
        steps: Int = 24,
        top: Float = topInRootDp,
        height: Float = heightDp
    ): List<Pair<Float, Float>> {
        val points = ArrayList<Pair<Float, Float>>()
        for (s in outline(top, height)) {
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
        val radius = screenDp / 2f - marginDp
        val centerLocal = screenDp / 2f - topInRootDp
        val cap = SettingsCurveGeometry.balloonCapPx(heightDp, centerX, centerLocal, radius, capDp)
        assertTrue("收口必须高过 20dp 下限，肩部才有足够长度过渡", cap > capDp)
        assertTrue("收口不得超过 2.4 倍下限（48dp）", cap <= capDp * SettingsCurveGeometry.CapMaxRatio + 0.01f)
        val shoulder = (kotlin.math.sqrt((radius * radius - (cap - centerLocal) * (cap - centerLocal)).coerceAtLeast(0f)))
            .coerceAtMost(centerX)
        assertTrue(
            "收口高 " + cap + " 不得超过肩部半宽 " + shoulder + "，否则窄气球会被收成一个尖",
            cap <= shoulder + 0.01f
        )
        assertTrue("半宽:拱高必须大于 2:1 才不是平盖", shoulder / cap >= 2f - 0.01f)
    }

    @Test
    fun theSilhouetteNeverBitesBackFromItsWidestRow() {
        // 组落在屏幕中部偏下：旧的两段拼法会在这里把控制点甩到宽度极值之外，肩外鼓出一块。
        val points = flatten(top = 15f, height = 200f)
        val rows = points.groupBy { it.second.toInt() }
            .mapValues { entry -> entry.value.maxOf { abs(it.first - centerX) } }
        val keys = rows.keys.sorted()
        val widestKey = keys.maxByOrNull { rows.getValue(it) } ?: return
        var previous = 0f
        keys.filter { it < widestKey }.forEach { key ->
            val value = rows.getValue(key)
            assertTrue(
                "第 " + key + " 行半宽 " + value + " 小于之前的 " + previous +
                    "：轮廓在肩部咬进去，这就是小犄角",
                value >= previous - 0.6f
            )
            previous = maxOf(previous, value)
        }
    }

    @Test
    fun theSmallestWatchGeometryKeepsTheSameRules() {
        // 192dp 圆屏（Pixel Watch 2）：内容盒 176dp，气球只有 88dp 半宽，是最紧的一档
        val screen = 192f
        val half = 88f
        val segments = SettingsCurveGeometry.outlineSegments(
            widthPx = 176f,
            heightPx = 130f,
            topInRootPx = 40f,
            screenHeightPx = screen,
            screenMarginPx = 4f,
            capHeightPx = 20f
        )
        val points = ArrayList<Pair<Float, Float>>()
        for (s in segments) {
            for (i in 0..24) {
                val t = i / 24f
                val mt = 1f - t
                points += Pair(
                    mt * mt * mt * s.x0 + 3f * mt * mt * t * s.c1x + 3f * mt * t * t * s.c2x + t * t * t * s.x1,
                    mt * mt * mt * s.y0 + 3f * mt * mt * t * s.c1y + 3f * mt * t * t * s.c2y + t * t * t * s.y1
                )
            }
        }
        val overshoot = points.maxOf { p ->
            val fromCenter = 40f + p.second - screen / 2f
            kotlin.math.abs(p.first - 88f) - SettingsCurveMath.halfWidthDp(fromCenter, screen, half)
        }
        assertTrue("192dp 屏上轮廓同样不得越过圆弦，实测超界 " + overshoot + "dp", overshoot <= 0.25f)
        val widest = points.maxOf { p -> kotlin.math.abs(p.first - 88f) }
        assertEquals("192dp 屏上气球仍要撑满 176dp 内容盒", 88f, widest, 0.5f)
        // 左右镜像
        for (p in points) {
            val mirror = 176f - p.first
            val nearest = points.minOf { q -> kotlin.math.hypot(q.first - mirror, q.second - p.second) }
            assertTrue("192dp 屏上必须左右镜像，最近距离 " + nearest, nearest <= 0.1f)
        }
    }

    @Test
    fun theOutlineStaysWideAtItsWidestPoint() {
        val widest = flatten().maxOf { point -> abs(point.first - centerX) }
        assertEquals("主气球仍要撑满内容盒", centerX, widest, 0.5f)
    }

    @Test
    fun theOutlineIsClosedAndSymmetric() {
        val segments = outline()
        assertTrue("至少要有收口与侧壁", segments.size > 8)
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
