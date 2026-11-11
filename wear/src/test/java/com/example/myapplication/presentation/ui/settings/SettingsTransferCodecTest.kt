package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.ToolApprovalPolicy
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 配置快照的编解码与写回。
 *
 * 三条硬约束：往返不丢字符、非法输入零写入、密钥永不进快照 ——
 * 每一条都有对照数据，不靠"跑通一次"就算过。
 */
class SettingsTransferCodecTest {

    @Test
    fun exportThenParseRoundTripsEveryPortableKey() {
        val values = linkedMapOf<String, String>()
        SettingsTransferCodec.PortableKeys.forEach { key -> values[key] = "v-" + key }
        // 需要转义的三种字符：引号、反斜杠、换行
        values["reasoningEffort"] = "a\"b\\c\nd"

        val parsed = SettingsTransferCodec.parse(SettingsTransferCodec.export(values))
        assertTrue("合法快照必须解析成功", parsed is TransferResult.Ok)
        val entries = (parsed as TransferResult.Ok).entries
        assertEquals(SettingsTransferCodec.PortableKeys, entries.map { it.key })
        assertEquals("a\"b\\c\nd", entries.first { it.key == "reasoningEffort" }.value)
        entries.forEach { entry ->
            assertEquals("键 " + entry.key + " 的值没原样往返", values[entry.key], entry.value)
        }
    }

    @Test
    fun exportSkipsKeysOutsideTheWhitelist() {
        val text = SettingsTransferCodec.export(
            mapOf(
                "securityMode" to "EXPERT",
                "apiKey" to "sk-should-not-appear",
                "baiduSecretKey" to "secret-should-not-appear"
            )
        )
        assertTrue("白名单内的键必须在", text.contains("securityMode"))
        assertFalse("密钥字段绝不能进快照", text.contains("apiKey"))
        assertFalse("语音密钥同样不能进快照", text.contains("baiduSecretKey"))
        assertFalse(text.contains("sk-should-not-appear"))
    }

    @Test
    fun exportIsDeterministicForTheSameInput() {
        val values = SettingsTransferBridge.snapshot(AgentPreferences(FakeSharedPreferences()))
        assertEquals(SettingsTransferCodec.export(values), SettingsTransferCodec.export(values))
    }

    @Test
    fun parseRejectsEverythingThatIsNotAVersionOneSnapshot() {
        val cases = listOf(
            "" to "空串",
            "not json" to "根本不是 JSON",
            "{\"version\":2,\"settings\":{}}" to "版本不对",
            "{\"version\":1}" to "缺 settings 块",
            "{\"version\":1,\"settings\":{\"apiKey\":\"x\"}}" to "白名单外的键",
            "{\"version\":1,\"settings\":{\"securityMode\":\"A\",\"securityMode\":\"B\"}}" to "重复键",
            "{\"version\":1,\"settings\":{}}" + " extra" to "尾部多余内容"
        )
        cases.forEach { (raw, why) ->
            val result = SettingsTransferCodec.parse(raw)
            assertTrue(why + " 必须被拒绝，实际却解析成功", result is TransferResult.Invalid)
            assertTrue(why + " 必须给出可读原因", (result as TransferResult.Invalid).reason.isNotBlank())
        }
    }

    @Test
    fun snapshotAndPortableKeysCoverExactlyTheSameSet() {
        val snapshot = SettingsTransferBridge.snapshot(AgentPreferences(FakeSharedPreferences()))
        assertEquals(
            "快照键集与白名单必须一一对应，多一个少一个都会让导入静默丢项",
            SettingsTransferCodec.PortableKeys.toSet(),
            snapshot.keys
        )
    }

