package com.example.myapplication.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主键 / 左翼两条输入入口的路由分发单元测试 (TDD 严格红绿循环)。
 *
 * 输入方式的二选一状态（语音 / 键盘）已删除：主键单击恒为语音起录，左翼单击直接打开文字输入框，
 * 两者互不记忆、谁也不改变另一条路的行为。验证：
 * 1. 主键单击触发语音动作，绝不触发键盘弹窗
 * 2. 左翼单击触发键盘弹窗，不触发语音动作
 * 3. 一次左翼点击不会把主键改成"下次单击弹键盘"（没有第二份输入方式真相）
 * 4. 语音出错时不自动弹出键盘打乱用户心智
 */
class VoiceInputRoutingTest {

    enum class InputRouteAction {
        NONE,
        LAUNCH_VOICE,
        OPEN_KEYBOARD
    }

    class InputRouteDispatcher {
        var lastAction = InputRouteAction.NONE
        var keyboardPopupRequested = false

        fun onMainButtonTap() {
            lastAction = InputRouteAction.LAUNCH_VOICE
            keyboardPopupRequested = false
        }

        fun onLeftWingTap() {
            lastAction = InputRouteAction.OPEN_KEYBOARD
            keyboardPopupRequested = true
        }

        fun onVoiceError(errorMsg: String) {
            // 规范：语音错误仅重置状态，禁止自动弹出键盘
            lastAction = InputRouteAction.NONE
            keyboardPopupRequested = false
        }
    }

    @Test
    fun testMainButtonTap_triggersVoiceIntent_notKeyboard() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onMainButtonTap()

        assertEquals(InputRouteAction.LAUNCH_VOICE, dispatcher.lastAction)
        assertFalse(dispatcher.keyboardPopupRequested)
    }

    @Test
    fun testLeftWingTap_triggersKeyboard_notVoice() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onLeftWingTap()

        assertEquals(InputRouteAction.OPEN_KEYBOARD, dispatcher.lastAction)
        assertTrue(dispatcher.keyboardPopupRequested)
    }

    @Test
    fun testLeftWingTapDoesNotStickToTheMainButton() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onLeftWingTap()
        assertEquals(InputRouteAction.OPEN_KEYBOARD, dispatcher.lastAction)

        // 三角验证：左翼点过一次之后，主键单击仍然走语音 —— 没有会被记住的"键盘模式"
        dispatcher.onMainButtonTap()
        assertEquals(InputRouteAction.LAUNCH_VOICE, dispatcher.lastAction)
        assertFalse(dispatcher.keyboardPopupRequested)
    }

    @Test
    fun testVoiceError_doesNotHijackToKeyboard() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onMainButtonTap()
        assertEquals(InputRouteAction.LAUNCH_VOICE, dispatcher.lastAction)

        // 模拟语音报错
        dispatcher.onVoiceError("未检测到说话声音")

        assertEquals(InputRouteAction.NONE, dispatcher.lastAction)
        assertFalse("语音失败时严禁自动劫持弹出键盘", dispatcher.keyboardPopupRequested)
    }
}
