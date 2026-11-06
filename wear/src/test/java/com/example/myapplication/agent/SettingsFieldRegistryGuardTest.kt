package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.readProjectFile
import com.example.myapplication.voice.AsrLanguage
import com.example.myapplication.voice.AsrProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 偏好字段登记守护：每个设置字段都要有「命名键常量 + 命名默认值 + 往返读写」三件套。
 *
 * 断言对象是 AgentPreferences.kt 的真实源码与真实读写路径（内存版 SharedPreferences）：
 * 不复制一份字段清单，布尔开关靠反射自动进网 —— 新增开关忘记登记也躲不过。
 */
class SettingsFieldRegistryGuardTest {

    @Test
    fun everyStorageKeyConstantIsReadByAGetter() {
        val names = storageKeyConstants().keys
        assertTrue("KEY_ 常量表异常缩小（当前 " + names.size + " 条），先确认没被改名", names.size >= 20)
        val unread = names.filter { name ->
            !Regex("prefs\\.get[A-Za-z]+\\(\\s*" + name + "\\b").containsMatchIn(preferencesSource())
        }
        assertEquals("KEY_ 常量声明了却没有属性读它（写进去的值永远读不回来）：", emptyList<String>(), unread)
    }

    @Test
    fun noStorageKeyIsSharedByTwoGetters() {
        val keys = storageKeyConstants()
        val used = getterCalls().map { call -> keys[call.keyArgument] ?: call.keyArgument.trim('"') }
        assertEquals("同一个存储键被两个字段读写（后写的会静默覆盖先写的）：", emptySet<String>(), duplicates(used))
    }

    @Test
    fun everyGetterKeyedByAConstantFallsBackToANamedDefault() {
        val keys = storageKeyConstants().keys
        val literalDefaults = getterCalls()
            .filter { it.keyArgument in keys }
            .filter { !namedDefaultPattern.matches(it.fallback) && it.fallback != "null" && it.fallback != "\"\"" }
            .map { it.keyArgument + " 的默认值是裸字面量 " + it.fallback }
        assertEquals("新字段的默认值必须来自 DEFAULT_* 命名常量，禁止裸字面量：", emptyList<String>(), literalDefaults)
    }

    @Test
    fun everyDefaultConstantIsActuallyUsed() {
        val declared = defaultConstantPattern.findAll(preferencesSource()).map { it.groupValues[1] }.toList()
        assertTrue("DEFAULT_ 常量表异常缩小（当前 " + declared.size + " 条）", declared.size >= 15)
        val unused = declared.filter { name -> Regex("\\b" + name + "\\b").findAll(preferencesSource()).count() < 2 }
        assertEquals("DEFAULT_ 常量声明了却没被任何 getter 使用（默认值已经漂走）：", emptyList<String>(), unused)
    }

    @Test
    fun numericDefaultsSitInReasonableRanges() {
        val prefs = emptyPreferences()
        assertTrue("temperature 默认 " + prefs.temperature + " 必须落在 0..2", prefs.temperature in 0f..2f)
        assertTrue(
            "maxOutputTokens 默认 " + prefs.maxOutputTokens + " 必须大于 300（300 会截断正常回复）且不超过 8192",
            prefs.maxOutputTokens in 301..8192
        )
        assertTrue("maxContextTurns 默认 " + prefs.maxContextTurns + " 必须落在 4..200", prefs.maxContextTurns in 4..200)
        assertTrue("matrixSpacingDp 默认 " + prefs.matrixSpacingDp + " 必须落在 4..32", prefs.matrixSpacingDp in 4f..32f)
        assertTrue(
            "asrDevPid 默认 " + prefs.asrDevPid + " 必须是百度真实 dev_pid 档位",
            AsrLanguage.values().any { it.devPid == prefs.asrDevPid }
        )
    }

    @Test
    fun storedValueDefaultsAreValidStoredValues() {
        val prefs = emptyPreferences()
        assertEquals(
            "asrProvider 默认值必须是 AsrProviderId 的 storageKey，否则读取时静默回退",
            prefs.asrProvider,
            AsrProviderId.fromStorage(prefs.asrProvider).storageKey
        )
        assertEquals("reasoningEffort 默认值必须是 ReasoningEffort 的真实档位", ReasoningEffort.DEFAULT_STORAGE, prefs.reasoningEffort)
        assertTrue("baseUrl 默认 " + prefs.baseUrl + " 必须是 http(s) 地址", prefs.baseUrl.startsWith("http"))
        assertTrue("mcpEndpoint 默认 " + prefs.mcpEndpoint + " 必须是 http(s) 地址", prefs.mcpEndpoint.startsWith("http"))
        assertTrue("mainInputType 默认值必须落在 VOICE / KEYBOARD", prefs.mainInputType in setOf("VOICE", "KEYBOARD"))
        assertEquals("themeMode 默认值", ThemeMode.DARK, prefs.themeMode)
        assertEquals("securityMode 默认值", SecurityMode.STANDARD, prefs.securityMode)
        assertEquals("rightButtonAction 默认值", RightButtonAction.MODEL_SWITCH, prefs.rightButtonAction)
    }

