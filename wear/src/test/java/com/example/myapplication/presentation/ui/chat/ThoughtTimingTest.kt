package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.agent.ThoughtDuration
import org.junit.Assert.assertEquals
import org.junit.Test

/** 耗时换算：引擎给的是起止墙钟毫秒，界面报的是秒；两端都不许出现负数或"负零"。 */
class ThoughtTimingTest {

    @Test
    fun durationIsThePlainWallClockSpan() {
        assertEquals(3_400L, ThoughtDuration.between(startedAtMs = 1_000L, finishedAtMs = 4_400L))
        assertEquals(29_500L, ThoughtDuration.between(startedAtMs = 500L, finishedAtMs = 30_000L))
    }

    @Test
    fun durationNeverGoesNegativeOrZeroWhenClockIsBroken() {
        assertEquals(0L, ThoughtDuration.between(startedAtMs = 4_400L, finishedAtMs = 1_000L))
        assertEquals(0L, ThoughtDuration.between(startedAtMs = 1_000L, finishedAtMs = 1_000L))
        assertEquals(0L, ThoughtDuration.between(startedAtMs = 0L, finishedAtMs = 60_000L))
    }

    @Test
    fun secondsFloorTowardsTheSecondBelow() {
        assertEquals(3, ThoughtTiming.seconds(3_400L))
        assertEquals(0, ThoughtTiming.seconds(999L))
        assertEquals(60, ThoughtTiming.seconds(60_000L))
        assertEquals(0, ThoughtTiming.seconds(0L))
        assertEquals(0, ThoughtTiming.seconds(-5L))
    }

    @Test
    fun realReasoningSpanIsNotReportedAsZero() {
        // 真机症状：推理数秒却报 0s。以请求发出为起点，4.4 秒必须报 4s，不是 0s。
        val span = ThoughtDuration.between(startedAtMs = 1_000L, finishedAtMs = 5_400L)
        assertEquals(4, ThoughtTiming.seconds(span))
    }

    @Test
    fun capsuleTitleReadsTheBlockDurationAfterFinish() {
        // 收尾后的胶囊把引擎记下的跨度原样报成秒数，不再看"当前时间"
        val finished = com.example.myapplication.agent.contract.ConversationBlock.Thought(
            id = "t1",
            reasoning = "想完了",
            durationMs = ThoughtDuration.between(1_000L, 5_400L),
            isStreaming = false,
            startedAtMs = 1_000L
        )
        assertEquals(4, ThoughtCapsuleState.elapsedSeconds(finished, nowMs = 999_999L))
    }
}
