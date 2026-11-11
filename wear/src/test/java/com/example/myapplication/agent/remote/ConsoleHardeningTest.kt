package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/** 网页可写非法值、写入被拒却报成功、以及「邻居把合法用户锁死」三类真实缺陷的回归护栏。 */
class ConsoleHardeningTest {

    private class RecordingPort(
        initial: Map<String, Any?> = linkedMapOf("modelName" to "deepseek-chat", "temperature" to 0.7f),
        private val alwaysReadOnly: Boolean = false,
    ) : ConsoleSettingsPort {

        val values = LinkedHashMap<String, Any?>(initial)
        val written = ArrayList<String>()

        override fun snapshot(): Map<String, Any?> = LinkedHashMap(values)

        override fun write(key: String, value: Any): ConsoleWriteResult {
            if (alwaysReadOnly) return ConsoleWriteResult.READ_ONLY
            written += key
            values[key] = value
            return ConsoleWriteResult.OK
        }
    }

    private fun api(port: ConsoleSettingsPort = RecordingPort()): ConsoleApi = ConsoleApi(
        settings = port,
        capabilities = { emptyMap() },
        actions = { "" },
        assets = { null },
        token = { TOKEN },
        serverPort = { 8080 },
        uptime = { 0L },
    )

    private fun post(port: ConsoleSettingsPort, body: String): Pair<Int, Map<String, Any?>> {
        val response = api(port).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        return response.status to ConsoleJson.parseObject(response.text())!!
    }

    @Test
    fun writeRejectedByStorageIsReportedInsteadOfSilentSuccess() {
        val port = RecordingPort(alwaysReadOnly = true)
        val (status, parsed) = post(port, "{\"modelName\":\"deepseek-reasoner\"}")
        assertEquals(200, status)
        assertEquals(true, parsed["ok"])
        @Suppress("UNCHECKED_CAST")
        val ignored = parsed["ignored"] as? List<*>
        assertTrue("写入被存储层拒绝时必须回 ignored，不能假装成功：" + parsed, ignored?.contains("modelName") == true)
        assertEquals("被拒绝时不得改动存储", "deepseek-chat", port.values["modelName"])
    }

    @Test
    fun temperatureOutsideProductRangeIsRejected() {
        for (illegal in listOf("99", "-0.1", "1.51")) {
            val (status, parsed) = post(RecordingPort(), "{\"temperature\":" + illegal + "}")
            assertEquals("temperature=" + illegal + " 必须被拒：" + parsed, 400, status)
            assertEquals("bad_value", parsed["error"])
            assertEquals("temperature", parsed["field"])
        }
    }

    @Test
    fun floatFieldsOnlyAcceptJsonNumberShapes() {
        val field = ConsoleFields.field("temperature")!!
        assertTrue("JSON 小数解码成 Double，必须收", ConsoleFields.coerce(field, 0.5) is ConsoleValue.Valid)
        assertTrue("JSON 整数解码成 Int，必须收", ConsoleFields.coerce(field, 1) is ConsoleValue.Valid)
        assertEquals(
            "HTTP 体解码不出 Float：送 Float 属于类型不符（取样器造错形状时应当在这里红，而不是在联动测试里变成违规）",
            ConsoleValue.BadType,
            ConsoleFields.coerce(field, 0.5f),
        )
    }

    @Test
    fun temperatureInsideProductRangeIsAccepted() {
        val port = RecordingPort()
        val (status, parsed) = post(port, "{\"temperature\":1.5}")
        assertEquals(200, status)
        assertEquals(true, parsed["ok"])
        assertEquals(1.5f, port.values["temperature"])
    }

    @Test
    fun nonFiniteNumberIsRejected() {
        val (status, parsed) = post(RecordingPort(), "{\"temperature\":1e400}")
        assertEquals("Infinity 不能当温度写进去：" + parsed, 400, status)
        assertEquals("bad_type", parsed["error"])
    }

    @Test
    fun enumFieldOnlyAcceptsKnownValues() {
        val (status, parsed) = post(RecordingPort(), "{\"sandboxLevel\":\"COFFEE\"}")
        assertEquals("网页不得写入手表不认识的沙箱等级：" + parsed, 400, status)
        assertEquals("bad_value", parsed["error"])
        assertEquals("sandboxLevel", parsed["field"])
        assertEquals(listOf("READ_ONLY", "WORKSPACE", "FULL"), parsed["allowed"])
    }

    @Test
    fun enumFieldAcceptsKnownValue() {
        val port = RecordingPort()
        val (status, _) = post(port, "{\"sandboxLevel\":\"WORKSPACE\"}")
        assertEquals(200, status)
        assertEquals("WORKSPACE", port.values["sandboxLevel"])
    }

    @Test
    fun failuresFromOnePeerDoNotLockTheOwnerOut() {
        val api = api()
        val attacker = "10.0.0.9"
        repeat(5) {
            assertEquals(
                401,
                api.handle(ConsoleRequest("GET", "/api/ping", query = mapOf("t" to "9999"), client = attacker)).status
            )
        }
        assertEquals(
            "攻击者自己必须被锁",
            429,
            api.handle(ConsoleRequest("GET", "/api/ping", query = mapOf("t" to "9999"), client = attacker)).status
        )
        assertEquals(
            "同一局域网里合法用户不得被邻居锁死",
            200,
            api.handle(ConsoleRequest("GET", "/api/ping", query = mapOf("t" to TOKEN), client = "192.168.1.20")).status
        )
    }

    private companion object {
        const val TOKEN = "1234"
    }
}