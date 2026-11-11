package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.ToolApprovalPolicy
import com.example.myapplication.agent.contract.SecurityMode

/** 快照里的一项：键取 [SettingsTransferCodec] 的白名单，值一律是字符串。 */
data class TransferEntry(val key: String, val value: String)

/** 一次导入的解析结果。失败时必须给出人话原因，调用方据此决定"一个字都不写"。 */
sealed class TransferResult {
    data class Ok(val entries: List<TransferEntry>) : TransferResult()
    data class Invalid(val reason: String) : TransferResult()
}

/**
 * 设置快照的文本编解码（纯 Kotlin，零 org.json / 零 Android）。
 *
 * 格式固定成 `{"version":1,"settings":{"键":"值",...}}` —— 扁平一层，值只有字符串。
 * 不用 org.json 是为了能在纯 JVM 单测里跑：Android 的 org.json 在单测里是桩，会直接抛异常。
 *
 * 白名单只收"可移植、且不含密钥"的项：apiKey / baiduApiKey / baiduSecretKey 永远不进快照，
 * 于是导出字符串可以被随意贴到任何地方而不会泄露凭据。
 */
object SettingsTransferCodec {

    const val Version = 1

    /** 参与快照的键，顺序即导出顺序（保证同一份设置永远产出同一段文本）。 */
    val PortableKeys: List<String> = listOf(
        "securityMode",
        "toolApprovalPolicy",
        "sandboxLevel",
        "longPressAction",
        "doubleTapAction",
        "sideKeyShortAction",
        "sideKeyLongAction",
        "maxContextTurns",
        "historyCompressThreshold",
        "autoCompressContext",
        "memoryEnabled",
        "defaultThinkingEnabled",
        "providerManagedSearchEnabled",
        "compactMode",
        "animationsEnabled",
        "autoSpeakReplies",
        "asrLiveTranscript",
        "temperature",
        "maxOutputTokens",
        "streamMode",
        "reasoningEffort",
        "showThoughtChain",
        "subAgentEnabled",
        "matrixSpacingDp",
        "asrDevPid",
        "toolDeviceStatusEnabled",
        "toolVibrateEnabled",
        "toolMatrixDensityEnabled",
        "toolPowerModeEnabled",
        "toolWeatherEnabled",
        "toolWebSearchEnabled",
        // 外观两项：存在独立的 wear_appearance 文件里，但漏了它们就等于"导出再导入换台设备外观全丢"
        KeyFontScale,
        KeyAccentArgb
    )

    /** 字号档（浮点缩放系数）。 */
    const val KeyFontScale = "fontScale"

    /** 强调色档（ARGB 整数）。 */
    const val KeyAccentArgb = "accentArgb"

    /** 把一份键值表编码成快照文本；不在白名单里的键被忽略。 */
    fun export(values: Map<String, String>): String {
        val body = PortableKeys
            .mapNotNull { key -> values[key]?.let { key to it } }
            .joinToString(",") { (key, value) -> quote(key) + ":" + quote(value) }
        return "{\"version\":" + Version + ",\"settings\":{" + body + "}}"
    }

    /** 解析快照文本。任何结构、版本或键名问题都返回 [TransferResult.Invalid] 且不产生半份数据。 */
    fun parse(raw: String): TransferResult {
        val text = raw.trim()
        if (text.isEmpty()) return TransferResult.Invalid("empty snapshot")
        val reader = Reader(text)
        return try {
            reader.expect('{')
            var version = -1
            val entries = mutableListOf<TransferEntry>()
            var sawSettings = false
            while (true) {
                if (reader.peek() == '}') { reader.next(); break }
                val key = reader.readString()
                reader.expect(':')
                when (key) {
                    "version" -> version = reader.readInt()
                    "settings" -> {
                        if (sawSettings) return TransferResult.Invalid("duplicate settings block")
                        sawSettings = true
                        entries.addAll(readSettings(reader))
                    }
                    else -> return TransferResult.Invalid("unknown field: " + key)
                }
                if (reader.peek() == ',') { reader.next(); continue }
                reader.expect('}')
                break
            }
            if (reader.hasNext()) return TransferResult.Invalid("trailing content")
            when {
                version != Version -> TransferResult.Invalid("unsupported version: " + version)
                !sawSettings -> TransferResult.Invalid("missing settings block")
                else -> TransferResult.Ok(entries)
            }
        } catch (e: IllegalArgumentException) {
            TransferResult.Invalid(e.message ?: "malformed snapshot")
        }
    }

