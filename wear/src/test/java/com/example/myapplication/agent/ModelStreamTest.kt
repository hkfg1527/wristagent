package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 思考增量与正文增量的分流：两条通道各自累积，任何一侧缺省都不能污染另一侧。 */
class StreamEventMapperTest {

    @Test
    fun reasoningContentBecomesReasoningEvent() {
        assertEquals(
            listOf<StreamDelta>(StreamDelta.Reasoning("先看设备状态")),
            StreamEventMapper.eventsOf(DeltaFields(reasoningContent = "先看设备状态"))
        )
    }

    @Test
    fun reasoningAliasKeyProducesTheSameEvent() {
        assertEquals(
            listOf<StreamDelta>(StreamDelta.Reasoning("想一下")),
            StreamEventMapper.eventsOf(DeltaFields(reasoning = "想一下"))
        )
    }

    @Test
    fun reasoningWinsOverAliasWhenBothPresent() {
        assertEquals(
            listOf<StreamDelta>(StreamDelta.Reasoning("正式字段")),
            StreamEventMapper.eventsOf(DeltaFields(reasoningContent = "正式字段", reasoning = "别名"))
        )
    }

    @Test
    fun contentAndReasoningInOneChunkStayInTheirOwnChannel() {
        assertEquals(
            listOf<StreamDelta>(StreamDelta.Reasoning("思考"), StreamDelta.Content("正文")),
            StreamEventMapper.eventsOf(DeltaFields(reasoningContent = "思考", content = "正文"))
        )
    }

    @Test
    fun blankPiecesProduceNoEvents() {
        assertTrue(StreamEventMapper.eventsOf(DeltaFields(reasoningContent = "", content = "")).isEmpty())
        assertTrue(StreamEventMapper.eventsOf(DeltaFields(reasoningContent = null, reasoning = null)).isEmpty())
    }
}

/** 累加器：思考与正文分开存，工具调用分片拼成一条完整调用。 */
class StreamAccumulatorTest {

    @Test
    fun reasoningAndContentAccumulateIndependently() {
        val accumulator = StreamAccumulator()
        accumulator.applyAll(
            listOf(
                StreamDelta.Reasoning("先"),
                StreamDelta.Content("答"),
                StreamDelta.Reasoning("再"),
                StreamDelta.Content("复")
            )
        )
        assertEquals("先再", accumulator.reasoning)
        assertEquals("答复", accumulator.content)
    }

    @Test
    fun missingReasoningDeltasLeaveContentIntact() {
        val accumulator = StreamAccumulator()
        accumulator.applyAll(
            listOf(
                StreamDelta.Content("你好"),
                StreamDelta.Content("，我是手表助手")
            )
        )
        assertEquals("你好，我是手表助手", accumulator.content)
        assertEquals("", accumulator.reasoning)
    }

    @Test
    fun toolCallFragmentsAssembleIntoOneStructuredCall() {
        val accumulator = StreamAccumulator()
        accumulator.applyAll(
            listOf(
                StreamDelta.ToolCall(index = 0, id = "call_1", nameDelta = "get_device_status", argumentsDelta = ""),
                StreamDelta.ToolCall(index = 0, id = null, nameDelta = "", argumentsDelta = "{\"detail\":"),
                StreamDelta.ToolCall(index = 0, id = null, nameDelta = "", argumentsDelta = "true}")
            )
        )
        val calls = accumulator.toolCalls()
        assertEquals(1, calls.size)
        assertEquals("call_1", calls[0].id)
        assertEquals("get_device_status", calls[0].name)
        assertEquals("{\"detail\":true}", calls[0].arguments)
        // 工具调用分片不是正文：正文通道必须保持干净
        assertEquals("", accumulator.content)
    }

    @Test
    fun parallelToolCallsKeepTheirOwnIndexOrder() {
        val accumulator = StreamAccumulator()
        accumulator.applyAll(
            listOf(
                StreamDelta.ToolCall(0, "call_a", "vibrate_device", "{\"ms\":100}"),
                StreamDelta.ToolCall(1, "call_b", "get_device_status", "{}")
            )
        )
        val calls = accumulator.toolCalls()
        assertEquals(2, calls.size)
        assertEquals("vibrate_device", calls[0].name)
        assertEquals("{\"ms\":100}", calls[0].arguments)
        assertEquals("get_device_status", calls[1].name)
    }

    @Test
    fun namelessToolCallFragmentIsDropped() {
        val accumulator = StreamAccumulator()
        accumulator.applyAll(listOf(StreamDelta.ToolCall(0, null, "", "{\"detail\":true}")))
        assertTrue(accumulator.toolCalls().isEmpty())
    }

    @Test
    fun resetClearsBothChannels() {
        val accumulator = StreamAccumulator()
        accumulator.applyAll(
            listOf(
                StreamDelta.Reasoning("思考"),
                StreamDelta.Content("正文"),
                StreamDelta.ToolCall(0, "call_1", "vibrate_device", "{}")
            )
        )
        accumulator.reset()
        assertEquals("", accumulator.reasoning)
        assertEquals("", accumulator.content)
        assertTrue(accumulator.toolCalls().isEmpty())
    }
}
