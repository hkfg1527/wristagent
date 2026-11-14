package com.example.myapplication.agent

import com.example.myapplication.agent.mcp.McpClient
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 工具与 MCP 页两个新设置：分页上限（mcp_max_tool_pages）与子代理读超时（subagent_read_timeout_ms）。
 *
 * 这两个都属于"远端慢的时候别把主流程拖死"，所以既要钉夹取范围，也要钉真正读它们的那一行代码。
 */
class McpAndSubAgentLimitsTest {

    @Test
    fun theMcpPageLimitIsClampedToOneThroughTen() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals("出厂值就是常量本身", McpClient.MAX_TOOL_PAGES, p.mcpMaxToolPages)
        p.mcpMaxToolPages = 0
        assertEquals(McpClient.MIN_TOOL_PAGES, p.mcpMaxToolPages)
        p.mcpMaxToolPages = 99
        assertEquals(McpClient.MAX_TOOL_PAGE_LIMIT, p.mcpMaxToolPages)
        p.mcpMaxToolPages = 7
        assertEquals(7, p.mcpMaxToolPages)
    }

    @Test
    fun theSubAgentReadTimeoutIsClampedToFifteenToThreeMinutes() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals(60_000L, p.subagentReadTimeoutMs)
        p.subagentReadTimeoutMs = 1_000L
        assertEquals(AgentPreferences.MIN_SUBAGENT_READ_TIMEOUT_MS, p.subagentReadTimeoutMs)
        p.subagentReadTimeoutMs = 999_999L
        assertEquals(AgentPreferences.MAX_SUBAGENT_READ_TIMEOUT_MS, p.subagentReadTimeoutMs)
    }

    @Test
    fun theClientLoopUsesTheConfiguredLimitNotTheConstant() {
        val client = readProjectFile("src/main/java/com/example/myapplication/agent/mcp/McpClient.kt")
        assertTrue("分页循环必须用构造参数", client.contains("page < toolPageLimit"))
        assertTrue(
            "构造时就夹取，避免界外值进循环",
            client.contains("coerceIn(MIN_TOOL_PAGES, MAX_TOOL_PAGE_LIMIT)")
        )
    }

    @Test
    fun theBindingAndRegistryCarryThePreferenceThrough() {
        val binding = readProjectFile("src/main/java/com/example/myapplication/agent/mcp/McpToolBinding.kt")
        assertTrue("绑定层必须把上限传给客户端", binding.contains("maxToolPages = maxToolPages"))
        val registry = readProjectFile("src/main/java/com/example/myapplication/agent/AgentToolRegistry.kt")
        assertTrue("注册处必须读偏好", registry.contains("maxToolPages = preferences?.mcpMaxToolPages"))
    }

    @Test
    fun theSubAgentCallUsesThePreferenceForItsReadTimeout() {
        val dispatcher = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
        assertTrue(
            "子代理读超时必须来自偏好，而不是写死的常量",
            dispatcher.contains("readTimeout = preferences.subagentReadTimeoutMs.toInt()")
        )
    }
}
