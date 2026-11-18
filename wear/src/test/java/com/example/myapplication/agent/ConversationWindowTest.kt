package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 对话窗口整形的纯逻辑测试。
 *
 * 这两条规则（补缺失回执 / 丢孤儿回执）以前一处只补不删、另一处完全不处理，
 * 于是"按条数裁剪正好切在工具调用中间"时，第一条就是没人认领的 `tool` 消息 ——
 * 上游整单 400，用户看到的是整段对话卡在"网络通信异常"。
 */
class ConversationWindowTest {

    private fun user(text: String) = ChatMessage(role = AgentRole.USER, content = text)

    private fun assistantCalls(vararg ids: String) = ChatMessage(
        role = AgentRole.ASSISTANT,
        content = null,
        toolCalls = ids.map { ToolCall(id = it, name = "run_shell", arguments = "{}") }
    )

    private fun toolReceipt(id: String) = ChatMessage(
        role = AgentRole.TOOL,
        content = "{\"status\":\"success\"}",
        toolCallId = id,
        name = "run_shell"
    )

    @Test
    fun orphanToolReceiptsAreDropped() {
        val history = listOf(
            user("帮我查一下"),
            toolReceipt("call_orphan"),          // 发起方在上一轮被裁掉了
            assistantCalls("call_a"),
            toolReceipt("call_a")
        )
        val sanitized = ConversationWindow.sanitizeForRequest(history)
        assertEquals("孤儿回执必须被丢掉", 3, sanitized.size)
        assertTrue("留下的必须是 user / assistant / tool 各一条", sanitized.none { it.toolCallId == "call_orphan" })
        assertEquals(AgentRole.USER, sanitized[0].role)
        assertEquals(AgentRole.ASSISTANT, sanitized[1].role)
        assertEquals("call_a", sanitized[2].toolCallId)
    }

    @Test
    fun missingReceiptsAreStillFilledIn() {
        // 反向：发起方在、回执缺 —— 这条老规则不能因为"丢孤儿"被弄坏
        val history = listOf(user("跑两个命令"), assistantCalls("call_x", "call_y"), toolReceipt("call_x"))
        val sanitized = ConversationWindow.sanitizeForRequest(history)
        assertEquals("缺的那条回执必须补齐", 4, sanitized.size)
        val ids = sanitized.filter { it.role == AgentRole.TOOL }.map { it.toolCallId }
        assertEquals(listOf("call_x", "call_y"), ids)
        assertTrue(
            "补齐的回执要写清是中断自愈",
            sanitized.last { it.role == AgentRole.TOOL }.content!!.contains("回执自愈补齐")
        )
    }

    @Test
    fun trimmingNeverStartsWithAToolReceipt() {
        val history = listOf(
            user("u1"),
            assistantCalls("call_a"),
            toolReceipt("call_a"),
            user("u2"),
            assistantCalls("call_b"),
            toolReceipt("call_b")
        )
        // limit = 4 会切在 "tool_a / u2 / assistant_b / tool_b" —— 首条是孤儿，
        // 必须一路往前挪到 non-TOOL 为止（挪到 u2），于是只剩 3 条
        val trimmed = ConversationWindow.trimRecentTurns(history, 4)
        assertEquals("必须一直往前挪到非 TOOL 为止", 3, trimmed.size)
        assertEquals(AgentRole.USER, trimmed.first().role)
        assertEquals("u2", trimmed.first().content)
    }

    @Test
    fun trimmingKeepsEverythingWhenItAlreadyFits() {
        val history = listOf(user("u1"), assistantCalls("call_a"), toolReceipt("call_a"))
        assertEquals("放得下就原样返回", 3, ConversationWindow.trimRecentTurns(history, 10).size)
        assertEquals("limit=0 时不能返回负区间崩溃", 0, ConversationWindow.trimRecentTurns(history, 0).size)
        val orphanLed = listOf(toolReceipt("call_gone"), user("u1"))
        assertEquals(
            "整体放得下时也要丢掉开头的孤儿",
            listOf(AgentRole.USER),
            ConversationWindow.trimRecentTurns(orphanLed, 10).map { it.role }
        )
    }
}
