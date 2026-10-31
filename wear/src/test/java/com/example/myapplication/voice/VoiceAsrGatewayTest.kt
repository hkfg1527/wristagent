package com.example.myapplication.voice

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class VoiceAsrGatewayTest {

    private class FakeProvider(
        private val id: AsrProviderId,
        private val result: AsrResult
    ) : AsrProvider {
        override val providerId: AsrProviderId = id
        var receivedWav: ByteArray? = null
        var callCount: Int = 0

        override suspend fun transcribe(wavBytes: ByteArray, sampleRate: Int): AsrResult {
            callCount++
            receivedWav = wavBytes
            return result
        }
    }

    private class CountingCapture(private val payload: Result<ByteArray>) : AudioCapture {
        var callCount: Int = 0

        override suspend fun recordWav(sampleRate: Int, maxDurationMs: Int): Result<ByteArray> {
            callCount++
            return payload
        }
    }

    private val baiduReady = AsrSettings(
        enabled = true,
        providerId = AsrProviderId.BAIDU,
        baiduApiKey = "AK",
        baiduSecretKey = "SK"
    )

    @Test
    fun disabledSwitchNeverRecordsAndExplainsWhy() = runBlocking {
        val capture = CountingCapture(Result.success(ByteArray(8)))
        val gateway = VoiceAsrGateway(capture = capture, providers = emptyMap())

        val result = gateway.transcribe(baiduReady.copy(enabled = false), systemRecognizerAvailable = true)

        val failure = result as AsrResult.Failure
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue(failure.reason.contains("关闭"))
        assertEquals(0, capture.callCount)
    }

    @Test
    fun recordingFailureIsSurfacedAndProviderIsSkipped() = runBlocking {
        val capture = CountingCapture(Result.failure(IllegalStateException(AsrMessages.RECORD_FAILED)))
        val provider = FakeProvider(AsrProviderId.BAIDU, AsrResult.Success("不该被调用"))
        val gateway = VoiceAsrGateway(capture = capture, providers = mapOf(AsrProviderId.BAIDU to provider))

        val result = gateway.transcribe(baiduReady, systemRecognizerAvailable = false)

        val failure = result as AsrResult.Failure
        assertEquals(AsrMessages.RECORD_FAILED, failure.reason)
        assertEquals(1, capture.callCount)
        assertEquals(0, provider.callCount)
    }

    @Test
    fun happyPathPassesRecordedWavToProviderAndReturnsText() = runBlocking {
        val pcm = ByteArray(320)
        val wav = WavEncoder.encode(pcm, 16000)
        val capture = CountingCapture(Result.success(wav))
        val provider = FakeProvider(AsrProviderId.BAIDU, AsrResult.Success("打开手电筒"))
        val gateway = VoiceAsrGateway(capture = capture, providers = mapOf(AsrProviderId.BAIDU to provider))

        val result = gateway.transcribe(baiduReady, systemRecognizerAvailable = false)

        assertEquals(AsrResult.Success("打开手电筒"), result)
        assertEquals(1, provider.callCount)
        val forwarded = provider.receivedWav ?: ByteArray(0)
        assertEquals(wav.size, forwarded.size)
        assertEquals("RIFF", String(forwarded, 0, 4, StandardCharsets.US_ASCII))
    }

    @Test
    fun blankProviderReasonIsNormalizedToReadableText() = runBlocking {
        val wav = WavEncoder.encode(ByteArray(64), 16000)
        val capture = CountingCapture(Result.success(wav))
        val provider = FakeProvider(AsrProviderId.BAIDU, AsrResult.Failure("", null))
        val gateway = VoiceAsrGateway(capture = capture, providers = mapOf(AsrProviderId.BAIDU to provider))

        val result = gateway.transcribe(baiduReady, systemRecognizerAvailable = false)

        val failure = result as AsrResult.Failure
        assertEquals(AsrMessages.UNKNOWN, failure.reason)
    }

    @Test
    fun systemRouteDoesNotUseBytePipeline() = runBlocking {
        val capture = CountingCapture(Result.success(ByteArray(8)))
        val gateway = VoiceAsrGateway(capture = capture, providers = emptyMap())
        val systemSettings = AsrSettings(enabled = true, providerId = AsrProviderId.SYSTEM)

        val result = gateway.transcribe(systemSettings, systemRecognizerAvailable = true)

        val failure = result as AsrResult.Failure
        assertEquals(AsrMessages.SYSTEM_PLATFORM_ENTRY, failure.reason)
        assertEquals(0, capture.callCount)
    }

    @Test
    fun missingBaiduKeysAndNoEngineReportsMissingKeysWithoutRecording() = runBlocking {
        val capture = CountingCapture(Result.success(ByteArray(8)))
        val gateway = VoiceAsrGateway(capture = capture, providers = emptyMap())

        val result = gateway.transcribe(
            baiduReady.copy(baiduApiKey = "", baiduSecretKey = ""),
            systemRecognizerAvailable = false
        )

        val failure = result as AsrResult.Failure
        assertTrue("原因不能为空串", failure.reason.isNotBlank())
        assertTrue("应提示缺少密钥: " + failure.reason, failure.reason.contains("密钥"))
        assertEquals(0, capture.callCount)
    }
}