    private fun readSettings(reader: Reader): List<TransferEntry> {
        reader.expect('{')
        val out = mutableListOf<TransferEntry>()
        val seen = mutableSetOf<String>()
        while (true) {
            if (reader.peek() == '}') { reader.next(); break }
            val key = reader.readString()
            if (key !in PortableKeys) throw IllegalArgumentException("unknown setting: " + key)
            if (!seen.add(key)) throw IllegalArgumentException("duplicate setting: " + key)
            reader.expect(':')
            out.add(TransferEntry(key, reader.readString()))
            if (reader.peek() == ',') { reader.next(); continue }
            reader.expect('}')
            break
        }
        return out
    }

    private fun quote(value: String): String {
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> out.append(ch)
            }
        }
        out.append('"')
        return out.toString()
    }

    /** 只认这一种语法的极简读取器：字符串、整数、'{' '}' ':' ','。 */
    private class Reader(private val text: String) {
        private var index = 0

        fun hasNext(): Boolean = index < text.length

        fun peek(): Char {
            if (index >= text.length) throw IllegalArgumentException("unexpected end of snapshot")
            return text[index]
        }

        fun next(): Char = peek().also { index++ }

        fun expect(target: Char) {
            val actual = next()
            if (actual != target) {
                throw IllegalArgumentException("expected '" + target + "' but found '" + actual + "'")
            }
        }

        fun readInt(): Int {
            val start = index
            while (index < text.length && (text[index].isDigit() || text[index] == '-')) index++
            if (start == index) throw IllegalArgumentException("expected a number")
            return text.substring(start, index).toIntOrNull()
                ?: throw IllegalArgumentException("expected a number")
        }

        fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                val ch = next()
                when (ch) {
                    '"' -> return out.toString()
                    '\\' -> {
                        when (val escaped = next()) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            else -> throw IllegalArgumentException("bad escape: " + escaped)
                        }
                    }
                    else -> out.append(ch)
                }
            }
        }
    }
}

/**
 * 快照与 [AgentPreferences] 之间的桥：读成键值表、写回键值表。
 *
 * 写回是**逐项校验**的：解析得过但类型不对的值（例如温度写成 "hot"）跳过并计入失败，
 * 绝不把整块设置写坏一半 —— 于是"导入失败"永远是零写入，而不是部分生效。
 */
object SettingsTransferBridge {

