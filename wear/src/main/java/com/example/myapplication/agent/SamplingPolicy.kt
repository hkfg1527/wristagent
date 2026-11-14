package com.example.myapplication.agent

/**
 * 采样参数的夹取与兜底。
 *
 * 抽成纯对象是为了能在 JVM 单测里验证：偏好的 getter/setter 夹取与这里必须是同一份规则，
 * 否则"设了 0 但引擎收到 0.05"这种事会在控制台写入时悄悄发生。
 */
object SamplingPolicy {

    /** top_p 夹到 0.05..1.0；NaN（控制台写坏的值）退回出厂值。 */
    fun clampTopP(value: Float): Float =
        if (value.isNaN()) {
            AgentPreferences.DEFAULT_SAMPLING_TOP_P
        } else {
            value.coerceIn(AgentPreferences.MIN_SAMPLING_TOP_P, AgentPreferences.MAX_SAMPLING_TOP_P)
        }
}
