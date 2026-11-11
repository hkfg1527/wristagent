package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.rootSnapshot
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 根页缓存的回读契约：清空记忆（[AgentPreferences.resetAll] = prefs.clear()）之后，
 * 根页必须从存储重新读一遍，否则屏上旧值会在下一次点击时被写回存储 —— 清空失效。
 *
 * 三条防线：快照与 getter 逐字段一致、清空后快照必须变、源码里"缓存初始化用到的每个字段都必须
 * 在 rootSnapshot() 里回读 + 子页收起必须触发回读"。
 */
class SettingsRootSnapshotTest {

    @Test
    fun snapshotMirrorsEveryPreferenceTheRootPageCaches() {
        val prefs = populated()
        val snapshot = prefs.rootSnapshot()

        assertEquals("custom-model-x", snapshot.modelName)
        assertEquals("https://example.invalid/v1", snapshot.baseUrl)
        assertEquals(SecurityMode.EXPERT, snapshot.securityMode)
        assertEquals(GestureAction.STOP_TURN, snapshot.gestureBindings.longPress)
        assertEquals(GestureAction.SWITCH_MODEL, snapshot.gestureBindings.sideKeyLong)
        assertEquals("http://127.0.0.1:9999/sse", snapshot.mcpEndpoint)
        assertFalse(snapshot.isMcpEnabled)
        assertEquals("you are a test", snapshot.customSystemPrompt)
        assertEquals(0.3f, snapshot.temperature, 0.001f)
        assertEquals(150, snapshot.maxOutputTokens)
        assertFalse(snapshot.streamMode)
        assertFalse(snapshot.animationsEnabled)
        assertTrue(snapshot.asrEnabled)
        assertEquals("asr-provider-x", snapshot.asrProviderKey)
        assertTrue(snapshot.asrKeyReady)

        // 快照只能"读"，不能变成第二份真值：逐字段再与 getter 对一遍
        assertEquals(prefs.modelName, snapshot.modelName)
        assertEquals(prefs.baseUrl, snapshot.baseUrl)
        assertEquals(prefs.securityMode, snapshot.securityMode)
        assertEquals(prefs.gestureBindings(), snapshot.gestureBindings)
        assertEquals(prefs.mcpEndpoint, snapshot.mcpEndpoint)
        assertEquals(prefs.isMcpEnabled, snapshot.isMcpEnabled)
        assertEquals(prefs.customSystemPrompt, snapshot.customSystemPrompt)
        assertEquals(prefs.temperature, snapshot.temperature, 0.001f)
        assertEquals(prefs.maxOutputTokens, snapshot.maxOutputTokens)
        assertEquals(prefs.streamMode, snapshot.streamMode)
        assertEquals(prefs.animationsEnabled, snapshot.animationsEnabled)
        assertEquals(prefs.asrEnabled, snapshot.asrEnabled)
        assertEquals(prefs.asrProvider, snapshot.asrProviderKey)
        assertEquals(prefs.asrSettings().hasBaiduCredentials, snapshot.asrKeyReady)
        assertEquals(prefs.providerProfiles(), snapshot.providerBook)
    }

    /**
     * 回归阀门：清空之后重新回读必须拿到默认值，而不是清空前缓存的那一批。
     * 快照第二次读到的仍等于旧值，就是"清空没生效"这个 bug 本身。
     */
    @Test
    fun snapshotFollowsResetAllInsteadOfRepeatingTheStaleValues() {
        val prefs = populated()
        val beforeClear = prefs.rootSnapshot()

        prefs.resetAll()
        val afterClear = prefs.rootSnapshot()

        assertTrue("modelName 没跟着清空", beforeClear.modelName != afterClear.modelName)
        assertTrue("提示词没跟着清空", beforeClear.customSystemPrompt != afterClear.customSystemPrompt)
        assertTrue("温度没跟着清空", beforeClear.temperature != afterClear.temperature)
        assertTrue("Token 档位没跟着清空", beforeClear.maxOutputTokens != afterClear.maxOutputTokens)
        assertTrue("安全模式没跟着清空", beforeClear.securityMode != afterClear.securityMode)
        assertTrue("MCP 开关没跟着清空", beforeClear.isMcpEnabled != afterClear.isMcpEnabled)
        assertTrue("语音开关没跟着清空", beforeClear.asrEnabled != afterClear.asrEnabled)
        assertTrue("整份快照清空前后相同，说明回读拿到的还是旧值", beforeClear != afterClear)

        // 回读结果 == 存储里的真值（getter），默认值以 getter 为准，测试不二次硬编码
        assertEquals(prefs.modelName, afterClear.modelName)
        assertEquals(0.7f, afterClear.temperature, 0.001f)
        assertEquals(AgentPreferences.DEFAULT_MAX_OUTPUT_TOKENS, afterClear.maxOutputTokens)
        assertEquals(SecurityMode.STANDARD, afterClear.securityMode)
        assertEquals("", afterClear.customSystemPrompt)
    }

    /** 结构守护：根页每个 remember 缓存的 preferences.X 都必须在 rootSnapshot() 里回读。 */
    @Test
    fun everyCachedPreferenceIsReadBackByTheSnapshot() {
        val source = readSettingsScreen()
        val cached = cacheInit.findAll(source).map { it.groupValues[1] }.toSet()
        val readBack = source.substringAfter("fun AgentPreferences.rootSnapshot()")
            .substringBefore("\n}")

        assertTrue("没扫到根页缓存初始化，扫描正则已失效", cached.size >= 15)
        val missing = cached.filterNot { readBack.contains(it) }
        assertEquals(
            "这些缓存没在 rootSnapshot() 里回读，清空记忆后会停在旧值：",
            emptyList<String>(),
            missing
        )
    }

    /** 结构守护：子页 / 浮层收起与嵌套「清空记忆」页返回都必须触发回读。 */
    @Test
    fun rootPageResyncsItsCacheWhenASubPageCloses() {
        val source = readSettingsScreen()

        assertTrue(
            "activeDialog / showVoiceAsr 变化后没有调用 syncFromPreferences()",
            Regex("""LaunchedEffect\(activeDialog, showVoiceAsr\) \{\s*syncFromPreferences\(\)""")
                .containsMatchIn(source)
        )
        assertTrue(
            "嵌套的「清空记忆」页返回没有触发回读",
            Regex("""memoryPage = \{ dismiss ->[\s\S]*?syncFromPreferences\(\)[\s\S]*?dismiss\(\)""")
                .containsMatchIn(source)
        )
    }

    private fun populated(): AgentPreferences = AgentPreferences(FakeSharedPreferences()).apply {
        modelName = "custom-model-x"
        baseUrl = "https://example.invalid/v1"
        securityMode = SecurityMode.EXPERT
        longPressAction = GestureAction.STOP_TURN
        sideKeyLongAction = GestureAction.SWITCH_MODEL
        mcpEndpoint = "http://127.0.0.1:9999/sse"
        isMcpEnabled = false
        customSystemPrompt = "you are a test"
        temperature = 0.3f
        maxOutputTokens = 150
        streamMode = false
        animationsEnabled = false
        asrEnabled = true
        asrProvider = "asr-provider-x"
        baiduApiKey = "ak"
        baiduSecretKey = "sk"
    }

    private fun readSettingsScreen(): String = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/SettingsScreen.kt"
    )

    private companion object {
        val cacheInit =
            Regex("by remember \\{ mutable(?:State|FloatState|IntState)Of\\(preferences\\.([A-Za-z]+)")
    }
}
