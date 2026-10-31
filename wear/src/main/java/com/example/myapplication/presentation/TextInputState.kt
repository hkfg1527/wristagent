package com.example.myapplication.presentation

/**
 * 纯逻辑：文本输入弹窗状态机（无 Compose / Android 依赖，可单测）
 */
data class TextInputState(
    val isOpen: Boolean = false,
    val text: String = "",
    val lastSent: String? = null
)

sealed class TextInputEvent {
    /** 打开弹窗：不清空未发送文本 */
    object Open : TextInputEvent()

    /** 取消 / 返回键关闭：文本原样保留，重开可继续编辑 */
    object Cancel : TextInputEvent()

    data class Change(val text: String) : TextInputEvent()

    /** 提交：仅非空白文本才关闭并产出一条一次性待发指令 */
    object Submit : TextInputEvent()
}

fun TextInputState.reduce(event: TextInputEvent): TextInputState = when (event) {
    TextInputEvent.Open -> copy(isOpen = true)
    TextInputEvent.Cancel -> copy(isOpen = false)
    is TextInputEvent.Change -> copy(text = event.text)
    TextInputEvent.Submit ->
        if (text.isBlank()) this else copy(isOpen = false, text = "", lastSent = text)
}
