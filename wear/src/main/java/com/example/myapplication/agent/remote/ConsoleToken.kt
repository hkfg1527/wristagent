package com.example.myapplication.agent.remote

import java.security.SecureRandom

/**
 * 控制台访问令牌：32 位小写十六进制。
 * 持久化由 ConsoleTokenStore 负责，本对象只保留可纯逻辑测试的生成/校验/掩码。
 */
object ConsoleToken {

    const val LENGTH = 32

    private const val HEX = "0123456789abcdef"

    private val random = SecureRandom()

    fun generate(): String {
        val builder = StringBuilder(LENGTH)
        repeat(LENGTH) { builder.append(HEX[random.nextInt(HEX.length)]) }
        return builder.toString()
    }

    fun isValidFormat(token: String?): Boolean {
        if (token == null || token.length != LENGTH) return false
        return token.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
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

    /** 展示用掩码 ab12…ef34；长度不足时整体隐藏。 */
    fun mask(token: String?): String {
        val text = token.orEmpty()
        return if (text.length >= 12) text.take(4) + "\u2026" + text.takeLast(4) else "\u2026"
    }
}
