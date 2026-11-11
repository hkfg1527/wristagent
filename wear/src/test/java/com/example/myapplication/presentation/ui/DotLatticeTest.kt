package com.example.myapplication.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * 主键按压位移层的纯函数契约：零振幅/超距的零输出、原点不产生 NaN、
 * 四个象限的方向一致性、位移与缩放随距离单调递减、关于振幅的线性。
 */
class DotLatticeTest {

    // 衰减半径取 10px：t = distancePx / 10，断言里的权重刻度一眼可核对
    private val falloffPx = 10f

    @Test
    fun zeroAmplitudeYieldsZeroDisplacementAndUnitScale() {
        for (distance in listOf(0f, 1f, 5f, 9.999f)) {
            val (dx, dy) = DotLattice.displacement(1f, 0f, distance, amplitudePx = 0f, falloffPx = falloffPx)
            assertEquals(0f, dx, 1e-6f)
            assertEquals(0f, dy, 1e-6f)
            assertEquals(1f, DotLattice.scale(distance, amplitude = 0f, falloffPx = falloffPx), 1e-6f)
        }
    }

    @Test
    fun distanceBeyondFalloffYieldsZeroDisplacementAndUnitScale() {
        assertEquals(0f, DotLattice.displacementX(1f, falloffPx, 6f, falloffPx), 1e-6f)
        assertEquals(0f, DotLattice.displacementY(0f, falloffPx * 3f, 6f, falloffPx), 1e-6f)
        val (dx, dy) = DotLattice.displacement(0.6f, 0.8f, falloffPx + 0.001f, 6f, falloffPx)
        assertEquals(0f, dx, 1e-6f)
        assertEquals(0f, dy, 1e-6f)
        assertEquals(1f, DotLattice.scale(falloffPx, 0.35f, falloffPx), 1e-6f)
        assertEquals(1f, DotLattice.scale(falloffPx * 2.5f, 0.35f, falloffPx), 1e-6f)
    }

    @Test
    fun zeroDistanceYieldsZeroDisplacementWithoutNaN() {
        val (dx, dy) = DotLattice.displacement(0f, 0f, distancePx = 0f, amplitudePx = 8f, falloffPx = falloffPx)
        assertFalse("原点方向未定义，位移必须是 0 而不是 NaN", dx.isNaN() || dy.isNaN())
        assertEquals(0f, dx, 1e-6f)
        assertEquals(0f, dy, 1e-6f)
        assertFalse(DotLattice.displacementX(1f, 0f, 8f, falloffPx).isNaN())
        assertFalse(DotLattice.displacementY(1f, 0f, 8f, falloffPx).isNaN())
    }

    @Test
    fun displacementFollowsRadialDirectionInAllFourQuadrants() {
        val distance = 3f
        val amplitude = 4f
        // 第一象限：方向 (+x, +y) → 位移必须同号
        val q1x = DotLattice.displacementX(0.6f, distance, amplitude, falloffPx)
        val q1y = DotLattice.displacementY(0.8f, distance, amplitude, falloffPx)
        assertTrue(q1x > 0f && q1y > 0f)
        // 第二象限：方向 (-x, +y)
        val q2x = DotLattice.displacementX(-0.6f, distance, amplitude, falloffPx)
        val q2y = DotLattice.displacementY(0.8f, distance, amplitude, falloffPx)
        assertTrue(q2x < 0f && q2y > 0f)
        // 第三象限：方向 (-x, -y)
        val q3x = DotLattice.displacementX(-0.6f, distance, amplitude, falloffPx)
        val q3y = DotLattice.displacementY(-0.8f, distance, amplitude, falloffPx)
        assertTrue(q3x < 0f && q3y < 0f)
        // 第四象限：方向 (+x, -y)
        val q4x = DotLattice.displacementX(0.6f, distance, amplitude, falloffPx)
        val q4y = DotLattice.displacementY(-0.8f, distance, amplitude, falloffPx)
        assertTrue(q4x > 0f && q4y < 0f)
        // 同一距离下四个象限的位移模长必须相等：推开的只是方向，不是力度
        val magnitude = sqrt(q1x * q1x + q1y * q1y)
        assertEquals(magnitude, sqrt(q2x * q2x + q2y * q2y), 1e-5f)
        assertEquals(magnitude, sqrt(q3x * q3x + q3y * q3y), 1e-5f)
        assertEquals(magnitude, sqrt(q4x * q4x + q4y * q4y), 1e-5f)
    }

