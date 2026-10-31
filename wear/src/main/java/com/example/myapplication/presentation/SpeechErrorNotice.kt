package com.example.myapplication.presentation

import com.example.myapplication.R

/**
 * 纯逻辑：语音错误提示规整（空白消息不产生提示条）。
 * 只产出资源 id 与占位参数，文案本地化交给 UI 层解析。
 */
object SpeechErrorNotice {
    fun notice(raw: String?): ChatNotice? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { message ->
            ChatNotice(
                textRes = R.string.wearchat_voice_error,
                args = listOf(message),
                isError = true
            )
        }
}
