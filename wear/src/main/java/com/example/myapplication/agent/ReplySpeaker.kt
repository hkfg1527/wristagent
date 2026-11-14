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

    /**
     * 朗读一段正文。
     *
     * [speechRate] 来自设置项 `tts_speech_rate`（[AgentPreferences.ttsSpeechRate]）。
     * 语速是引擎级状态而不是单条 utterance 的参数，因此每次合成前都要重设一遍：
     * 否则上一轮设过倍速、之后被别处改回 1.0，用户就会看到"设置还在 1.5 但读得越来越慢"。
     */
    fun speak(
        text: String,
        speechRate: Float = AgentPreferences.DEFAULT_TTS_SPEECH_RATE,
        volume: Float = AgentPreferences.DEFAULT_TTS_VOLUME
    ) {
        if (text.isBlank()) return
        try {
            val tts = ensureEngine()
            if (!ready) {
                Log.i(TAG, "TTS 未就绪，跳过本次播报")
                return
            }
            tts.setSpeechRate(speechRateFor(speechRate))
            utteranceSeq++
            // 音量走官方 per-utterance 参数（KEY_PARAM_VOLUME），不改引擎全局状态：
            // 这样即使这一条被系统打断，下一条也不会继承上一条的音量。
            val params = android.os.Bundle().apply {
                putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumeFor(volume))
            }
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, "reply-" + utteranceSeq)
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

    companion object {
        private const val TAG = "ReplySpeaker"

        /**
         * 合成前真正交给引擎的语速。
         *
         * 上下限与出厂值都取自 [AgentPreferences]，与设置项是同一份真值：
         * NaN（控制台写坏的值）退回出厂语速，越界值夹到 0.5..2.0，
         * 于是"语速设有值但引擎收到 0 或 Infinity"这条路在结构上不存在。
         */
        fun speechRateFor(preferenceRate: Float): Float =
            if (preferenceRate.isNaN()) {
                AgentPreferences.DEFAULT_TTS_SPEECH_RATE
            } else {
                preferenceRate.coerceIn(
                    AgentPreferences.MinTtsSpeechRate,
                    AgentPreferences.MaxTtsSpeechRate
                )
            }

        /**
         * 合成前真正交给引擎的音量。
         *
         * 夹到 0.1..1.0：0 会让用户以为播报坏了，而 setVolume 本身接受 0..1，
         * 所以"设了音量但听不见"这条路由夹取结构性地堵住。
         */
        fun volumeFor(preferenceVolume: Float): Float =
            if (preferenceVolume.isNaN()) {
                AgentPreferences.DEFAULT_TTS_VOLUME
            } else {
                preferenceVolume.coerceIn(
                    AgentPreferences.MIN_TTS_VOLUME,
                    AgentPreferences.MAX_TTS_VOLUME
                )
            }
    }
}
