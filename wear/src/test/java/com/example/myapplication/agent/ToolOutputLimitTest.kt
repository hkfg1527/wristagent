package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具结果上限（设置项 tool_result_max_chars）的语义与真实接线。
 *
 * 裁剪发生在**请求体构造**这一处（主循环与子代理共用），因为历史要保留原文：
 * 用户把上限从 8000 调到 50000 时，上一轮的完整输出必须还能取回来。
 */
class ToolOutputLimitTest {

    @Test
    fun theLimitIsClampedToTheAllowedRange() {
        assertEquals(AgentPreferences.MIN_TOOL_RESULT_CHARS, ToolOutputPolicy.clampMaxChars(0))
        assertEquals(AgentPreferences.MIN_TOOL_RESULT_CHARS, ToolOutputPolicy.clampMaxChars(-10))
        assertEquals(AgentPreferences.MAX_TOOL_RESULT_CHARS, ToolOutputPolicy.clampMaxChars(999999))
        assertEquals(8000, ToolOutputPolicy.clampMaxChars(8000))
    }

    @Test
    fun shortContentPassesThroughUntouched() {
        assertEquals("ok", ToolOutputPolicy.truncate("ok", AgentRole.TOOL, 8000))
        assertEquals("", ToolOutputPolicy.truncate("", AgentRole.TOOL, 8000))
        assertNull(ToolOutputPolicy.truncate(null, AgentRole.TOOL, 8000))
    }

    @Test
    fun longToolOutputIsCutAndTheDropIsReported() {
        val long = "x".repeat(1200)
        val cut = ToolOutputPolicy.truncate(long, AgentRole.TOOL, 500)!!
        assertTrue("必须真的变短", cut.length < long.length)
        assertTrue("必须保留前 500 个字符", cut.startsWith("x".repeat(500)))
        assertTrue("必须告诉模型被截断了多少", cut.contains("700"))
    }

    @Test
    fun userAndAssistantMessagesAreNeverTruncated() {
        val long = "y".repeat(3000)
        assertEquals(long, ToolOutputPolicy.truncate(long, AgentRole.USER, 500))
        assertEquals(long, ToolOutputPolicy.truncate(long, AgentRole.ASSISTANT, 500))
        assertEquals(long, ToolOutputPolicy.truncate(long, AgentRole.SYSTEM, 500))
    }

    @Test
    fun bothRequestBuildersApplyThePolicy() {
        val dispatcher = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
        val occurrences = Regex(Regex.escape("forPayload(preferences.toolResultMaxChars)")).findAll(dispatcher).count()
        assertTrue("主请求体与子代理请求体都必须裁剪工具结果，实际 " + occurrences + " 处", occurrences >= 2)
    }

    @Test
    fun theSourceOfTruthIsThePreferenceNotAConstant() {
        val preferences = readProjectFile("src/main/java/com/example/myapplication/agent/AgentPreferences.kt")
        assertTrue(
            "上限必须走工具策略，不能在别处藏第二份夹取规则",
            preferences.contains("ToolOutputPolicy.clampMaxChars(")
        )
    }
}
