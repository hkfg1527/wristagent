package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 核采样 top_p 的取值语义与真实接线。
 *
 * 消费者是主请求体与子代理请求体两个构造器（AgentDispatcher.buildMainRequestBody /
 * buildSubAgentRequestBody）。这里既钉夹取规则，也钉"两个构造器都真的把值写进 payload"，
 * 否则设置项会变成调了没反应的假开关。
 */
class SamplingTopPTest {

    @Test
    fun theFactoryValueIsOneAndOneIsAllowed() {
        assertEquals(1f, SamplingPolicy.clampTopP(1f), 0.0001f)
        assertEquals(0.9f, SamplingPolicy.clampTopP(0.9f), 0.0001f)
        assertEquals(AgentPreferences.DEFAULT_SAMPLING_TOP_P, SamplingPolicy.clampTopP(1f), 0.0001f)
    }

    @Test
    fun zeroAndNegativeAndOverOneAreClamped() {
        assertEquals(AgentPreferences.MIN_SAMPLING_TOP_P, SamplingPolicy.clampTopP(0f), 0.0001f)
        assertEquals(AgentPreferences.MIN_SAMPLING_TOP_P, SamplingPolicy.clampTopP(-2f), 0.0001f)
        assertEquals(AgentPreferences.MAX_SAMPLING_TOP_P, SamplingPolicy.clampTopP(1.7f), 0.0001f)
    }

    @Test
    fun nanFallsBackToTheFactoryValue() {
        assertEquals(AgentPreferences.DEFAULT_SAMPLING_TOP_P, SamplingPolicy.clampTopP(Float.NaN), 0.0001f)
    }

    @Test
    fun bothRequestBuildersSendTopP() {
        val dispatcher = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
        val occurrences = Regex(Regex.escape("put(\"top_p\", preferences.samplingTopP)")).findAll(dispatcher).count()
        assertTrue("主请求体与子代理请求体都必须带 top_p，实际 " + occurrences + " 处", occurrences >= 2)
        assertTrue(
            "top_p 必须取偏好里的值，不能是字面量",
            dispatcher.contains("preferences.samplingTopP")
        )
    }
}
