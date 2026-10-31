package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.StreamingThrottle
import com.example.myapplication.presentation.ui.StreamingThrottleState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式刷新节流与"过快"判定的边界测试
 * 1. [StreamingThrottle.shouldEmit]：间隔不足/刚好/超出、自定义间隔、从未提交过、时钟回拨
 * 2. [StreamingThrottle.isBurst]：单次增量 11/12/13 字、文本变短或不变不算突发
 * 3. [StreamingThrottleState]：被节流的更新不能推进基线文本
 */
class StreamingThrottleTest {

    private fun chars(n: Int): String = "字".repeat(n)

    @Test
    fun shouldEmit_belowInterval_isFalse() {
        assertFalse(StreamingThrottle.shouldEmit(lastEmitMs = 1_000L, nowMs = 1_069L))
    }

    @Test
    fun shouldEmit_exactlyAtInterval_isTrue() {
        assertTrue(StreamingThrottle.shouldEmit(lastEmitMs = 1_000L, nowMs = 1_070L))
    }

    @Test
    fun shouldEmit_aboveInterval_isTrue() {
        assertTrue(StreamingThrottle.shouldEmit(lastEmitMs = 1_000L, nowMs = 1_071L))
    }

    @Test
    fun shouldEmit_customInterval_followsGivenInterval() {
        assertFalse(StreamingThrottle.shouldEmit(lastEmitMs = 1_000L, nowMs = 1_149L, intervalMs = 150L))
        assertTrue(StreamingThrottle.shouldEmit(lastEmitMs = 1_000L, nowMs = 1_150L, intervalMs = 150L))
    }

    @Test
    fun shouldEmit_neverEmittedBefore_isTrue() {
        assertTrue(StreamingThrottle.shouldEmit(lastEmitMs = 0L, nowMs = 0L))
    }

    @Test
    fun shouldEmit_clockWentBackwards_isFalse() {
        assertFalse(StreamingThrottle.shouldEmit(lastEmitMs = 5_000L, nowMs = 4_990L))
    }

    @Test
    fun isBurst_deltaElevenChars_isFalse() {
        assertFalse(StreamingThrottle.isBurst(prevText = "", newText = chars(11)))
    }

    @Test
    fun isBurst_deltaExactlyTwelveChars_isFalse() {
        assertFalse(StreamingThrottle.isBurst(prevText = "", newText = chars(12)))
    }

    @Test
    fun isBurst_deltaThirteenChars_isTrue() {
        assertTrue(StreamingThrottle.isBurst(prevText = "", newText = chars(13)))
    }

    @Test
    fun isBurst_deltaMeasuredFromPreviousText_isTrueOnlyAboveThreshold() {
        assertFalse(StreamingThrottle.isBurst(prevText = chars(5), newText = chars(5 + 12)))
        assertTrue(StreamingThrottle.isBurst(prevText = chars(5), newText = chars(5 + 13)))
    }

    @Test
    fun isBurst_customThreshold_followsGivenThreshold() {
        assertFalse(StreamingThrottle.isBurst(prevText = "", newText = chars(3), charsPerEmitThreshold = 3))
        assertTrue(StreamingThrottle.isBurst(prevText = "", newText = chars(4), charsPerEmitThreshold = 3))
    }

    @Test
    fun isBurst_shrinkingText_isFalse() {
        assertFalse(StreamingThrottle.isBurst(prevText = chars(20), newText = chars(4)))
    }

    @Test
    fun isBurst_unchangedText_isFalse() {
        assertFalse(StreamingThrottle.isBurst(prevText = chars(20), newText = chars(20)))
    }

    @Test
    fun state_firstAcceptAtSameMillisecond_isApplied() {
        val state = StreamingThrottleState()
        assertTrue(state.accept(newText = chars(3), nowMs = 0L))
        assertEquals(chars(3), state.lastText)
        assertEquals(0L, state.lastEmitMs)
    }

    @Test
    fun state_acceptInsideInterval_isDroppedAndKeepsBaseline() {
        val state = StreamingThrottleState()
        assertTrue(state.accept(newText = chars(3), nowMs = 1_000L))
        assertFalse(state.accept(newText = chars(9), nowMs = 1_069L))
        assertEquals(chars(3), state.lastText)
        assertEquals(1_000L, state.lastEmitMs)
    }

    @Test
    fun state_burstFlagFollowsAppliedDelta() {
        val state = StreamingThrottleState()
        state.accept(newText = "", nowMs = 1_000L)
        state.accept(newText = chars(12), nowMs = 1_070L)
        assertFalse(state.isBurst)
        state.accept(newText = chars(12 + 13), nowMs = 1_140L)
        assertTrue(state.isBurst)
    }

    @Test
    fun state_forcedAcceptBypassesIntervalAndRecordsText() {
        val state = StreamingThrottleState()
        state.accept(newText = chars(2), nowMs = 1_000L)
        assertTrue(state.accept(newText = chars(40), nowMs = 1_010L, force = true))
        assertEquals(chars(40), state.lastText)
        assertEquals(1_010L, state.lastEmitMs)
    }

    @Test
    fun state_reset_clearsBaselineAndBurst() {
        val state = StreamingThrottleState()
        state.accept(newText = chars(30), nowMs = 1_000L)
        assertTrue(state.isBurst)
        state.reset()
        assertEquals("", state.lastText)
        assertEquals(0L, state.lastEmitMs)
        assertFalse(state.isBurst)
        assertTrue(state.accept(newText = chars(1), nowMs = 0L))
    }
}
