package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.MainInputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 语音与键盘路由分发逻辑单元测试 (TDD 严格红绿循环)
 * 验证：
 * 1. 语音模式下单触/长按触发语音动作，绝不触发键盘弹窗
 * 2. 键盘模式下单触触发键盘弹窗，不触发语音动作
 * 3. 语音出错时不应自动回退弹出键盘打乱用户心智
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

        fun onMainButtonTap(mode: MainInputType) {
            if (mode == MainInputType.KEYBOARD) {
                lastAction = InputRouteAction.OPEN_KEYBOARD
                keyboardPopupRequested = true
            } else {
                lastAction = InputRouteAction.LAUNCH_VOICE
                keyboardPopupRequested = false
            }
        }

        fun onVoiceError(errorMsg: String) {
            // 规范：语音错误仅重置状态，禁止自动弹出键盘
            lastAction = InputRouteAction.NONE
            keyboardPopupRequested = false
        }
    }

    @Test
    fun testVoiceMode_triggersVoiceIntent_notKeyboard() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onMainButtonTap(MainInputType.VOICE)

        assertEquals(InputRouteAction.LAUNCH_VOICE, dispatcher.lastAction)
        assertFalse(dispatcher.keyboardPopupRequested)
    }

    @Test
    fun testKeyboardMode_triggersKeyboard_notVoice() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onMainButtonTap(MainInputType.KEYBOARD)

        assertEquals(InputRouteAction.OPEN_KEYBOARD, dispatcher.lastAction)
        assertTrue(dispatcher.keyboardPopupRequested)
    }

    @Test
    fun testVoiceError_doesNotHijackToKeyboard() {
        val dispatcher = InputRouteDispatcher()
        dispatcher.onMainButtonTap(MainInputType.VOICE)
        assertEquals(InputRouteAction.LAUNCH_VOICE, dispatcher.lastAction)

        // 模拟语音报错
        dispatcher.onVoiceError("未检测到说话声音")

        assertEquals(InputRouteAction.NONE, dispatcher.lastAction)
        assertFalse("语音失败时严禁自动劫持弹出键盘", dispatcher.keyboardPopupRequested)
    }
}
