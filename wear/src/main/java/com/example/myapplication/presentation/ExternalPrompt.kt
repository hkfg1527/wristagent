package com.example.myapplication.presentation

/**
 * 纯逻辑：externalPrompt 一次性消费（seq 标识单次 intent，重组不重放）
 */
data class ExternalPrompt(val seq: Long, val text: String)

object ExternalPromptGate {
    fun shouldConsume(prompt: ExternalPrompt?, ready: Boolean, lastConsumedSeq: Long?): Boolean =
        prompt != null && ready && prompt.text.isNotBlank() && prompt.seq != lastConsumedSeq
}
