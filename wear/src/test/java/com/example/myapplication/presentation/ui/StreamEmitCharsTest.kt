package com.example.myapplication.presentation.ui

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 突发判定字数（设置项 stream_emit_chars）的语义与真实接线。
 *
 * 和合帧间隔是一对：间隔决定"多久刷一次"，阈值决定"涨多少算吐了一大段"。
 * 阈值跑不进状态机，滑杆就是个假开关，所以这里既测语义也测接线。
 */
class StreamEmitCharsTest {

    @Test
    fun theThresholdDecidesWhatCountsAsABurst() {
        // 阈值 12：涨 13 个字算突发，涨 12 个不算
        assertTrue(StreamingThrottle.isBurst("", "x".repeat(13), 12))
        assertFalse(StreamingThrottle.isBurst("", "x".repeat(12), 12))
        // 阈值 4：涨 5 个字就算突发
        assertTrue(StreamingThrottle.isBurst("abc", "abcdefgh", 4))
    }

    @Test
    fun theStateMachineUsesTheConfiguredThreshold() {
        val throttle = StreamingThrottleState(intervalMs = 0L, charsPerEmitThreshold = 40)
        throttle.accept("x".repeat(10), 1L, force = true)
        throttle.accept("x".repeat(30), 2L, force = true)
        assertFalse("阈值 40 时涨 20 个字不算突发", throttle.isBurst)

        throttle.updateCharsPerEmitThreshold(4)
        assertEquals(4, throttle.currentCharsPerEmitThreshold)
        throttle.accept("x".repeat(40), 3L, force = true)
        assertTrue("改成 4 之后涨 10 个字就是突发", throttle.isBurst)
    }

    @Test
    fun theThresholdIsClampedInsideTheStateMachine() {
        val throttle = StreamingThrottleState(intervalMs = 0L)
        throttle.updateCharsPerEmitThreshold(0)
        assertEquals(StreamingThrottle.MIN_STREAM_EMIT_CHARS, throttle.currentCharsPerEmitThreshold)
        throttle.updateCharsPerEmitThreshold(9999)
        assertEquals(StreamingThrottle.MAX_STREAM_EMIT_CHARS, throttle.currentCharsPerEmitThreshold)
    }

    @Test
    fun theViewModelFeedsThePreferenceIntoTheThrottle() {
        val viewModel = readProjectFile("src/main/java/com/example/myapplication/presentation/ChatViewModel.kt")
        assertTrue(
            "必须把偏好里的阈值交给节流器",
            viewModel.contains("throttle.updateCharsPerEmitThreshold(displayPreferences().streamEmitChars)") ||
                viewModel.contains("updateCharsPerEmitThreshold(gateway.displayPreferences().streamEmitChars)")
        )
    }
}
