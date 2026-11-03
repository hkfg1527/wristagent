package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 思考流式的粒度证明：每个 reasoning 增量都必须**分别**产生一次状态提交，
 * 且耗时以请求发出为起点 —— 这两件事一起决定用户看到的是"逐块流式"还是"一次成型 · 0s"。
 */
class ThoughtTimelineTest {

    private fun thoughtOf(blocks: List<ConversationBlock>): ConversationBlock.Thought =
        blocks.last() as ConversationBlock.Thought

    @Test
    fun twoIncrementsAtDifferentTimesProduceTwoStateUpdates() {
        val blocks = mutableListOf<ConversationBlock>()
        var now = 1_000L
        val timeline = ThoughtTimeline(requestStartedAtMs = 1_000L, clock = { now })
        val emitted = mutableListOf<List<ConversationBlock>>()
        val accumulator = StreamAccumulator()

        listOf("先看", "设备状态").forEach { piece ->
            now += 700
            accumulator.applyAll(listOf(StreamDelta.Reasoning(piece)))
            if (timeline.onReasoning(blocks, accumulator.reasoning)) emitted += blocks.toList()
        }

        // 两个不同时刻的增量 -> 两次提交（ViewModel 侧就是两次 onStateUpdate）
        assertEquals(2, emitted.size)
        assertEquals("先看", thoughtOf(emitted[0]).reasoning)
        assertEquals("先看设备状态", thoughtOf(emitted[1]).reasoning)
        // 两次提交改的是同一粒胶囊，不是新加一块
        assertEquals(thoughtOf(emitted[0]).id, thoughtOf(emitted[1]).id)
        assertTrue(thoughtOf(emitted[1]).isStreaming)
        assertEquals(1, blocks.size)
    }

    @Test
    fun emptyReasoningCreatesNoBlock() {
        val blocks = mutableListOf<ConversationBlock>()
        val timeline = ThoughtTimeline(requestStartedAtMs = 1_000L, clock = { 1_000L })

        assertFalse(timeline.onReasoning(blocks, ""))
        assertTrue(blocks.isEmpty())
        assertFalse(timeline.finish(blocks, "", finishedAtMs = 9_000L))
        assertTrue(blocks.isEmpty())
    }

    @Test
    fun durationIsMeasuredFromTheRequestNotFromTheFirstDelta() {
        val blocks = mutableListOf<ConversationBlock>()
        var now = 1_000L
        val timeline = ThoughtTimeline(requestStartedAtMs = 1_000L, clock = { now })

        // 首块增量在 3.5 秒才到（推理模型先憋很久才吐字）：起点仍是请求发出时刻
        now = 3_500L
        assertTrue(timeline.onReasoning(blocks, "想了很久"))
        assertEquals(3_400L, timeline.durationMs(finishedAtMs = 4_400L))
        assertTrue(timeline.finish(blocks, "想了很久", finishedAtMs = 4_400L))
        assertEquals(3_400L, thoughtOf(blocks).durationMs)
        assertFalse(thoughtOf(blocks).isStreaming)
    }

    @Test
    fun oneShotReasoningStillCarriesTheRealSpan() {
        // 非流式回显：整段思考一次到齐。此时也不能报 0s，跨度必须覆盖整轮等待。
        val blocks = mutableListOf<ConversationBlock>()
        val timeline = ThoughtTimeline(requestStartedAtMs = 10_000L, clock = { 14_400L })

        assertTrue(timeline.onReasoning(blocks, "整段思考一次到齐"))
        assertEquals(1, timeline.incrementCount)
        assertTrue(timeline.finish(blocks, "整段思考一次到齐", finishedAtMs = 14_400L))
        assertEquals(4_400L, thoughtOf(blocks).durationMs)
        assertFalse(thoughtOf(blocks).isStreaming)
        assertEquals(10_000L, thoughtOf(blocks).startedAtMs)
    }

    @Test
    fun streamingBlockTicksFromTheRequestStart() {
        val blocks = mutableListOf<ConversationBlock>()
        var now = 1_000L
        val timeline = ThoughtTimeline(requestStartedAtMs = 1_000L, clock = { now })

        assertTrue(timeline.onReasoning(blocks, "想"))
        now = 4_500L
        assertEquals(3_500L, timeline.elapsedMs())
        // 进行中的胶囊报的是同一时基的起点，标题里的秒数不会在收尾时突然跳变
        assertEquals(1_000L, thoughtOf(blocks).startedAtMs)
        assertNotNull(timeline.id)
    }

    @Test
    fun finishWithoutAnyIncrementTouchesNothing() {
        val blocks = mutableListOf<ConversationBlock>()
        val timeline = ThoughtTimeline(requestStartedAtMs = 1_000L, clock = { 2_000L })

        assertFalse(timeline.finish(blocks, "凭空收尾", finishedAtMs = 5_000L))
        assertTrue(blocks.isEmpty())
        assertEquals(0L, timeline.durationMs(finishedAtMs = 5_000L))
    }
}
