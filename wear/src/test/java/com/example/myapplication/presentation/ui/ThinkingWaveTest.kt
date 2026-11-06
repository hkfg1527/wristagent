package com.example.myapplication.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 思考行波相位与三个动效重置判据的纯函数契约。
 *
 * 这一组用例钉住的是 bug A 的根因：
 * 旧实现把相位交给一条 2400ms 的无限动画，再用 `phase * 1.4R % 1.25R` 换算半径，
 * 于是"扫满一次可达半径"只需要 2400 × 1.25/1.4 ≈ 2143ms —— 视觉周期与动画周期不等长，
 * 每个周期末尾都会多回绕一次。用户看到的就是"波先放一点点 → 回到初始 → 再过一遍"。
 */
class ThinkingWaveTest {

    private val radius = 240f

    @Test
    fun phaseIsLinearWithinOneCycle() {
        assertEquals(0f, ThinkingWave.phaseOf(0L), 1e-4f)
        assertEquals(0.25f, ThinkingWave.phaseOf(ThinkingWave.CYCLE_MS / 4L), 1e-4f)
        assertEquals(0.5f, ThinkingWave.phaseOf(ThinkingWave.CYCLE_MS / 2L), 1e-4f)
        assertEquals(0f, ThinkingWave.phaseOf(ThinkingWave.CYCLE_MS), 1e-4f)
        // 越过周期后相位必须重新从 0 起：累计值本身不减，只有取模后的相位回绕
        assertEquals(0.25f, ThinkingWave.phaseOf(ThinkingWave.CYCLE_MS + ThinkingWave.CYCLE_MS / 4L), 1e-4f)
    }

    @Test
    fun negativeElapsedStillLandsInUnitRange() {
        val phase = ThinkingWave.phaseOf(-ThinkingWave.CYCLE_MS / 4L)
        assertTrue("相位必须落在 [0,1): $phase", phase >= 0f && phase < 1f)
        assertEquals(0.75f, phase, 1e-4f)
    }

    @Test
    fun travelSweepsReachExactlyOncePerCycle() {
        val reach = ThinkingWave.reachPx(radius)
        assertEquals(0f, ThinkingWave.travelPx(0L, radius), 1e-3f)
        assertEquals(reach * 0.5f, ThinkingWave.travelPx(ThinkingWave.CYCLE_MS / 2L, radius), 1e-3f)
        // 周期末尾无限接近可达半径，然后才回到 0 —— 一个周期只扫一次
        val nearEnd = ThinkingWave.travelPx(ThinkingWave.CYCLE_MS - 1L, radius)
        assertTrue("周期末尾必须接近 reach: $nearEnd vs $reach", reach - nearEnd < reach * 0.01f)
    }

    @Test
    fun travelIsMonotonicWithinOneCycle() {
        var previous = -1f
        var t = 0L
        while (t < ThinkingWave.CYCLE_MS) {
            val travel = ThinkingWave.travelPx(t, radius)
            assertTrue("行波半径必须单调递增: t=$t travel=$travel previous=$previous", travel >= previous)
            previous = travel
            t += 16L
        }
    }

    @Test
    fun oldPhaseMappingWrappedTwice_新映射只回绕一次() {
        val reach = ThinkingWave.reachPx(radius)
        // 旧实现：半径 = (相位 * 1.4R) % (1.25R)
        fun legacyTravel(elapsedMs: Long): Float {
            val phase = (elapsedMs % ThinkingWave.CYCLE_MS).toFloat() / ThinkingWave.CYCLE_MS.toFloat()
            return (phase * radius * 1.4f) % reach
        }
        var legacyWraps = 0
        var newWraps = 0
        var previousLegacy = legacyTravel(0L)
        var previousNew = ThinkingWave.travelPx(0L, radius)
        var t = 16L
        while (t < ThinkingWave.CYCLE_MS) {
            val legacy = legacyTravel(t)
            val current = ThinkingWave.travelPx(t, radius)
            if (legacy < previousLegacy) legacyWraps++
            if (current < previousNew) newWraps++
            previousLegacy = legacy
            previousNew = current
            t += 16L
        }
        // 旧映射在 2400ms 内额外回绕一次 = 用户看到的"回到初始，再过一遍"
        assertEquals(1, legacyWraps)
        assertEquals(0, newWraps)
        // 回绕发生的时刻正是 2400 × 1.25/1.4 ≈ 2143ms —— 证明两个周期不相等
        val legacyWrapAtMs = (ThinkingWave.CYCLE_MS * ThinkingWave.REACH_FACTOR / 1.4f).toLong()
        assertTrue("旧映射回绕点约在 2143ms: $legacyWrapAtMs", abs(legacyWrapAtMs - 2143L) <= 2L)
        assertTrue(legacyTravel(legacyWrapAtMs - 16L) > legacyTravel(legacyWrapAtMs + 16L))
    }

