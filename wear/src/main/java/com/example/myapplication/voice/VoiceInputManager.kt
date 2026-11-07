package com.example.myapplication.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * 手表语音输入交互管理器（系统识别通道 + 可插拔第三方 ASR 通道）
 *
 * 约定：任何失败都必须通过 onSpeechError 交付一条非空的中文原因，
 * 上层只需把该字符串显示出来，不需要再判断错误类型。
 */
class VoiceInputManager(
    private val context: Context,
    private val asrGateway: VoiceAsrGateway? = null,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null
    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null
    private val session = AsrSession(scope)

    val sampleVoiceCommands = listOf(
        "帮我检查手表的电量和当前天气。如果电量充足且天气晴朗，就把表盘点阵网格调整为微距14dp，并双击振动马达提醒我出门散步。",
        "诊断当前电池电量和温控，如果正常，开启极致省电模式并短脉冲振动确认。",
        "把表盘点阵网格密度调整为20dp宽广模式，并触发一次长振动。",
        "查询当前本地系统时间和天气状况。"
    )

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 系统识别通道：启动平台 SpeechRecognizer
     */
    fun startListening() {
        if (!hasAudioPermission()) {
            reportError(AsrMessages.NO_MIC_PERMISSION)
            return
        }
        if (!isRecognitionAvailable()) {
            reportError(AsrMessages.NO_ENGINE)
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        onListeningStateChanged?.invoke(true)
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        onListeningStateChanged?.invoke(false)
                    }

                    override fun onError(error: Int) {
                        reportError(describeRecognizerError(error))
                    }

                    override fun onResults(results: Bundle?) {
                        onListeningStateChanged?.invoke(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            onSpeechResult?.invoke(text)
                        } else {
                            reportError(AsrMessages.EMPTY_RESULT)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {}

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: SecurityException) {
            reportError(AsrMessages.NO_MIC_PERMISSION)
        } catch (e: IllegalStateException) {
            reportError("启动语音录入失败：" + describeThrowable(e))
        } catch (e: RuntimeException) {
            reportError("启动语音录入失败：" + describeThrowable(e))
        }
    }

    /**
     * 第三方转写通道：录音 16kHz WAV 后交给可插拔 provider（百度等）
     */
    fun startThirdPartyListening(settings: AsrSettings) {
        if (!hasAudioPermission()) {
            reportError(AsrMessages.NO_MIC_PERMISSION)
            return
        }
        val gateway = asrGateway
        if (gateway == null) {
            reportError(AsrMessages.NO_PROVIDER)
            return
        }

        onListeningStateChanged?.invoke(true)
        session.launch {
            val result = gateway.transcribe(settings, isRecognitionAvailable())
            // 取消后麦克风已释放，再弹"录音失败"只会误导用户
            if (session.isCancelled) return@launch
            onListeningStateChanged?.invoke(false)
            when (result) {
                is AsrResult.Success -> {
                    val text = result.text.trim()
                    if (text.isBlank()) {
                        reportError(AsrMessages.EMPTY_RESULT)
                    } else {
                        onSpeechResult?.invoke(text)
                    }
                }
                is AsrResult.Failure -> reportError(result.reason)
            }
        }
    }

    fun stopListening() {
        val recognizer = speechRecognizer
        if (recognizer != null) {
            try {
                recognizer.stopListening()
            } catch (e: IllegalStateException) {
                reportError("停止语音识别失败：" + describeThrowable(e))
            }
        }
        onListeningStateChanged?.invoke(false)
    }

    fun cancel() {
        session.cancel()
        stopListening()
    }

    fun destroy() {
        session.cancel()
        scope.cancel()
        val recognizer = speechRecognizer
        speechRecognizer = null
        if (recognizer != null) {
            try {
                recognizer.destroy()
            } catch (e: IllegalStateException) {
                reportError("释放语音识别器失败：" + describeThrowable(e))
            } catch (e: RuntimeException) {
                reportError("释放语音识别器失败：" + describeThrowable(e))
            }
        }
        onListeningStateChanged?.invoke(false)
    }

    /** 唯一错误出口：兜底空串，保证上层永远拿到可显示的文字 */
    private fun reportError(reason: String) {
        onListeningStateChanged?.invoke(false)
        onSpeechError?.invoke(reason.takeIf { it.isNotBlank() } ?: AsrMessages.UNKNOWN)
    }

    private fun describeThrowable(error: Throwable): String =
        error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    private fun describeRecognizerError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> AsrMessages.NO_MIC_PERMISSION
        SpeechRecognizer.ERROR_NO_MATCH -> AsrMessages.EMPTY_RESULT
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "没有听到说话声，请靠近麦克风重试"
        SpeechRecognizer.ERROR_AUDIO -> "录音失败，麦克风可能被其他应用占用"
        SpeechRecognizer.ERROR_NETWORK -> "语音识别网络不可用，请检查手表网络"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "语音识别网络超时，请重试"
        SpeechRecognizer.ERROR_SERVER -> "语音识别服务出错，请稍后重试"
        SpeechRecognizer.ERROR_CLIENT -> "语音识别客户端异常，请重试"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "语音识别器正忙，请稍后重试"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "语音识别请求过于频繁，请稍后重试"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "与语音识别服务断开连接，请重试"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "当前语言不被语音识别服务支持"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "当前语言暂不可用，请改选其他语言"
        else -> "语音识别异常（错误码 " + error + "），请重试"
    }
}
