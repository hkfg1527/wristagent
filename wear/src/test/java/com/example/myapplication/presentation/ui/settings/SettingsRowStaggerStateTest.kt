package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 入场窗口的状态机：入场只属于「页面刚进来时首屏看得见的那批行」。
 *
 * 回归背景（真机 bug）：ScalingLazyColumn 只保留可见项，屏幕外的行是滚动把它带进视口时才第一次
 * 组合的。旧实现把「第一次组合」直接当成「播入场」，于是手指一滑就是一串行同时从 +24dp 处淡入浮起，
 * 表现为「所有组件都飞起来了」；表冠一格一行慢慢过，症状轻得多。
 */
class SettingsRowStaggerStateTest {

    @Test
    fun rowVisibleOnTheFirstFramePlaysTheEntrance() {
        val state = SettingsRowStaggerState()
        assertTrue("首屏可见行的第一次组合必须能播入场", state.claimEnter(0))
        assertTrue(state.claimEnter(1))
    }

    @Test
    fun rowDoesNotReplayWhenItScrollsOutAndBack() {
        val state = SettingsRowStaggerState()
        assertTrue(state.claimEnter(3))
        assertFalse("同一行滚出再滚回不能重播", state.claimEnter(3))
    }

    @Test
    fun rowFirstComposedByScrollingNeverPlaysTheEntrance() {
        val state = SettingsRowStaggerState()
        state.closeEntry()
        assertFalse("滚动带进视口的行必须直接落位", state.claimEnter(7))
        assertFalse(state.claimEnter(8))
    }

    @Test
    fun closingTheWindowDoesNotDisturbRowsAlreadyAnimating() {
        val state = SettingsRowStaggerState()
        assertTrue(state.claimEnter(0))
        state.closeEntry()
        assertFalse("已经起跑的行不受关窗影响，晚到的行才被拦下", state.claimEnter(0))
        assertTrue("关窗不代表行没组合过", state.hasPlayedEnter(0))
    }

    @Test
    fun everyVisibleRowIsClaimedExactlyOnce() {
        val state = SettingsRowStaggerState()
        val firstPass = (0..5).map { state.claimEnter(it) }
        val secondPass = (0..5).map { state.claimEnter(it) }
        assertTrue("首屏六行都要播", firstPass.all { it })
        assertFalse("第二遍一个都不许重播", secondPass.any { it })
    }

    @Test
    fun entryWindowStartsOpenAndStaysClosedOnceClosed() {
        val state = SettingsRowStaggerState()
        assertTrue(state.isEntryOpen())
        state.closeEntry()
        assertFalse(state.isEntryOpen())
    }

    @Test
    fun thePageClosesTheWindowAfterTheFirstFrames() {
        // 窗口必须由页面级 remember 关掉：只写状态机不接线，滚动进来的行照样会飞。
        val source = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsRowStagger.kt"
        )
        val closeCall = source.indexOf("state.closeEntry()")
        assertTrue("入场窗口必须由页面级 remember 关闭", closeCall > 0)
        assertTrue("关窗前必须等满两帧（首屏 lazy 项在 measure 阶段才组合）", source.substring(0, closeCall).contains("withFrameNanos"))
    }
}
