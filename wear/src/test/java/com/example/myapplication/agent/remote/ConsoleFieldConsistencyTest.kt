package com.example.myapplication.agent.remote

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * 端到端字段一致性（ISSUES B3）：ConsoleFields 暴露的每个可写字段都必须
 * 「POST /api/settings → 真的落到 AgentPreferences → 重新读回是新值 → 响应回显新值」。
 *
 * 覆盖度由集合相等断言守住：白名单新增字段但不补用例会直接失败，杜绝「网页能编辑、后端静默丢弃」。
 * 落库走真实的 [AgentPreferencesSettingsPort] + 内存版 SharedPreferences，不桩掉被测映射。
 */
class ConsoleFieldConsistencyTest {

    private class FieldCase(
        val key: String,
        val body: (current: Any?) -> Map<String, Any?>,
        val expected: (current: Any?) -> Any?,
    )

    private val booleanCases: List<FieldCase> = ConsoleFields.ALL
        .filter { it.type == ConsoleFieldType.BOOLEAN && !it.readOnly }
        .map { flag(it.key) }

    private val enumCases: List<FieldCase> = ConsoleFields.ALL
        .filter { it.type == ConsoleFieldType.ENUM && !it.readOnly }
        .map { field ->
            FieldCase(
                field.key,
                { current -> mapOf(field.key to enumSample(field, current)) },
                { current -> enumSample(field, current) },
            )
        }

    private val cases: List<FieldCase> = buildList {
        addAll(booleanCases)
        addAll(enumCases)
        add(text("baseUrl", "https://console.example.com/v1"))
        add(text("apiKey", "sk-console-0123456789abcdef"))
        add(text("modelName", "deepseek-console"))
        add(text("customSystemPrompt", "console prompt"))
        add(text("baiduApiKey", "baidu-ak-0123456789"))
        add(text("baiduSecretKey", "baidu-sk-9876543210"))
        add(text("mcpEndpoint", "http://console.invalid:9000/sse"))
        add(int("maxOutputTokens", 4096))
        add(int("maxContextTurns", 12))
        add(int("requestTimeoutSeconds", 90))
        add(int("requestRetries", 2))
        add(int("retryBackoffMs", 1200))
        add(float("temperature", 1.2f))
        add(float("matrixSpacingDp", 20f))
        add(float("historyCompressThreshold", 0.5f))
        add(providerProfiles())
    }

    private fun text(key: String, value: String) = FieldCase(key, { _ -> mapOf(key to value) }, { _ -> value })

    private fun int(key: String, value: Int) = FieldCase(key, { _ -> mapOf(key to value) }, { _ -> value })

    private fun float(key: String, value: Float) = FieldCase(key, { _ -> mapOf(key to value) }, { _ -> value })

    private fun flag(key: String) = FieldCase(
        key,
        { current -> mapOf(key to !(current as Boolean)) },
        { current -> !(current as Boolean) },
    )

    /** ENUM 样值必须与当前值不同：写成默认值的话，「落库成功」与「被丢弃」无法区分。 */
    private fun enumSample(field: ConsoleField, current: Any?): String =
        field.allowed.firstOrNull { it != current } ?: field.allowed.last()

    private fun providerProfiles() = FieldCase(
        "providerProfiles",
        { _ ->
            mapOf(
                "providerProfiles" to listOf(
                    linkedMapOf("name" to "console-primary", "baseUrl" to "https://a.example.com", "model" to "deepseek-chat"),
                    linkedMapOf("name" to "console-backup", "baseUrl" to "https://b.example.com", "model" to "deepseek-reasoner"),
                )
            )
        },
        { _ ->
            listOf(
                linkedMapOf<String, Any?>("name" to "console-primary", "baseUrl" to "https://a.example.com", "model" to "deepseek-chat"),
                linkedMapOf<String, Any?>("name" to "console-backup", "baseUrl" to "https://b.example.com", "model" to "deepseek-reasoner"),
            )
        },
    )

    private fun port(store: FakeSharedPreferences): AgentPreferencesSettingsPort =
        AgentPreferencesSettingsPort(AgentPreferences(store))

