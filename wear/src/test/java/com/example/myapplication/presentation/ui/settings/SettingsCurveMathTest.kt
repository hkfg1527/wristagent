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
    fun theHalfWidthHasNoStepAtARowBoundary() {
        // 旧写法把同一个表达式算两遍再 assertEquals(above, below) —— 纯函数同参数同结果，
        // 数学上恒成立，边界处哪怕有 1dp 台阶也永远绿（0.0.0.113 审查点名）。
        // 现在改成真判据：跨过边界 y 前后各 0.5dp 取值，宽度不许跳变。
        // 入参是"距屏心的 dp"，必须落在圆内（|y| < radius），否则两边都是 0、判据空转。
        val boundary = 50f
        val justAbove = SettingsCurveMath.halfWidthDp(boundary - 0.5f, 233f, 108f)
        val justBelow = SettingsCurveMath.halfWidthDp(boundary + 0.5f, 233f, 108f)
        assertTrue("取样点必须在圆内（否则两边都是 0）：above=$justAbove", justAbove > 1f)
        assertTrue(
            "边界两侧宽度出现台阶：above=$justAbove below=$justBelow",
            kotlin.math.abs(justAbove - justBelow) < 0.5f
        )
        // 反证函数在这个位置不是常数：越靠上越窄
        val atCenter = SettingsCurveMath.halfWidthDp(0f, 233f, 108f)
        assertTrue("越远离屏心必须越窄：center=$atCenter boundary=$justAbove", atCenter > justAbove)
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
        // 0.0.0.49：`cardPath`（每行自绘一段弦长轮廓）连同它唯一那条不可达调用一起删掉了 ——
        // 现在全项目只剩"整组一张卡"的 `buildGroupPath`，这条曲线契约就钉在它身上。
        assertFalse("cardPath 已随不可达分支删除，别再加回来", source.contains("fun cardPath("))
        val groupPath = source.substringAfter("fun buildGroupPath(").substringBefore("private fun wallSlice")
        assertTrue("轮廓必须用三次曲线连成", groupPath.contains("cubicTo("))
        assertFalse("轮廓不得退化成直线拼接", groupPath.contains("lineTo("))
        val wall = source.substringAfter("private fun wallSlice(").substringBefore("fun ")
        assertTrue("侧壁两端的切线控制点必须真的算出来", wall.contains("c1x =") && wall.contains("c2x ="))
        assertTrue("封口只保留 20dp 浅弧", source.contains("CapHeightDp = 20f"))
        assertFalse("封口不得再用第二个四分之一圆拼出圆拱", source.contains("BezierCircle"))
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