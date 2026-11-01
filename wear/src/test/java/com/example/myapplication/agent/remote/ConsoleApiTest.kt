package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ConsoleApiTest {

    private val token = "ab12cd34ef56ab78cd90ef12ab34cd56"

    private class FakeSettings(initial: Map<String, Any?>) : ConsoleSettingsPort {

        val values = LinkedHashMap<String, Any?>(initial)
        val written = ArrayList<String>()

        override fun snapshot(): Map<String, Any?> = LinkedHashMap(values)

        override fun write(key: String, value: Any): ConsoleWriteResult {
            written += key
            values[key] = value
            return ConsoleWriteResult.OK
        }
    }

    private fun fullSnapshot(): Map<String, Any?> = linkedMapOf(
        "baseUrl" to "https://api.deepseek.com",
        "apiKey" to "sk-test",
        "modelName" to "deepseek-chat",
        "temperature" to 0.7f,
        "maxOutputTokens" to 2048,
        "customSystemPrompt" to "",
        "securityMode" to "STANDARD",
        "maxContextTurns" to 30,
        "asrProvider" to "SYSTEM",
        "baiduApiKey" to "",
        "baiduSecretKey" to "",
        "asrEnabled" to false,
        "toolDeviceStatusEnabled" to true,
        "toolVibrateEnabled" to true,
        "toolMatrixDensityEnabled" to true,
        "toolPowerModeEnabled" to true,
        "toolWeatherEnabled" to true,
        "toolWebSearchEnabled" to true,
        "isMcpEnabled" to true,
        "mcpEndpoint" to "http://127.0.0.1:8000/sse",
        "matrixSpacingDp" to 16f,
        "mainInputType" to "VOICE",
        "streamMode" to true,
        "hasCompletedOnboarding" to true,
    )

    private fun api(
        settings: ConsoleSettingsPort = FakeSettings(fullSnapshot()),
        assets: (String) -> ByteArray? = { name -> ("asset:" + name).toByteArray(StandardCharsets.UTF_8) },
        actions: (String) -> String = { name -> "done:" + name },
        currentToken: String = token,
        port: Int = 8080,
        uptimeMs: Long = 1234L,
    ): ConsoleApi = ConsoleApi(
        settings = settings,
        capabilities = {
            mapOf(
                "root" to true,
                "shizukuInstalled" to true,
                "shizukuReady" to false,
                "activeBackend" to "ROOT",
                "internet" to true,
            )
        },
        actions = actions,
        assets = assets,
        token = { currentToken },
        serverPort = { port },
        uptime = { uptimeMs },
    )

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        query: Map<String, String> = mapOf("t" to token),
        headers: Map<String, String> = emptyMap(),
        bodyBytes: Int = body?.toByteArray(StandardCharsets.UTF_8)?.size ?: 0,
        bodyTooLarge: Boolean = false,
    ): ConsoleRequest = ConsoleRequest(method, path, query, headers, body, bodyBytes, bodyTooLarge)

    private fun json(response: ConsoleResponse): Map<String, Any?> {
        assertEquals(ConsoleResponse.JSON_CONTENT_TYPE, response.contentType)
        val parsed = ConsoleJson.parseObject(response.text())
        assertNotNull("响应不是合法 JSON: " + response.text(), parsed)
        return parsed!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun map(value: Any?): Map<String, Any?> = value as Map<String, Any?>

    @Test
    fun pingReturnsContractFields() {
        val response = api().handle(request("GET", "/api/ping"))
        assertEquals(200, response.status)
        val body = json(response)
        assertEquals(true, body["ok"])
        assertEquals(1, body["version"])
        assertEquals("wristagent", body["app"])
    }

    @Test
    fun missingTokenIsRejectedWith401() {
        val response = api().handle(request("GET", "/api/state", query = emptyMap()))
        assertEquals(401, response.status)
        val body = json(response)
        assertEquals(false, body["ok"])
        assertEquals("unauthorized", body["error"])
    }

    @Test
    fun wrongTokenIsRejectedWith401() {
        val response = api().handle(request("GET", "/api/ping", query = mapOf("t" to "ffffffffffffffffffffffffffffffff")))
        assertEquals(401, response.status)
        assertEquals("unauthorized", json(response)["error"])
    }

    @Test
    fun tokenIsAcceptedFromQueryParameter() {
        assertEquals(200, api().handle(request("GET", "/api/ping")).status)
    }

    @Test
    fun tokenIsAcceptedFromHeader() {
        val response = api().handle(
            ConsoleRequest("GET", "/api/ping", headers = mapOf("x-console-token" to token))
        )
        assertEquals(200, response.status)
    }

    @Test
    fun stateReturnsSettingsCapabilitiesAndRuntime() {
        val response = api(port = 8081, uptimeMs = 4321L).handle(request("GET", "/api/state"))
        assertEquals(200, response.status)
        val body = json(response)
        assertEquals(true, body["ok"])
        assertEquals("deepseek-chat", map(body["settings"])["modelName"])
        val capabilities = map(body["capabilities"])
        assertEquals(true, capabilities["root"])
        assertEquals(true, capabilities["shizukuInstalled"])
        assertEquals(false, capabilities["shizukuReady"])
        assertEquals("ROOT", capabilities["activeBackend"])
        assertEquals(true, capabilities["internet"])
        assertEquals("ab12…cd56", capabilities["tokenMasked"])
        assertFalse("不得回传完整 token", capabilities["tokenMasked"] == token)
        val runtime = map(body["runtime"])
        assertEquals(8081, runtime["serverPort"])
        assertEquals(4321, runtime["uptimeMs"])
    }

    @Test
    fun settingsPartialUpdateWritesOnlyGivenKeys() {
        val settings = FakeSettings(fullSnapshot())
        val response = api(settings = settings)
            .handle(request("POST", "/api/settings", body = """{"modelName":"deepseek-reasoner"}"""))
        assertEquals(200, response.status)
        val body = json(response)
        assertEquals(true, body["ok"])
        assertEquals("deepseek-reasoner", map(body["settings"])["modelName"])
        assertEquals("https://api.deepseek.com", map(body["settings"])["baseUrl"])
        assertEquals(listOf("modelName"), settings.written)
    }

    @Test
    fun settingsMultiKeyUpdateAppliesEveryField() {
        val settings = FakeSettings(fullSnapshot())
        val response = api(settings = settings)
            .handle(request("POST", "/api/settings", body = """{"temperature":0.3,"streamMode":false,"maxContextTurns":10}"""))
        assertEquals(200, response.status)
        assertEquals(listOf("temperature", "streamMode", "maxContextTurns"), settings.written)
        val updated = map(json(response)["settings"])
        assertEquals(0.3, updated["temperature"] as Double, 1e-6)
        assertEquals(false, updated["streamMode"])
        assertEquals(10, updated["maxContextTurns"])
    }

    @Test
    fun unknownKeysAreIgnoredAndReported() {
        val settings = FakeSettings(fullSnapshot())
        val body = json(
            api(settings = settings)
                .handle(request("POST", "/api/settings", body = """{"modelName":"m2","notAField":1}"""))
        )
        assertEquals(true, body["ok"])
        assertEquals(listOf("notAField"), body["ignored"])
        assertEquals(listOf("modelName"), settings.written)
    }

    @Test
    fun typeMismatchReturnsBadTypeAndWritesNothing() {
        val settings = FakeSettings(fullSnapshot())
        val response = api(settings = settings)
            .handle(request("POST", "/api/settings", body = """{"maxContextTurns":"thirty"}"""))
        assertEquals(400, response.status)
        val body = json(response)
        assertEquals(false, body["ok"])
        assertEquals("bad_type", body["error"])
        assertEquals("maxContextTurns", body["field"])
        assertTrue("类型不符时不得落库", settings.written.isEmpty())
    }

    @Test
    fun aliasWritesLandOnRealProperty() {
        val settings = FakeSettings(fullSnapshot())
        val body = json(api(settings = settings).handle(request("POST", "/api/settings", body = """{"maxTokens":4096}""")))
        assertEquals(true, body["ok"])
        assertEquals(listOf("maxOutputTokens"), settings.written)
        val snapshot = map(body["settings"])
        assertEquals(4096, snapshot["maxOutputTokens"])
        assertEquals(4096, snapshot["maxTokens"])
    }

    @Test
    fun readOnlyFieldIsIgnoredWithoutWriting() {
        val settings = FakeSettings(fullSnapshot())
        val body = json(
            api(settings = settings)
                .handle(request("POST", "/api/settings", body = """{"hasCompletedOnboarding":false}"""))
        )
        assertEquals(true, body["ok"])
        assertTrue((body["ignored"] as List<*>).contains("hasCompletedOnboarding"))
        assertTrue(settings.written.isEmpty())
        assertEquals(true, map(body["settings"])["hasCompletedOnboarding"])
    }

    @Test
    fun invalidJsonReturnsBadJson() {
        val response = api().handle(request("POST", "/api/settings", body = "{not json"))
        assertEquals(400, response.status)
        assertEquals("bad_json", json(response)["error"])
    }

    @Test
    fun nonObjectJsonReturnsBadJson() {
        val response = api().handle(request("POST", "/api/settings", body = "[1,2,3]"))
        assertEquals(400, response.status)
        assertEquals("bad_json", json(response)["error"])
    }

    @Test
    fun oversizedBodyReturnsPayloadTooLarge() {
        val response = api().handle(
            request("POST", "/api/settings", body = """{"modelName":"m"}""", bodyBytes = ConsoleApi.MAX_BODY_BYTES + 1)
        )
        assertEquals(413, response.status)
        assertEquals("body_too_large", json(response)["error"])
    }

    @Test
    fun oversizedActionBodyReturnsPayloadTooLarge() {
        val response = api().handle(
            request("POST", "/api/action/probe-privilege", body = "{}", bodyTooLarge = true)
        )
        assertEquals(413, response.status)
        assertEquals("body_too_large", json(response)["error"])
    }

    @Test
    fun unknownActionReturns404() {
        val response = api().handle(request("POST", "/api/action/reboot"))
        assertEquals(404, response.status)
        assertEquals(false, json(response)["ok"])
    }

    @Test
    fun knownActionReturnsHumanReadableResult() {
        val response = api(actions = { "uid=0(root) gid=0(root)" })
            .handle(request("POST", "/api/action/probe-privilege"))
        assertEquals(200, response.status)
        val body = json(response)
        assertEquals(true, body["ok"])
        assertEquals("uid=0(root) gid=0(root)", body["result"])
    }

    @Test
    fun actionFailureReturnsStructuredError() {
        val response = api(actions = { throw IllegalStateException("boom") })
            .handle(request("POST", "/api/action/test-asr"))
        assertEquals(500, response.status)
        val body = json(response)
        assertEquals(false, body["ok"])
        assertEquals("action_failed", body["error"])
    }

    @Test
    fun indexIsServedFromAssetsWithoutToken() {
        val response = api(assets = { name -> ("<html>" + name + "</html>").toByteArray(StandardCharsets.UTF_8) })
            .handle(ConsoleRequest("GET", "/"))
        assertEquals(200, response.status)
        assertEquals("text/html; charset=utf-8", response.contentType)
        assertEquals("<html>index.html</html>", response.text())
    }

    @Test
    fun scriptAndStyleUseTheirOwnContentTypes() {
        val js = api(assets = { name -> name.toByteArray(StandardCharsets.UTF_8) }).handle(ConsoleRequest("GET", "/app.js"))
        assertEquals(200, js.status)
        assertEquals("text/javascript; charset=utf-8", js.contentType)
        assertEquals("app.js", js.text())
        val css = api(assets = { name -> name.toByteArray(StandardCharsets.UTF_8) }).handle(ConsoleRequest("GET", "/app.css"))
        assertEquals("text/css; charset=utf-8", css.contentType)
        assertEquals("app.css", css.text())
        val index = api(assets = { name -> name.toByteArray(StandardCharsets.UTF_8) })
            .handle(ConsoleRequest("GET", "/index.html"))
        assertEquals("index.html", index.text())
    }

    @Test
    fun missingAssetReturns404Json() {
        val response = api(assets = { null }).handle(ConsoleRequest("GET", "/"))
        assertEquals(404, response.status)
        val body = json(response)
        assertEquals(false, body["ok"])
        assertEquals("not_found", body["error"])
    }

    @Test
    fun unknownPathsReturn404() {
        assertEquals(404, api().handle(ConsoleRequest("GET", "/nope")).status)
        assertEquals(404, api().handle(request("GET", "/api/nope")).status)
        assertEquals(404, api().handle(request("GET", "/api/ping/unknown")).status)
    }

    @Test
    fun wrongMethodReturns405() {
        assertEquals(405, api().handle(request("POST", "/api/ping")).status)
        assertEquals(405, api().handle(request("GET", "/api/settings")).status)
        assertEquals(405, api().handle(request("GET", "/api/action/probe-privilege")).status)
    }

    @Test
    fun invalidEnumValueReturnsBadValueWithAllowedList() {
        val response = api().handle(request("POST", "/api/settings", body = """{"securityMode":"GOD"}"""))
        assertEquals(400, response.status)
        val body = json(response)
        assertEquals("bad_value", body["error"])
        assertEquals("securityMode", body["field"])
        assertEquals(listOf("NOVICE", "STANDARD", "EXPERT"), body["allowed"])
    }

    @Test
    fun snapshotCoversEveryContractFieldAndMirrorsAliases() {
        val settings = map(json(api().handle(request("GET", "/api/state")))["settings"])
        val expected = listOf(
            "baseUrl", "apiKey", "modelName", "temperature", "maxOutputTokens", "customSystemPrompt",
            "securityMode", "maxContextTurns", "asrProvider", "baiduApiKey", "baiduSecretKey", "asrEnabled",
            "toolDeviceStatusEnabled", "toolVibrateEnabled", "toolMatrixDensityEnabled", "toolPowerModeEnabled",
            "toolWeatherEnabled", "toolWebSearchEnabled", "isMcpEnabled", "mcpEndpoint", "matrixSpacingDp",
            "mainInputType", "streamMode", "hasCompletedOnboarding",
        )
        for (key in expected) assertTrue("缺少字段 " + key, settings.containsKey(key))
        assertEquals(settings["baseUrl"], settings["apiBaseUrl"])
        assertEquals(settings["maxOutputTokens"], settings["maxTokens"])
        assertEquals(settings["customSystemPrompt"], settings["systemPrompt"])
        assertEquals(settings["maxContextTurns"], settings["memoryRounds"])
        assertEquals(settings["asrProvider"], settings["asrEngine"])
    }
}
