package com.example.myapplication.agent.session

import com.example.myapplication.agent.ChatMessage
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCodecTest {

    private fun session(
        id: String,
        title: String,
        blocks: List<ConversationBlock.Text>,
        history: List<ChatMessage>
    ) = ChatSession(id = id, title = title, createdAt = 1_700_000_000_000L, conversationBlocks = blocks, history = history)

    private fun text(content: String, isUser: Boolean) =
        ConversationBlock.Text(id = "x", content = content, isUser = isUser)

    @Test
    fun roundTripKeepsSessionsTitlesAndActiveId() {
        val a = session("s1", "手表排障", listOf(text("你好", true), text("在", false)),
            listOf(ChatMessage("user", "你好"), ChatMessage("assistant", "在")))
        val b = session("s2", "新对话", listOf(text("第二段", true)), emptyList())

        val restored = SessionCodec.decode(SessionCodec.encode(listOf(a, b), "s2"))!!

        assertEquals(2, restored.sessions.size)
        assertEquals("s2", restored.activeSessionId)
        assertEquals("手表排障", restored.sessions[0].title)
        assertEquals(2, restored.sessions[0].history.size)
        assertEquals("在", restored.sessions[0].history[1].content)
    }

    @Test
    fun contentWithTabsAndNewlinesSurvives() {
        val tricky = "第一行\n第二行\t带制表符 \"引号\" 🙂"
        val a = session("s1", tricky, listOf(text(tricky, true)), listOf(ChatMessage("user", tricky)))

        val restored = SessionCodec.decode(SessionCodec.encode(listOf(a), "s1"))!!

        assertEquals(tricky, restored.sessions[0].title)
        assertEquals(tricky, restored.sessions[0].history[0].content)
    }

    @Test
    fun toolPlumbingIsNotPersisted() {
        val a = session("s1", "t", listOf(text("结果", false)),
            listOf(ChatMessage("user", "查电量"), ChatMessage("tool", "76%"), ChatMessage("assistant", "结果是 76%")))

        val restored = SessionCodec.decode(SessionCodec.encode(listOf(a), "s1"))!!

        assertEquals(
            "只保留人机文本轮次",
            listOf("user", "assistant"),
            restored.sessions[0].history.map { it.role }
        )
        assertEquals(2, restored.sessions[0].history.size)
    }

    @Test
    fun unknownActiveIdFallsBackToFirstSession() {
        val a = session("s1", "t", emptyList(), emptyList())

        val restored = SessionCodec.decode(SessionCodec.encode(listOf(a), "missing"))!!

        assertEquals("s1", restored.activeSessionId)
    }

    @Test
    fun garbageInputYieldsNullInsteadOfThrowing() {
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("not-a-snapshot\nS\tbroken"))
    }

    @Test
    fun savingTwiceIsIdempotent() {
        val a = session("s1", "t", listOf(text("一段", true)), listOf(ChatMessage("user", "一段")))
        val once = SessionCodec.encode(listOf(a), "s1")
        val twice = SessionCodec.encode(SessionCodec.decode(once)!!.sessions, "s1")

        assertTrue("往返后编码应稳定", once == twice)
    }
}
