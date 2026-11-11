package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * 密钥掩码回写防护（ISSUES C2）。
 *
 * 网页把 /api/state 里的掩码塞进 input，用户改了别的字段或顺手回送时，掩码会被当成新密钥写回，
 * 真钥当场被毁。这里把三条语义钉成契约：
 * 1. 回传掩码（任何掩码风格）→ 不写库，旧密钥原样保留；
 * 2. 回传真实新钥 → 生效并落库；
 * 3. 回传空串 → 视为清空（secret 框默认显示掩码，能变成空只能是用户主动删除）。
 */
class ConsoleSecretMaskTest {

    private class RecordingPort(initial: Map<String, Any?>) : ConsoleSettingsPort {

        val values = LinkedHashMap<String, Any?>(initial)
        val written = ArrayList<String>()

        override fun snapshot(): Map<String, Any?> = LinkedHashMap(values)

        override fun write(key: String, value: Any): ConsoleWriteResult {
            written += key
            values[key] = value
            return ConsoleWriteResult.OK
        }
    }

    private fun snapshot(): Map<String, Any?> = linkedMapOf(
        "modelName" to "deepseek-chat",
        "apiKey" to LIVE_API_KEY,
        "baiduApiKey" to LIVE_BAIDU_AK,
        "baiduSecretKey" to LIVE_BAIDU_SK,
    )

    private fun api(port: ConsoleSettingsPort): ConsoleApi = ConsoleApi(
        settings = port,
        capabilities = { emptyMap() },
        actions = { "" },
        assets = { null },
        token = { TOKEN },
        serverPort = { 8080 },
        uptime = { 0L },
    )

    private fun get(port: ConsoleSettingsPort): Map<String, Any?> {
        val response = api(port).handle(ConsoleRequest("GET", "/api/state", query = mapOf("t" to TOKEN)))
        assertEquals(200, response.status)
        return ConsoleJson.parseObject(response.text())!!
    }

    private fun post(port: ConsoleSettingsPort, body: Map<String, Any?>): Map<String, Any?> {
        val text = ConsoleJson.write(body)
        val response = api(port).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = text,
                bodyBytes = text.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(200, response.status)
        return ConsoleJson.parseObject(response.text())!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun settingsOf(parsed: Map<String, Any?>): Map<String, Any?> = parsed["settings"] as Map<String, Any?>

    @Test
    fun echoedMaskFromStateDoesNotOverwriteTheLiveKey() {
        val port = RecordingPort(snapshot())
        val masked = settingsOf(get(port))["apiKey"] as String
        assertNotEquals("状态接口不得回明文密钥", LIVE_API_KEY, masked)
        assertTrue("掩码必须非空，网页才知道密钥已设置: " + masked, masked.isNotEmpty())

        val parsed = post(port, mapOf("apiKey" to masked))

        assertEquals(true, parsed["ok"])
        assertEquals("掩码不得被写回存储", LIVE_API_KEY, port.values["apiKey"])
        assertFalse("掩码回传时不得触发写入", port.written.contains("apiKey"))
    }

    @Test
    fun realNewKeyIsWrittenAndTakesEffect() {
        val port = RecordingPort(snapshot())
        val parsed = post(port, mapOf("apiKey" to NEW_API_KEY))

        assertEquals(true, parsed["ok"])
        assertEquals(NEW_API_KEY, port.values["apiKey"])
        val echoed = settingsOf(parsed)["apiKey"] as String
        assertNotEquals("回执仍然只能给掩码", NEW_API_KEY, echoed)
    }

    @Test
    fun emptyStringClearsTheKeyOnPurpose() {
        val port = RecordingPort(snapshot())
        post(port, mapOf("apiKey" to ""))
        assertEquals("空串 = 显式清空", "", port.values["apiKey"])
    }

    @Test
    fun masksOfEveryStyleAreTreatedAsEcho() {
        val port = RecordingPort(snapshot())
        val stateMask = settingsOf(get(port))["apiKey"] as String
        val styles = listOf(
            stateMask,
            "sk-live-0123456789abcd\u2026",
            "sk-l****bcdef",
            "\u2026",
        )
        for (style in styles) {
            post(port, mapOf("apiKey" to style))
            assertEquals("掩码风格 " + style + " 不得覆盖真钥", LIVE_API_KEY, port.values["apiKey"])
        }
        assertEquals("一次都不该写", emptyList<String>(), port.written)
    }

    @Test
    fun everySecretFieldIsProtected() {
        val secrets = linkedMapOf(
            "apiKey" to LIVE_API_KEY,
            "baiduApiKey" to LIVE_BAIDU_AK,
            "baiduSecretKey" to LIVE_BAIDU_SK,
        )
        val state = settingsOf(get(RecordingPort(snapshot())))
        for ((key, live) in secrets) {
            val port = RecordingPort(snapshot())
            val masked = state[key] as String
            assertNotEquals(key + " 不得回明文", live, masked)
            post(port, mapOf(key to masked))
            assertEquals(key + " 的掩码不得覆盖真值", live, port.values[key])
        }
    }

    @Test
    fun shortSecretsAreNeverEchoedInClear() {
        val port = RecordingPort(
            linkedMapOf(
                "modelName" to "deepseek-chat",
                "apiKey" to "abcd",
                "baiduApiKey" to "e",
                "baiduSecretKey" to "12345",
            )
        )
        val response = api(port).handle(ConsoleRequest("GET", "/api/state", query = mapOf("t" to TOKEN)))
        val text = response.text()
        assertFalse("短密钥不得原样出现在 /api/state：" + text, text.contains("abcd"))
        assertFalse(text.contains("\"e\""))
        assertFalse("5 位密钥也不得以尾部明文出现", text.contains("12345"))
        val settings = settingsOf(ConsoleJson.parseObject(text)!!)
        assertTrue("掩码非空才表示已设置", (settings["apiKey"] as String).isNotEmpty())
    }

    private companion object {
        const val TOKEN = "1234"
        const val LIVE_API_KEY = "sk-live-0123456789abcdef"
        const val LIVE_BAIDU_AK = "baidu-ak-0123456789"
        const val LIVE_BAIDU_SK = "baidu-sk-9876543210"
        const val NEW_API_KEY = "sk-brand-new-0123456789"
    }
}
