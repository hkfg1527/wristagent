package com.example.myapplication.agent.remote

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
 */
data class ConsoleField(
    val key: String,
    val type: ConsoleFieldType,
    val readOnly: Boolean = false,
    val allowed: List<String> = emptyList(),
    val structured: Boolean = false,
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
        ConsoleField("temperature", ConsoleFieldType.FLOAT),
        ConsoleField("maxOutputTokens", ConsoleFieldType.INT),
        ConsoleField("customSystemPrompt", ConsoleFieldType.STRING),
        ConsoleField("securityMode", ConsoleFieldType.ENUM, allowed = SecurityMode.values().map { it.name }),
        ConsoleField("maxContextTurns", ConsoleFieldType.INT),
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
        ConsoleField("matrixSpacingDp", ConsoleFieldType.FLOAT),
        ConsoleField("mainInputType", ConsoleFieldType.STRING),
        ConsoleField("streamMode", ConsoleFieldType.BOOLEAN),
        ConsoleField("animationsEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("subAgentEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("visionBetaEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("compactMode", ConsoleFieldType.BOOLEAN),
        ConsoleField("providerManagedSearchEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("autoSpeakReplies", ConsoleFieldType.BOOLEAN),
        ConsoleField("asrLiveTranscript", ConsoleFieldType.BOOLEAN),
        ConsoleField("defaultThinkingEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("memoryEnabled", ConsoleFieldType.BOOLEAN),
        ConsoleField("autoCompressContext", ConsoleFieldType.BOOLEAN),
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
            is Double -> ConsoleValue.Valid(raw.toFloat())
            is Int -> ConsoleValue.Valid(raw.toFloat())
            else -> ConsoleValue.BadType
        }

        ConsoleFieldType.INT -> when {
            raw is Int -> ConsoleValue.Valid(raw)
            raw is Double && raw == Math.floor(raw) &&
                raw >= Int.MIN_VALUE.toDouble() && raw <= Int.MAX_VALUE.toDouble() -> ConsoleValue.Valid(raw.toInt())
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
}
