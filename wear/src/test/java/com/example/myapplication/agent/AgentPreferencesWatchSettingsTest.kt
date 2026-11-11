package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 新增手表设置项的默认值与写入读回。
 *
 * 用内存版 SharedPreferences 注入 [AgentPreferences]：测的是真实属性读写路径，不打桩被测算逻辑。
 */
class AgentPreferencesWatchSettingsTest {

    @Test
    fun defaultsAreExplicitAndBatterySafe() {
        assertFalse(AgentPreferences.DEFAULT_AUTO_SPEAK_REPLIES)
        assertTrue(AgentPreferences.DEFAULT_ASR_LIVE_TRANSCRIPT)
        assertFalse(AgentPreferences.DEFAULT_PROVIDER_MANAGED_SEARCH)
        assertTrue(AgentPreferences.DEFAULT_DEFAULT_THINKING)
        assertTrue(AgentPreferences.DEFAULT_MEMORY_ENABLED)
        assertTrue(AgentPreferences.DEFAULT_AUTO_COMPRESS_CONTEXT)
        assertFalse(AgentPreferences.DEFAULT_COMPACT_MODE)
    }

    @Test
    fun storageKeysMatchConsoleFieldNames() {
        assertEquals("autoSpeakReplies", AgentPreferences.KEY_AUTO_SPEAK_REPLIES)
        assertEquals("asrLiveTranscript", AgentPreferences.KEY_ASR_LIVE_TRANSCRIPT)
        assertEquals("providerManagedSearchEnabled", AgentPreferences.KEY_PROVIDER_MANAGED_SEARCH)
        assertEquals("defaultThinking", AgentPreferences.KEY_DEFAULT_THINKING)
        assertEquals("memoryEnabled", AgentPreferences.KEY_MEMORY_ENABLED)
        assertEquals("autoCompressContext", AgentPreferences.KEY_AUTO_COMPRESS_CONTEXT)
        assertEquals("compactMode", AgentPreferences.KEY_COMPACT_MODE)
        // 本轮补全的三档：键名与网页控制台字段逐字一致，两侧共用一份真值
        assertEquals("historyCompressThreshold", AgentPreferences.KEY_HISTORY_COMPRESS_THRESHOLD)
        assertEquals("toolApprovalPolicy", AgentPreferences.KEY_TOOL_APPROVAL_POLICY)
        assertEquals("sandboxLevel", AgentPreferences.KEY_SANDBOX_LEVEL)
    }