    fun snapshot(preferences: AgentPreferences, appearance: AppearanceStore): Map<String, String> = linkedMapOf(
        "securityMode" to preferences.securityMode.name,
        "toolApprovalPolicy" to preferences.toolApprovalPolicy.storageKey,
        "sandboxLevel" to preferences.sandboxLevel.storageKey,
        "longPressAction" to preferences.longPressAction.storageKey,
        "doubleTapAction" to preferences.doubleTapAction.storageKey,
        "sideKeyShortAction" to preferences.sideKeyShortAction.storageKey,
        "sideKeyLongAction" to preferences.sideKeyLongAction.storageKey,
        "maxContextTurns" to preferences.maxContextTurns.toString(),
        "historyCompressThreshold" to preferences.historyCompressThreshold.toString(),
        "autoCompressContext" to preferences.autoCompressContext.toString(),
        "memoryEnabled" to preferences.memoryEnabled.toString(),
        "defaultThinkingEnabled" to preferences.defaultThinkingEnabled.toString(),
        "providerManagedSearchEnabled" to preferences.providerManagedSearchEnabled.toString(),
        "compactMode" to preferences.compactMode.toString(),
        "animationsEnabled" to preferences.animationsEnabled.toString(),
        "autoSpeakReplies" to preferences.autoSpeakReplies.toString(),
        "asrLiveTranscript" to preferences.asrLiveTranscript.toString(),
        "temperature" to preferences.temperature.toString(),
        "maxOutputTokens" to preferences.maxOutputTokens.toString(),
        "streamMode" to preferences.streamMode.toString(),
        "reasoningEffort" to preferences.reasoningEffort,
        "showThoughtChain" to preferences.showThoughtChain.toString(),
        "subAgentEnabled" to preferences.subAgentEnabled.toString(),
        "matrixSpacingDp" to preferences.matrixSpacingDp.toString(),
        "asrDevPid" to preferences.asrDevPid.toString(),
        "toolDeviceStatusEnabled" to preferences.toolDeviceStatusEnabled.toString(),
        "toolVibrateEnabled" to preferences.toolVibrateEnabled.toString(),
        "toolMatrixDensityEnabled" to preferences.toolMatrixDensityEnabled.toString(),
        "toolPowerModeEnabled" to preferences.toolPowerModeEnabled.toString(),
        "toolWeatherEnabled" to preferences.toolWeatherEnabled.toString(),
        "toolWebSearchEnabled" to preferences.toolWebSearchEnabled.toString(),
        SettingsTransferCodec.KeyFontScale to appearance.fontScale().toString(),
        // 没有覆盖时导出令牌色：导入端只能落在五个预设档上，空值会让"默认档"无处可落
        SettingsTransferCodec.KeyAccentArgb to
            (appearance.accent() ?: AccentPreset.Default.color).toArgb().toString()
    )

    /**
     * 写回：返回真正落盘的项数。类型不合法的项被跳过，不会写坏存储。
     *
     * 外观两项单列一支：它们住在 [AppearanceStore]（独立偏好文件），且**非法值不跳过而是回落默认档** ——
     * 跳过等于"导进去了但没生效"，用户拿到的是一台外观不对的设备，却没有任何反馈。
     */
    fun apply(
        preferences: AgentPreferences,
        entries: List<TransferEntry>,
        appearance: AppearanceStore
    ): Int {
        var applied = 0
        var appearanceImported = false
        entries.forEach { entry ->
            when (entry.key) {
                SettingsTransferCodec.KeyFontScale -> {
                    appearance.saveFontScale(fontScaleOf(entry.value))
                    applied++
                    appearanceImported = true
                }
                SettingsTransferCodec.KeyAccentArgb -> {
                    appearance.saveAccent(accentOf(entry.value))
                    applied++
                    appearanceImported = true
                }
                else -> if (applyOne(preferences, entry)) applied++
            }
        }
        // 界面读的是 WearAppearance 的快照状态：导入完立刻灌一次，不必等重启
        if (appearanceImported) appearance.install()
        return applied
    }

    /** 字号档：解析不了的字符串落到默认档；野值由 [FontScaleTier.nearest] 夹回最近的档。 */
    private fun fontScaleOf(raw: String): Float =
        FontScaleTier.nearest(raw.toFloatOrNull() ?: FontScaleTier.STANDARD.scale).scale

    /** 强调色：解析不了或不属于五个预设档的颜色一律回落到默认档，野色不写进存储。 */
    private fun accentOf(raw: String): Color =
        AccentPreset.of(Color(raw.toIntOrNull() ?: AccentPreset.Default.color.toArgb())).color

