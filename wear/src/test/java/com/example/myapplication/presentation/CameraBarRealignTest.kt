package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.CameraCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 按钮形变 → 可读区变化的纯函数契约（bug B）。
 *
 * 底栏三种形态占住屏幕底部的高度完全不同：完整主键 58+12dp、审批扁条 64+12dp、
 * 收成贴底弧条只剩 8+12dp。镜头必须能把"形变了多少"换算成"要补滚多少像素"，
 * 否则按钮变形时正文还在按旧可读区匀速推。
 */
class CameraBarRealignTest {

    private val expanded = 58f
    private val approval = 64f
    private val collapsed = 8f
    private val inset = 12f

    @Test
    fun footprintInterpolatesAlongTheCollapseTimeline() {
        // 展开：完整块 + 底距
        assertEquals(expanded + inset, CameraCurve.barFootprintDp(expanded, inset, collapsed, 1f), 1e-4f)
        // 收起：只剩贴底弧条 + 底距
        assertEquals(collapsed + inset, CameraCurve.barFootprintDp(expanded, inset, collapsed, 0f), 1e-4f)
        // 半程：同一条时间线的中点，不是第二条曲线
        assertEquals(
            (expanded + inset + collapsed + inset) / 2f,
            CameraCurve.barFootprintDp(expanded, inset, collapsed, 0.5f),
            1e-4f
        )
        // 审批态块更高 → 占得更多
        assertTrue(
            CameraCurve.barFootprintDp(approval, inset, collapsed, 1f) >
                CameraCurve.barFootprintDp(expanded, inset, collapsed, 1f)
        )
    }

    @Test
    fun footprintClampsOutOfRangeProgress() {
        assertEquals(
            CameraCurve.barFootprintDp(expanded, inset, collapsed, 1f),
            CameraCurve.barFootprintDp(expanded, inset, collapsed, 3.7f),
            1e-4f
        )
        assertEquals(
            CameraCurve.barFootprintDp(expanded, inset, collapsed, 0f),
            CameraCurve.barFootprintDp(expanded, inset, collapsed, -2f),
            1e-4f
        )
    }

    @Test
    fun onlyExpansionNeedsCompensation() {
        val before = collapsed + inset
        val after = expanded + inset
        // 按钮变大 → 压住底部内容 → 必须补滚同样的量把内容抬出来
        assertEquals(after - before, CameraCurve.realignCompensationPx(before, after), 1e-4f)
        // 按钮变小 → 只是露出更多内容，再滚就是推着人走
        assertEquals(0f, CameraCurve.realignCompensationPx(after, before), 1e-4f)
        // 没变 → 不补
        assertEquals(0f, CameraCurve.realignCompensationPx(after, after), 1e-4f)
    }

    @Test
    fun compensationAccumulatesWithoutDoubleCounting() {
        // 逐帧形变（弹簧过冲会来回走）：累加量必须等于净变化量，收起方向不抵消
        val steps = listOf(20f, 30f, 70f, 60f, 50f, 70f)
        var accumulated = 0f
        var previous = steps.first()
        for (current in steps.drop(1)) {
            accumulated += CameraCurve.realignCompensationPx(previous, current)
            previous = current
        }
        // 正向累计：20→30 (+10)、30→70 (+40)、50→70 (+20)；70→60、60→50 不收负值
        assertEquals(70f, accumulated, 1e-4f)
    }

    @Test
    fun onlyRealMorphsTriggerRealign() {
        val threshold = CameraCurve.REALIGN_THRESHOLD_DP
        assertFalse("亚像素抖动不该抢镜头", CameraCurve.shouldRealign(70f, 70f + threshold * 0.5f, threshold))
        assertTrue(CameraCurve.shouldRealign(70f, 70f + threshold, threshold))
        assertTrue("整段形变必须触发", CameraCurve.shouldRealign(20f, 70f, threshold))
        assertTrue("负方向同样算形变", CameraCurve.shouldRealign(70f, 20f, threshold))
        // 阈值为 0 时任何变化都算（退化输入不得恒 false）
        assertTrue(CameraCurve.shouldRealign(1f, 1.01f, 0f))
    }
}