    @Test
    fun everySwitchStartsAtItsDocumentedDefault() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        flagSpecs.forEach { spec ->
            assertEquals(spec.name + " 默认值", spec.default, spec.read(prefs))
        }
    }

    @Test
    fun everySwitchRoundTripsThroughStorage() {
        flagSpecs.forEach { spec ->
            val prefs = AgentPreferences(FakeSharedPreferences())
            val flipped = !spec.default
            spec.write(prefs, flipped)
            assertEquals(spec.name + " 写入后读回", flipped, spec.read(prefs))
            spec.write(prefs, spec.default)
            assertEquals(spec.name + " 复位后读回", spec.default, spec.read(prefs))
        }
    }

    @Test
    fun flagsSurviveANewRepositoryInstance() {
        val store = FakeSharedPreferences()
        AgentPreferences(store).memoryEnabled = false
        AgentPreferences(store).autoCompressContext = false
        val reopened = AgentPreferences(store)
        assertFalse(reopened.memoryEnabled)
        assertFalse(reopened.autoCompressContext)
    }

    @Test
    fun newTriStatesDefaultToTheirDocumentedValues() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        assertEquals(ToolApprovalPolicy.RISKY, prefs.toolApprovalPolicy)
        assertEquals(SandboxLevel.FULL, prefs.sandboxLevel)
        assertEquals(CompressPolicy.DefaultThreshold, prefs.historyCompressThreshold, 0.0001f)
        assertEquals(0.8f, prefs.historyCompressThreshold, 0.0001f)
    }

    @Test
    fun everyTriStateRoundTripsThroughStorage() {
        val store = FakeSharedPreferences()
        ToolApprovalPolicy.values().forEach { policy ->
            AgentPreferences(store).toolApprovalPolicy = policy
            assertEquals(policy.name + " 没被读回", policy, AgentPreferences(store).toolApprovalPolicy)
        }
        SandboxLevel.values().forEach { level ->
            AgentPreferences(store).sandboxLevel = level
            assertEquals(level.name + " 没被读回", level, AgentPreferences(store).sandboxLevel)
        }
        GestureAction.values().forEach { action ->
            AgentPreferences(store).longPressAction = action
            AgentPreferences(store).doubleTapAction = action
            AgentPreferences(store).sideKeyShortAction = action
            AgentPreferences(store).sideKeyLongAction = action
            val reopened = AgentPreferences(store)
            assertEquals("longPress " + action.name, action, reopened.longPressAction)
            assertEquals("doubleTap " + action.name, action, reopened.doubleTapAction)
            assertEquals("sideKeyShort " + action.name, action, reopened.sideKeyShortAction)
            assertEquals("sideKeyLong " + action.name, action, reopened.sideKeyLongAction)
        }
    }

    @Test
    fun unknownStoredValuesFallBackToTheDocumentedDefaults() {
        val store = FakeSharedPreferences()
        AgentPreferences(store).toolApprovalPolicy = ToolApprovalPolicy.NEVER
        AgentPreferences(store).sandboxLevel = SandboxLevel.READ_ONLY
        AgentPreferences(store).longPressAction = GestureAction.STOP_TURN
        // 直接改盘上的原值：模拟旧版本 / 别的端写入一个本端不认识的字符串
        val raw = store.edit()
        raw.putString(AgentPreferences.KEY_TOOL_APPROVAL_POLICY, "SOMETIMES")
        raw.putString(AgentPreferences.KEY_SANDBOX_LEVEL, "JAIL")
        raw.putString(AgentPreferences.KEY_LONG_PRESS_ACTION, "FIRE_THE_MISSILES")
        raw.apply()

        val prefs = AgentPreferences(store)
        assertEquals(ToolApprovalPolicy.RISKY, prefs.toolApprovalPolicy)
        assertEquals(SandboxLevel.FULL, prefs.sandboxLevel)
        assertEquals(GestureAction.NONE, prefs.longPressAction)
    }

    @Test
    fun compressThresholdIsClampedOnWrite() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.historyCompressThreshold = 0.1f
        assertEquals("下界以外一律夹到 0.3", CompressPolicy.MinThreshold, prefs.historyCompressThreshold, 0.0001f)
        prefs.historyCompressThreshold = 5f
        assertEquals("上界以外一律夹到 0.95", CompressPolicy.MaxThreshold, prefs.historyCompressThreshold, 0.0001f)
        prefs.historyCompressThreshold = Float.NaN
        assertEquals("NaN 回落到默认值", CompressPolicy.DefaultThreshold, prefs.historyCompressThreshold, 0.0001f)
        prefs.historyCompressThreshold = 0.55f
        assertEquals("合法值原样落盘", 0.55f, prefs.historyCompressThreshold, 0.0001f)
    }

    @Test
    fun gestureBindingsSnapshotReadsAllFourAtOnce() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.longPressAction = GestureAction.STOP_TURN
        prefs.sideKeyLongAction = GestureAction.SWITCH_MODEL
        val bindings = prefs.gestureBindings()
        assertEquals(GestureAction.STOP_TURN, bindings.longPress)
        assertEquals(GestureAction.NONE, bindings.doubleTap)
        assertEquals(GestureAction.NONE, bindings.sideKeyShort)
        assertEquals(GestureAction.SWITCH_MODEL, bindings.sideKeyLong)
    }

    /** 新增布尔项的读写定义：名称 / 默认值 / 写 / 读。 */
    private class FlagSpec(
        val name: String,
        val default: Boolean,
        val write: (AgentPreferences, Boolean) -> Unit,
        val read: (AgentPreferences) -> Boolean
    )

    private val flagSpecs = listOf(
        FlagSpec("autoSpeakReplies", AgentPreferences.DEFAULT_AUTO_SPEAK_REPLIES, { p, v -> p.autoSpeakReplies = v }, { p -> p.autoSpeakReplies }),
        FlagSpec("asrLiveTranscript", AgentPreferences.DEFAULT_ASR_LIVE_TRANSCRIPT, { p, v -> p.asrLiveTranscript = v }, { p -> p.asrLiveTranscript }),
        FlagSpec("providerManagedSearchEnabled", AgentPreferences.DEFAULT_PROVIDER_MANAGED_SEARCH, { p, v -> p.providerManagedSearchEnabled = v }, { p -> p.providerManagedSearchEnabled }),
        FlagSpec("defaultThinkingEnabled", AgentPreferences.DEFAULT_DEFAULT_THINKING, { p, v -> p.defaultThinkingEnabled = v }, { p -> p.defaultThinkingEnabled }),
        FlagSpec("memoryEnabled", AgentPreferences.DEFAULT_MEMORY_ENABLED, { p, v -> p.memoryEnabled = v }, { p -> p.memoryEnabled }),
        FlagSpec("autoCompressContext", AgentPreferences.DEFAULT_AUTO_COMPRESS_CONTEXT, { p, v -> p.autoCompressContext = v }, { p -> p.autoCompressContext }),
        FlagSpec("compactMode", AgentPreferences.DEFAULT_COMPACT_MODE, { p, v -> p.compactMode = v }, { p -> p.compactMode })
    )
}
