package com.example.myapplication.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 错误文案映射：屏幕上永远只说人话。
 *
 * 背景：改这一版之前 provider 的原始 JSON 会被拼进异常消息，再原样铺进聊天气泡，
 * 占屏 55%、对比 21:1，成了整屏最亮最大的一块 —— 层级彻底倒置。
 */
class ErrorMessageTest {

    @Test
    fun insufficientBalanceIsNamedNotQuoted() {
        val json = "HTTP 402: {\"error\":{\"message\":\"Balance insufficient\",\"type\":\"insufficient_balance_error\"}}"
        val friendly = ErrorMessage.of(json)
        assertEquals("余额不足", friendly.headline)
        assertEquals("充值后重试", friendly.hint)
    }

    @Test
    fun errorCodeAloneIsEnoughToClassify() {
        assertEquals("余额不足", ErrorMessage.of("HTTP 402: Anything").headline)
        assertEquals("密钥无效", ErrorMessage.of("HTTP 401: nope").headline)
        assertEquals("请求太频繁", ErrorMessage.of("HTTP 429: slow down").headline)
    }

    @Test
    fun authAndRateLimitUseTheirOwnCopy() {
        assertEquals("密钥无效", ErrorMessage.of("Incorrect API key provided").headline)
        assertEquals("请求太频繁", ErrorMessage.of("rate limit reached").headline)
    }

    @Test
    fun networkFailuresAreDistinguished() {
        assertEquals("连接超时", ErrorMessage.of("connect timed out").headline)
        assertEquals("域名解析失败", ErrorMessage.of("java.net.UnknownHostException: api.x.com").headline)
        assertEquals("模型不可用", ErrorMessage.of("HTTP 404: model not found").headline)
    }

    @Test
    fun unknownFailureKeepsTheHumanSentenceAndDropsTheWrapper() {
        val friendly = ErrorMessage.of("HTTP 500: {\"message\":\"internal glitch\"}")
        assertEquals("请求失败", friendly.headline)
        assertEquals("internal glitch", friendly.hint)
    }

    @Test
    fun rawJsonNeverReachesTheScreen() {
        val json = "HTTP 400: {\"error\":{\"message\":\"bad param\",\"param\":null,\"code\":\"invalid_request_error\"}}"
        val shown = ErrorMessage.display(json)
        assertFalse("气泡里不许出现 JSON 大括号：" + shown, shown.contains("{"))
        assertFalse(shown.contains("}"))
        assertFalse(shown.contains("invalid_request_error"))
        assertTrue(shown.contains("bad param"))
    }

    @Test
    fun blankAndNullStillProduceAReadableLine() {
        assertEquals("请求失败", ErrorMessage.of(null).headline)
        assertEquals("请求失败", ErrorMessage.of("   ").headline)
        assertEquals("请求失败", ErrorMessage.display(null))
    }

    @Test
    fun longDetailIsTruncatedSoItCannotFloodTheScreen() {
        val long = "HTTP 500: " + "x".repeat(400)
        val friendly = ErrorMessage.of(long)
        assertTrue("hint 必须截断：" + (friendly.hint?.length ?: 0), (friendly.hint?.length ?: 0) <= 80)
    }
}
