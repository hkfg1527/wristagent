package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.CameraIdle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 镜头静止休眠的判定契约。
 *
 * 审计结论：镜头循环 while(isActive){ withFrameMillis } 没有静止退出，静置时每 vsync 空转一次。
 * 休眠判据必须是纯函数才可锁定：连续多少帧"无事可做"才停、被打断怎么算、醒来第一帧怎么算。
 */
class CameraIdleTest {

    @Test
    fun `连续无事可做攒够十二帧才休眠`() {
        assertFalse("11 帧还不够", CameraIdle.shouldSleep(11))
        assertTrue("恰好 12 帧就该停", CameraIdle.shouldSleep(12))
        assertTrue("超过阈值同样是停", CameraIdle.shouldSleep(37))
    }

    @Test
    fun `阈值锁定为十二帧约两百毫秒`() {
        assertEquals(12, CameraIdle.SLEEP_AFTER_IDLE_FRAMES)
        val elapsedMs = CameraIdle.SLEEP_AFTER_IDLE_FRAMES * 1000f / 60f
        assertTrue("12 帧应落在 150~250ms（60Hz 下约 200ms），实际 $elapsedMs", elapsedMs in 150f..250f)
    }

    @Test
    fun `中间被打断的连续计数不退出`() {
        var streak = 0
        repeat(5) { streak = CameraIdle.nextStreak(streak, isIdle = true) }
        assertEquals(5, streak)
        // 中途真的动了一帧：计数必须归零，不能"累计五次就睡"
        streak = CameraIdle.nextStreak(streak, isIdle = false)
        assertEquals(0, streak)
        repeat(11) { streak = CameraIdle.nextStreak(streak, isIdle = true) }
        assertFalse("打断之后只攒到 11 帧", CameraIdle.shouldSleep(streak))
        streak = CameraIdle.nextStreak(streak, isIdle = true)
        assertTrue("重新攒够 12 帧才允许休眠", CameraIdle.shouldSleep(streak))
    }

    @Test
    fun `休眠后重新激活的第一帧必须重新判定`() {
        // 睡着那一刻计数就在阈值上；醒来若直接沿用就会立刻再睡，一帧都不判
        val atSleep = CameraIdle.SLEEP_AFTER_IDLE_FRAMES
        assertTrue(CameraIdle.shouldSleep(atSleep))
        val firstFrameAfterWake = CameraIdle.nextStreak(atSleep, isIdle = false)
        assertEquals(0, firstFrameAfterWake)
        assertFalse("醒来第一帧必须逐帧判定", CameraIdle.shouldSleep(firstFrameAfterWake))
        // 醒来第一帧就"动了"的路径同样不能回到休眠
        assertFalse(CameraIdle.shouldSleep(CameraIdle.nextStreak(0, isIdle = true)))
    }

    @Test
    fun `退化阈值不会把镜头立刻睡死`() {
        assertFalse("阈值为 0 时第 0 帧不能睡", CameraIdle.shouldSleep(0, threshold = 0))
        assertTrue(CameraIdle.shouldSleep(1, threshold = 0))
        assertFalse("负数计数不成立", CameraIdle.shouldSleep(-5))
        assertEquals(0, CameraIdle.nextStreak(3, isIdle = false))
    }
}
