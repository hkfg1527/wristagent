package com.example.myapplication.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * E7：3308–3317 这 10 个未核实语义的错误码必须映射成用户读得懂的中文，
 * 且任何失败原因都不得把 appId / API Key / Secret Key / access_token 回传给用户。
 */
class BaiduAsrErrorCodeMappingTest {

    private val unconfirmedCodes = 3308..3317

    private val sentinelSecrets = listOf("AK-LEAK-9f3a", "SK-LEAK-7c1d", "24.token-1")

    private class QueueTransport(private val outcomes: MutableList<HttpOutcome>) : HttpTransport {
        override fun execute(url: String, method: String, jsonBody: String?): HttpOutcome =
            if (outcomes.isEmpty()) HttpOutcome(0, null, "no queued response") else outcomes.removeAt(0)
    }

    private val oauthOk =
        HttpOutcome(200, "{\"access_token\":\"24.token-1\",\"expires_in\":2592000}", null)

    private fun asrError(code: Int, errMsg: String = "speech decode error.") = HttpOutcome(
        200,
        "{\"err_no\":" + code + ",\"err_msg\":\"" + errMsg + "\",\"sn\":\"9\"}",
        null
    )

    private fun newProvider(transport: HttpTransport) = BaiduAsrProvider(
        apiKey = "AK-LEAK-9f3a",
        secretKey = "SK-LEAK-7c1d",
        devPid = 1537,
        transport = transport,
        tokenCache = BaiduTokenCache { 1_700_000_000_000L },
        cuid = "watch-test"
    )

    @Test
    fun everyUnconfirmedCodeMapsToItsOwnReadableChineseSentence() {
        val messages = unconfirmedCodes.map { BaiduErrorMessages.forCode(it) }

        assertEquals("10 个码必须给出 10 句不同文案", unconfirmedCodes.count(), messages.toSet().size)
        unconfirmedCodes.zip(messages).forEach { (code, message) ->
            assertTrue("文案不能为空串: " + code, message.isNotBlank())
            assertTrue("文案必须带出错误码 " + code + ": " + message, message.contains(code.toString()))
            assertTrue("文案必须是中文: " + message, message.any { it.code in 0x4E00..0x9FFF })
            assertTrue("必须走已归类文案而非未知兜底: " + message, message.contains("返回错误 " + code))
        }
    }

    @Test
    fun unconfirmedCodesStayUnknownSoServerDetailIsKept() {
        unconfirmedCodes.forEach { code ->
            assertFalse(
                "3308–3317 语义未核实，必须保持未知以便附带服务端 err_msg: " + code,
                BaiduErrorMessages.isKnown(code)
            )
        }
    }

    @Test
    fun failureReasonNeverLeaksCredentialsOrToken() = runBlocking {
        unconfirmedCodes.forEach { code ->
            val transport = QueueTransport(mutableListOf(oauthOk, asrError(code)))

            val result = newProvider(transport).transcribe(ByteArray(64), 16000)

            val failure = result as AsrResult.Failure
            assertEquals(code, failure.code)
            assertTrue("失败原因不能为空串: " + code, failure.reason.isNotBlank())
            sentinelSecrets.forEach { secret ->
                assertFalse(
                    "失败原因泄露敏感串 " + secret + ": " + failure.reason,
                    failure.reason.contains(secret)
                )
            }
            assertFalse(
                "失败原因不得带出 client_secret 字段: " + failure.reason,
                failure.reason.contains("client_secret")
            )
            assertTrue(
                "服务端 err_msg 应保留给排查: " + failure.reason,
                failure.reason.contains("speech decode error.")
            )
        }
    }
}