    @Test
    fun oldCycleBoundaryCutAVisibleRing_新映射在边界上是淡完的() {
        val reach = ThinkingWave.reachPx(radius)
        // 旧实现：2400ms 是**无限动画**的周期，但 `phase*1.4R % 1.25R` 在 2143ms 就回绕过一次，
        // 于是周期末尾 travel 又爬回 0.15R 左右 —— 包络给到 ≈0.67，环是肉眼可见的。
        fun legacyTravel(elapsedMs: Long): Float {
            val phase = (elapsedMs % ThinkingWave.CYCLE_MS).toFloat() / ThinkingWave.CYCLE_MS.toFloat()
            return (phase * radius * 1.4f) % reach
        }
        fun legacyEnvelope(travel: Float): Float {
            val ramp = reach * ThinkingWave.RAMP_FACTOR
            return ((travel / ramp).coerceIn(0f, 1f) * ((reach - travel) / ramp).coerceIn(0f, 1f))
        }
        val legacyLastFrame = legacyTravel(ThinkingWave.CYCLE_MS - 1L)
        val legacyEnvelopeLastFrame = legacyEnvelope(legacyLastFrame)
        // 周期最后一帧：环还在约 15% 半径处、亮度 2/3 —— 下一帧被 Restart 剪成 0 = 用户看到的"闪一下"
        assertTrue("旧实现末帧 travel 应约为 0.12R: $legacyLastFrame", legacyLastFrame > reach * 0.1f)
        assertTrue("旧实现末帧包络应约为 0.67: $legacyEnvelopeLastFrame", legacyEnvelopeLastFrame > 0.6f)
        assertEquals(0f, legacyTravel(ThinkingWave.CYCLE_MS), 1e-3f)

        // 新实现：同一时刻 travel 已经把一条波完整送到可达半径，包络为 0 —— 边界处不可见
        val newLastFrame = ThinkingWave.travelPx(ThinkingWave.CYCLE_MS - 1L, radius)
        val newEnvelopeLastFrame = ThinkingWave.envelope(newLastFrame, radius)
        assertTrue("新实现末帧必须已经把波送到边界: $newLastFrame vs $reach", reach - newLastFrame < reach * 0.01f)
        assertTrue("新实现末帧包络必须约为 0: $newEnvelopeLastFrame", newEnvelopeLastFrame < 0.01f)
    }

    @Test
    fun envelopeIsZeroAtBothEndsAndOneInTheMiddle() {
        val reach = ThinkingWave.reachPx(radius)
        assertEquals(0f, ThinkingWave.envelope(0f, radius), 1e-4f)
        assertEquals(0f, ThinkingWave.envelope(reach, radius), 1e-4f)
        assertEquals(0f, ThinkingWave.envelope(reach * 1.05f, radius), 1e-4f)
        assertEquals(1f, ThinkingWave.envelope(reach * 0.5f, radius), 1e-4f)
        // 过渡区内单调上升
        val ramp = ThinkingWave.rampPx(radius)
        assertTrue(ThinkingWave.envelope(ramp * 0.5f, radius) < ThinkingWave.envelope(ramp * 0.9f, radius))
    }

    @Test
    fun zeroRadiusNeverDividesByZero() {
        assertEquals(0f, ThinkingWave.envelope(0f, 0f), 1e-4f)
        assertEquals(0f, ThinkingWave.travelPx(1234L, 0f), 1e-4f)
    }

    @Test
    fun cascadeOnlyResetsWhenAlreadyAtRest() {
        assertTrue("停在 0 才可以重置", ThinkingWave.cascadeAtRest(0f))
        assertTrue(ThinkingWave.cascadeAtRest(0.01f))
        assertFalse("退场途中不许硬拍回 0", ThinkingWave.cascadeAtRest(0.05f))
        assertFalse(ThinkingWave.cascadeAtRest(0.9f))
    }

    @Test
    fun glintOnlyReplaysAfterPreviousSweepFinished() {
        assertTrue("起点 -0.5 算静止", ThinkingWave.glintAtRest(-0.5f))
        assertFalse("可见带内就是在扫，不许重放", ThinkingWave.glintAtRest(0f))
        assertFalse(ThinkingWave.glintAtRest(-0.3f))
        assertFalse(ThinkingWave.glintAtRest(1.3f))
        assertTrue("扫过终点才算静止", ThinkingWave.glintAtRest(1.4f))
    }

    @Test
    fun shockwaveFinalValueIsNotInFlight() {
        assertFalse("关闭动效时是 -1", ThinkingWave.shockInFlight(-1f))
        assertTrue(ThinkingWave.shockInFlight(0f))
        assertTrue(ThinkingWave.shockInFlight(0.5f))
        // 1f 是 animateTo(1f) 的终值：旧判据 in 0f..1.2f 会把它算在途，
        // 于是第一轮回答结束后空闲降频永久失效（每帧重绘）
        assertFalse(ThinkingWave.shockInFlight(1f))
        assertFalse(ThinkingWave.shockInFlight(1.2f))
    }
}
