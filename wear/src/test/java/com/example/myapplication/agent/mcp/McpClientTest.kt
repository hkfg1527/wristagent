package com.example.myapplication.agent.mcp

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * MCP 客户端端到端：真实本地 HTTP 服务器 + 真实 JSON-RPC 收发，
 * 握手、清单、调用、SSE、错误降级、超时全部走真链路。
 */
class McpClientTest {

    private lateinit var server: FakeMcpServer

    @Before
    fun setUp() {
        server = FakeMcpServer()
        server.startServer()
    }

    @After
    fun tearDown() {
        server.stop()
    }

    private fun client(timeouts: McpTimeouts = McpTimeouts(connectMs = 1500, readMs = 2000, requestMs = 5000)): McpClient {
        val verdict = McpEndpointPolicy.parse(server.endpointUrl)
        assertTrue(verdict is McpEndpointVerdict.Accept)
        return McpClient((verdict as McpEndpointVerdict.Accept).endpoint, timeouts)
    }

    @Test
    fun handshakeAndToolDiscovery() = runBlocking {
        val client = client()
        val info = client.ensureInitialized()
        assertEquals("fake-mcp", info.name)
        assertEquals("2024-11-05", info.protocolVersion)

        val tools = client.listTools()
        assertEquals(listOf("echo", "add", "fail-now"), tools.map { tool -> tool.name })
        assertEquals("回显传入的文本", tools.first().description)
        assertNotNull("远端 inputSchema 必须被解析出来", tools.first().inputSchema)
        assertEquals(
            listOf("initialize", "notifications/initialized", "tools/list"),
            server.methods.toList()
        )
    }

    @Test
    fun handshakeAndDiscoveryWorkOverSse() = runBlocking {
        server.useSse = true
        val client = client()
        assertEquals("fake-mcp", client.ensureInitialized().name)
        assertEquals(listOf("echo", "add", "fail-now"), client.listTools().map { tool -> tool.name })
    }

    @Test
    fun toolCallReturnsServerText() = runBlocking {
        val client = client()
        val result = client.callTool("echo", McpJson.obj("text" to McpJson.Str("你好")))
        assertFalse(result.isError)
        assertEquals("echo:你好", result.text)
        assertEquals(1, result.content.size)

        val sum = client.callTool("add", McpJson.obj("a" to McpJson.Num(1.0), "b" to McpJson.Num(2.0)))
        assertEquals("sum=3", sum.text)
    }

    @Test
    fun serverReportedToolErrorIsReturnedNotThrown() = runBlocking {
        val client = client()
        val result = client.callTool("fail-now", McpJson.obj())
        assertTrue("工具自报失败必须体现在结果里", result.isError)
        assertEquals("tool exploded", result.text)
    }

    @Test
    fun jsonRpcErrorBecomesTransportFailure() = runBlocking {
        server.failCallWithRpcError = true
        val client = client()
        try {
            client.callTool("echo", McpJson.obj("text" to McpJson.Str("x")))
            fail("服务端返回 JSON-RPC error 时必须失败")
        } catch (error: McpTransportException) {
            assertEquals("rpc_error", error.kind)
            assertTrue("错误说明要带服务端原文：" + error.detail, error.detail.contains("fake server boom"))
        }
    }

    @Test
    fun refusedConnectionFailsFast() = runBlocking {
        val deadPort = freePort()
        val endpoint = McpEndpoint("http", "127.0.0.1", deadPort, "/mcp", null)
        val client = McpClient(endpoint, McpTimeouts(connectMs = 1500, readMs = 2000, requestMs = 4000))
        val started = System.currentTimeMillis()
        try {
            client.ensureInitialized()
            fail("端口没人监听时必须失败")
        } catch (error: McpTransportException) {
            assertEquals("connect", error.kind)
        }
        val elapsed = System.currentTimeMillis() - started
        assertTrue("连接被拒必须立刻返回，实际 " + elapsed + "ms", elapsed < 3000)
    }

    @Test
    fun readTimeoutIsBounded() = runBlocking {
        server.callDelayMs = 900
        val client = client(McpTimeouts(connectMs = 1500, readMs = 250, requestMs = 4000))
        client.ensureInitialized()
        val started = System.currentTimeMillis()
        try {
            client.callTool("echo", McpJson.obj("text" to McpJson.Str("slow")))
            fail("服务端拖过读超时后必须失败")
        } catch (error: McpTransportException) {
            assertEquals("timeout", error.kind)
        }
        val elapsed = System.currentTimeMillis() - started
        assertTrue("超时必须被 250ms 读超时截断，实际 " + elapsed + "ms", elapsed < 900)
    }

    @Test
    fun crossHostRedirectIsRefusedWithoutFollowingIt() = runBlocking {
        server.redirectLocation = "http://evil.example:" + server.port + "/mcp"
        val client = client()
        try {
            client.ensureInitialized()
            fail("跨主机重定向必须被拒绝")
        } catch (error: McpTransportException) {
            assertEquals("redirect_refused", error.kind)
            assertTrue("理由要记下两端主机：" + error.detail, error.detail.contains("evil.example"))
        }
        assertEquals("重定向不能被跟随：只该有原始那一次请求", 1, server.requestCount.get())
    }
}
