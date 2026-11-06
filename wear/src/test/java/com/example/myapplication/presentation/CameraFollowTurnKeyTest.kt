package com.example.myapplication.presentation

import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.RenderableConversationItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 镜头接管的准入契约。
 *
 * 真机现象："刚出来有工具调用它就不跟随"、"思考文本的时候也不跟随"。
 * 旧准入只认"已有正文"，于是先出思考/先调工具的回合整段没有镜头。
 */
class CameraFollowTurnKeyTest {

    private val userPrompt = RenderableConversationItem.UserPrompt(
        ConversationBlock.Text(id = "u1", content = "帮我看下这个报错", isUser = true)
    )

    private fun turn(vararg blocks: ConversationBlock) =
        RenderableConversationItem.AssistantTurn(key = "turn_1", blocks = blocks.toList())

    @Test
    fun `思考胶囊一出现就开始跟随`() {
        assertEquals(
            "turn_1",
            CameraCurve.readingTurnKey(
                listOf(userPrompt, turn(ConversationBlock.Thought(id = "t1", reasoning = "先定位空指针")))
            )
        )
    }

    @Test
    fun `工具胶囊一出现就开始跟随`() {
        assertEquals(
            "turn_1",
            CameraCurve.readingTurnKey(
                listOf(
                    userPrompt,
                    turn(
                        ConversationBlock.ToolCall(
                            id = "c1",
                            toolName = "web_search",
                            argsJson = "{}",
                            isExecuting = true
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `空思考与空正文都还不能接管`() {
        assertNull(
            CameraCurve.readingTurnKey(
                listOf(userPrompt, turn(ConversationBlock.Thought(id = "t1", reasoning = "   ")))
            )
        )
        assertNull(
            CameraCurve.readingTurnKey(
                listOf(userPrompt, turn(ConversationBlock.Text(id = "a1", content = "  ")))
            )
        )
    }

    @Test
    fun `欢迎语与无提问回合都不接管`() {
        assertNull(
            CameraCurve.readingTurnKey(
                listOf(
                    turn(ConversationBlock.Text(id = "init_0", content = "主人，今天有什么需要解决的技术或生活问题？"))
                )
            )
        )
        assertNull(CameraCurve.readingTurnKey(listOf(userPrompt)))
        assertNull(CameraCurve.readingTurnKey(emptyList()))
    }
}
