package com.example.myapplication.agent.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * `abort()` 必须断掉**所有**在途连接。
 *
 * 回归背景（0.0.0.100 审查）：`McpHttpTransport` 只有一个 `active` 槽位，而同一个 client 会并发发请求
 * （`ensureFresh` 后台刷新 + `callTool`）。后开的连接覆盖前者：
 * ① `abort()` 只断得掉最后一条，另一条要等读超时；
 * ② 先结束者的 `finally` 会把仍在途的连接从槽里清空，此后 `abort()` 变成空操作。
 */
class McpTransportAbortTest {

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

    @Test
    fun abortDisconnectsEveryRequestThatIsStillInFlight() = runBlocking {
        // 假服务器把每个 tools/call 拖到 10 秒；abort 之前两条请求都已经在途
        val endpoint = (McpEndpointPolicy.parse(server.endpointUrl) as McpEndpointVerdict.Accept).endpoint
        val client = McpClient(
            endpoint = endpoint,
            timeouts = McpTimeouts(connectMs = 2000, readMs = 30_000, requestMs = 30_000)
        )
        // 先完成 initialize（这时服务器还不慢），之后的 tools/call 才是"两条并发在途"
        client.callTool("mcp_echo", McpJson.obj("text" to McpJson.Str("warm")))
        server.callDelayMs = 10_000L

        val failures = AtomicInteger(0)
        val first = async(Dispatchers.IO) {
            runCatching { client.callTool("mcp_echo", McpJson.obj("text" to McpJson.Str("a"))) }
                .onFailure { failures.incrementAndGet() }
        }
        val second = async(Dispatchers.IO) {
            runCatching { client.callTool("mcp_echo", McpJson.obj("text" to McpJson.Str("b"))) }
                .onFailure { failures.incrementAndGet() }
        }
        delay(400) // 两条都已在途
        val started = System.currentTimeMillis()
        client.abort()
        first.await()
        second.await()
        val elapsed = System.currentTimeMillis() - started
        client.close()

        assertEquals("两条在途请求都必须被断开（只断一条 = 单槽位的老毛病）", 2, failures.get())
        assertTrue("abort 后必须立刻返回，而不是等 10s 的服务端延迟，实际 " + elapsed + "ms", elapsed < 5000)
    }
}