    @Test
    fun everyBooleanFlagRoundTripsThroughStorage() {
        val store = FakeSharedPreferences()
        val methods = AgentPreferences::class.java.methods
        val booleanTypes = setOf(java.lang.Boolean::class.java, java.lang.Boolean.TYPE)
        val setters = methods.filter {
            it.name.startsWith("set") && it.parameterCount == 1 && it.parameterTypes[0] in booleanTypes
        }
        assertTrue("反射没扫到布尔开关（当前 " + setters.size + " 个），先确认属性还在", setters.size >= 20)

        val covered = mutableListOf<String>()
        val unpaired = mutableListOf<String>()
        for (setter in setters) {
            val suffix = setter.name.removePrefix("set")
            // is 前缀的布尔属性按 JVM Bean 约定生成 isXxx() + setXxx()，两种读法都要认
            val getter = methods.firstOrNull {
                (it.name == "get" + suffix || it.name == "is" + suffix) && it.parameterCount == 0
            }
            if (getter == null) {
                unpaired += suffix
                continue
            }
            val original = getter.invoke(AgentPreferences(store)) as Boolean
            setter.invoke(AgentPreferences(store), !original)
            assertEquals(
                suffix + " 写入后必须能被新实例读回（键写错或没落盘）",
                !original,
                getter.invoke(AgentPreferences(store)) as Boolean
            )
            setter.invoke(AgentPreferences(store), original)
            assertEquals(suffix + " 复位后必须读回原值", original, getter.invoke(AgentPreferences(store)) as Boolean)
            covered += suffix
        }
        assertEquals("这些布尔属性只有 setter 没有 getter，读不回来：", emptyList<String>(), unpaired)

        val newFlags = listOf(
            "AutoSpeakReplies", "AsrLiveTranscript", "VoiceWakeOnScreen", "VoiceWakeOnLockScreen",
            "ProviderManagedSearchEnabled", "DefaultThinkingEnabled", "MemoryEnabled", "AutoCompressContext",
            "UsePureBlack", "CompactMode", "AnimationsEnabled", "VisionBetaEnabled", "SubAgentEnabled", "McpEnabled"
        )
        assertEquals("这些新增开关必须可读写（不在覆盖集里说明被改名或退化成只读）：", emptyList<String>(), newFlags - covered)
    }

    @Test
    fun compactModeRoundTripsThroughStorage() {
        val store = FakeSharedPreferences()
        assertFalse("compactMode 默认关闭", AgentPreferences(store).compactMode)
        AgentPreferences(store).compactMode = true
        assertTrue("compactMode 写入后必须被新实例读回", AgentPreferences(store).compactMode)
        AgentPreferences(store).compactMode = false
        assertFalse("compactMode 复位后必须读回 false", AgentPreferences(store).compactMode)
        assertEquals("compactMode 的存储键必须与网页字段同名", "compactMode", AgentPreferences.KEY_COMPACT_MODE)
    }

    @Test
    fun animationsEnabledRoundTripsThroughStorage() {
        val store = FakeSharedPreferences()
        assertTrue("animationsEnabled 默认开启", AgentPreferences(store).animationsEnabled)
        AgentPreferences(store).animationsEnabled = false
        assertFalse("animationsEnabled 写入后必须被新实例读回", AgentPreferences(store).animationsEnabled)
        AgentPreferences(store).animationsEnabled = true
        assertTrue("animationsEnabled 复位后必须读回 true", AgentPreferences(store).animationsEnabled)
        assertEquals("animationsEnabled 的存储键必须与网页字段同名", "animationsEnabled", AgentPreferences.KEY_ANIMATIONS_ENABLED)
    }

    @Test
    fun theDuplicateKeyScannerReportsADuplicate() {
        // 三角验证：重复检测不是恒返回空表
        assertEquals(setOf("b"), duplicates(listOf("a", "b", "b", "c")))
        assertEquals(emptySet<String>(), duplicates(listOf("a", "b", "c")))
    }

    private fun emptyPreferences(): AgentPreferences = AgentPreferences(FakeSharedPreferences())

    private fun preferencesSource(): String = readProjectFile(PreferencesPath)

    private fun storageKeyConstants(): Map<String, String> =
        keyConstantPattern.findAll(preferencesSource())
            .associate { it.groupValues[1] to it.groupValues[2] }

    private fun getterCalls(): List<GetterCall> =
        getterCallPattern.findAll(preferencesSource()).map { match ->
            GetterCall(
                keyArgument = match.groupValues[2].trim(),
                fallback = match.groupValues[3].trim()
            )
        }.toList()

    private fun duplicates(values: List<String>): Set<String> =
        values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys

    private data class GetterCall(val keyArgument: String, val fallback: String)

    private companion object {
        const val PreferencesPath = "src/main/java/com/example/myapplication/agent/AgentPreferences.kt"

        val keyConstantPattern = Regex("const val (KEY_[A-Z_0-9]+) = \"([^\"]*)\"")
        val defaultConstantPattern = Regex("const val (DEFAULT_[A-Z_0-9]+) =")
        val getterCallPattern = Regex("prefs\\.(get[A-Za-z]+)\\(\\s*([^,]+),\\s*([^)]+)\\)")
        val namedDefaultPattern = Regex("^[A-Za-z_][A-Za-z0-9_]*([.][A-Za-z_][A-Za-z0-9_]*)*$")
    }
}
