package com.example.myapplication.agent.mcp

import android.content.ContextWrapper
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.AgentToolRegistry
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.agent.ToolCall
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 注册表接线：远端工具必须真的出现在 AgentToolRegistry.getToolDefinitions() 里，
 * 并且 executeTool 能把调用路由到远端；开关关闭时清单干净且一次请求都不发。
 *
 * Context 只在执行内置硬件工具时才会用到，这里传 ContextWrapper(null) 是为了让
 * AgentToolRegistry 能构造出来，测试路径本身不碰任何 Context 方法。
 */
class AgentToolRegistryMcpTest {

    private lateinit var server: FakeMcpServer

    private var registry: AgentToolRegistry? = null

    @Before
    fun setUp() {
        server = FakeMcpServer()
        server.startServer()
    }

    @After
    fun tearDown() {
        registry?.mcpTools?.close()
        server.stop()
    }

    private fun registryFor(endpoint: String, enabled: Boolean): AgentToolRegistry {
        val preferences = AgentPreferences(FakeSharedPreferences())
        preferences.mcpEndpoint = endpoint
        preferences.isMcpEnabled = enabled
        val created = AgentToolRegistry(ContextWrapper(null), preferences)
        registry = created
        return created
    }

    private fun field(json: McpJson, name: String): String? = json.field(name)?.stringValue()

    @Test
    fun remoteToolsJoinTheToolCatalog() = runBlocking {
        val registry = registryFor(server.endpointUrl, enabled = true)
        registry.mcpTools.refresh()

        val names = registry.getToolDefinitions().map { it.name }
        assertTrue("内置工具必须还在：" + names, names.contains("get_device_status"))
        assertTrue("远端工具必须进清单：" + names, names.contains("mcp_echo"))
        assertTrue(names.contains("mcp_add"))
        assertTrue("远端工具名必须带 mcp_ 前缀以便区分来源", names.filter { it.startsWith("mcp_") }.size == 3)
    }

    @Test
    fun executeToolRoutesToRemoteAndTheLoopKeepsGoingAfterAFailure() = runBlocking {
        val registry = registryFor(server.endpointUrl, enabled = true)
        registry.mcpTools.refresh()

        val failed = registry.executeTool(ToolCall(id = "c1", name = "mcp_fail_now", arguments = "{}"))
        val failedJson = McpJson.parse(failed.output)
        assertEquals("error", field(failedJson, "status"))
        assertEquals("tool exploded", field(failedJson, "text"))

        val ok = registry.executeTool(ToolCall(id = "c2", name = "mcp_echo", arguments = "{\"text\":\"继续跑\"}"))
        assertEquals("mcp_echo", ok.toolName)
        val okJson = McpJson.parse(ok.output)
        assertEquals("一次失败不能打断工具循环", "success", field(okJson, "status"))
        assertEquals("echo:继续跑", field(okJson, "text"))
    }

    @Test
    fun disabledSwitchKeepsTheCatalogCleanAndSilent() = runBlocking {
        val registry = registryFor(server.endpointUrl, enabled = false)

        val names = registry.getToolDefinitions().map { it.name }
        assertTrue("开关关闭时清单里不该有远端工具：" + names, names.none { it.startsWith("mcp_") })

        val payload = McpJson.parse(registry.executeTool(ToolCall(id = "c3", name = "mcp_echo", arguments = "{}")).output)
        assertEquals("mcp_disabled", field(payload, "error_type"))
        assertEquals("开关关闭后一次请求都不许发出", 0, server.requestCount.get())
    }

    @Test
    fun refusedConnectionReturnsAtOnceInsteadOfStallingTheLoop() = runBlocking {
        val deadPort = freePort()
        val registry = registryFor("http://127.0.0.1:" + deadPort + "/mcp", enabled = true)

        val started = System.currentTimeMillis()
        val broken = registry.executeTool(ToolCall(id = "c4", name = "mcp_echo", arguments = "{}"))
        val elapsed = System.currentTimeMillis() - started
        val payload = McpJson.parse(broken.output)
        assertEquals("error", field(payload, "status"))
        assertEquals("mcp_connect", field(payload, "error_type"))
        assertTrue("连接被拒必须立刻回到循环，实际 " + elapsed + "ms", elapsed < 3000)

        val again = registry.executeTool(ToolCall(id = "c5", name = "mcp_echo", arguments = "{}"))
        assertEquals("error", field(McpJson.parse(again.output), "status"))
    }
}
