package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 模型清单 URL 的拼接规则。
 *
 * 回归背景（0.0.0.100 审查）：旧实现一律 `$base/v1/models` —— 用户把 Base URL 填成
 * OpenAI 风格 `https://api.openai.com/v1` 时拼成 `/v1/v1/models` 得 404，
 * 模型清单恒空且被 `-1L to emptyList()` 静默吞掉，界面只表现为"实拉模型永远失败"。
 */
class ModelsUrlTest {

    @Test
    fun plainBaseGetsTheVersionSegmentOnce() {
        assertEquals("https://api.deepseek.com/v1/models", AgentDispatcher.modelsUrl("https://api.deepseek.com"))
        assertEquals("https://api.deepseek.com/v1/models", AgentDispatcher.modelsUrl("https://api.deepseek.com/"))
        assertEquals("https://api.deepseek.com/v1/models", AgentDispatcher.modelsUrl("  https://api.deepseek.com  "))
    }

    @Test
    fun baseThatAlreadyEndsWithV1IsNotDoubled() {
        assertEquals("https://api.openai.com/v1/models", AgentDispatcher.modelsUrl("https://api.openai.com/v1"))
        assertEquals("https://api.openai.com/v1/models", AgentDispatcher.modelsUrl("https://api.openai.com/v1/"))
    }

    @Test
    fun baseThatIsAlreadyTheModelsEndpointStaysPut() {
        assertEquals(
            "https://gw.example/proxy/v1/models",
            AgentDispatcher.modelsUrl("https://gw.example/proxy/v1/models")
        )
    }

    @Test
    fun blankBaseYieldsNoUrlInsteadOfABrokenOne() {
        assertEquals("", AgentDispatcher.modelsUrl(""))
        assertEquals("", AgentDispatcher.modelsUrl("   "))
        assertEquals("", AgentDispatcher.modelsUrl("/"))
    }
}
