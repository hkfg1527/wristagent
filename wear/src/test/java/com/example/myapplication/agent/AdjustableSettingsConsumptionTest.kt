package com.example.myapplication.agent

import com.example.myapplication.presentation.ui.ApprovalSliderLogic
import com.example.myapplication.presentation.ui.StreamingThrottle
import com.example.myapplication.presentation.ui.StreamingThrottleState
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 四个"真实可调"数值项的完整闭环：默认值 / 夹取边界 / 消费点真的读到了它。
 *
 * 每个消费断言都用**生产路径上的同一个函数**（[StreamingThrottleState.updateInterval]、
 * [ShellPolicy.clampTimeout] 的 fallback 实参、[ApprovalSliderLogic.shouldAllow] 的两参重载、
 * [ReplySpeaker.speechRateFor]），不是测试里另抄一份换算。
 */
class AdjustableSettingsConsumptionTest {

    private fun emptyPreferences(): AgentPreferences = AgentPreferences(FakeSharedPreferences())

    // ==================== 1. stream_emit_interval_ms ====================

    @Test
    fun streamEmitIntervalDefaultsToTheThrottleWindow() {
        val prefs = emptyPreferences()
        assertEquals("storage key", "stream_emit_interval_ms", AgentPreferences.KEY_STREAM_EMIT_INTERVAL_MS)
        assertEquals(32, AgentPreferences.DEFAULT_STREAM_EMIT_INTERVAL_MS)
        assertEquals(32, prefs.streamEmitIntervalMs)
        assertEquals(
            "默认值必须就是节流器的出厂窗口，升级不改行为",
            StreamingThrottle.DEFAULT_INTERVAL_MS,
            prefs.streamEmitIntervalMs.toLong()
        )
    }

    @Test
    fun streamEmitIntervalClampsOnBothSides() {
        val prefs = emptyPreferences()
        prefs.streamEmitIntervalMs = 1
        assertEquals(AgentPreferences.MinStreamEmitIntervalMs, prefs.streamEmitIntervalMs)
        prefs.streamEmitIntervalMs = 9_999
        assertEquals(AgentPreferences.MaxStreamEmitIntervalMs, prefs.streamEmitIntervalMs)

        // 读侧同样夹紧：盘上被写坏的越界值不会让节流窗口失真
        val stale = AgentPreferences(
            FakeSharedPreferences(mutableMapOf<String, Any?>("stream_emit_interval_ms" to 0))
        )
        assertEquals(AgentPreferences.MinStreamEmitIntervalMs, stale.streamEmitIntervalMs)
    }

    @Test
    fun streamEmitIntervalMovesTheRealThrottleWindow() {
        val prefs = emptyPreferences()
        val throttle = StreamingThrottleState()
        // ChatViewModel 每轮开场做的同一件事
        throttle.updateInterval(prefs.streamEmitIntervalMs.toLong())
        assertEquals(32L, throttle.currentIntervalMs)

        assertTrue(throttle.accept(newText = "字", nowMs = 1_000L))
        assertFalse("10ms 不足默认 32ms 窗口，必须被节流", throttle.accept(newText = "字字", nowMs = 1_010L))

        // 把窗口调到 8ms：同一帧间隔（10ms）现在必须放行
        prefs.streamEmitIntervalMs = 8
        throttle.updateInterval(prefs.streamEmitIntervalMs.toLong())
        assertTrue("调快之后同一帧必须立刻放行", throttle.accept(newText = "字字", nowMs = 1_010L))

        // 反向三角：调到 120ms 后，119ms 的间隔重新被挡，满 120ms 才放行
        prefs.streamEmitIntervalMs = 120
        throttle.updateInterval(prefs.streamEmitIntervalMs.toLong())
        assertFalse(throttle.accept(newText = "字字字", nowMs = 1_129L))
        assertTrue(throttle.accept(newText = "字字字", nowMs = 1_130L))
    }

    // ==================== 2. shell_command_timeout_ms ====================

    @Test
    fun shellCommandTimeoutDefaultsToTheShellPolicyLimit() {
        val prefs = emptyPreferences()
        assertEquals("storage key", "shell_command_timeout_ms", AgentPreferences.KEY_SHELL_COMMAND_TIMEOUT_MS)
        assertEquals(15_000L, AgentPreferences.DEFAULT_SHELL_COMMAND_TIMEOUT_MS)
        assertEquals(15_000L, prefs.shellCommandTimeoutMs)
        assertEquals(
            "默认值必须就是 run_shell 的既有上限",
            ShellPolicy.DEFAULT_TIMEOUT_MS,
            prefs.shellCommandTimeoutMs
        )
    }

