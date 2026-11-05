package com.example.myapplication.agent

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * 自动播报的 Android 侧出口。
 *
 * TTS 引擎缺失、初始化失败、语言包缺失、合成报错一律只记日志：
 * 播报是锦上添花，任何一步失败都不允许把对话链路带崩。
 */
class ReplySpeaker(private val context: Context) {

    private var engine: TextToSpeech? = null
    private var ready = false
    private var utteranceSeq = 0

    fun speak(text: String) {
        if (text.isBlank()) return
        try {
            val tts = ensureEngine()
            if (!ready) {
                Log.i(TAG, "TTS 未就绪，跳过本次播报")
                return
            }
            utteranceSeq++
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply-" + utteranceSeq)
        } catch (t: Throwable) {
            Log.w(TAG, "播报失败，忽略：" + t.javaClass.simpleName)
        }
    }

    fun release() {
        try {
            engine?.stop()
            engine?.shutdown()
        } catch (_: Throwable) {
        } finally {
            engine = null
            ready = false
        }
    }

    private fun ensureEngine(): TextToSpeech {
        engine?.let { return it }
        val created = TextToSpeech(context) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                // 语言包缺失不致命：合成时会退化为引擎默认语言，失败也只记日志
                runCatching { engine?.language = Locale.getDefault() }
            } else {
                Log.i(TAG, "本机没有可用的 TTS 引擎")
            }
        }
        engine = created
        return created
    }

    private companion object {
        const val TAG = "ReplySpeaker"
    }
}
