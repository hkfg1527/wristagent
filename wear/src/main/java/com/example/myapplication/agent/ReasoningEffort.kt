package com.example.myapplication.agent

/** 推理努力：低/中/高，直接映射到请求体 reasoning_effort。 */
enum class ReasoningEffort(val apiValue: String) {
    LOW("low"),
    MEDIUM("medium"),
    HIGH("high");

    companion object {
        /** 与其它偏好 getter 一致：读不到或读到脏值一律回落默认档。 */
        const val DEFAULT_STORAGE = "MEDIUM"

        fun fromStored(stored: String?): ReasoningEffort =
            values().firstOrNull { it.name == stored } ?: MEDIUM
    }
}

/**
 * reasoning_effort 的发送策略：只有推理模型接受该字段，其余模型静默忽略（不发送），
 * 供应商明确拒绝该字段时由调度引擎去掉字段重试一次并提示。
 */
object ReasoningEffortPolicy {

    /** 需要发出去的值；null 表示本次请求不带该字段。 */
    fun bodyValue(modelName: String, effort: ReasoningEffort): String? =
        if (supports(modelName)) effort.apiValue else null

    /** 认识 reasoning_effort 的模型族：推理模型（deepseek-reasoner / r1 / qwq）与 OpenAI o 系列。 */
    fun supports(modelName: String): Boolean {
        val name = modelName.trim().lowercase()
        if (name.isEmpty()) return false
        if (reasoningPrefixes.any { name.startsWith(it) }) return true
        return reasoningMarkers.any { name.contains(it) }
    }

    /** 供应商因不认识 reasoning_effort 而拒绝请求（4xx 且错误体点名该字段）。 */
    fun isUnsupportedFieldError(statusCode: Int, errorBody: String): Boolean {
        if (statusCode !in 400..499) return false
        val body = errorBody.lowercase()
        if (!body.contains(REQUEST_FIELD)) return false
        return unsupportedMarkers.any { body.contains(it) }
    }

    private const val REQUEST_FIELD = "reasoning_effort"
    private val reasoningMarkers = listOf("reason", "r1", "qwq", "think")
    private val reasoningPrefixes = listOf("o1", "o3", "o4")
    private val unsupportedMarkers = listOf("unrecognized", "unsupported", "unknown", "invalid", "extra", "not support")
}
