package com.example.myapplication.agent

import android.content.SharedPreferences
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
        assertFalse(AgentPreferences.DEFAULT_VOICE_WAKE_ON_SCREEN)
        assertFalse(AgentPreferences.DEFAULT_VOICE_WAKE_ON_LOCK)
        assertFalse(AgentPreferences.DEFAULT_PROVIDER_MANAGED_SEARCH)
        assertTrue(AgentPreferences.DEFAULT_DEFAULT_THINKING)
        assertTrue(AgentPreferences.DEFAULT_MEMORY_ENABLED)
        assertTrue(AgentPreferences.DEFAULT_AUTO_COMPRESS_CONTEXT)
        assertTrue(AgentPreferences.DEFAULT_USE_PURE_BLACK)
        assertFalse(AgentPreferences.DEFAULT_COMPACT_MODE)
        assertEquals(ThemeMode.DARK, AgentPreferences.DEFAULT_THEME_MODE)
    }

    @Test
    fun storageKeysMatchConsoleFieldNames() {
        assertEquals("autoSpeakReplies", AgentPreferences.KEY_AUTO_SPEAK_REPLIES)
        assertEquals("asrLiveTranscript", AgentPreferences.KEY_ASR_LIVE_TRANSCRIPT)
        assertEquals("voiceWakeOnScreen", AgentPreferences.KEY_VOICE_WAKE_ON_SCREEN)
        assertEquals("voiceWakeOnLockScreen", AgentPreferences.KEY_VOICE_WAKE_ON_LOCK)
        assertEquals("providerManagedSearchEnabled", AgentPreferences.KEY_PROVIDER_MANAGED_SEARCH)
        assertEquals("defaultThinking", AgentPreferences.KEY_DEFAULT_THINKING)
        assertEquals("memoryEnabled", AgentPreferences.KEY_MEMORY_ENABLED)
        assertEquals("autoCompressContext", AgentPreferences.KEY_AUTO_COMPRESS_CONTEXT)
        assertEquals("usePureBlack", AgentPreferences.KEY_USE_PURE_BLACK)
        assertEquals("themeMode", AgentPreferences.KEY_THEME_MODE)
        assertEquals("compactMode", AgentPreferences.KEY_COMPACT_MODE)
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
    fun themeModeMapsEveryStoredKey() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorage("SYSTEM"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorage("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(null))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage(""))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("dark"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorage("紫罗兰"))
        assertEquals("SYSTEM", ThemeMode.SYSTEM.storageKey)
    }

    @Test
    fun themeModeRoundTripsThroughStorage() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        prefs.themeMode = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)
        prefs.themeMode = ThemeMode.SYSTEM
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
    }

    /** 11 个新增布尔项的读写定义：名称 / 默认值 / 写 / 读。 */
    private class FlagSpec(
        val name: String,
        val default: Boolean,
        val write: (AgentPreferences, Boolean) -> Unit,
        val read: (AgentPreferences) -> Boolean
    )

    private val flagSpecs = listOf(
        FlagSpec("autoSpeakReplies", AgentPreferences.DEFAULT_AUTO_SPEAK_REPLIES, { p, v -> p.autoSpeakReplies = v }, { p -> p.autoSpeakReplies }),
        FlagSpec("asrLiveTranscript", AgentPreferences.DEFAULT_ASR_LIVE_TRANSCRIPT, { p, v -> p.asrLiveTranscript = v }, { p -> p.asrLiveTranscript }),
        FlagSpec("voiceWakeOnScreen", AgentPreferences.DEFAULT_VOICE_WAKE_ON_SCREEN, { p, v -> p.voiceWakeOnScreen = v }, { p -> p.voiceWakeOnScreen }),
        FlagSpec("voiceWakeOnLockScreen", AgentPreferences.DEFAULT_VOICE_WAKE_ON_LOCK, { p, v -> p.voiceWakeOnLockScreen = v }, { p -> p.voiceWakeOnLockScreen }),
        FlagSpec("providerManagedSearchEnabled", AgentPreferences.DEFAULT_PROVIDER_MANAGED_SEARCH, { p, v -> p.providerManagedSearchEnabled = v }, { p -> p.providerManagedSearchEnabled }),
        FlagSpec("defaultThinkingEnabled", AgentPreferences.DEFAULT_DEFAULT_THINKING, { p, v -> p.defaultThinkingEnabled = v }, { p -> p.defaultThinkingEnabled }),
        FlagSpec("memoryEnabled", AgentPreferences.DEFAULT_MEMORY_ENABLED, { p, v -> p.memoryEnabled = v }, { p -> p.memoryEnabled }),
        FlagSpec("autoCompressContext", AgentPreferences.DEFAULT_AUTO_COMPRESS_CONTEXT, { p, v -> p.autoCompressContext = v }, { p -> p.autoCompressContext }),
        FlagSpec("usePureBlack", AgentPreferences.DEFAULT_USE_PURE_BLACK, { p, v -> p.usePureBlack = v }, { p -> p.usePureBlack }),
        FlagSpec("compactMode", AgentPreferences.DEFAULT_COMPACT_MODE, { p, v -> p.compactMode = v }, { p -> p.compactMode })
    )
}

/** 内存版 SharedPreferences：只覆盖 AgentPreferences 用到的布尔/字符串读写。 */
internal class FakeSharedPreferences(
    private val map: MutableMap<String, Any?> = mutableMapOf()
) : SharedPreferences {

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()

    override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues

    override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue

    override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue

    override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue

    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clearAll = false

        override fun putString(key: String?, value: String?): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor = also { pending[key!!] = values }

        override fun putInt(key: String?, value: Int): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putLong(key: String?, value: Long): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = also { pending[key!!] = value }

        override fun remove(key: String?): SharedPreferences.Editor = also { removals += key!! }

        override fun clear(): SharedPreferences.Editor = also { clearAll = true }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() = flush()

        private fun flush() {
            if (clearAll) map.clear()
            removals.forEach { map.remove(it) }
            pending.forEach { (key, value) -> map[key] = value }
        }
    }
}
