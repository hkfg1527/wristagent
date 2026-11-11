package com.example.myapplication.agent.mcp

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * MCP 装配点：真实 HTTP 服务器 + 真实 JSON-RPC。
 * 覆盖注册（前缀 + 重名让位）、执行、服务端 error 降级、连接被拒降级、超时预算、
 * 以及开关关闭时的"零请求"。
 */
class McpToolBindingTest {

    private lateinit var server: FakeMcpServer

    private var binding: McpToolBinding? = null

    @Before
    fun setUp() {
        server = FakeMcpServer()
        server.startServer()
    }

    @After
    fun tearDown() {
        binding?.close()
        server.stop()
    }

    private fun bindingFor(endpoint: String, enabled: Boolean = true): McpToolBinding {
        val created = McpToolBinding(
            endpointSource = { endpoint },
            enabledSource = { enabled },
            timeouts = McpTimeouts(connectMs = 1500, readMs = 2000, requestMs = 5000)
        )
        binding = created
        return created
    }

    private fun json(text: String): McpJson.Obj = McpJson.parse(text) as McpJson.Obj

    private fun field(json: McpJson.Obj, name: String): String? = json.field(name)?.stringValue()

    @Test
    fun refreshRegistersPrefixedToolsAndYieldsToReservedNames() = runBlocking {
        val binding = bindingFor(server.endpointUrl)
        val report = binding.refresh()

        assertEquals("ok", report.status)
        assertEquals("fake-mcp", report.serverName)
        assertEquals("2024-11-05", report.protocolVersion)
        assertEquals(listOf("mcp_echo", "mcp_add", "mcp_fail_now"), report.tools)

        val definitions = binding.toolDefinitions(emptySet())
        assertEquals(listOf("mcp_echo", "mcp_add", "mcp_fail_now"), definitions.map { it.name })
        assertTrue("描述里必须标出 MCP 来源：" + definitions.first().description, definitions.first().description.contains("MCP"))
        assertNotNull(definitions.first().parametersSchema)

        val collision = binding.toolDefinitions(setOf("mcp_echo"))
        assertEquals("与内置工具重名时远端工具必须让位", listOf("mcp_add", "mcp_fail_now"), collision.map { it.name })
    }

    @Test
    fun toolCallRoundTripsThroughRealServer() = runBlocking {
        val binding = bindingFor(server.endpointUrl)
        binding.refresh()

        val result = binding.toolResult("call_1", "mcp_echo", "{\"text\":\"你好\"}")
        assertEquals("call_1", result.toolCallId)
        assertEquals("mcp_echo", result.toolName)
        assertFalse("回执走 output 而不是 isError，与内置工具保持一致", result.isError)

        val payload = json(result.output)
        assertEquals("success", field(payload, "status"))
        assertEquals("mcp", field(payload, "source"))
        assertEquals("echo:你好", field(payload, "text"))
        assertEquals("127.0.0.1:" + server.port, field(payload, "server"))
        assertEquals("echo", field(payload, "tool"))
    }

    @Test
    fun serverReportedToolErrorDegradesToReadableResult() = runBlocking {
        val binding = bindingFor(server.endpointUrl)
        binding.refresh()

        val payload = json(binding.toolResult("call_2", "mcp_fail_now", "{}").output)
        assertEquals("error", field(payload, "status"))
        assertEquals("工具自报失败也要原样带回模型", "tool exploded", field(payload, "text"))
        assertEquals(true, payload.field("is_error")?.boolValue())
    }

    @Test
    fun jsonRpcErrorDegradesToStructuredFailure() = runBlocking {
        server.failCallWithRpcError = true
        val binding = bindingFor(server.endpointUrl)
        binding.refresh()

        val payload = json(binding.toolResult("call_3", "mcp_echo", "{}").output)
        assertEquals("error", field(payload, "status"))
        assertEquals("mcp_rpc_error", field(payload, "error_type"))
        assertEquals(true, payload.field("degraded")?.boolValue())
        assertTrue(
            "失败说明要带服务端原文：" + field(payload, "message"),
            field(payload, "message")!!.contains("fake server boom")
        )
    }

    @Test
    fun disabledSwitchMeansZeroRequests() = runBlocking {
        val binding = bindingFor(server.endpointUrl, enabled = false)

        assertEquals("disabled", binding.refresh().status)
        assertTrue("开关关闭时清单里不能有任何远端工具", binding.toolDefinitions(emptySet()).isEmpty())

        val payload = json(binding.toolResult("call_4", "mcp_echo", "{}").output)
        assertEquals("mcp_disabled", field(payload, "error_type"))
        assertEquals("开关关闭后一次请求都不许发出", 0, server.requestCount.get())
    }

    @Test
    fun nonHttpEndpointNeverConnects() = runBlocking {
        val binding = bindingFor("ftp://127.0.0.1:" + server.port + "/mcp")
        assertFalse(binding.isEnabled())

        val report = binding.refresh()
        assertEquals("invalid_endpoint", report.status)
        assertEquals("invalid_endpoint", report.errorKind)
        assertTrue("拒绝理由要说清是协议不允许：" + report.error, report.error.contains("ftp"))

        val payload = json(binding.toolResult("call_5", "mcp_echo", "{}").output)
        assertEquals("mcp_invalid_endpoint", field(payload, "error_type"))
        assertEquals(0, server.requestCount.get())
    }

    @Test
    fun refusedConnectionDegradesWithoutHanging() = runBlocking {
        val deadPort = freePort()
        val binding = bindingFor("http://127.0.0.1:" + deadPort + "/mcp")

        val started = System.currentTimeMillis()
        val report = binding.refresh()
        val refreshMs = System.currentTimeMillis() - started
        assertEquals("failed", report.status)
        assertEquals("connect", report.errorKind)
        assertTrue("连接被拒必须立刻返回，实际 " + refreshMs + "ms", refreshMs < 3000)

        val payload = json(binding.toolResult("call_6", "mcp_echo", "{}").output)
        assertEquals("mcp_connect", field(payload, "error_type"))
        assertEquals(true, payload.field("degraded")?.boolValue())
    }

    @Test
    fun readTimeoutIsBoundedInsideTheBinding() = runBlocking {
        server.callDelayMs = 900
        val binding = McpToolBinding(
            endpointSource = { server.endpointUrl },
            enabledSource = { true },
            timeouts = McpTimeouts(connectMs = 1500, readMs = 250, requestMs = 4000)
        )
        this@McpToolBindingTest.binding = binding
        binding.refresh()

        val started = System.currentTimeMillis()
        val payload = json(binding.toolResult("call_7", "mcp_echo", "{\"text\":\"slow\"}").output)
        val elapsed = System.currentTimeMillis() - started
        assertEquals("mcp_timeout", field(payload, "error_type"))
        assertTrue("读超时必须截断在 250ms 档，实际 " + elapsed + "ms", elapsed < 900)
    }
}