    @Test
    fun displacementDecreasesMonotonicallyAsDistanceGrows() {
        val amplitude = 6f
        var previous = DotLattice.displacementX(1f, 0.5f, amplitude, falloffPx)
        assertTrue("贴原点的位移必须是最大值", previous > 0f)
        var distance = 0.5f
        while (distance <= falloffPx) {
            val current = DotLattice.displacementX(1f, distance, amplitude, falloffPx)
            assertTrue("距离 " + distance + " 的位移 " + current + " 不得大于更近处的 " + previous, current <= previous)
            previous = current
            distance += 0.25f
        }
        assertEquals(0f, previous, 1e-6f)
    }

    @Test
    fun scaleDecreasesMonotonicallyAsDistanceGrows() {
        val amplitude = 0.35f
        var previous = DotLattice.scale(0.5f, amplitude, falloffPx)
        assertTrue("放大倍数必须 >= 1", previous >= 1f)
        var distance = 0.5f
        while (distance <= falloffPx) {
            val current = DotLattice.scale(distance, amplitude, falloffPx)
            assertTrue("距离 " + distance + " 的放大倍数 " + current + " 不得大于更近处的 " + previous, current <= previous)
            assertTrue(current >= 1f)
            previous = current
            distance += 0.25f
        }
        assertEquals(1f, previous, 1e-6f)
    }

    @Test
    fun scaleAtOriginReachesPeakAmplitude() {
        // 原点没有方向问题，但放大必须有：主键正下方的点是被顶起来的那一颗
        assertEquals(1f + 0.35f, DotLattice.scale(0f, 0.35f, falloffPx), 1e-6f)
        // 半程（t = 0.5）的权重是 0.25：1 + 0.35 * 0.25
        assertEquals(1.0875f, DotLattice.scale(falloffPx * 0.5f, 0.35f, falloffPx), 1e-6f)
    }

    @Test
    fun pairDisplacementMatchesScalarComponents() {
        // Pair 版是契约面，标量版是绘制热路径：两者必须逐位一致，否则测试通过而真机不生效
        val dirX = 0.28f
        val dirY = -0.96f
        val (dx, dy) = DotLattice.displacement(dirX, dirY, 4.5f, 7f, falloffPx)
        assertEquals(DotLattice.displacementX(dirX, 4.5f, 7f, falloffPx), dx, 0f)
        assertEquals(DotLattice.displacementY(dirY, 4.5f, 7f, falloffPx), dy, 0f)
    }

    @Test
    fun displacementIsLinearInAmplitude() {
        val half = DotLattice.displacementX(1f, 2f, amplitudePx = 3f, falloffPx = falloffPx)
        val full = DotLattice.displacementX(1f, 2f, amplitudePx = 6f, falloffPx = falloffPx)
        assertEquals(half * 2f, full, 1e-5f)
        assertTrue(full > half)
    }

    @Test
    fun degenerateFalloffCollapsesToZeroInsteadOfNaN() {
        // 衰减半径为 0 / 负数 / NaN 时不能除零：位移收敛到 0，放大收敛到 1
        for (falloff in listOf(0f, -3f, Float.NaN)) {
            assertEquals(0f, DotLattice.displacementX(1f, 1f, 5f, falloff), 1e-6f)
            assertEquals(1f, DotLattice.scale(1f, 0.35f, falloff), 1e-6f)
        }
    }
}
