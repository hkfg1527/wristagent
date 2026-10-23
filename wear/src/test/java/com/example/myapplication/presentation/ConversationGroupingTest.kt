package com.example.myapplication.presentation

import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ui.ConversationGrouping
import com.example.myapplication.presentation.ui.RenderableConversationItem
import com.example.myapplication.presentation.ui.ToolVisualMeta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话块分组与工具视觉映射单元测试 (TDD 严格红绿循环)
 * 验证：
 * 1. 用户输入始终被解析为独立右对齐气泡
 * 2. 属于 Assistant 的所有思考、工具调用、正文回复均被聚合在同一个方形框 (AssistantTurn) 内部
 * 3. 工具视觉映射具备专属颜色与中文描述
 */
class ConversationGroupingTest {

    @Test
    fun testGrouping_userPromptFollowedByThoughtAndText_groupedIntoSingleAssistantTurn() {
        val promptBlock = ConversationBlock.Text(id = "user_1", content = "今天北京天气怎么样？")
        val thoughtBlock = ConversationBlock.Thought(id = "thought_1", reasoning = "主人想查询北京的天气...")
        val textBlock = ConversationBlock.Text(id = "agent_1", content = "北京今天晴，气温 22℃。")

        val blocks = listOf(promptBlock, thoughtBlock, textBlock)
        val groups = ConversationGrouping.groupBlocks(blocks, activeUserPrompts = setOf(promptBlock.id))

        assertEquals(2, groups.size)
        assertTrue(groups[0] is RenderableConversationItem.UserPrompt)
        assertEquals("user_1", (groups[0] as RenderableConversationItem.UserPrompt).block.id)

        assertTrue(groups[1] is RenderableConversationItem.AssistantTurn)
        val assistantTurn = groups[1] as RenderableConversationItem.AssistantTurn
        assertEquals(2, assistantTurn.blocks.size)
        assertEquals("thought_1", assistantTurn.blocks[0].id)
        assertEquals("agent_1", assistantTurn.blocks[1].id)
    }

    @Test
    fun testGrouping_interleavedToolExecution_containedInsideAssistantTurn() {
        val promptBlock = ConversationBlock.Text(id = "user_2", content = "帮我查一下手表电量")
        val executingToolBlock = ConversationBlock.ToolCall(
            id = "tool_1",
            toolName = "get_device_status",
            argsJson = "{}",
            isExecuting = true
        )

        val blocks = listOf(promptBlock, executingToolBlock)
        val groups = ConversationGrouping.groupBlocks(blocks, activeUserPrompts = setOf(promptBlock.id))

        assertEquals(2, groups.size)
        assertTrue(groups[0] is RenderableConversationItem.UserPrompt)

        // 工具调用必须完整包含在 AssistantTurn 内部，不能孤立分散在外
        assertTrue(groups[1] is RenderableConversationItem.AssistantTurn)
        val assistantTurn = groups[1] as RenderableConversationItem.AssistantTurn
        assertEquals(1, assistantTurn.blocks.size)
        assertEquals("tool_1", assistantTurn.blocks[0].id)
    }

    @Test
    fun testGrouping_multipleConsecutiveToolsAndStreamingText_singleSquareBox() {
        val promptBlock = ConversationBlock.Text(id = "user_3", content = "设置省电模式并检索天气")
        val tool1 = ConversationBlock.ToolCall(id = "t1", toolName = "set_system_power_mode", argsJson = "{\"ultraSaving\":true}", resultSummary = "极致省电已开启")
        val tool2 = ConversationBlock.ToolCall(id = "t2", toolName = "query_time_weather", argsJson = "{}", resultSummary = "晴朗 20℃")
        val streamText = ConversationBlock.Text(id = "reply_1", content = "已为您开启省电模式，当前气温20度。", isStreaming = true)

        val blocks = listOf(promptBlock, tool1, tool2, streamText)
        val groups = ConversationGrouping.groupBlocks(blocks, activeUserPrompts = setOf(promptBlock.id))

        assertEquals(2, groups.size)
        assertTrue(groups[0] is RenderableConversationItem.UserPrompt)

        val turn = groups[1] as RenderableConversationItem.AssistantTurn
        assertEquals(3, turn.blocks.size)
        assertTrue(turn.blocks[0] is ConversationBlock.ToolCall)
        assertTrue(turn.blocks[1] is ConversationBlock.ToolCall)
        assertTrue(turn.blocks[2] is ConversationBlock.Text)
    }

    @Test
    fun testToolVisualMeta_providesDistinctColorsAndNames() {
        val searchMeta = ToolVisualMeta.resolve("web_search")
        assertEquals("联网搜索", searchMeta.displayName)
        assertTrue(searchMeta.tagColorHex != 0L)

        val weatherMeta = ToolVisualMeta.resolve("query_time_weather")
        assertEquals("时间天气", weatherMeta.displayName)

        val batteryMeta = ToolVisualMeta.resolve("get_device_status")
        assertEquals("设备状态", batteryMeta.displayName)

        val vibrateMeta = ToolVisualMeta.resolve("vibrate_device")
        assertEquals("马达振动", vibrateMeta.displayName)

        val powerMeta = ToolVisualMeta.resolve("set_system_power_mode")
        assertEquals("电源模式", powerMeta.displayName)

        val densityMeta = ToolVisualMeta.resolve("adjust_matrix_density")
        assertEquals("点阵网格", densityMeta.displayName)
    }
}