    @Test
    fun shellCommandTimeoutClampsToTheShellPolicyRange() {
        val prefs = emptyPreferences()
        prefs.shellCommandTimeoutMs = 1L
        assertEquals(ShellPolicy.MIN_TIMEOUT_MS, prefs.shellCommandTimeoutMs)
        prefs.shellCommandTimeoutMs = 999_999L
        assertEquals(ShellPolicy.MAX_TIMEOUT_MS, prefs.shellCommandTimeoutMs)

        val stale = AgentPreferences(
            FakeSharedPreferences(mutableMapOf<String, Any?>("shell_command_timeout_ms" to 99_999_999L))
        )
        assertEquals(ShellPolicy.MAX_TIMEOUT_MS, stale.shellCommandTimeoutMs)
    }

    @Test
    fun shellCommandTimeoutBecomesTheRunShellFallback() {
        val prefs = emptyPreferences()
        assertEquals(
            "模型没给超时时，落到的就是设置值",
            ShellPolicy.DEFAULT_TIMEOUT_MS,
            ShellPolicy.clampTimeout(null, prefs.shellCommandTimeoutMs)
        )

        prefs.shellCommandTimeoutMs = 45_000L
        assertEquals(
            "调大之后同一次 run_shell 的上限跟着变",
            45_000L,
            ShellPolicy.clampTimeout(null, prefs.shellCommandTimeoutMs)
        )

        // 显式 timeout 仍然优先于设置值；越界仍按 MIN/MAX 夹紧
        assertEquals(5_000L, ShellPolicy.clampTimeout(5_000L, prefs.shellCommandTimeoutMs))
        assertEquals(ShellPolicy.MAX_TIMEOUT_MS, ShellPolicy.clampTimeout(null, 999_999L))
    }

    // ==================== 3. approval_allow_threshold ====================

    @Test
    fun approvalAllowThresholdDefaultsToTheSliderDesignValue() {
        val prefs = emptyPreferences()
        assertEquals("storage key", "approval_allow_threshold", AgentPreferences.KEY_APPROVAL_ALLOW_THRESHOLD)
        assertEquals(0.85f, AgentPreferences.DEFAULT_APPROVAL_ALLOW_THRESHOLD, 0f)
        assertEquals(0.85f, prefs.approvalAllowThreshold, 0f)
        assertEquals(
            "默认值必须就是滑杆的设计阈值，两处不许漂",
            ApprovalSliderLogic.ALLOW_THRESHOLD,
            prefs.approvalAllowThreshold,
            0f
        )
    }

    @Test
    fun approvalAllowThresholdClampsToItsRange() {
        val prefs = emptyPreferences()
        prefs.approvalAllowThreshold = 0.1f
        assertEquals(AgentPreferences.MinApprovalAllowThreshold, prefs.approvalAllowThreshold, 0f)
        prefs.approvalAllowThreshold = 5f
        assertEquals(AgentPreferences.MaxApprovalAllowThreshold, prefs.approvalAllowThreshold, 0f)

        val stale = AgentPreferences(
            FakeSharedPreferences(mutableMapOf<String, Any?>("approval_allow_threshold" to 0.999f))
        )
        assertEquals(AgentPreferences.MaxApprovalAllowThreshold, stale.approvalAllowThreshold, 0f)
    }

    @Test
    fun approvalAllowThresholdMovesTheSliderDecision() {
        val prefs = emptyPreferences()
        // 90% 的位移在默认 0.85 阈值下放行
        assertTrue(ApprovalSliderLogic.shouldAllow(0.90f, prefs.approvalAllowThreshold))

        prefs.approvalAllowThreshold = 0.98f
        assertFalse(
            "阈值抬到 0.98 后，同一段 90% 位移必须改为拒绝",
            ApprovalSliderLogic.shouldAllow(0.90f, prefs.approvalAllowThreshold)
        )

        prefs.approvalAllowThreshold = 0.5f
        assertTrue(
            "阈值降到 0.5 后，只要拖过一半就放行",
            ApprovalSliderLogic.shouldAllow(0.52f, prefs.approvalAllowThreshold)
        )
        // 单参重载仍是设计阈值，既有行为没有被改写
        assertFalse(ApprovalSliderLogic.shouldAllow(0.52f))
        assertTrue(ApprovalSliderLogic.shouldAllow(0.85f))
    }

