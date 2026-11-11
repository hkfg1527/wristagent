package com.example.myapplication.agent.session

import com.example.myapplication.agent.ChatMessage
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * 落盘契约：**写成功与写失败都必须对调用方可观测**。
 *
 * 空 catch 的代价不是"缺一条日志"，而是会话静默丢失：调用方以为合并写落盘了，
 * 下一次启动却只读到更早的快照。所以这里钉死两件事 ——
 * 成功时 [SessionStore.save] 报告成功且能被 [SessionStore.load] 读回；
 * 失败时报告失败（带原因）且不留下半个文件。
 */
class SessionStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun sessions() = listOf(
        ChatSession(
            id = "s1",
            title = "写盘契约",
            createdAt = 1_700_000_000_000L,
            conversationBlocks = listOf(
                ConversationBlock.Text(id = "b1", content = "你好", isUser = true)
            ),
            history = listOf(ChatMessage(role = "user", content = "你好"))
        )
    )

    @Test
    fun `写成功时报告成功且能原样读回`() {
        val store = SessionStore(File(temp.root, "sessions.txt"))

        val result = store.save(sessions(), "s1")

        assertTrue("写成功必须报告成功，实际 $result", result.isSuccess)
        val restored = store.load()
        assertNotNull("写成功的快照必须能被读回", restored)
        val snapshot = restored!!
        assertEquals("s1", snapshot.activeSessionId)
        assertEquals(1, snapshot.sessions.size)
        assertEquals("写盘契约", snapshot.sessions[0].title)
        assertEquals("你好", snapshot.sessions[0].history[0].content)
    }

    @Test
    fun `写失败时调用方能拿到失败及其原因而不是静默丢掉`() {
        // 把父路径先占成一个普通文件：mkdirs 与 writeText 必然失败。
        // 这条构造不依赖权限模型（既不靠 chmod 也不靠 root），在各平台都成立。
        val notADirectory = File(temp.root, "blocked")
        notADirectory.writeText("占位")
        assertTrue("构造前置条件失败：父路径必须是个文件", notADirectory.isFile)

        val store = SessionStore(File(notADirectory, "sessions.txt"))

        val result = store.save(sessions(), "s1")

        assertTrue("写失败必须返回失败，实际 $result", result.isFailure)
        assertNotNull(
            "失败必须带上原因，调用方才能区分'没写成'与'没有会话'",
            result.exceptionOrNull()
        )
        assertNull("写失败不许留下半个快照", store.load())
    }

    @Test
    fun `失败之后障碍消失再写一次必须成功`() {
        // 失败不许把 store 变成"坏掉的一次性对象"：挡住父路径、失败、放开、再写 —— 必须转成功
        val notADirectory = File(temp.root, "blocked")
        notADirectory.writeText("占位")
        assertTrue("构造前置条件失败：父路径必须是个文件", notADirectory.isFile)
        val target = File(notADirectory, "sessions.txt")
        val store = SessionStore(target)
        assertTrue(store.save(sessions(), "s1").isFailure)

        assertTrue("解除障碍", notADirectory.delete())

        val retry = store.save(sessions(), "s1")
        assertTrue("障碍消失后必须能写进去，实际 $retry", retry.isSuccess)
        assertEquals("s1", store.load()?.activeSessionId)
    }
}
