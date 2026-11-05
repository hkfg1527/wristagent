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
