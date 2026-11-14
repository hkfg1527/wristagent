package com.example.myapplication.agent

import android.speech.tts.TextToSpeech
import androidx.annotation.StringRes
import com.example.myapplication.R

/**
 * 播报队列语义：新的一条播报是把上一句**打断**，还是**排在后面**。
 *
 * 打断（QUEUE_FLUSH）是对话场景的默认：用户问下一句时，上一句还没读完就该让位。
 * 排队（QUEUE_ADD）适合"让手表把一段读完整"，尤其是让它复述长答案的时候。
 */
enum class TtsQueueMode(
    @StringRes val labelRes: Int,
    /** 直接传给 [TextToSpeech.speak] 的队列常量。 */
    val queueConstant: Int
) {
    INTERRUPT(R.string.settings_tts_queue_interrupt, TextToSpeech.QUEUE_FLUSH),
    QUEUE(R.string.settings_tts_queue_append, TextToSpeech.QUEUE_ADD);

    companion object {
        fun fromStorage(raw: String?): TtsQueueMode =
            entries.firstOrNull { it.name == raw } ?: INTERRUPT
    }
}
