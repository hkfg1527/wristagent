package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.ToolApprovalPolicy
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 配置快照的编解码与写回。
 *
 * 三条硬约束：往返不丢字符、非法输入零写入、密钥永不进快照 ——
 * 每一条都有对照数据，不靠"跑通一次"就算过。
 */
class SettingsTransferCodecTest {

    /**
     * 导入会把外观灌进 [WearAppearance] 的运行时状态：每个用例前后都归零，
     * 免得"导入即刻生效"这件事泄漏成别的测试类的隐藏前提。
     */
    @Before
    fun resetAppearanceRuntime() {
        WearAppearance.reset()
        AppearanceBootstrap.reset()
    }

    @After
    fun resetAppearanceRuntimeAfterTheCase() {
        WearAppearance.reset()
        AppearanceBootstrap.reset()
    }

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
        // —— 外观两项 + 回答风格后的扩充：键集 31 -> 34 ——
        assertEquals("可移植键必须扩到 34 个", 34, SettingsTransferCodec.PortableKeys.size)
        assertEquals(34, entries.size)
        assertTrue(
            "外观两项必须进可移植键，否则导出再导入会把外观丢掉",
            SettingsTransferCodec.PortableKeys.containsAll(
                listOf(SettingsTransferCodec.KeyFontScale, SettingsTransferCodec.KeyAccentArgb)
            )
        )
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
        val values = SettingsTransferBridge.snapshot(
            AgentPreferences(FakeSharedPreferences()),
            AppearanceStore(FakeSharedPreferences())
        )
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
        val snapshot = SettingsTransferBridge.snapshot(
            AgentPreferences(FakeSharedPreferences()),
            AppearanceStore(FakeSharedPreferences())
        )
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
        val text = SettingsTransferCodec.export(
            SettingsTransferBridge.snapshot(source, AppearanceStore(FakeSharedPreferences()))
        )
        val parsed = SettingsTransferCodec.parse(text) as TransferResult.Ok

        val targetStore = FakeSharedPreferences()
        val target = AgentPreferences(targetStore)
        val applied = SettingsTransferBridge.apply(
            target,
            parsed.entries,
            AppearanceStore(FakeSharedPreferences())
        )

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
            ),
            AppearanceStore(FakeSharedPreferences())
        )
        assertEquals("只有最后一条合法", 1, applied)
        assertEquals(before, prefs.rootLikeSnapshot())
        assertTrue("唯一合法的那条必须真的写进去", prefs.autoCompressContext)
    }

    // ---------- 外观两项：fontScale / accentArgb ----------

    @Test
    fun exportCarriesTheRuntimeAppearanceSoImportCannotLoseIt() {
        val source = AppearanceStore(FakeSharedPreferences()).apply {
            saveFontScale(FontScaleTier.XLARGE.scale)
            saveAccent(AccentPreset.AMBER.color)
        }
        val text = SettingsTransferCodec.export(
            SettingsTransferBridge.snapshot(AgentPreferences(FakeSharedPreferences()), source)
        )
        val entries = (SettingsTransferCodec.parse(text) as TransferResult.Ok).entries
        val byKey = entries.associate { entry -> entry.key to entry.value }

        assertEquals(FontScaleTier.XLARGE.scale.toString(), byKey[SettingsTransferCodec.KeyFontScale])
        assertEquals(
            "强调色必须按 ARGB 原值导出",
            AccentPreset.AMBER.color.toArgb().toString(),
            byKey[SettingsTransferCodec.KeyAccentArgb]
        )

        // 导到一台全新设备：外观必须原样回来，而不是回到默认档
        val target = AppearanceStore(FakeSharedPreferences())
        SettingsTransferBridge.apply(AgentPreferences(FakeSharedPreferences()), entries, target)
        assertEquals(FontScaleTier.XLARGE.scale, target.fontScale(), 0.0001f)
        assertEquals(AccentPreset.AMBER.color.toArgb(), target.accent()!!.toArgb())
        // 导入即刻生效：界面读的是运行时状态，不必等重启
        assertEquals(FontScaleTier.XLARGE.scale, WearAppearance.fontScale, 0.0001f)
        assertEquals(AccentPreset.AMBER.color, WearAppearance.accent)
    }

    @Test
    fun importSnapsIllegalFontScaleBackToTheDefaultTier() {
        val store = AppearanceStore(FakeSharedPreferences()).apply {
            saveFontScale(FontScaleTier.XLARGE.scale)
        }
        val applied = SettingsTransferBridge.apply(
            AgentPreferences(FakeSharedPreferences()),
            listOf(TransferEntry(SettingsTransferCodec.KeyFontScale, "huge")),
            store
        )
        assertEquals("非法字号也必须落盘成默认档，不能当成没导入", 1, applied)
        assertEquals(FontScaleTier.STANDARD.scale, store.fontScale(), 0.0001f)

        // 三角验证：解析得过但落在档位之外的数值同样夹回最近的档
        SettingsTransferBridge.apply(
            AgentPreferences(FakeSharedPreferences()),
            listOf(TransferEntry(SettingsTransferCodec.KeyFontScale, "0.2")),
            store
        )
        assertEquals(FontScaleTier.STANDARD.scale, store.fontScale(), 0.0001f)
    }

    @Test
    fun importRejectsAccentColoursThatAreNotOneOfTheFivePresets() {
        val store = AppearanceStore(FakeSharedPreferences()).apply {
            saveAccent(AccentPreset.VIOLET.color)
        }
        // 野色（不在五个预设档上）：回落默认档，而不是把野色写进存储
        SettingsTransferBridge.apply(
            AgentPreferences(FakeSharedPreferences()),
            listOf(
                TransferEntry(
                    SettingsTransferCodec.KeyAccentArgb,
                    Color(0xFF123456).toArgb().toString()
                )
            ),
            store
        )
        assertEquals(AccentPreset.Default.color.toArgb(), store.accent()!!.toArgb())

        // 连整数都不是：同样回落默认档
        SettingsTransferBridge.apply(
            AgentPreferences(FakeSharedPreferences()),
            listOf(TransferEntry(SettingsTransferCodec.KeyAccentArgb, "amber-ish")),
            store
        )
        assertEquals(AccentPreset.Default.color.toArgb(), store.accent()!!.toArgb())
    }

    @Test
    fun importingAnOlderSnapshotWithoutAppearanceKeysLeavesAppearanceUntouched() {
        // 旧快照（31 个键）里没有外观两项：不许把它们重置成默认档
        val store = AppearanceStore(FakeSharedPreferences()).apply {
            saveFontScale(FontScaleTier.LARGE.scale)
            saveAccent(AccentPreset.ROSE.color)
        }
        SettingsTransferBridge.apply(
            AgentPreferences(FakeSharedPreferences()),
            listOf(TransferEntry("memoryEnabled", "false")),
            store
        )
        assertEquals(FontScaleTier.LARGE.scale, store.fontScale(), 0.0001f)
        assertEquals(AccentPreset.ROSE.color.toArgb(), store.accent()!!.toArgb())
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
