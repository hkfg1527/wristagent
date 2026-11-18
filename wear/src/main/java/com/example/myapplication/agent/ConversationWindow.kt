package com.example.myapplication.agent

/**
 * 送请求前的对话窗口整形：**补缺失回执、丢孤儿回执、按条数取最近 N 条**。
 *
 * 为什么单独抽出来：这两件事都是纯逻辑（输入 List<ChatMessage> → 输出 List<ChatMessage>），
 * 但它们的错误后果极重 —— 上游要求每条 `tool` 消息都能找到发起它的 `assistant(tool_calls)`，
 * 只要有一条找不到，整单 400（"tool must be a response to a preceding tool_calls"），
 * 用户看到的是整段对话长期卡在"网络通信异常"，而日志里只有一句不相关的错误。
 *
 * 孤儿回执在两种情况下必然出现：
 * 1. 上一次请求被取消，回执只写了一半；
 * 2. 上下文窗口按条数裁剪时正好切在"assistant 发起 → tool 返回"中间。
 *
 * 原来这两件事一处写在 `AgentDispatcher.sanitizeConversationHistory`（只补不删）、
 * 另一处是请求侧裸 `takeLast(turns * 2)`（完全不处理），于是第 2 种情况一直没有兜住。
 */
internal object ConversationWindow {

    /**
     * 补齐回执的固定内容。
     *
     * 刻意用常量而不是 `JSONObject().apply { ... }.toString()`：纯 JVM 单测里 `org.json`
     * 是 Android jar 的桩（`isReturnDefaultValues = true`），`JSONObject()` 返回的对象
     * `toString()` 为 null，取它当回执内容会直接 NPE —— 而这恰恰是最需要被测到的一条路径。
     */
    private const val FALLBACK_RECEIPT = "{\"status\":\"cancelled\",\"message\":\"任务中断，回执自愈补齐\"}"

    /** 补上发起方缺失的回执，并丢掉没人认领的孤儿回执。 */
    fun sanitizeForRequest(history: List<ChatMessage>): List<ChatMessage> {
        val sanitized = mutableListOf<ChatMessage>()
        var i = 0
        while (i < history.size) {
            val msg = history[i]
            if (msg.role == AgentRole.TOOL) {
                // 走到这里说明没有任何 assistant 认领它：丢掉，不要送进请求体
                i++
                continue
            }
            sanitized.add(msg)
            if (msg.role == AgentRole.ASSISTANT && !msg.toolCalls.isNullOrEmpty()) {
                val requiredToolCallIds = msg.toolCalls.map { it.id to it.name }.toMutableList()
                var j = i + 1
                while (j < history.size && history[j].role == AgentRole.TOOL) {
                    val toolMsg = history[j]
                    sanitized.add(toolMsg)
                    requiredToolCallIds.removeAll { it.first == toolMsg.toolCallId }
                    j++
                }
                for ((missingId, missingName) in requiredToolCallIds) {
                    sanitized.add(
                        ChatMessage(
                            role = AgentRole.TOOL,
                            content = FALLBACK_RECEIPT,
                            toolCallId = missingId,
                            name = missingName
                        )
                    )
                }
                i = j
            } else {
                i++
            }
        }
        return sanitized
    }

    /**
     * 取最近 [limit] 条，并保证**不以 TOOL 开头**。
     *
     * 按条数裁剪最容易切在"assistant 发起 → tool 返回"中间，切完的第一条就是孤儿回执。
     * 丢掉开头连续的 TOOL 是安全的：如果发起方还在，它一定排在它们前面。
     */
    fun trimRecentTurns(messages: List<ChatMessage>, limit: Int): List<ChatMessage> {
        if (limit <= 0) return emptyList()
        if (messages.size <= limit) return messages.dropWhile { it.role == AgentRole.TOOL }
        var start = messages.size - limit
        while (start < messages.size && messages[start].role == AgentRole.TOOL) start++
        return messages.subList(start, messages.size).toList()
    }
}
