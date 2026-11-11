package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.BottomBlockMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底栏是否强制展开只认状态标志（执行/审批/录音/弹窗/抽屉）。
 *
 * 真机现象：底栏某些时刻收不回去 —— 某个写者把 bottomBlockMode 落成非 IDLE 之后再没人重置，
 * "非 IDLE 就强制展开"这条规则于是被一条残值永久钉住。形态既然已由 [deriveBottomBlockMode] 派生，
 * 展开与否就不该再读这个字段，只该读审批闩锁 [ChatUiState.isApprovalFocus]。
 */
class BottomBarForceExpandFlagTest {

    private fun idle() = ChatUiState.initial()

    /** 语音收尾后残留在字段里的形态：所有标志都已归零，底栏必须允许收起。 */
    @Test
    fun `残留的非审批形态不再钉住底栏`() {
        assertFalse(
            "残值 RECORDING 不该让底栏永远展开",
            idle().copy(bottomBlockMode = BottomBlockMode.RECORDING).isBottomBarForceExpanded
        )
        assertFalse(
            "残值 THINKING_STOP 不该让底栏永远展开",
            idle().copy(bottomBlockMode = BottomBlockMode.THINKING_STOP).isBottomBarForceExpanded
        )
    }

    /** 审批闩锁仍必须强制展开：它是滑块唯一入口。 */
    @Test
    fun `审批闩锁仍然强制展开`() {
        assertTrue(
            idle().copy(bottomBlockMode = BottomBlockMode.ACTION_ALLOW).isBottomBarForceExpanded
        )
    }
}
