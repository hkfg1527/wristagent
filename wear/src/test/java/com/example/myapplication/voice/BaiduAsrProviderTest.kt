package com.example.myapplication.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class BaiduAsrProviderTest {

    private class QueueTransport(private val outcomes: MutableList<HttpOutcome>) : HttpTransport {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String?>()

        override fun execute(url: String, method: String, jsonBody: String?): HttpOutcome {
            urls.add(url)
            bodies.add(jsonBody)
            return if (outcomes.isEmpty()) HttpOutcome(0, null, "no queued response") else outcomes.removeAt(0)
        }

        fun tokenCalls(): Int = urls.count { it.startsWith(BaiduAsrProvider.TOKEN_URL) }
        fun asrCalls(): Int = urls.count { it.startsWith(BaiduAsrProvider.ASR_URL) }
    }

    private val oauthOk = HttpOutcome(
        200,
        "{\"access_token\":\"24.token-1\",\"expires_in\":2592000,\"refresh_token\":\"25.x\"}",
        null
    )
    private val asrOk = HttpOutcome(
        200,
        "{\"err_no\":0,\"err_msg\":\"success.\",\"sn\":\"1234\",\"result\":[\"你好世界\"]}",
        null
    )

    private fun newProvider(
        transport: HttpTransport,
        apiKey: String = "AK-TEST",
        secretKey: String = "SK-TEST",
        clock: () -> Long = { 1_700_000_000_000L }
    ): BaiduAsrProvider = BaiduAsrProvider(
        apiKey = apiKey,
        secretKey = secretKey,
        devPid = 1537,
        transport = transport,
        tokenCache = BaiduTokenCache(clock),
        cuid = "watch-test"
    )

    @Test
    fun blankKeysReturnChineseReasonWithoutAnyNetworkCall() = runBlocking {
        val transport = QueueTransport(mutableListOf())
        val provider = newProvider(transport, apiKey = "  ", secretKey = "")

        val result = provider.transcribe(ByteArray(64), 16000)

        assertTrue(result is AsrResult.Failure)
        val reason = (result as AsrResult.Failure).reason
        assertTrue("原因不能为空串", reason.isNotBlank())
        assertTrue("应提示缺少密钥: " + reason, reason.contains("密钥"))
        assertEquals(0, transport.urls.size)
    }

    @Test
    fun successReturnsRecognizedText() = runBlocking {
        val transport = QueueTransport(mutableListOf(oauthOk, asrOk))
        val provider = newProvider(transport)

        val result = provider.transcribe(ByteArray(64), 16000)

        assertEquals(AsrResult.Success("你好世界"), result)
    }

    @Test
    fun requestCarriesDocumentedBaiduFields() = runBlocking {
        val transport = QueueTransport(mutableListOf(oauthOk, asrOk))
        val provider = newProvider(transport)
        val wav = WavEncoder.encode(ByteArray(64) { 7 }, 16000)

        provider.transcribe(wav, 16000)

        val tokenUrl = transport.urls.first { it.startsWith(BaiduAsrProvider.TOKEN_URL) }
        assertTrue(tokenUrl.contains("grant_type=client_credentials"))
        assertTrue(tokenUrl.contains("client_id=AK-TEST"))
        assertTrue(tokenUrl.contains("client_secret=SK-TEST"))

        val body = transport.bodies.last().orEmpty()
        assertTrue("缺少 format: " + body, body.contains("\"format\":\"wav\""))
        assertTrue("缺少 rate: " + body, body.contains("\"rate\":16000"))
        assertTrue("缺少 channel: " + body, body.contains("\"channel\":1"))
        assertTrue("缺少 cuid: " + body, body.contains("\"cuid\":\"watch-test\""))
        assertTrue("缺少 dev_pid: " + body, body.contains("\"dev_pid\":1537"))
        assertTrue("缺少 token: " + body, body.contains("\"token\":\"24.token-1\""))
        assertTrue("缺少 speech: " + body, body.contains("\"speech\":\"" + Base64.getEncoder().encodeToString(wav) + "\""))
        assertTrue("缺少 len: " + body, body.contains("\"len\":" + wav.size))
    }

    @Test
    fun tokenIsFetchedOnceAndReused() = runBlocking {
        val transport = QueueTransport(mutableListOf(oauthOk, asrOk, asrOk))
        val provider = newProvider(transport)

        provider.transcribe(ByteArray(64), 16000)
        provider.transcribe(ByteArray(64), 16000)

        assertEquals(1, transport.tokenCalls())
        assertEquals(2, transport.asrCalls())
    }

    @Test
    fun expiredTokenIsRefreshedBeforeNextRequest() = runBlocking {
        var now = 1_700_000_000_000L
        val refreshedOauth = HttpOutcome(200, "{\"access_token\":\"24.token-2\",\"expires_in\":2592000}", null)
        val transport = QueueTransport(mutableListOf(oauthOk, asrOk, refreshedOauth, asrOk))
        val provider = newProvider(transport) { now }

        provider.transcribe(ByteArray(64), 16000)
        now += 30L * 24 * 3600 * 1000
        provider.transcribe(ByteArray(64), 16000)

        assertEquals(2, transport.tokenCalls())
        assertTrue(transport.bodies.last().orEmpty().contains("\"token\":\"24.token-2\""))
    }

    @Test
    fun asrErrorCodeMapsToChineseText() = runBlocking {
        val asrError = HttpOutcome(
            200,
            "{\"err_no\":3301,\"err_msg\":\"speech quality error.\",\"sn\":\"9\"}",
            null
        )
        val transport = QueueTransport(mutableListOf(oauthOk, asrError))
        val provider = newProvider(transport)

        val result = provider.transcribe(ByteArray(64), 16000)

        val failure = result as AsrResult.Failure
        assertEquals(3301, failure.code)
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue("应命中 3301 文案: " + failure.reason, failure.reason.equals(BaiduErrorMessages.forCode(3301)))
    }

    @Test
    fun oauthFailureReportsAuthProblemWithoutCallingAsr() = runBlocking {
        val oauthFail = HttpOutcome(
            401,
            "{\"error\":\"invalid_client\",\"error_description\":\"unknown client id\"}",
            null
        )
        val transport = QueueTransport(mutableListOf(oauthFail))
        val provider = newProvider(transport)

        val result = provider.transcribe(ByteArray(64), 16000)

        val failure = result as AsrResult.Failure
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue("应提示密钥或鉴权问题: " + failure.reason, failure.reason.contains("密钥"))
        assertEquals(0, transport.asrCalls())
    }

    @Test
    fun transportFailureReportsNetworkProblem() = runBlocking {
        val transport = QueueTransport(mutableListOf(HttpOutcome(0, null, "connect timed out")))
        val provider = newProvider(transport)

        val result = provider.transcribe(ByteArray(64), 16000)

        val failure = result as AsrResult.Failure
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue("应提示网络问题: " + failure.reason, failure.reason.contains("网络"))
    }

    @Test
    fun emptyResultListReportsNoSpeech() = runBlocking {
        val emptyResult = HttpOutcome(
            200,
            "{\"err_no\":0,\"err_msg\":\"success.\",\"sn\":\"1\",\"result\":[]}",
            null
        )
        val transport = QueueTransport(mutableListOf(oauthOk, emptyResult))
        val provider = newProvider(transport)

        val result = provider.transcribe(ByteArray(64), 16000)

        val failure = result as AsrResult.Failure
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue("应提示未识别到语音: " + failure.reason, failure.reason.contains("未识别"))
    }

    @Test
    fun unparsableBodyReturnsReasonInsteadOfThrowing() = runBlocking {
        val broken = HttpOutcome(200, "<html>gateway error</html>", null)
        val transport = QueueTransport(mutableListOf(oauthOk, broken))
        val provider = newProvider(transport)

        val result = provider.transcribe(ByteArray(64), 16000)

        val failure = result as AsrResult.Failure
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue("应提示返回内容异常: " + failure.reason, failure.reason.contains("解析"))
    }

    @Test
    fun errorCodesProduceDistinctChineseMessages() {
        val distinct = setOf(
            BaiduErrorMessages.forCode(3300),
            BaiduErrorMessages.forCode(3301),
            BaiduErrorMessages.forCode(3302),
            BaiduErrorMessages.forCode(3305),
            BaiduErrorMessages.forCode(3307)
        )
        assertEquals("不同错误码必须给出不同文案", 5, distinct.size)
        for (message in distinct) {
            assertTrue("文案不能为空", message.isNotBlank())
            assertTrue("文案必须含中文: " + message, message.any { it.code in 0x4E00..0x9FFF })
        }
    }

    @Test
    fun unknownErrorCodeStillCarriesCodeAndChineseText() {
        val message = BaiduErrorMessages.forCode(3999)
        assertTrue(message.contains("3999"))
        assertTrue(message.any { it.code in 0x4E00..0x9FFF })
        assertNotEquals(message, BaiduErrorMessages.forCode(3301))
        assertFalse(message.isBlank())
    }
}
