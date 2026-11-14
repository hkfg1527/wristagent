package com.example.myapplication.agent

/**
 * 工具结果回灌给模型前的裁剪策略。
 *
 * 一次 shell/文件读取吐回几十 KB 是常态，而模型真正需要的通常只是开头那一段。
 * 裁剪放在**请求体构造**这一处（主循环与子代理共用同一份规则），而不是在各个工具里各裁一刀：
 * 工具那边裁会把截断永久写进历史，用户放大上限也找不回原文；这里裁只影响这一次请求。
 */
object ToolOutputPolicy {

    /** 上限夹到 500..50000 字符；NaN 之类不适用（Int），越界一律夹取。 */
    fun clampMaxChars(value: Int): Int =
        value.coerceIn(AgentPreferences.MIN_TOOL_RESULT_CHARS, AgentPreferences.MAX_TOOL_RESULT_CHARS)

    /**
     * 只裁工具结果（role == TOOL），其余角色原样返回 ——
     * 用户自己说的话被截断会出大事故。
     */
    fun truncate(content: String?, role: String, limit: Int): String? {
        if (content == null) return null
        if (role != AgentRole.TOOL) return content
        val clamped = clampMaxChars(limit)
        if (content.length <= clamped) return content
        val dropped = content.length - clamped
        return content.take(clamped) + "\n……[truncated " + dropped + " chars]"
    }
}
