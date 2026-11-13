package com.example.myapplication.agent.remote

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.ReplyStyle
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.ToolApprovalPolicy
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.voice.AsrProviderId

/** settings 字段的值类型，决定写入口的类型校验分支。 */
enum class ConsoleFieldType { STRING, FLOAT, INT, BOOLEAN, ENUM }

/**
 * @param key 规范键，与 [com.example.myapplication.agent.AgentPreferences] 的真实属性同名
 * @param readOnly 只读字段：POST 里出现时忽略并回传在 ignored 中
 * @param allowed ENUM 字段的合法取值
 * @param structured 列表型字段：STRING 承载时也接受对象数组，原值直接交给 Port 编码，
 *   免得为每一种列表再造一个字段类型
 * @param min 数值下界（含）；null 表示不限
 * @param max 数值上界（含）；null 表示不限
 */
data class ConsoleField(
    val key: String,
    val type: ConsoleFieldType,
    val readOnly: Boolean = false,
    val allowed: List<String> = emptyList(),
    val structured: Boolean = false,
    val min: Double? = null,
    val max: Double? = null,
)

enum class ConsoleWriteResult { OK, READ_ONLY }

/** settings 读写出口：实现方必须落到现有 AgentPreferences，不允许第二份存储。 */
interface ConsoleSettingsPort {
    fun snapshot(): Map<String, Any?>
    fun write(key: String, value: Any): ConsoleWriteResult
}

/** 单字段写入校验的结果。 */
sealed class ConsoleValue {
    data class Valid(val value: Any) : ConsoleValue()
    object BadType : ConsoleValue()
    data class BadValue(val allowed: List<String>) : ConsoleValue()
    data class OutOfRange(val min: Double?, val max: Double?) : ConsoleValue()
}

object ConsoleFields {

    /** 契约别名 → 真实属性键。网页端沿用契约拼写时按此映射写入，读取时双写同一份值。 */
    val ALIASES: Map<String, String> = linkedMapOf(
        "apiBaseUrl" to "baseUrl",
        "maxTokens" to "maxOutputTokens",
        "systemPrompt" to "customSystemPrompt",
        "memoryRounds" to "maxContextTurns",
        "longTermMemoryEnabled" to "memoryEnabled",
        "asrEngine" to "asrProvider",
    )

