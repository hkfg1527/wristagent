package com.example.myapplication.agent.remote

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.agent.ProviderProfile
import com.example.myapplication.agent.ProviderProfileBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

/**
 * 白名单（[ConsoleFields.ALL]）与落库映射（[AgentPreferencesSettingsPort]）必须是同一批键。
 *
 * 这一对表不同步的两种后果都不可接受：网页上有一行、后端写不进（键进 ignored），
 * 或后端能读、白名单里没有（POST 直接被拒）。因此断言键集**完全相等**，多一个少一个都失败。
 *
 * 用内存版 SharedPreferences 注入 [AgentPreferences]：测的是真实属性读写路径，不桩掉被测映射。
 */
class ConsoleSettingsMappingTest {

    private fun freshStore(): FakeSharedPreferences = FakeSharedPreferences()

    private fun settings(store: FakeSharedPreferences): AgentPreferencesSettingsPort =
        AgentPreferencesSettingsPort(AgentPreferences(store))

    /** 同一次写入后重新构造 AgentPreferences：证明值真的落到存储，而不是停在某个实例的内存里。 */
    private fun readBack(store: FakeSharedPreferences, key: String): Any? =
        settings(store).snapshot()[key]

    private fun sampleFor(field: ConsoleField, current: Any?): Any = when (field.type) {
        ConsoleFieldType.STRING -> if (field.structured) {
            listOf(mapOf("name" to "sample-profile", "baseUrl" to "https://sample.example.com", "model" to "sample-model"))
        } else {
            "console-" + field.key
        }
        ConsoleFieldType.FLOAT -> {
            val min = field.min ?: 0.0
            val max = field.max ?: 1.0
            (min + (max - min) / 2.0).toFloat()
        }
        ConsoleFieldType.INT -> 7
        ConsoleFieldType.BOOLEAN -> !(current as? Boolean ?: false)
        ConsoleFieldType.ENUM -> field.allowed.last()
    }

    @Test
    fun whitelistAndMappingTableShareExactlyTheSameKeys() {
        val mapped = settings(freshStore()).snapshot().keys
        val allowed = ConsoleFields.ALL.map { it.key }.toSet()
        assertEquals("白名单里有、映射表写不了的键：" + (allowed - mapped), allowed, mapped)
        assertEquals("映射表认、白名单没注册的键：" + (mapped - allowed), mapped, allowed)
    }

    @Test
    fun everyWritableFieldPassesThroughToStorage() {
        for (field in ConsoleFields.ALL.filter { !it.readOnly }) {
            val store = freshStore()
            val port = settings(store)
            val sample = sampleFor(field, port.snapshot()[field.key])
            assertEquals(field.key + " 写入必须被接受", ConsoleWriteResult.OK, port.write(field.key, sample))
            assertEquals(field.key + " 写入后必须从存储读回", sample, readBack(store, field.key))
        }
    }

    @Test
    fun everyReadOnlyFieldIsVisibleButRejectsWrites() {
        for (field in ConsoleFields.ALL.filter { it.readOnly }) {
            val port = settings(freshStore())
            assertTrue(field.key + " 只读字段仍必须出现在快照里", port.snapshot().containsKey(field.key))
            assertEquals(field.key + " 只读字段必须拒写", ConsoleWriteResult.READ_ONLY, port.write(field.key, true))
        }
    }

    @Test
    fun newlyMappedBooleanFieldsReadAndWrite() {
        for (key in NEW_BOOLEAN_KEYS) {
            val store = freshStore()
            val port = settings(store)
            val before = port.snapshot()[key]
            assertTrue(key + " 必须已进白名单", ConsoleFields.field(key) != null)
            assertTrue(key + " 必须是布尔字段", port.snapshot()[key] is Boolean)
            val flipped = !(before as Boolean)
            assertEquals(key + " 写入必须被接受", ConsoleWriteResult.OK, port.write(key, flipped))
            assertEquals(key + " 写入后必须读回", flipped, readBack(store, key))
        }
    }

