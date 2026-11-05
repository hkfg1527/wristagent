package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ChatUiState
import com.example.myapplication.presentation.ui.MainInputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 七个新增设置项的运行时消费点：每一条都钉住"开 = 有行为 / 关 = 无行为"，
 * 全部是纯逻辑闸门，不依赖 Android、不依赖 UI。
 */
class AgentSettingConsumptionTest {

    private fun replyBlocks(text: String): List<ConversationBlock> = listOf(
        ConversationBlock.Text(id = "u1", content = "帮我看看电量", isUser = true),
        ConversationBlock.Text(id = "a1", content = text, isUser = false)
    )

    @Test
    fun autoSpeakRepliesGatesSpeaking() {
        assertEquals(
            "开关打开时读出最后一段助手正文",
            "电量 88%",
            ReplySpeech.spokenText(true, replyBlocks("电量 88%"))
        )

        assertNull("开关关闭时不该建 TTS、不该出声", ReplySpeech.spokenText(false, replyBlocks("电量 88%")))
        assertNull("正文为空不出声", ReplySpeech.spokenText(true, replyBlocks("   ")))
        assertNull(
            "只有用户消息时没有可播报的正文",
            ReplySpeech.spokenText(
                true,
                listOf(ConversationBlock.Text(id = "u1", content = "在吗", isUser = true))
            )
        )
    }

    @Test
    fun asrLiveTranscriptGatesDraftMerge() {
        assertEquals("边说边显示：中间结果覆盖草稿", "今天天气", VoiceDraftGate.merge("", "今天天气", true))
        assertEquals("关闭时草稿一动不动", "旧草稿", VoiceDraftGate.merge("旧草稿", "今天天气", false))
        assertEquals("空中间结果不覆盖", "旧草稿", VoiceDraftGate.merge("旧草稿", "   ", true))
    }

    @Test
    fun providerManagedSearchGatesToolPresence() {
        assertEquals(
            listOf(SearchToolGate.TOOL_LOCAL_SEARCH),
            SearchToolGate.exposedSearches(localEnabled = true, providerManagedEnabled = false)
        )
        assertEquals(
            "开关打开才有 Provider 托管检索工具",
            listOf(SearchToolGate.TOOL_PROVIDER_SEARCH),
            SearchToolGate.exposedSearches(localEnabled = false, providerManagedEnabled = true)
        )
        assertTrue(
            "两个开关都关时清单里没有任何检索工具",
            SearchToolGate.exposedSearches(localEnabled = false, providerManagedEnabled = false).isEmpty()
        )
        assertEquals(
            listOf(SearchToolGate.TOOL_LOCAL_SEARCH, SearchToolGate.TOOL_PROVIDER_SEARCH),
            SearchToolGate.exposedSearches(localEnabled = true, providerManagedEnabled = true)
        )
    }

    @Test
    fun defaultThinkingGatesReasoningRequest() {
        assertEquals(
            "打开且模型支持推理时才发 reasoning_effort",
            "medium",
            ThinkingPolicy.reasoningEffortBody(true, "deepseek-reasoner", ReasoningEffort.MEDIUM)
        )
        assertNull(
            "关闭后整条链路不发推理请求",
            ThinkingPolicy.reasoningEffortBody(false, "deepseek-reasoner", ReasoningEffort.MEDIUM)
        )
        assertNull(
            "非推理模型即使打开也不发",
            ThinkingPolicy.reasoningEffortBody(true, "deepseek-chat", ReasoningEffort.HIGH)
        )
    }

    @Test
    fun memoryEnabledGatesMemoryHeader() {
        val header = "【宿主平台与长记忆核心约束】\n- 持久用户偏好: 喜欢简短"

        assertEquals(header, MemoryInjection.header(true, header))
        assertEquals("关闭后记忆头整段不注入", "", MemoryInjection.header(false, header))

        val telemetry = "TELEMETRY"
        val sections = listOf("SEC-A")
        assertFalse(
            "关闭时上下文里不应出现长期记忆",
            AgentSystemPrompt.compose(
                sections = sections,
                memoryHeader = MemoryInjection.header(false, header),
                telemetryBlock = telemetry,
                customPrompt = ""
            ).contains("持久用户偏好")
        )
        assertTrue(
            "打开时长期记忆回到 system 最顶部",
            AgentSystemPrompt.compose(
                sections = sections,
                memoryHeader = MemoryInjection.header(true, header),
                telemetryBlock = telemetry,
                customPrompt = ""
            ).contains("持久用户偏好")
        )
    }

    @Test
    fun autoCompressContextGatesHistoryTrim() {
        val history = mutableListOf(ChatMessage(role = AgentRole.SYSTEM, content = "SYS"))
        repeat(6) { index ->
            history.add(ChatMessage(role = AgentRole.USER, content = "u" + index))
            history.add(ChatMessage(role = AgentRole.ASSISTANT, content = "a" + index))
        }

        assertEquals("关闭时历史原样保留", history, ContextCompactor.trim(history, maxTurns = 2, enabled = false))

        val trimmed = ContextCompactor.trim(history, maxTurns = 2, enabled = true)
        assertEquals("打开时压到 maxTurns*2 + system 头", 5, trimmed.size)
        assertEquals(AgentRole.SYSTEM, trimmed.first().role)
        assertEquals("最近一轮必须留下", "a5", trimmed.last().content)

        val shortHistory = history.take(3)
        assertEquals("没超上限不动", shortHistory, ContextCompactor.trim(shortHistory, maxTurns = 2, enabled = true))
    }

    @Test
    fun autoCompressionDropsOrphanToolRepliesAtTheCut() {
        val history = listOf(
            ChatMessage(role = AgentRole.SYSTEM, content = "SYS"),
            ChatMessage(role = AgentRole.USER, content = "u1"),
            ChatMessage(role = AgentRole.ASSISTANT, content = "a1"),
            ChatMessage(role = AgentRole.TOOL, content = "t-old", toolCallId = "c1", name = "get_device_status"),
            ChatMessage(role = AgentRole.USER, content = "u2"),
            ChatMessage(role = AgentRole.ASSISTANT, content = "a2"),
            ChatMessage(role = AgentRole.TOOL, content = "t-new", toolCallId = "c2", name = "get_device_status")
        )

        val trimmed = ContextCompactor.trim(history, maxTurns = 2, enabled = true)
        assertEquals("切点上的孤立 tool 回执必须丢掉", 4, trimmed.size)
        assertEquals(AgentRole.USER, trimmed[1].role)
    }

    @Test
    fun compactModeSurfacesThroughUiState() {
        assertTrue("设置打开时初始状态带紧凑标志", ChatUiState.initial(MainInputType.VOICE, compactMode = true).compactMode)
        assertFalse("默认不紧凑", ChatUiState.initial(MainInputType.VOICE).compactMode)
    }
}
