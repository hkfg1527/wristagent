package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子代理请求体必须与主循环带同一个 reasoning_effort。
 *
 * ⚠ 断言为什么不落在请求体的 JSONObject 上：wear/build.gradle.kts 打开
 * `unitTests.isReturnDefaultValues = true`，而测试依赖只有 junit 与 coroutines-test，
 * 因此 org.json（android.jar）在本模块的 JVM 单测里是 AGP 的 mockable 桩：put 返回 null、
 * get/opt 返回默认值。读请求体字段要么 NPE，要么得到永远相同的空值 —— 那是空断言。
 *
 * 所以选"取值层 + 接线层"两段验证：
 * - 取值层（真代码直跑）：[requestReasoningEffort] 是三档取值 + 总闸 + 模型能力的唯一判定点，
 *   逐档断言它给出的值；两个请求体都只从这里取值，因此"两处相同"由这一点保证；
 * - 接线层（源码守卫）：主循环请求体、子代理请求体确实把这个值写进 reasoning_effort，
 *   且子代理请求确实走 buildSubAgentRequestBody —— 这一段是 JSON 装配，JVM 里不可断言。
 */
class RequestReasoningEffortParityTest {

    private fun preferences(
        model: String,
        thinking: Boolean = true,
        effort: ReasoningEffort = ReasoningEffort.HIGH
    ): AgentPreferences = AgentPreferences(FakeSharedPreferences()).apply {
        modelName = model
        defaultThinkingEnabled = thinking
        reasoningEffort = effort.name
    }

    // ==================== 取值层：两处请求体共用的唯一判定点 ====================

    @Test
    fun reasoningModelCarriesTheConfiguredEffort() {
        for (effort in ReasoningEffort.values()) {
            assertEquals(
                "推理模型必须按主人选的档位发：" + effort.name,
                effort.apiValue,
                requestReasoningEffort(preferences("deepseek-reasoner", effort = effort))
            )
        }
        assertEquals("r1 系列同样认这个字段", "low", requestReasoningEffort(preferences("deepseek-r1-distill", effort = ReasoningEffort.LOW)))
        assertEquals("o 系列同样认这个字段", "medium", requestReasoningEffort(preferences("o3-mini", effort = ReasoningEffort.MEDIUM)))
    }

    @Test
    fun plainChatModelGetsNoEffort() {
        assertNull("普通对话模型不认识这个字段", requestReasoningEffort(preferences("deepseek-chat")))
        assertNull(requestReasoningEffort(preferences("gpt-4o-mini")))
    }

    @Test
    fun thinkingSwitchOffStopsTheEffortEvenOnAReasoningModel() {
        // defaultThinkingEnabled 是总闸：关掉后两处都不发，与模型能力无关
        assertNull(requestReasoningEffort(preferences("deepseek-reasoner", thinking = false)))
    }

    // ==================== 接线层：两个请求体都写这个字段 ====================

    @Test
    fun bothRequestBodiesEmitTheEffortFromTheSameSingleSource() {
        val source = dispatcherSource()

        // 主循环：值来自 requestReasoningEffort，逐轮现读，请求体字段由 reasoningEffort 参数写入
        assertTrue(
            "主循环的推理档必须从唯一取值点取",
            source.contains("var reasoningEffortValue: String? = requestReasoningEffort(preferences)")
        )
        assertTrue(
            "主循环请求体必须把该值写进 reasoning_effort",
            functionBody(source, "internal fun buildMainRequestBody").contains("put(\"reasoning_effort\", it)")
        )
        assertTrue(
            "主循环请求体的推理档必须由调用方传入（供应商拒收时置空重建）",
            source.contains("reasoningEffort = reasoningEffortValue")
        )

        // 子代理：请求体函数自己取同一个值 —— 这里就是原来漏掉字段的地方
        val subAgentBody = functionBody(source, "internal fun buildSubAgentRequestBody")
        assertTrue(
            "子代理请求体必须写 reasoning_effort，且取自同一个 requestReasoningEffort",
            subAgentBody.contains("requestReasoningEffort(preferences)?.let { put(\"reasoning_effort\", it) }")
        )
        assertTrue(
            "子代理请求确实走这个装配函数（不是另写一份请求体）",
            source.contains("val body = buildSubAgentRequestBody(preferences, history, tools)")
        )
    }

    private fun functionBody(source: String, marker: String): String =
        source.substringAfter(marker).substringBefore("\n}", missingDelimiterValue = "")

    /** 接线守卫读的是真实源文件（CWD=模块目录，从仓库根跑也兼容，见 readProjectFile）。 */
    private fun dispatcherSource(): String =
        readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
}
