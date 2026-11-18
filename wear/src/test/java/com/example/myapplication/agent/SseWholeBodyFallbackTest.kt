package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * SSE 兜底：网关忽略 `Accept: text/event-stream`、直接回 200 + 一整段 JSON 时的解析。
 *
 * 回归背景（0.0.0.100 审查）：流式分支只认 `data:` 行，整段 JSON 时每一行都不以它开头，
 * 累加器一个字都收不到 —— 界面表现是"模型回了空消息"，而且**没有任何错误提示**，比报错更难查。
 *
 * 必须跑 Robolectric：这里用的是真 `org.json`（纯 JVM 下它是桩，`JSONObject` 直接返回 null）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SseWholeBodyFallbackTest {

    @Test
    fun aPlainChatCompletionBodyStillYieldsContent() {
        val raw = """
            {"id":"chatcmpl-1","choices":[{"index":0,"message":{"role":"assistant","content":"整段回复"}}]}
        """.trimIndent()
        val events = parseWholeBodyEvents(raw)
        val content = events.filterIsInstance<StreamDelta.Content>().joinToString("") { it.text }
        assertEquals("整段 JSON 也必须能读出正文", "整段回复", content)
    }

    @Test
    fun reasoningAndToolCallsSurviveTheFallbackToo() {
        val raw = """
            {"choices":[{"message":{"role":"assistant","content":"","tool_calls":[
              {"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"x\"}"}}
            ]}}]}
        """.trimIndent()
        val events = parseWholeBodyEvents(raw)
        assertTrue("必须解析出工具调用", events.any { it is StreamDelta.ToolCall })
    }

    @Test
    fun garbageStaysEmptyInsteadOfThrowing() {
        assertEquals(emptyList<StreamDelta>(), parseWholeBodyEvents(""))
        assertEquals(emptyList<StreamDelta>(), parseWholeBodyEvents("data: {\"choices\":[]}"))
        assertEquals(emptyList<StreamDelta>(), parseWholeBodyEvents("不是 JSON"))
        assertEquals(emptyList<StreamDelta>(), parseWholeBodyEvents("{\"choices\":[]}"))
    }
}
