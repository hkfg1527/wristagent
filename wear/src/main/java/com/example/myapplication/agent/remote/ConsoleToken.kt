package com.example.myapplication.agent.remote

import java.security.SecureRandom

/**
 * 控制台访问口令：4 位数字验证码。
 *
 * 为什么不沿用 32 位十六进制：手表上没有键盘，用户要把口令敲进手机浏览器，
 * 32 位随机串等于逼用户每次复制粘贴；4 位数字在「同一 Wi-Fi + 手表在手边」
 * 的实际威胁模型下够用，代价是猜测空间只有 10000，因此服务端必须限流（见 ConsoleServer 的 401 计数）。
 *
 * 持久化由 ConsoleTokenStore 负责，本对象只保留可纯逻辑测试的生成/校验/掩码。
 */
object ConsoleToken {

    /** 验证码位数。 */
    const val LENGTH = 4

    private const val DIGITS = "0123456789"

    private val random = SecureRandom()

    fun generate(): String {
        val builder = StringBuilder(LENGTH)
        repeat(LENGTH) { builder.append(DIGITS[random.nextInt(DIGITS.length)]) }
        return builder.toString()
    }

    fun isValidFormat(token: String?): Boolean {
        if (token == null || token.length != LENGTH) return false
        return token.all { it in '0'..'9' }
    }

    /** 定长比较：不因前缀相同而提前返回。 */
    fun matches(expected: String?, candidate: String?): Boolean {
        if (expected.isNullOrEmpty() || candidate == null) return false
        var diff = expected.length xor candidate.length
        for (index in expected.indices) {
            val other = if (index < candidate.length) candidate[index] else '\u0000'
            diff = diff or (expected[index].code xor other.code)
        }
        return diff == 0
    }

    /**
     * 展示用掩码：4 位验证码没有可遮蔽的余量（遮住等于不给用户看），直接回原样；
     * 只有历史遗留的长口令才折叠成 ab12…ef34。
     */
    fun mask(token: String?): String {
        val text = token.orEmpty()
        return when {
            text.isEmpty() -> "\u2026"
            text.length <= LENGTH -> text
            text.length >= 12 -> text.take(4) + "\u2026" + text.takeLast(4)
            else -> "\u2026"
        }
    }
}
