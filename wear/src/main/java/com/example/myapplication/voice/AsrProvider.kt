package com.example.myapplication.voice

/**
 * 语音转写统一契约：调用方只关心「给一段 WAV，拿到文字或可读的中文失败原因」。
 * 平台识别器是流式回调，不走字节管道；字节管道专供可插拔的第三方 ASR。
 */
interface AsrProvider {
    val providerId: AsrProviderId

    /**
     * @param wavBytes 16kHz 单声道 16bit 的完整 WAV 字节流（含 44 字节 RIFF 头）
     * @param sampleRate 采样率，用于构造请求体
     */
    suspend fun transcribe(wavBytes: ByteArray, sampleRate: Int): AsrResult
}

/** 语音服务来源；持久化为 storageKey 字符串，避免枚举序号漂移 */
enum class AsrProviderId(val storageKey: String, val displayName: String) {
    SYSTEM("SYSTEM", "系统识别"),
    BAIDU("BAIDU", "百度短语音");

    companion object {
        fun fromStorage(raw: String?): AsrProviderId {
            val target = raw?.trim().orEmpty()
            return values().firstOrNull { it.storageKey.equals(target, ignoreCase = true) } ?: SYSTEM
        }
    }
}

sealed interface AsrResult {
    data class Success(val text: String) : AsrResult
    data class Failure(val reason: String, val code: Int? = null) : AsrResult
}

/** 可选识别语言，devPid 对应百度短语音识别的 dev_pid 参数 */
enum class AsrLanguage(val devPid: Int, val displayName: String) {
    MANDARIN(1537, "普通话"),
    ENGLISH(1737, "英语"),
    CANTONESE(1637, "粤语"),
    SICHUAN(1837, "四川话");

    companion object {
        val DEFAULT: AsrLanguage = MANDARIN
        fun fromDevPid(devPid: Int): AsrLanguage = values().firstOrNull { it.devPid == devPid } ?: DEFAULT
    }
}

/** 语音转写运行期配置；不依赖 Android，可直接纯单测 */
data class AsrSettings(
    val enabled: Boolean = false,
    val providerId: AsrProviderId = AsrProviderId.SYSTEM,
    val baiduApiKey: String = "",
    val baiduSecretKey: String = "",
    val language: AsrLanguage = AsrLanguage.DEFAULT
) {
    val hasBaiduCredentials: Boolean
        get() = baiduApiKey.isNotBlank() && baiduSecretKey.isNotBlank()
}

/** 语音失败文案的唯一来源，任何分支都不得返回空串 */
object AsrMessages {
    const val UNKNOWN = "语音识别失败，原因未知，请重试"
    const val DISABLED = "语音转写已关闭，请到 设置-语音转写 打开开关"
    const val NO_MIC_PERMISSION = "未授予麦克风权限，请在系统设置中允许本应用录音"
    const val NO_ENGINE = "设备未安装系统语音识别服务，请在设置中改用百度语音并填写密钥"
    const val NO_BAIDU_KEY = "未配置百度语音密钥（API Key / Secret Key），请在 设置-语音转写 填写"
    const val BAIDU_FALLBACK_NOTE = "未配置百度语音密钥，已自动回退系统识别"
    const val SYSTEM_PLATFORM_ENTRY = "系统语音识别由平台识别器完成，请使用系统语音入口"
    const val EMPTY_RESULT = "未识别到有效语音，请重说一遍"
    const val EMPTY_AUDIO = "没有录到声音，请靠近麦克风重试"
    const val RECORD_FAILED = "录音失败，请检查麦克风是否被占用"
    const val UNSUPPORTED_SAMPLE_RATE = "麦克风不支持 16kHz 单声道录音"
    const val NO_PROVIDER = "没有可用的语音转写服务，请到设置中检查语音转写配置"
    const val CANCELLED = "录音已取消"
    const val BAIDU_BAD_RESPONSE = "百度语音返回内容无法解析，请稍后重试"
    const val BAIDU_EMPTY_RESULT = "百度语音未识别到有效语音，请重说一遍"
}