    @Test
    fun wrongTypeForNewBooleanFieldWritesNothing() {
        for (key in NEW_BOOLEAN_KEYS) {
            val store = freshStore()
            val port = settings(store)
            val before = port.snapshot()[key]
            assertEquals(key + " 字符串不能当开关写", ConsoleWriteResult.READ_ONLY, port.write(key, "true"))
            assertEquals(key + " 类型不符时不得改动存储", before, readBack(store, key))
        }
    }

    @Test
    fun consoleApiAcceptsAnimationsEnabledFromWeb() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        val before = preferences.animationsEnabled
        val body = "{\"animationsEnabled\":" + !before + "}"
        val response = webApi(settings(store)).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(200, response.status)
        val parsed = ConsoleJson.parseObject(response.text())!!
        assertEquals(true, parsed["ok"])
        assertFalse("animationsEnabled 不得再进 ignored", parsed.containsKey("ignored"))
        assertEquals("写入必须落到 AgentPreferences", !before, preferences.animationsEnabled)
        @Suppress("UNCHECKED_CAST")
        val echoed = parsed["settings"] as Map<String, Any?>
        assertEquals("网页必须能读回这一项（否则下次打开是旧值）", !before, echoed["animationsEnabled"])
    }

    @Test
    fun consoleApiRejectsBadTypeForNewFieldWithoutWriting() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        val before = preferences.subAgentEnabled
        val body = "{\"subAgentEnabled\":\"yes\"}"
        val response = webApi(settings(store)).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(400, response.status)
        assertEquals("bad_type", ConsoleJson.parseObject(response.text())!!["error"])
        assertEquals("类型不符时不得落库", before, preferences.subAgentEnabled)
    }

    @Test
    fun webLongTermMemorySwitchWritesMemoryEnabled() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        val before = preferences.memoryEnabled
        val body = "{\"longTermMemoryEnabled\":" + !before + "}"
        val response = webApi(settings(store)).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(200, response.status)
        val parsed = ConsoleJson.parseObject(response.text())!!
        assertFalse("longTermMemoryEnabled 不得进 ignored", parsed.containsKey("ignored"))
        assertEquals("网页开关必须落到 memoryEnabled", !before, preferences.memoryEnabled)
        @Suppress("UNCHECKED_CAST")
        val echoed = parsed["settings"] as Map<String, Any?>
        assertEquals("真实值必须按网页拼写回显", !before, echoed["longTermMemoryEnabled"])
        assertEquals("原有 memoryEnabled 键同时保留", !before, echoed["memoryEnabled"])
    }

    @Test
    fun webProviderProfilesWriteThroughToTheStoredBook() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        val body = """{"providerProfiles":[{"name":"主用","baseUrl":"https://a.example.com","model":"deepseek-chat"},""" +
            """{"name":"备用","baseUrl":"https://b.example.com","model":"deepseek-reasoner"}]}"""
        val parsed = postSettings(store, body)
        assertEquals(true, parsed["ok"])
        assertFalse("providerProfiles 不得再进 ignored", parsed.containsKey("ignored"))

        val book = preferences.providerProfiles()
        assertEquals(2, book.profiles.size)
        assertEquals("主用", book.profiles[0].name)
        assertEquals("https://a.example.com", book.profiles[0].baseUrl)
        assertEquals("deepseek-reasoner", book.profiles[1].modelName)
        assertEquals("第一套成为选中项", book.profiles[0].id, book.activeProfileId)

        @Suppress("UNCHECKED_CAST")
        val echoed = (parsed["settings"] as Map<String, Any?>)["providerProfiles"] as List<Map<String, Any?>>
        assertEquals(2, echoed.size)
        assertEquals("https://b.example.com", echoed[1]["baseUrl"])
        assertEquals("deepseek-reasoner", echoed[1]["model"])
    }

    @Test
    fun webProviderProfilesKeepHiddenCredentialsByName() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        preferences.saveProviderProfiles(
            ProviderProfileBook(
                profiles = listOf(
                    ProviderProfile(
                        id = "keep-id",
                        name = "主用",
                        baseUrl = "https://old.example.com",
                        apiKey = "sk-hidden",
                        modelName = "old-model",
                    )
                ),
                activeProfileId = "keep-id",
            )
        )
        val body = """{"providerProfiles":[{"name":"主用","baseUrl":"https://new.example.com","model":"new-model"},""" +
            """{"name":"新增","baseUrl":"https://c.example.com","model":"extra-model"}]}"""
        val parsed = postSettings(store, body)
        assertEquals(true, parsed["ok"])

        val book = preferences.providerProfiles()
        assertEquals(2, book.profiles.size)
        assertEquals("网页看不到的 id 必须沿用", "keep-id", book.profiles[0].id)
        assertEquals("网页看不到的 apiKey 必须保留", "sk-hidden", book.profiles[0].apiKey)
        assertEquals("https://new.example.com", book.profiles[0].baseUrl)
        assertEquals("新条目沿用当前单值 apiKey", preferences.apiKey, book.profiles[1].apiKey)
        assertEquals("既有选中项必须保留", "keep-id", book.activeProfileId)
    }

    @Test
    fun providerProfilesRejectEntriesThatAreNotObjects() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        val before = preferences.providerProfiles()
        val body = """{"providerProfiles":["不是一个对象"]}"""
        val response = webApi(settings(store)).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(400, response.status)
        assertEquals("bad_type", ConsoleJson.parseObject(response.text())!!["error"])
        assertEquals("整单拒绝时不得改动账本", before.profiles.size, preferences.providerProfiles().profiles.size)
    }

    private fun postSettings(store: FakeSharedPreferences, body: String): Map<String, Any?> {
        val response = webApi(settings(store)).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(200, response.status)
        return ConsoleJson.parseObject(response.text())!!
    }

    /**
     * 反证：同一套请求通道对"白名单外的键"必须回 ignored，证明上面两条绿灯不是恒真。
     *
     * 用作反证样本的键必须同时满足两件事：AgentPreferences 里有真实 getter（才能验证"没落库"），
     * 且不在 ConsoleFields.ALL 里。reasoningEffort 两者都满足；
     * 原先取的 voiceWakeOnScreen 已随设置审计删除（零消费点的假开关）。
     */
    @Test
    fun unregisteredKeyStillLandsInIgnored() {
        val store = freshStore()
        val preferences = AgentPreferences(store)
        val before = preferences.reasoningEffort
        val body = "{\"reasoningEffort\":\"high\"}"
        val response = webApi(settings(store)).handle(
            ConsoleRequest(
                "POST",
                "/api/settings",
                query = mapOf("t" to TOKEN),
                body = body,
                bodyBytes = body.toByteArray(StandardCharsets.UTF_8).size,
            )
        )
        assertEquals(200, response.status)
        assertEquals(listOf("reasoningEffort"), ConsoleJson.parseObject(response.text())!!["ignored"])
        assertEquals("未注册的键不得落库", before, preferences.reasoningEffort)
    }

    private fun webApi(port: ConsoleSettingsPort): ConsoleApi = ConsoleApi(
        settings = port,
        capabilities = { emptyMap() },
        actions = { "" },
        assets = { null },
        token = { TOKEN },
        serverPort = { 8080 },
        uptime = { 0L },
    )

    private companion object {
        const val TOKEN = "1234"

        /** 本轮补齐映射的开关：此前网页改不动、快照也读不到的整批字段。 */
        val NEW_BOOLEAN_KEYS = listOf(
            "animationsEnabled",
            "subAgentEnabled",
            "compactMode",
            "providerManagedSearchEnabled",
            "autoSpeakReplies",
            "asrLiveTranscript",
            "defaultThinkingEnabled",
            "memoryEnabled",
            "autoCompressContext",
        )
    }
}