    private fun applyOne(preferences: AgentPreferences, entry: TransferEntry): Boolean {
        val value = entry.value
        return when (entry.key) {
            "securityMode" -> SecurityMode.values()
                .firstOrNull { it.name == value }
                ?.let { preferences.securityMode = it; true } ?: false
            "toolApprovalPolicy" -> ToolApprovalPolicy.fromStorage(value)
                .takeIf { it.storageKey == value }
                ?.let { preferences.toolApprovalPolicy = it; true } ?: false
            "sandboxLevel" -> SandboxLevel.fromStorage(value)
                .takeIf { it.storageKey == value }
                ?.let { preferences.sandboxLevel = it; true } ?: false
            "longPressAction" -> GestureAction.fromStorage(value)
                .takeIf { it.storageKey == value }
                ?.let { preferences.longPressAction = it; true } ?: false
            "doubleTapAction" -> GestureAction.fromStorage(value)
                .takeIf { it.storageKey == value }
                ?.let { preferences.doubleTapAction = it; true } ?: false
            "sideKeyShortAction" -> GestureAction.fromStorage(value)
                .takeIf { it.storageKey == value }
                ?.let { preferences.sideKeyShortAction = it; true } ?: false
            "sideKeyLongAction" -> GestureAction.fromStorage(value)
                .takeIf { it.storageKey == value }
                ?.let { preferences.sideKeyLongAction = it; true } ?: false
            "maxContextTurns" -> value.toIntOrNull()?.takeIf { it in 4..200 }
                ?.let { preferences.maxContextTurns = it; true } ?: false
            "historyCompressThreshold" -> value.toFloatOrNull()
                ?.let { preferences.historyCompressThreshold = it; true } ?: false
            "autoCompressContext" -> boolValue(value)?.let { preferences.autoCompressContext = it; true } ?: false
            "memoryEnabled" -> boolValue(value)?.let { preferences.memoryEnabled = it; true } ?: false
            "defaultThinkingEnabled" -> boolValue(value)?.let { preferences.defaultThinkingEnabled = it; true } ?: false
            "providerManagedSearchEnabled" -> boolValue(value)
                ?.let { preferences.providerManagedSearchEnabled = it; true } ?: false
            "compactMode" -> boolValue(value)?.let { preferences.compactMode = it; true } ?: false
            "animationsEnabled" -> boolValue(value)?.let { preferences.animationsEnabled = it; true } ?: false
            "autoSpeakReplies" -> boolValue(value)?.let { preferences.autoSpeakReplies = it; true } ?: false
            "asrLiveTranscript" -> boolValue(value)?.let { preferences.asrLiveTranscript = it; true } ?: false
            "temperature" -> value.toFloatOrNull()?.takeIf { it in 0f..2f }
                ?.let { preferences.temperature = it; true } ?: false
            "maxOutputTokens" -> value.toIntOrNull()?.takeIf { it in 1..65536 }
                ?.let { preferences.maxOutputTokens = it; true } ?: false
            "streamMode" -> boolValue(value)?.let { preferences.streamMode = it; true } ?: false
            "reasoningEffort" -> value.takeIf { it.isNotBlank() }
                ?.let { preferences.reasoningEffort = it; true } ?: false
            "showThoughtChain" -> boolValue(value)?.let { preferences.showThoughtChain = it; true } ?: false
            "subAgentEnabled" -> boolValue(value)?.let { preferences.subAgentEnabled = it; true } ?: false
            "matrixSpacingDp" -> value.toFloatOrNull()?.takeIf { it in 4f..32f }
                ?.let { preferences.matrixSpacingDp = it; true } ?: false
            "asrDevPid" -> value.toIntOrNull()?.let { preferences.asrDevPid = it; true } ?: false
            "toolDeviceStatusEnabled" -> boolValue(value)?.let { preferences.toolDeviceStatusEnabled = it; true } ?: false
            "toolVibrateEnabled" -> boolValue(value)?.let { preferences.toolVibrateEnabled = it; true } ?: false
            "toolMatrixDensityEnabled" -> boolValue(value)?.let { preferences.toolMatrixDensityEnabled = it; true } ?: false
            "toolPowerModeEnabled" -> boolValue(value)?.let { preferences.toolPowerModeEnabled = it; true } ?: false
            "toolWeatherEnabled" -> boolValue(value)?.let { preferences.toolWeatherEnabled = it; true } ?: false
            "toolWebSearchEnabled" -> boolValue(value)?.let { preferences.toolWebSearchEnabled = it; true } ?: false
            else -> false
        }
    }

    private fun boolValue(raw: String): Boolean? = when (raw) {
        "true" -> true
        "false" -> false
        else -> null
    }
}
