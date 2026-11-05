package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock

/**
 * UI 展示与出向语音的只读快照：ViewModel 每次需要时现读一次偏好，
 * 因此在设置页拨动开关后立刻生效，不需要重建界面。
 */
data class AgentDisplayPreferences(
    val compactMode: Boolean = false,
    val autoSpeakReplies: Boolean = false,
    val asrLiveTranscript: Boolean = false
)

/**
 * 语音中间结果的落屏闸门：asrLiveTranscript 打开时用中间结果覆盖草稿（边说边显示），
 * 关闭时草稿一动不动，只等最终结果走 onVoiceResult 提交。
 */
object VoiceDraftGate {
    fun merge(current: String, partial: String, live: Boolean): String {
        if (!live) return current
        val trimmed = partial.trim()
        return if (trimmed.isEmpty()) current else trimmed
    }
}

/**
 * 自动播报的取文闸门：只有开关打开、且最后一段助手正文非空时才朗读；
 * null 表示这一轮不该出声 —— 不建 TTS、不占音频链路。
 */
object ReplySpeech {
    fun spokenText(enabled: Boolean, blocks: List<ConversationBlock>): String? {
        if (!enabled) return null
        for (block in blocks.asReversed()) {
            if (block is ConversationBlock.Text && !block.isUser) {
                val text = block.content.trim()
                if (text.isNotEmpty()) return text
            }
        }
        return null
    }
}

/**
 * defaultThinkingEnabled 的发送闸门：关掉后整条链路不发 reasoning_effort，
 * 即使模型支持推理也不会被要求推理；开着仍只在推理模型上发。
 */
object ThinkingPolicy {
    fun reasoningEffortBody(enabled: Boolean, modelName: String, effort: ReasoningEffort): String? =
        if (!enabled) null else ReasoningEffortPolicy.bodyValue(modelName, effort)
}

/** memoryEnabled 的注入闸门：关闭时连记忆头都不拼，长期笔记自然也不会进上下文。 */
object MemoryInjection {
    fun header(enabled: Boolean, header: String): String = if (enabled) header else ""
}

/**
 * autoCompressContext 的上下文压缩：复用请求侧既有的"滚动修剪"语义，
 * 打开时把落盘历史也压到同一上限（system 头保留 + 最近 N 轮），
 * 关闭时只留请求侧截断，历史原样保留。
 *
 * 修剪边界上若切在工具回执中间，丢掉孤立的 tool 消息即可 ——
 * 缺回执的 tool_calls 由既有的 sanitizeConversationHistory 自愈补齐。
 */
object ContextCompactor {
    fun trim(history: List<ChatMessage>, maxTurns: Int, enabled: Boolean): List<ChatMessage> {
        if (!enabled) return history
        val limit = maxTurns.coerceAtLeast(1) * 2
        val head = history.takeWhile { it.role == AgentRole.SYSTEM }
        val dialog = history
            .filterIndexed { index, message -> index >= head.size && message.role != AgentRole.SYSTEM }
        if (dialog.size <= limit) return history
        val kept = dialog.takeLast(limit).dropWhile { it.role == AgentRole.TOOL }
        return head + kept
    }
}

/**
 * providerManagedSearchEnabled 的工具清单闸门：
 * 本地检索（web_search）与 Provider 托管检索各自独立，关掉的那个不出现在清单里。
 */
object SearchToolGate {
    const val TOOL_LOCAL_SEARCH = "web_search"
    const val TOOL_PROVIDER_SEARCH = "provider_web_search"

    fun exposedSearches(localEnabled: Boolean, providerManagedEnabled: Boolean): List<String> {
        val names = mutableListOf<String>()
        if (localEnabled) names.add(TOOL_LOCAL_SEARCH)
        if (providerManagedEnabled) names.add(TOOL_PROVIDER_SEARCH)
        return names
    }
}
