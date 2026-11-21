package com.example.myapplication.presentation.ui.settingsv2.common

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * 表冠（rotary）事件的**跨层总线**。
 *
 * 为什么需要它：Compose 的 `onRotaryScrollEvent` 要求目标节点持有焦点，真机上焦点可能被别处拿走
 * （实测：注入 `REL_WHEEL` 后 `onRotaryScrollEvent` 一行日志都没有）。Wear 上更可靠的做法是在
 * Activity 的 `onGenericMotionEvent` 里接 `ACTION_SCROLL`，再分发给需要的页面 —— 那条路径不依赖焦点。
 *
 * [enabled] 是安全阀：只有终端页在屏幕上时才让 Activity 消费滚动，
 * 否则会把聊天页等其它页面的表冠滚动一起吞掉。
 */
object RotaryBus {

    private val _events = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    /** 表冠增量（像素语义，正 = 内容向上滚）。 */
    val events: SharedFlow<Float> = _events

    /** 终端页在栈上时才为 true。 */
    @Volatile
    var enabled: Boolean = false

    fun emit(delta: Float) {
        if (enabled) _events.tryEmit(delta)
    }
}
