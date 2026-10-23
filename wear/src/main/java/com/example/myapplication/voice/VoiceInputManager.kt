package com.example.myapplication.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat

/**
 * 手表语音输入交互管理器 (带模拟器智能语料降级与真实系统语音双通道)
 */
class VoiceInputManager(private val context: Context) {

    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null
    var onListeningStateChanged: ((Boolean) -> Unit)? = null

    private var speechRecognizer: SpeechRecognizer? = null

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
     * 启动语音识别
     */
    fun startListening() {
        if (!isRecognitionAvailable()) {
            onListeningStateChanged?.invoke(false)
            onSpeechError?.invoke("设备未安装语音识别服务")
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
                        onListeningStateChanged?.invoke(false)
                        val errorMsg = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH -> "未识别到语音输入"
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "未检测到说话声音"
                            SpeechRecognizer.ERROR_AUDIO -> "音频录制异常"
                            SpeechRecognizer.ERROR_NETWORK -> "语音网络服务不可用"
                            else -> "语音识别异常 ($error)"
                        }
                        onSpeechError?.invoke(errorMsg)
                    }

                    override fun onResults(results: Bundle?) {
                        onListeningStateChanged?.invoke(false)
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull()?.trim()
                        if (!text.isNullOrBlank()) {
                            onSpeechResult?.invoke(text)
                        } else {
                            onSpeechError?.invoke("未识别到有效文字")
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
        } catch (e: Exception) {
            onListeningStateChanged?.invoke(false)
            onSpeechError?.invoke("启动语音录入失败: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {}
        onListeningStateChanged?.invoke(false)
    }

    fun cancel() {
        stopListening()
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }
}
