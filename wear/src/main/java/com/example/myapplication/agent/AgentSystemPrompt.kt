package com.example.myapplication.agent

import com.example.myapplication.R

/**
 * 执行型 agent 系统提示词的装配器。
 *
 * 四段硬约束（身份 / 能力 / 纪律 / 边界）放在资源里（values 与 values-zh 各一份），
 * 装配顺序与去空规则固定在这里，因此单测既能钉住顺序，也能钉住资源文案本身。
 */
object AgentSystemPrompt {

    /** 四段约束的资源 id，顺序即装配顺序：身份 → 能力 → 纪律 → 边界。 */
    val SECTION_RES_IDS: List<Int> = listOf(
        R.string.agent_prompt_identity,
        R.string.agent_prompt_capabilities,
        R.string.agent_prompt_discipline,
        R.string.agent_prompt_boundaries
    )

    /**
     * 长记忆头与实时遥测固定排在前面（用自定义提示词时也不动这套上下文），
     * [customPrompt] 非空时整体替换资源里的四段，否则按资源顺序拼接。
     */
    fun compose(
        sections: List<String>,
        memoryHeader: String,
        telemetryBlock: String,
        customPrompt: String
    ): String {
        val custom = customPrompt.trim()
        val body = if (custom.isNotEmpty()) {
            custom
        } else {
            sections.map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
        }
        return listOf(memoryHeader.trim(), telemetryBlock.trim(), body)
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }
}

/**
 * 系统提示词的重建键：记忆总开关 + 长期笔记全文。
 *
 * 这两项是会话内**可变**的（remember_fact 随时能写、设置页随时能关），
 * 其余输入（四段资源、遥测）在会话内不再变化，因此键只由它们两个组成。
 */
internal fun memoryPromptRevision(enabled: Boolean, notes: String): String =
    (if (enabled) "memory=on" else "memory=off") + "\n" + notes

/**
 * 会话系统提示词的重建闸门。
 *
 * 记忆变了就必须重装系统头，否则同一会话里新记住的偏好要重开会话才生效（就是"拨了没反应"）；
 * 记忆没变则复用上一份字符串 —— 每轮无条件重装会白读四段资源再拼一遍字符串。
 * 装配本身交给 [assemble]（生产上是 [AgentSystemPrompt.compose]），本类只管"要不要重装"。
 */
class SessionSystemPrompt(
    private val revision: () -> String,
    private val assemble: () -> String
) {
    private var builtForRevision: String? = null
    private var prompt: String? = null

    /**
     * 把最新提示词同步进会话历史：没有 system 头就插到最前，有就整条替换（位置不变）。
     *
     * @param force true 表示无视修订键强制重装（initSystemMessage / resetConversation 这类
     *              语义上是"新会话"的时机，资源段或自定义提示词可能已经变了）。
     * @return true = 这次真的重装了；false = 修订键没变，复用了旧字符串。
     */
    fun sync(history: MutableList<ChatMessage>, force: Boolean = false): Boolean {
        val currentRevision = revision()
        if (!force && currentRevision == builtForRevision && prompt != null) return false
        val fresh = assemble()
        prompt = fresh
        builtForRevision = currentRevision
        val headIndex = history.indexOfFirst { it.role == AgentRole.SYSTEM }
        val message = ChatMessage(role = AgentRole.SYSTEM, content = fresh)
        if (headIndex >= 0) history[headIndex] = message else history.add(0, message)
        return true
    }

    /** 最近一次装配结果；从未装配过时为空串。 */
    fun promptOrEmpty(): String = prompt ?: ""
}
