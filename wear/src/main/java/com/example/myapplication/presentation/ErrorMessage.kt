package com.example.myapplication.presentation

/**
 * 错误文案的唯一下沉点：把 provider 原始报错压成一句人话。
 *
 * 背景：改这一版之前 HTTP 状态码与 provider 的原始 JSON 会被拼进异常消息，
 * 再原样铺进聊天气泡（占屏 55%、对比 21:1），成了整屏最亮最大的一块。
 * 现在界面上只允许出现「一句结论 + 一句该怎么办」，原始 JSON 永不进气泡。
 *
 * 纯 Kotlin、无 Android 依赖，因此可以在 JVM 单测里逐条钉死。
 */
object ErrorMessage {

    /** [hint] 为 null 表示没有可给的建议，界面只显示 [headline]。 */
    data class Friendly(val headline: String, val hint: String?)

    /** 界面唯一入口：一行「结论 · 建议」，任何情况下都不含大括号。 */
    fun display(raw: String?): String {
        val friendly = of(raw)
        return if (friendly.hint.isNullOrBlank()) friendly.headline else friendly.headline + " · " + friendly.hint
    }

    fun of(raw: String?): Friendly {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return Friendly(HEADLINE_FAILED, null)
        val lower = text.lowercase()

        // 顺序即优先级：先认具体原因，最后才落到兜底
        return when {
            containsAny(lower, "insufficient_balance", "insufficient balance", "insufficient_quota") || codeIs(lower, 402) ->
                Friendly("余额不足", "充值后重试")
            containsAny(lower, "invalid api key", "incorrect api key", "unauthorized", "authentication", "401") ->
                Friendly("密钥无效", "检查 API Key")
            containsAny(lower, "rate limit", "too many requests", "429") ->
                Friendly("请求太频繁", "稍后重试")
            containsAny(lower, "timed out", "timeout", "socket timeout") ->
                Friendly("连接超时", "检查网络或地址")
            containsAny(lower, "unknownhost", "unable to resolve host", "enabled to resolve") ->
                Friendly("域名解析失败", "检查 Base URL")
            containsAny(lower, "model not found", "does not exist", "no such model") || codeIs(lower, 404) ->
                Friendly("模型不可用", "在设置里换一个模型")
            else -> Friendly(HEADLINE_FAILED, extract(text))
        }
    }

    private const val HEADLINE_FAILED = "请求失败"
    private const val HINT_MAX = 80

    private val messageField = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"")
    private val httpPrefix = Regex("^HTTP \\d{3}:\\s*")

    private fun containsAny(text: String, vararg needles: String): Boolean =
        needles.any { text.contains(it) }

    /** 只认独立的 3 位状态码，避免 "4021" 之类误命中。 */
    private fun codeIs(text: String, code: Int): Boolean =
        Regex("(^|\\D)" + code + "(\\D|$)").containsMatchIn(text)

    /** 先取 JSON 里的 message 字段（最接近人话的一层），再退回首行文本。 */
    private fun extract(text: String): String? {
        messageField.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it.take(HINT_MAX)
        }
        val firstLine = httpPrefix.replace(text, "").lineSequence().firstOrNull().orEmpty()
        val cleaned = firstLine.trim().trim('{', '}', '"', ' ', '\n', '\r')
        return cleaned.takeIf { it.isNotEmpty() }?.take(HINT_MAX)
    }
}
