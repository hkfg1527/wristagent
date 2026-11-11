package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 免鉴权健康探针：PC 排障时用它区分「没起 / 起在别的端口 / 401」。 */
class ConsoleHealthzTest {

    private val liveKey = "sk-live-0123456789abcdef"

    private fun api(port: Int = 8080, uptimeMs: Long = 61_000L): ConsoleApi = ConsoleApi(
        settings = object : ConsoleSettingsPort {
            override fun snapshot(): Map<String, Any?> = linkedMapOf(
                "modelName" to "deepseek-chat",
                "apiKey" to liveKey,
            )

            override fun write(key: String, value: Any): ConsoleWriteResult = ConsoleWriteResult.OK
        },
        capabilities = { emptyMap() },
        actions = { "" },
        assets = { null },
        token = { TOKEN },
        serverPort = { port },
        uptime = { uptimeMs },
    )

    private fun body(response: ConsoleResponse): Map<String, Any?> =
        ConsoleJson.parseObject(response.text())!!

    @Test
    fun healthzAnswersWithoutToken() {
        val response = api(port = 8081, uptimeMs = 61_000L).handle(ConsoleRequest("GET", "/healthz"))
        assertEquals(200, response.status)
        val parsed = body(response)
        assertEquals(true, parsed["ok"])
        assertEquals(1, parsed["version"])
        assertEquals("wristagent", parsed["app"])
        assertEquals(8081, parsed["port"])
        assertEquals(61_000, parsed["uptimeMs"])
        assertEquals("deepseek-chat", parsed["model"])
    }

    @Test
    fun healthzNeverLeaksSecretsOrClientAddress() {
        val response = api().handle(ConsoleRequest("GET", "/healthz", client = "192.168.1.77"))
        val text = response.text()
        assertFalse("不得回传真实密钥：" + text, text.contains(liveKey))
        assertFalse("不得回传访问令牌：" + text, text.contains(TOKEN))
        assertFalse("不得回传对端内网 IP：" + text, text.contains("192.168.1.77"))
    }

    @Test
    fun healthzIsNotBlockedByTheAuthLockout() {
        val api = api()
        repeat(5) {
            assertEquals(
                401,
                api.handle(ConsoleRequest("GET", "/api/ping", query = mapOf("t" to "9999"), client = "10.0.0.9")).status
            )
        }
        assertEquals(
            "锁定后 /api 应当拒绝",
            429,
            api.handle(ConsoleRequest("GET", "/api/ping", query = mapOf("t" to "9999"), client = "10.0.0.9")).status
        )
        assertEquals(
            "锁定期间 /healthz 仍要能答，否则探针无法区分 401 与没起",
            200,
            api.handle(ConsoleRequest("GET", "/healthz", client = "10.0.0.9")).status
        )
    }

    @Test
    fun healthzRejectsWrites() {
        assertEquals(405, api().handle(ConsoleRequest("POST", "/healthz", client = "10.0.0.9")).status)
        assertTrue(body(api().handle(ConsoleRequest("POST", "/healthz"))).containsKey("error"))
    }

    private companion object {
        const val TOKEN = "1234"
    }
}
