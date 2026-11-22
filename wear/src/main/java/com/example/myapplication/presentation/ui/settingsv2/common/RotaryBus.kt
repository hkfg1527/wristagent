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

    /** 一屏按多少行算（终端 36px 行网格、可视 10 行）。 */
    const val PageLines = 10

    /**
     * 一次按键该滚多少像素：**单按一行，长按一屏**。
     *
     * 真终端里按住方向键会加速（`less` / `vim` 都是），一行一行挪太慢。
     * 这里用按键重复次数（`KeyEvent.repeatCount`）区分：0 = 首次按下，>=1 = 长按重复。
     *
     * @param repeatCount 系统给的重复次数
     * @param linePx 一行的像素高度（36）
     * @param pageLines 一屏行数（默认 10）
     */
    fun deltaForKeyRepeat(repeatCount: Int, linePx: Float, pageLines: Int = PageLines): Float =
        linePx * if (repeatCount <= 0) 1 else pageLines.coerceAtLeast(1)

    private val _events = MutableSharedFlow<Float>(extraBufferCapacity = 64)

    /** 表冠增量（像素语义，正 = 内容向上滚）。 */
    val events: SharedFlow<Float> = _events

    private val _activations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    /**
     * "执行焦点行"（TUI 的 Enter）。
     *
     * 为什么要有这条总线：Compose 的 `onKeyEvent` 只在节点持有焦点时生效，而从后台切回、
     * 或焦点被别处拿走时它收不到键事件 —— 此时键会漏到系统，系统把 KeyUp 当返回动作，
     * 真机实测"执行命令 + 顺手退出设置页"。Activity 层接一遍就不依赖焦点了。
     */
    val activations: SharedFlow<Unit> = _activations

    /** 终端页在栈上时才为 true。 */
    @Volatile
    var enabled: Boolean = false

    fun emit(delta: Float) {
        if (enabled) _events.tryEmit(delta)
    }

    /** 执行当前焦点行（真终端的 Enter 语义）。 */
    fun emitActivate() {
        if (enabled) _activations.tryEmit(Unit)
    }
}