    val ALL: List<ConsoleField> = listOf(
        ConsoleField("baseUrl", ConsoleFieldType.STRING),
        ConsoleField("apiKey", ConsoleFieldType.STRING),
        ConsoleField("modelName", ConsoleFieldType.STRING),
        ConsoleField("temperature", ConsoleFieldType.FLOAT, min = 0.0, max = 1.5),
        ConsoleField("maxOutputTokens", ConsoleFieldType.INT, min = 16.0, max = 8192.0),
        ConsoleField("customSystemPrompt", ConsoleFieldType.STRING),
        ConsoleField("securityMode", ConsoleFieldType.ENUM, allowed = SecurityMode.values().map { it.name }),
        ConsoleField("maxContextTurns", ConsoleFieldType.INT, min = 0.0, max = 200.0),
        ConsoleField(
            "requestTimeoutSeconds",
            ConsoleFieldType.INT,
            min = AgentPreferences.MinRequestTimeoutSeconds.toDouble(),
            max = AgentPreferences.MaxRequestTimeoutSeconds.toDouble()
        ),
        ConsoleField(
            "requestRetries",
            ConsoleFieldType.INT,
            min = 0.0,
            max = AgentPreferences.MaxRequestRetries.toDouble()
        ),
        ConsoleField(
            "retryBackoffMs",
            ConsoleFieldType.INT,
            min = 0.0,
            max = AgentPreferences.MaxRetryBackoffMs.toDouble()
        ),
        ConsoleField("wifiOnlyRequests", ConsoleFieldType.BOOLEAN),
        ConsoleField("hapticsEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("asrProvider", ConsoleFieldType.ENUM, allowed = AsrProviderId.values().map { it.storageKey }),
        ConsoleField("baiduApiKey", ConsoleFieldType.STRING),
        ConsoleField("baiduSecretKey", ConsoleFieldType.STRING),
        ConsoleField("asrEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("toolDeviceStatusEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("toolVibrateEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("toolMatrixDensityEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("toolPowerModeEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("toolWeatherEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("toolWebSearchEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("isMcpEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("mcpEndpoint", ConsoleFieldType.STRING),
        ConsoleField("providerProfiles", ConsoleFieldType.STRING, structured = true),
        ConsoleField("matrixSpacingDp", ConsoleFieldType.FLOAT, min = 0.0, max = 64.0),
        ConsoleField("streamMode", ConsoleFieldType.BOOLEAN),
        ConsoleField("animationsEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("subAgentEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("compactMode", ConsoleFieldType.BOOLEAN),
        ConsoleField("providerManagedSearchEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("autoSpeakReplies", ConsoleFieldType.BOOLEAN),
        ConsoleField("asrLiveTranscript", ConsoleFieldType.BOOLEAN),
        ConsoleField("defaultThinkingEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("replyStyle", ConsoleFieldType.ENUM, allowed = ReplyStyle.values().map { it.storageKey }),
        ConsoleField(
            "settingsStyle",
            ConsoleFieldType.ENUM,
            allowed = SettingsStyle.values().map { it.name }
        ),
        ConsoleField("memoryEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("autoCompressContext", ConsoleFieldType.BOOLEAN),
        // 沙箱等级 / 审批策略 / 压缩阈值：手表端早就能改，网页控制台此前没接 HTTP 通路
        ConsoleField("sandboxLevel", ConsoleFieldType.ENUM, allowed = SandboxLevel.values().map { it.storageKey }),
        ConsoleField(
            "toolApprovalPolicy",
            ConsoleFieldType.ENUM,
            allowed = ToolApprovalPolicy.values().map { it.storageKey },
        ),
        ConsoleField("historyCompressThreshold", ConsoleFieldType.FLOAT, min = 0.3, max = 0.95),
        ConsoleField("hasCompletedOnboarding", ConsoleFieldType.BOOLEAN, readOnly = true),
    )

    private val byKey: Map<String, ConsoleField> = ALL.associateBy { it.key }

    fun canonical(key: String): String? = when {
        byKey.containsKey(key) -> key
        ALIASES.containsKey(key) -> ALIASES[key]
        else -> null
    }

    fun field(key: String): ConsoleField? = canonical(key)?.let { byKey[it] }

    fun coerce(field: ConsoleField, raw: Any?): ConsoleValue = when (field.type) {
        ConsoleFieldType.STRING -> when {
            raw is String -> ConsoleValue.Valid(raw)
            field.structured && raw is List<*> && raw.all { it is Map<*, *> } -> ConsoleValue.Valid(raw)
            else -> ConsoleValue.BadType
        }

        ConsoleFieldType.FLOAT -> when (raw) {
            is Double -> float(field, raw)
            is Int -> float(field, raw.toDouble())
            else -> ConsoleValue.BadType
        }

        ConsoleFieldType.INT -> when {
            raw is Int -> int(field, raw.toLong())
            raw is Double && raw == Math.floor(raw) &&
                raw >= Int.MIN_VALUE.toDouble() && raw <= Int.MAX_VALUE.toDouble() -> int(field, raw.toLong())
            else -> ConsoleValue.BadType
        }

        ConsoleFieldType.BOOLEAN ->
            if (raw is Boolean) ConsoleValue.Valid(raw) else ConsoleValue.BadType

        ConsoleFieldType.ENUM -> {
            val text = raw as? String
            if (text == null) {
                ConsoleValue.BadType
            } else {
                val normalized = field.allowed.firstOrNull { it.equals(text.trim(), ignoreCase = true) }
                if (normalized != null) ConsoleValue.Valid(normalized) else ConsoleValue.BadValue(field.allowed)
            }
        }
    }

    /** 非有限值（1e400 之类的 JSON 溢出）一律按类型错误拒绝，不能写进请求体。 */
    private fun float(field: ConsoleField, raw: Double): ConsoleValue {
        if (!raw.isFinite()) return ConsoleValue.BadType
        if (outOfRange(field, raw)) return ConsoleValue.OutOfRange(field.min, field.max)
        return ConsoleValue.Valid(raw.toFloat())
    }

    private fun int(field: ConsoleField, raw: Long): ConsoleValue {
        if (outOfRange(field, raw.toDouble())) return ConsoleValue.OutOfRange(field.min, field.max)
        return ConsoleValue.Valid(raw.toInt())
    }

    private fun outOfRange(field: ConsoleField, value: Double): Boolean {
        val min = field.min
        val max = field.max
        return (min != null && value < min) || (max != null && value > max)
    }
}
