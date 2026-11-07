package com.example.myapplication.agent.session

import com.example.myapplication.agent.ChatMessage
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import java.util.Base64
import java.util.UUID

/** 一次落盘快照：全部会话 + 当前会话 id。 */
data class SessionSnapshot(val sessions: List<ChatSession>, val activeSessionId: String)

/**
 * 会话的纯文本编解码。
 *
 * 用"行 + 制表符分隔 + base64 负载"而不是 JSON：base64 一劳永逸地消灭了换行、制表符、引号、
 * 表情符号带来的转义问题，且不依赖 android 的 org.json —— 于是往返可以在 JVM 单测里被钉死。
 *
 * 只存能重建对话的东西：会话元数据、文本气泡、文本轮次。
 * 工具调用与工具结果是执行细节，恢复时不重放（历史里只保留人机对话，模型照样接得上）。
 */
internal object SessionCodec {

    private const val VERSION = "v1"
    private const val SESSION = "S"
    private const val HISTORY = "H"
    private const val BLOCK = "B"
    private const val ACTIVE = "A"

    fun encode(sessions: List<ChatSession>, activeSessionId: String): String {
        val out = StringBuilder()
        out.append(VERSION).append('\n')
        sessions.forEach { session ->
            out.append(SESSION).append('\t')
                .append(session.id).append('\t')
                .append(session.createdAt).append('\t')
                .append(encodeText(session.title)).append('\n')
            session.history.forEach { message ->
                val content = message.content
                // tool 结果必须和它的 tool_calls 成对出现，只留文本会把历史拼成非法结构
                if (content != null && message.toolCalls.isNullOrEmpty() && message.role != "tool") {
                    out.append(HISTORY).append('\t')
                        .append(message.role).append('\t')
                        .append(encodeText(content)).append('\n')
                }
            }
            session.conversationBlocks.forEach { block ->
                if (block is ConversationBlock.Text) {
                    out.append(BLOCK).append('\t')
                        .append(if (block.isUser) "u" else "a").append('\t')
                        .append(encodeText(block.content)).append('\n')
                }
            }
        }
        out.append(ACTIVE).append('\t').append(activeSessionId).append('\n')
        return out.toString()
    }

    fun decode(text: String): SessionSnapshot? {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return null
        if (lines.first().trim() != VERSION) return null

        data class Draft(
            val id: String,
            val createdAt: Long,
            val title: String,
            val history: MutableList<ChatMessage> = mutableListOf(),
            val blocks: MutableList<ConversationBlock.Text> = mutableListOf()
        )

        val drafts = ArrayList<Draft>()
        var active = ""
        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                SESSION -> {
                    if (parts.size >= 4) {
                        drafts += Draft(
                            id = parts[1],
                            createdAt = parts[2].toLongOrNull() ?: 0L,
                            title = decodeText(parts[3])
                        )
                    }
                }
                HISTORY -> {
                    val current = drafts.lastOrNull()
                    if (current != null && parts.size >= 3) {
                        parts[2].let { raw ->
                            current.history += ChatMessage(role = parts[1], content = decodeText(raw))
                        }
                    }
                }
                BLOCK -> {
                    val current = drafts.lastOrNull()
                    if (current != null && parts.size >= 3) {
                        current.blocks += ConversationBlock.Text(
                            id = UUID.randomUUID().toString(),
                            content = decodeText(parts[2]),
                            isUser = parts[1] == "u"
                        )
                    }
                }
                ACTIVE -> if (parts.size >= 2) active = parts[1]
            }
        }

        val sessions = drafts.map { draft ->
            ChatSession(
                id = draft.id,
                title = draft.title,
                createdAt = draft.createdAt,
                conversationBlocks = draft.blocks.toList(),
                history = draft.history.toList()
            )
        }
        if (sessions.isEmpty()) return null
        val resolved = sessions.firstOrNull { it.id == active }?.id ?: sessions.first().id
        return SessionSnapshot(sessions, resolved)
    }

    private fun encodeText(raw: String): String =
        Base64.getEncoder().encodeToString(raw.toByteArray(Charsets.UTF_8))

    private fun decodeText(encoded: String): String = try {
        String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)
    } catch (t: Throwable) {
        ""
    }
}
