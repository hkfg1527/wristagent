package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.BottomBarVisibility
import com.example.myapplication.presentation.ui.MAX_AUTO_COLLAPSE_MS
import com.example.myapplication.presentation.ui.MIN_AUTO_COLLAPSE_MS
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏自动收起延时（设置项 bottom_bar_auto_hide_ms）的语义与真实接线。
 *
 * 状态机本身早已有测试；这里钉的是"延时可以调，而且真的被读"：
 * 同一组时间参数在不同延时下必须得出不同结论，否则滑杆就是个假开关。
 */
class BottomBarAutoHideTest {

    private val turnStart = 1_000L
    private val nowAfter4s = turnStart + 4_000L

    @Test
    fun aLongerDelayKeepsTheBarVisibleForLonger() {
        // 4 秒时：默认 3 秒档已经该收；调到 8 秒档则还不该收
        assertTrue(
            BottomBarVisibility.shouldAutoCollapseAfterTurn(
                nowMs = nowAfter4s,
                turnStartMs = turnStart,
                wakeLatchUntilMs = 0L,
                lastUserScrollMs = 0L,
                forceExpanded = false,
                delayMs = 3_000L
            )
        )
        assertFalse(
            BottomBarVisibility.shouldAutoCollapseAfterTurn(
                nowMs = nowAfter4s,
                turnStartMs = turnStart,
                wakeLatchUntilMs = 0L,
                lastUserScrollMs = 0L,
                forceExpanded = false,
                delayMs = 8_000L
            )
        )
    }

    @Test
    fun outOfRangeDelaysAreClampedNotObedient() {
        // 0 秒（越界下界）会被夹到 1 秒：2 秒时已经该收
        assertTrue(
            BottomBarVisibility.shouldAutoCollapseAfterTurn(
                nowMs = turnStart + 2_000L,
                turnStartMs = turnStart,
                wakeLatchUntilMs = 0L,
                lastUserScrollMs = 0L,
                forceExpanded = false,
                delayMs = 0L
            )
        )
        // 一小时（越界上界）夹到 8 秒：10 秒时该收
        assertTrue(
            BottomBarVisibility.shouldAutoCollapseAfterTurn(
                nowMs = turnStart + 10_000L,
                turnStartMs = turnStart,
                wakeLatchUntilMs = 0L,
                lastUserScrollMs = 0L,
                forceExpanded = false,
                delayMs = 3_600_000L
            )
        )
    }

    @Test
    fun theClampRangeIsTheOneTheSettingsPageShows() {
        org.junit.Assert.assertEquals(1_000L, MIN_AUTO_COLLAPSE_MS)
        org.junit.Assert.assertEquals(8_000L, MAX_AUTO_COLLAPSE_MS)
    }

    @Test
    fun theChatScreenReadsThePreferenceInsteadOfTheConstant() {
        val chat = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/chat/ChatScreen.kt")
        assertTrue("必须从偏好取延时", chat.contains("AgentPreferences(context).bottomBarAutoHideMs"))
        assertTrue("必须把延时交给状态机", chat.contains("delayMs = autoHideMs"))
        assertFalse("不许再直接用常量当延时", chat.contains("delay(AUTO_COLLAPSE_DELAY_MS)"))
    }
}
