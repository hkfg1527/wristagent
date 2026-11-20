package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 入场错峰的状态机（不渲染界面，只钉规则）。
 *
 * 为什么这么测：真机录屏在这台表上拿不到有效帧（0.0.0.142 实测 9020 字节、无抽帧工具），
 * 所以"逐行错峰"这件事必须由判据回答 —— 一行**只播一次**入场、**窗口关闭后**不再播。
 * 至于每个风格错开多少毫秒，由 `StyleMotionTest` 钉 `staggerMs`；两者合起来才是完整约束。
 */
class SettingsRowStaggerStateTest {

    @Test
    fun theFirstCompositionOfARowPlaysEnter() {
        val state = SettingsRowStaggerState()
        assertTrue("首次组合的行必须播入场", state.claimEnter(0))
        assertTrue("同一窗口内，另一行也应各自播入场（这样才可能错开）", state.claimEnter(1))
    }

    @Test
    fun aRowNeverReplaysEnterAfterScrollingBack() {
        val state = SettingsRowStaggerState()
        assertTrue(state.claimEnter(3))
        assertFalse("同一行滚出去再滚回来，不得重播入场", state.claimEnter(3))
    }

    @Test
    fun afterTheEntryWindowClosesNothingPlaysEnter() {
        val state = SettingsRowStaggerState()
        assertTrue("窗口开着时首行播入场", state.claimEnter(0))
        assertTrue(state.isEntryOpen())
        state.closeEntry()
        assertFalse(state.isEntryOpen())
        assertFalse(
            "窗口关闭后，滚动带进视口的行直接落终态，不播入场",
            state.claimEnter(7)
        )
        assertTrue("但它仍被记为已组合过", state.hasPlayedEnter(7))
    }
}