    // ==================== 4. tts_speech_rate ====================

    @Test
    fun ttsSpeechRateDefaultsToEngineNormalSpeed() {
        val prefs = emptyPreferences()
        assertEquals("storage key", "tts_speech_rate", AgentPreferences.KEY_TTS_SPEECH_RATE)
        assertEquals(1.0f, AgentPreferences.DEFAULT_TTS_SPEECH_RATE, 0f)
        assertEquals(1.0f, prefs.ttsSpeechRate, 0f)
        assertEquals(
            "出厂语速交给引擎前不应被改写",
            AgentPreferences.DEFAULT_TTS_SPEECH_RATE,
            ReplySpeaker.speechRateFor(prefs.ttsSpeechRate),
            0f
        )
    }

    @Test
    fun ttsSpeechRateClampsToItsRange() {
        val prefs = emptyPreferences()
        prefs.ttsSpeechRate = 0.1f
        assertEquals(AgentPreferences.MinTtsSpeechRate, prefs.ttsSpeechRate, 0f)
        prefs.ttsSpeechRate = 9f
        assertEquals(AgentPreferences.MaxTtsSpeechRate, prefs.ttsSpeechRate, 0f)

        val stale = AgentPreferences(
            FakeSharedPreferences(mutableMapOf<String, Any?>("tts_speech_rate" to 12f))
        )
        assertEquals(AgentPreferences.MaxTtsSpeechRate, stale.ttsSpeechRate, 0f)
    }

    @Test
    fun ttsSpeechRateReachesTheEngineCall() {
        val prefs = emptyPreferences()
        assertEquals(1.0f, ReplySpeaker.speechRateFor(prefs.ttsSpeechRate), 0f)

        prefs.ttsSpeechRate = 1.6f
        assertEquals(
            "设置值必须原样落到 setSpeechRate 的实参上",
            1.6f,
            ReplySpeaker.speechRateFor(prefs.ttsSpeechRate),
            0.0001f
        )

        // 引擎不接受 0 / 负数 / NaN：夹回区间，NaN 退回出厂语速，
        // 于是"语速设了值但引擎收到 0"这条路不存在
        assertEquals(
            AgentPreferences.DEFAULT_TTS_SPEECH_RATE,
            ReplySpeaker.speechRateFor(Float.NaN),
            0f
        )
        assertEquals(
            AgentPreferences.MinTtsSpeechRate,
            ReplySpeaker.speechRateFor(0f),
            0f
        )
    }

    // ==================== 结构守护：四个消费点都在生产路径上 ====================

    @Test
    fun everySettingIsReadByItsProductionConsumptionPoint() {
        val viewModel = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ChatViewModel.kt"
        )
        assertTrue(
            "ChatViewModel 必须把 stream_emit_interval_ms 套到节流器上",
            viewModel.contains("throttle.updateInterval(") &&
                viewModel.contains("displayPreferences().streamEmitIntervalMs")
        )
        assertTrue(
            "ChatViewModel 必须把 tts_speech_rate 交给播报器",
            viewModel.contains("displayPreferences().ttsSpeechRate") &&
                viewModel.contains("speaker.speak(text, display.ttsSpeechRate)")
        )

        val registry = readProjectFile(
            "src/main/java/com/example/myapplication/agent/AgentToolRegistry.kt"
        )
        assertTrue(
            "run_shell 的兜底超时必须读 shell_command_timeout_ms",
            registry.contains("shellTimeoutFallbackMs()") &&
                registry.contains("preferences?.shellCommandTimeoutMs")
        )

        val chatScreen = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/chat/ChatScreen.kt"
        )
        assertTrue(
            "审批滑杆的阈值必须来自设置项",
            chatScreen.contains("approvalAllowThreshold = preferences.approvalAllowThreshold")
        )

        val speaker = readProjectFile(
            "src/main/java/com/example/myapplication/agent/ReplySpeaker.kt"
        )
        assertTrue(
            "播报必须在合成前真的设置语速",
            speaker.contains("tts.setSpeechRate(speechRateFor(speechRate))")
        )
    }
}