    @Test
    fun applyWritesEveryPortableKeyBack() {
        val store = FakeSharedPreferences()
        val source = AgentPreferences(store).apply {
            securityMode = SecurityMode.EXPERT
            toolApprovalPolicy = ToolApprovalPolicy.NEVER
            sandboxLevel = SandboxLevel.READ_ONLY
            longPressAction = GestureAction.STOP_TURN
            doubleTapAction = GestureAction.SWITCH_MODEL
            sideKeyShortAction = GestureAction.FORGET_REMEMBERED
            sideKeyLongAction = GestureAction.STOP_TURN
            maxContextTurns = 12
            historyCompressThreshold = 0.45f
            autoCompressContext = false
            memoryEnabled = false
            defaultThinkingEnabled = false
            providerManagedSearchEnabled = true
            compactMode = true
            animationsEnabled = false
            autoSpeakReplies = true
            asrLiveTranscript = false
            temperature = 0.2f
            maxOutputTokens = 512
            streamMode = false
            reasoningEffort = "high"
            showThoughtChain = false
            subAgentEnabled = true
            matrixSpacingDp = 8f
            asrDevPid = 1737
            toolDeviceStatusEnabled = false
            toolVibrateEnabled = false
            toolMatrixDensityEnabled = false
            toolPowerModeEnabled = false
            toolWeatherEnabled = false
            toolWebSearchEnabled = false
        }
        val text = SettingsTransferCodec.export(SettingsTransferBridge.snapshot(source))
        val parsed = SettingsTransferCodec.parse(text) as TransferResult.Ok

        val targetStore = FakeSharedPreferences()
        val target = AgentPreferences(targetStore)
        val applied = SettingsTransferBridge.apply(target, parsed.entries)

        assertEquals("每一条都必须落盘", parsed.entries.size, applied)
        assertEquals(SecurityMode.EXPERT, target.securityMode)
        assertEquals(ToolApprovalPolicy.NEVER, target.toolApprovalPolicy)
        assertEquals(SandboxLevel.READ_ONLY, target.sandboxLevel)
        assertEquals(GestureAction.STOP_TURN, target.longPressAction)
        assertEquals(GestureAction.SWITCH_MODEL, target.doubleTapAction)
        assertEquals(GestureAction.FORGET_REMEMBERED, target.sideKeyShortAction)
        assertEquals(12, target.maxContextTurns)
        assertEquals(0.45f, target.historyCompressThreshold, 0.0001f)
        assertFalse(target.autoCompressContext)
        assertFalse(target.memoryEnabled)
        assertFalse(target.defaultThinkingEnabled)
        assertTrue(target.providerManagedSearchEnabled)
        assertTrue(target.compactMode)
        assertFalse(target.animationsEnabled)
        assertTrue(target.autoSpeakReplies)
        assertFalse(target.asrLiveTranscript)
        assertEquals(0.2f, target.temperature, 0.0001f)
        assertEquals(512, target.maxOutputTokens)
        assertFalse(target.streamMode)
        assertEquals("high", target.reasoningEffort)
        assertFalse(target.showThoughtChain)
        assertTrue(target.subAgentEnabled)
        assertEquals(8f, target.matrixSpacingDp, 0.0001f)
        assertEquals(1737, target.asrDevPid)
        assertFalse(target.toolWebSearchEnabled)
    }

    @Test
    fun applySkipsOutOfRangeValuesInsteadOfWritingThem() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        val before = prefs.rootLikeSnapshot()
        val applied = SettingsTransferBridge.apply(
            prefs,
            listOf(
                TransferEntry("maxContextTurns", "9000"),
                TransferEntry("historyCompressThreshold", "hot"),
                TransferEntry("securityMode", "NOT_A_MODE"),
                TransferEntry("toolApprovalPolicy", "SOMETIMES"),
                TransferEntry("temperature", "9.5"),
                TransferEntry("memoryEnabled", "maybe"),
                TransferEntry("autoCompressContext", "true")
            )
        )
        assertEquals("只有最后一条合法", 1, applied)
        assertEquals(before, prefs.rootLikeSnapshot())
        assertTrue("唯一合法的那条必须真的写进去", prefs.autoCompressContext)
    }

    /** 越界值不许写坏存储：把几个关键字段拼成可比较的一行。 */
    private fun AgentPreferences.rootLikeSnapshot(): String = listOf(
        maxContextTurns,
        historyCompressThreshold,
        securityMode.name,
        toolApprovalPolicy.name,
        temperature,
        memoryEnabled
    ).joinToString("|")
}