    private fun webApi(store: FakeSharedPreferences): ConsoleApi = ConsoleApi(
        settings = port(store),
        capabilities = { emptyMap() },
        actions = { "" },
        assets = { null },
        token = { TOKEN },
        serverPort = { 8080 },
        uptime = { 0L },
    )

    private fun post(store: FakeSharedPreferences, body: Map<String, Any?>): Map<String, Any?> {
        val text = ConsoleJson.write(body)
        val response = webApi(store).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = text,
                bodyBytes = text.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals("POST 必须成功：" + text + " → " + response.text(), 200, response.status)
        return ConsoleJson.parseObject(response.text())!!
    }

    @Suppress("UNCHECKED_CAST")
    private fun settingsOf(parsed: Map<String, Any?>): Map<String, Any?> = parsed["settings"] as Map<String, Any?>

    @Test
    fun everyWritableFieldLandsInStorageThroughTheHttpApi() {
        val writable = ConsoleFields.ALL.filter { !it.readOnly }.map { it.key }
        assertEquals(
            "白名单里每个可写字段都必须有用例",
            writable.toSet(),
            cases.map { it.key }.toSet(),
        )
        assertEquals("用例不得重复", cases.map { it.key }.toSet().size, cases.size)

        for (case in cases) {
            val store = FakeSharedPreferences()
            val current = port(store).snapshot()[case.key]
            val parsed = post(store, case.body(current))
            assertEquals(case.key + " 必须 ok", true, parsed["ok"])
            val ignored = parsed["ignored"] as? List<*>
            assertFalse(case.key + " 不得进 ignored（否则网页只是存了本地草稿）", ignored?.contains(case.key) == true)

            val expected = ConsoleJson.write(case.expected(current))
            val stored = ConsoleJson.write(port(store).snapshot()[case.key])
            assertEquals(case.key + " 必须真的落库，而不是只回显", expected, stored)

            val echo = settingsOf(parsed)
            assertTrue(case.key + " 必须出现在响应快照里", echo.containsKey(case.key))
            if (case.key in SECRET_KEYS) {
                val raw = case.expected(current) as String
                assertNotEquals(case.key + " 的回显必须是掩码", expected, ConsoleJson.write(echo[case.key]))
                assertFalse(case.key + " 的明文不得出现在响应里", ConsoleJson.write(echo).contains(raw))
            } else {
                assertEquals(case.key + " 必须回显新值，否则下次打开网页看到的还是旧值", expected, ConsoleJson.write(echo[case.key]))
            }
        }
    }

    @Test
    fun aliasKeysMirrorTheValueWrittenThroughTheRealKey() {
        for ((alias, real) in ConsoleFields.ALIASES) {
            val case = cases.firstOrNull { it.key == real }
            assertTrue("真实键 " + real + " 必须有端到端用例", case != null)
            val store = FakeSharedPreferences()
            val parsed = post(store, case!!.body(port(store).snapshot()[real]))
            val echo = settingsOf(parsed)
            assertTrue("契约别名 " + alias + " 必须出现在响应里", echo.containsKey(alias))
            assertEquals(
                "契约别名 " + alias + " 必须镜像 " + real,
                ConsoleJson.write(echo[real]),
                ConsoleJson.write(echo[alias]),
            )
        }
    }

    @Test
    fun readOnlyFieldIsVisibleButNeverWritten() {
        val readOnly = ConsoleFields.ALL.filter { it.readOnly }.map { it.key }
        assertEquals(listOf("hasCompletedOnboarding"), readOnly)
        val store = FakeSharedPreferences()
        val before = port(store).snapshot()["hasCompletedOnboarding"]
        val parsed = post(store, mapOf("hasCompletedOnboarding" to !(before as Boolean)))
        @Suppress("UNCHECKED_CAST")
        assertTrue((parsed["ignored"] as List<*>).contains("hasCompletedOnboarding"))
        assertEquals("只读字段不得落库", before, port(store).snapshot()["hasCompletedOnboarding"])
    }

    private companion object {
        const val TOKEN = "1234"
        val SECRET_KEYS = listOf("apiKey", "baiduApiKey", "baiduSecretKey")
    }
}
