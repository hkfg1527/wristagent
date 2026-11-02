package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.agent.contract.ConversationBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 胶囊折叠态持久化键：内容变化、重组、滚动回收后仍要落回同一粒胶囊。 */
class CapsuleKeysTest {

    private fun thought(id: String, reasoning: String) =
        ConversationBlock.Thought(id = id, reasoning = reasoning)

    private fun tool(id: String) =
        ConversationBlock.ToolCall(id = id, toolName = "get_device_status", argsJson = "{}")

    @Test
    fun keySurvivesStreamingRewritesOfTheSameBlock() {
        assertEquals(CapsuleKeys.of(thought("t1", "先")), CapsuleKeys.of(thought("t1", "先看设备状态再用工具")))
    }

    @Test
    fun thoughtAndToolKeysNeverCollide() {
        assertNotEquals(CapsuleKeys.of(thought("same", "x")), CapsuleKeys.of(tool("same")))
    }

    @Test
    fun keysKeepAStableKindPrefix() {
        assertTrue(CapsuleKeys.of(thought("t1", "x")).startsWith("thought:"))
        assertTrue(CapsuleKeys.of(tool("c1")).startsWith("tool:"))
    }
}

/** 展开态集合的纯变换：状态提升到 ViewModel 后，快捷键就是"加/减一个键"。 */
class CapsuleExpansionTest {

    @Test
    fun toggleExpandsThenCollapses() {
        val expanded = CapsuleExpansion.toggle(emptySet(), "thought:t1")
        assertTrue(CapsuleExpansion.isExpanded(expanded, "thought:t1"))
        val collapsed = CapsuleExpansion.toggle(expanded, "thought:t1")
        assertFalse(CapsuleExpansion.isExpanded(collapsed, "thought:t1"))
    }

    @Test
    fun togglingOneCapsuleKeepsTheOthersExpanded() {
        val both = CapsuleExpansion.toggle(CapsuleExpansion.toggle(emptySet(), "thought:t1"), "tool:c1")
        val onlyTool = CapsuleExpansion.toggle(both, "thought:t1")
        assertFalse(CapsuleExpansion.isExpanded(onlyTool, "thought:t1"))
        assertTrue(CapsuleExpansion.isExpanded(onlyTool, "tool:c1"))
    }

    @Test
    fun unknownKeyIsCollapsed() {
        assertFalse(CapsuleExpansion.isExpanded(emptySet(), "thought:missing"))
    }
}

/** 思考胶囊标题：进行中报已用秒数，结束后报记录下来的总耗时。 */
class ThoughtCapsuleStateTest {

    private fun streaming(startedAtMs: Long) =
        ConversationBlock.Thought(id = "t1", reasoning = "想", isStreaming = true, startedAtMs = startedAtMs)

    private fun finished(durationMs: Long) =
        ConversationBlock.Thought(id = "t1", reasoning = "想", isStreaming = false, durationMs = durationMs)

    @Test
    fun streamingThoughtCountsSecondsFromItsStart() {
        assertEquals(12, ThoughtCapsuleState.elapsedSeconds(streaming(1_000L), 13_400L))
    }

    @Test
    fun finishedThoughtReportsRecordedDuration() {
        assertEquals(12, ThoughtCapsuleState.elapsedSeconds(finished(12_345L), 9_999_999L))
    }

    @Test
    fun elapsedSecondsFloorAndNeverGoNegative() {
        assertEquals(0, ThoughtCapsuleState.elapsedSeconds(streaming(5_000L), 5_999L))
        assertEquals(0, ThoughtCapsuleState.elapsedSeconds(streaming(5_000L), 4_000L))
        assertEquals(0, ThoughtCapsuleState.elapsedSeconds(streaming(0L), 60_000L))
    }

    @Test
    fun streamingFlagFollowsTheBlock() {
        assertTrue(ThoughtCapsuleState.isStreaming(streaming(1L)))
        assertFalse(ThoughtCapsuleState.isStreaming(finished(1L)))
    }

    @Test
    fun thinkingDotsCycleOneToThree() {
        assertEquals(".", ThoughtCapsuleState.thinkingDots(0))
        assertEquals("..", ThoughtCapsuleState.thinkingDots(1))
        assertEquals("...", ThoughtCapsuleState.thinkingDots(2))
        assertEquals(".", ThoughtCapsuleState.thinkingDots(3))
    }
}

/** 32ms 节流通道的基准文本：思考增量与正文增量共用同一个尾部取值。 */
class StreamingTailTest {

    @Test
    fun streamingThoughtFeedsTheThrottleTail() {
        val blocks = listOf(
            ConversationBlock.Thought(id = "t1", reasoning = "先看设备状态", isStreaming = true)
        )
        assertEquals("先看设备状态", StreamingTail.of(blocks))
    }

    @Test
    fun newestStreamingSegmentWins() {
        val blocks = listOf(
            ConversationBlock.Thought(id = "t1", reasoning = "想完了", isStreaming = false, durationMs = 1200L),
            ConversationBlock.Text(id = "x1", content = "正在答复", isStreaming = true)
        )
        assertEquals("正在答复", StreamingTail.of(blocks))
    }

    @Test
    fun nothingStreamingMeansEmptyTail() {
        val blocks = listOf(
            ConversationBlock.Thought(id = "t1", reasoning = "想完了", durationMs = 1200L),
            ConversationBlock.Text(id = "x1", content = "答复完毕")
        )
        assertEquals("", StreamingTail.of(blocks))
    }
}
