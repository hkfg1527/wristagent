package com.example.myapplication.presentation

import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.presentation.ui.BottomBarEvent
import com.example.myapplication.presentation.ui.BottomBarMode
import com.example.myapplication.presentation.ui.BottomBarVisibility
import com.example.myapplication.presentation.ui.MainInputType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏可见性状态机：回复完成后正常展开；用户一旦滚动就收起成小白条；
 * 之后只有点小白条才恢复；强制态（执行/审批/录音/弹窗/抽屉）下一直展开且不可收起。
 */
class BottomBarVisibilityTest {

    private fun next(
        current: BottomBarMode,
        event: BottomBarEvent,
        forceExpanded: Boolean = false
    ): BottomBarMode = BottomBarVisibility.next(current, event, forceExpanded)

    @Test
    fun `展开态下用户开始滚动立即收起`() {
        assertEquals(BottomBarMode.Collapsed, next(BottomBarMode.Expanded, BottomBarEvent.UserScroll))
    }

    @Test
    fun `已收起时继续滚动保持收起`() {
        assertEquals(BottomBarMode.Collapsed, next(BottomBarMode.Collapsed, BottomBarEvent.UserScroll))
    }

    @Test
    fun `点击小白条立即展开`() {
        assertEquals(BottomBarMode.Expanded, next(BottomBarMode.Collapsed, BottomBarEvent.StripTap))
    }

    @Test
    fun `执行中滚动不收起`() {
        assertEquals(
            BottomBarMode.Expanded,
            next(BottomBarMode.Expanded, BottomBarEvent.UserScroll, forceExpanded = true)
        )
    }

    @Test
    fun `审批中滚动不收起`() {
        assertEquals(
            BottomBarMode.Expanded,
            next(BottomBarMode.Expanded, BottomBarEvent.UserScroll, forceExpanded = true)
        )
    }

    @Test
    fun `录入弹窗抽屉等强制态下即使已收起也立即展开`() {
        assertEquals(
            BottomBarMode.Expanded,
            next(BottomBarMode.Collapsed, BottomBarEvent.UserScroll, forceExpanded = true)
        )
        assertEquals(
            BottomBarMode.Expanded,
            next(BottomBarMode.Collapsed, BottomBarEvent.StripTap, forceExpanded = true)
        )
    }

    @Test
    fun `恢复空闲后再次滚动才会再收起`() {
        // 展开 -> 滚动收起 -> 点条展开 -> 空闲后滚动再收起：一路重放，中间没有自动展开
        var mode = BottomBarMode.Expanded
        mode = next(mode, BottomBarEvent.UserScroll)
        assertEquals(BottomBarMode.Collapsed, mode)
        mode = next(mode, BottomBarEvent.StripTap)
        assertEquals(BottomBarMode.Expanded, mode)
        mode = next(mode, BottomBarEvent.UserScroll)
        assertEquals(BottomBarMode.Collapsed, mode)
    }

    @Test
    fun `强制态结束后保持展开直到用户再滚动`() {
        var mode = BottomBarMode.Expanded
        mode = next(mode, BottomBarEvent.UserScroll)
        assertEquals(BottomBarMode.Collapsed, mode)
        // 进入强制态：立即展开
        mode = next(mode, BottomBarEvent.UserScroll, forceExpanded = true)
        assertEquals(BottomBarMode.Expanded, mode)
        // 强制态结束（forceExpanded 回落）时不会自己收起
        assertEquals(BottomBarMode.Expanded, next(mode, BottomBarEvent.StripTap))
        assertEquals(BottomBarMode.Collapsed, next(mode, BottomBarEvent.UserScroll))
    }
}

/** 哪些状态必须强制底栏展开：执行中、审批、录音、输入弹窗、抽屉。 */
class ChatUiStateBottomBarForceTest {

    private fun idle() = ChatUiState.initial(MainInputType.VOICE)

    @Test
    fun `空闲态不强制展开`() {
        assertFalse(idle().isBottomBarForceExpanded)
    }

    @Test
    fun `执行中强制展开`() {
        assertTrue(idle().copy(isExecuting = true).isBottomBarForceExpanded)
    }

    @Test
    fun `审批挂起强制展开`() {
        assertTrue(
            idle().copy(pendingApproval = PendingApproval("shell", RiskAssessment())).isBottomBarForceExpanded
        )
    }

    @Test
    fun `录音输入弹窗与抽屉都强制展开`() {
        assertTrue(idle().copy(isRecordingVoice = true).isBottomBarForceExpanded)
        assertTrue(idle().copy(draft = TextInputState(isOpen = true)).isBottomBarForceExpanded)
        assertTrue(idle().copy(isSessionDrawerOpen = true).isBottomBarForceExpanded)
    }

    @Test
    fun `输入弹窗关闭后回落为非强制`() {
        assertFalse(
            idle().copy(draft = TextInputState(isOpen = false)).isBottomBarForceExpanded
        )
    }
}
