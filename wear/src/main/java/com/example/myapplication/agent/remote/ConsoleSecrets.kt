package com.example.myapplication.agent.remote

/**
 * 控制台里的真实凭据：只在写入路径上落库，任何响应都只能给掩码。
 *
 * 掩码必须能被服务端自己认出来。网页把 /api/state 里的掩码塞进 input，用户改了别的字段后
 * 一次保存就会把掩码当新密钥写回，真钥当场被毁（ISSUES C2）——识别"这是我自己发的掩码"
 * 是服务端的职责，不能指望前端记得。
 */
object ConsoleSecrets {

    /** 响应里必须遮挡的键，与 ConsoleFields 的规范键同名。 */
    val KEYS: List<String> = listOf("apiKey", "baiduApiKey", "baiduSecretKey")

    const val ELLIPSIS: Char = '\u2026'

    fun isSecret(key: String): Boolean = KEYS.any { it == key }

    /**
     * 展示用掩码：任何非空值都不回明文。
     *
     * 不能复用 [ConsoleToken.mask]——那个是给 4 位访问码写的，短值直接回原样，
     * 拿去遮 4 位的密钥等于不遮。
     */
    fun mask(value: String?): String {
        val text = value.orEmpty()
        return when {
            text.isEmpty() -> ""
            text.length >= 12 -> text.take(4) + ELLIPSIS + text.takeLast(4)
            else -> ELLIPSIS.toString()
        }
    }

    /** 回传值是不是服务端自己发出的掩码（省略号风格，或前端惯用的 sk-****abcd 风格）。 */
    fun isMaskEcho(candidate: String, live: String?): Boolean {
        if (candidate.isEmpty()) return false
        if (candidate == mask(live)) return true
        if (candidate.indexOf(ELLIPSIS) >= 0) return true
        return candidate.contains("**")
    }
}
