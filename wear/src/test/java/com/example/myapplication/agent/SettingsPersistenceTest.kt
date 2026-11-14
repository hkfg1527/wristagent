package com.example.myapplication.agent

import com.example.myapplication.presentation.ui.settings.HapticAmplitude
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 落盘守门：十二个可调设置每一项都必须**写进自己的键**。
 *
 * 之前每个设置各自有"默认值/夹取/消费点"的测试，但没有一条统一回答"改了到底存哪儿"。
 * 界面点得动、行为也变了，键名却写错（或干脆没写）——这类 bug 只有冷启动后才发现。
 * 这里用内存版 SharedPreferences 把十二个键一次钉住。
 */
class SettingsPersistenceTest {

    private fun prefs() = AgentPreferences(FakeSharedPreferences())

    @Test
    fun everyAdjustableSettingWritesItsOwnKey() {
        val p = prefs()
        p.streamEmitIntervalMs = 48
        p.streamEmitChars = 20
        p.idleSpinStopAfter = 6
        p.memoryMaxFactLength = 80
        p.consoleLockoutMinutes = 5L
        p.subagentMaxDepth = 0
        p.shellCommandTimeoutMs = 30_000L
        p.approvalAllowThreshold = 0.6f
        p.ttsSpeechRate = 1.4f
        p.ttsVolume = 0.5f
        p.ttsQueueMode = TtsQueueMode.QUEUE
        p.rotaryScrollGain = 2f
        p.hapticAmplitude = HapticAmplitude.STRONG
        p.toolResultMaxChars = 12_000
        p.bottomBarAutoHideMs = 5_000L
        p.samplingTopP = 0.8f
        p.mcpMaxToolPages = 8
        p.subagentReadTimeoutMs = 90_000L
        p.netDiagTimeoutMs = 5_000
        p.consoleMaxFailures = 7

        // 逐个回读：写进去的值必须原样读得出来（夹取范围内）
        assertEquals(48, p.streamEmitIntervalMs)
        assertEquals(20, p.streamEmitChars)
        assertEquals(6, p.idleSpinStopAfter)
        assertEquals(80, p.memoryMaxFactLength)
        assertEquals(5L, p.consoleLockoutMinutes)
        assertEquals(0, p.subagentMaxDepth)
        assertEquals(30_000L, p.shellCommandTimeoutMs)
        assertEquals(0.6f, p.approvalAllowThreshold, 0.0001f)
        assertEquals(1.4f, p.ttsSpeechRate, 0.0001f)
        assertEquals(0.5f, p.ttsVolume, 0.0001f)
        assertEquals(TtsQueueMode.QUEUE, p.ttsQueueMode)
        assertEquals(2f, p.rotaryScrollGain, 0.0001f)
        assertEquals(HapticAmplitude.STRONG, p.hapticAmplitude)
        assertEquals(12_000, p.toolResultMaxChars)
        assertEquals(5_000L, p.bottomBarAutoHideMs)
        assertEquals(0.8f, p.samplingTopP, 0.0001f)
        assertEquals(8, p.mcpMaxToolPages)
        assertEquals(90_000L, p.subagentReadTimeoutMs)
        assertEquals(5_000, p.netDiagTimeoutMs)
        assertEquals(7, p.consoleMaxFailures)
    }

    @Test
    fun theKeyNamesAreTheDocumentedOnes() {
        // 键名是跨版本契约：网页控制台、导入导出、以及用户的既有数据都按这些名字找值
        assertEquals("stream_emit_interval_ms", AgentPreferences.KEY_STREAM_EMIT_INTERVAL_MS)
        assertEquals("shell_command_timeout_ms", AgentPreferences.KEY_SHELL_COMMAND_TIMEOUT_MS)
        assertEquals("approval_allow_threshold", AgentPreferences.KEY_APPROVAL_ALLOW_THRESHOLD)
        assertEquals("tts_speech_rate", AgentPreferences.KEY_TTS_SPEECH_RATE)
        assertEquals("tts_volume", AgentPreferences.KEY_TTS_VOLUME)
        assertEquals("tts_queue_mode", AgentPreferences.KEY_TTS_QUEUE_MODE)
        assertEquals("rotary_scroll_gain", AgentPreferences.KEY_ROTARY_SCROLL_GAIN)
        assertEquals("haptic_amplitude", AgentPreferences.KEY_HAPTIC_AMPLITUDE)
        assertEquals("tool_result_max_chars", AgentPreferences.KEY_TOOL_RESULT_MAX_CHARS)
        assertEquals("bottom_bar_auto_hide_ms", AgentPreferences.KEY_BOTTOM_BAR_AUTO_HIDE_MS)
        assertEquals("idle_spin_stop_after", AgentPreferences.KEY_IDLE_SPIN_STOP_AFTER)
        assertEquals("memory_max_fact_length", AgentPreferences.KEY_MEMORY_MAX_FACT_LENGTH)
        assertEquals("console_lockout_minutes", AgentPreferences.KEY_CONSOLE_LOCKOUT_MINUTES)
        assertEquals("subagent_max_depth", AgentPreferences.KEY_SUBAGENT_MAX_DEPTH)
        assertEquals("sampling_top_p", AgentPreferences.KEY_SAMPLING_TOP_P)
        assertEquals("mcp_max_tool_pages", AgentPreferences.KEY_MCP_MAX_TOOL_PAGES)
        assertEquals("subagent_read_timeout_ms", AgentPreferences.KEY_SUBAGENT_READ_TIMEOUT_MS)
        assertEquals("net_diag_timeout_ms", AgentPreferences.KEY_NET_DIAG_TIMEOUT_MS)
        assertEquals("console_max_failures", AgentPreferences.KEY_CONSOLE_MAX_FAILURES)
    }

    @Test
    fun unknownStoredEnumNamesFallBackInsteadOfCrashing() {
        val p = AgentPreferences(FakeSharedPreferences(mutableMapOf("tts_queue_mode" to "SOMETHING_ELSE", "haptic_amplitude" to "X")))
        assertEquals(TtsQueueMode.INTERRUPT, p.ttsQueueMode)
        assertEquals(HapticAmplitude.STANDARD, p.hapticAmplitude)
    }
}
