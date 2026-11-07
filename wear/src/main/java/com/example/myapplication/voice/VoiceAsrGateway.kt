package com.example.myapplication.voice

/**
 * 语音转写编排器：路由 -> 录音 -> 调用 provider。
 * 所有失败分支都必须给出可读中文原因，绝不返回空串，也绝不吞掉异常。
 */
class VoiceAsrGateway(
    private val capture: AudioCapture,
    private val providers: Map<AsrProviderId, AsrProvider> = emptyMap(),
    private val transport: HttpTransport = JvmHttpTransport(),
    private val sampleRate: Int = 16_000,
    private val maxDurationMs: Int = 8_000
) {

    suspend fun transcribe(
        settings: AsrSettings,
        systemRecognizerAvailable: Boolean,
        finishRequested: () -> Boolean = { false }
    ): AsrResult {
        return when (val route = AsrServiceRouter.resolve(settings, systemRecognizerAvailable)) {
            is AsrRoute.Unavailable -> AsrResult.Failure(route.reason)
            is AsrRoute.UseProvider -> runBytePipeline(settings, route.providerId, finishRequested)
        }
    }

    private suspend fun runBytePipeline(
        settings: AsrSettings,
        providerId: AsrProviderId,
        finishRequested: () -> Boolean
    ): AsrResult {
        if (providerId == AsrProviderId.SYSTEM) {
            return AsrResult.Failure(AsrMessages.SYSTEM_PLATFORM_ENTRY)
        }
        val provider = providerFor(settings) ?: return AsrResult.Failure(AsrMessages.NO_PROVIDER)

        val audio = capture.recordWav(sampleRate, maxDurationMs, finishRequested)
        val wav = audio.getOrNull()
        if (wav == null) {
            val error = audio.exceptionOrNull()
            val reason = error?.message?.takeIf { it.isNotBlank() } ?: AsrMessages.RECORD_FAILED
            return AsrResult.Failure(reason)
        }

        val result = try {
            provider.transcribe(wav, sampleRate)
        } catch (e: Exception) {
            return AsrResult.Failure("语音转写异常：" + (e.message ?: e.javaClass.simpleName))
        }

        return when (result) {
            is AsrResult.Success -> result
            is AsrResult.Failure -> AsrResult.Failure(
                result.reason.takeIf { it.isNotBlank() } ?: AsrMessages.UNKNOWN,
                result.code
            )
        }
    }

    private fun providerFor(settings: AsrSettings): AsrProvider? = when (settings.providerId) {
        AsrProviderId.BAIDU -> providers[AsrProviderId.BAIDU]
            ?: BaiduAsrProvider(
                apiKey = settings.baiduApiKey,
                secretKey = settings.baiduSecretKey,
                devPid = settings.language.devPid,
                transport = transport
            )
        AsrProviderId.SYSTEM -> providers[AsrProviderId.SYSTEM]
    }
}
