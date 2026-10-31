package com.example.myapplication.presentation

import androidx.compose.ui.graphics.Color
import com.example.myapplication.presentation.ui.ApprovalSliderLogic
import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审批滑块的纯逻辑单元测试 (TDD 严格红绿循环)
 * 覆盖：progress 边界 clamp、除零保护、红→绿连续插值、0.85 放行阈值、0.5 文案切换点。
 */
class ApprovalSliderLogicTest {

    /** 拇指从右端起手向左拖：位置全部换算成精确值，不做区间断言。 */
    @Test
    fun testProgress_dragFromRightEndTowardsLeft_returnsExactRatio() {
        // 轨道 200px、拇指 40px -> 可拖行程 160px
        assertEquals(0f, ApprovalSliderLogic.progress(200f, 200f, 200f, 40f), 0f)
        assertEquals(0.25f, ApprovalSliderLogic.progress(200f, 160f, 200f, 40f), 0f)
        assertEquals(0.5f, ApprovalSliderLogic.progress(200f, 120f, 200f, 40f), 0f)
        assertEquals(1f, ApprovalSliderLogic.progress(200f, 40f, 200f, 40f), 0f)
    }

    /** 向右拖出轨道或反向拖回：负数被夹到 0。 */
    @Test
    fun testProgress_dragBeyondRightEnd_clampsToZero() {
        assertEquals(0f, ApprovalSliderLogic.progress(200f, 280f, 200f, 40f), 0f)
        assertEquals(0f, ApprovalSliderLogic.progress(120f, 120.5f, 200f, 40f), 0f)
    }

    /** 向左拖过头：超过 1 的部分被夹到 1。 */
    @Test
    fun testProgress_dragBeyondLeftEnd_clampsToOne() {
        assertEquals(1f, ApprovalSliderLogic.progress(200f, -100f, 200f, 40f), 0f)
    }

    /** 轨道宽 == 拇指宽（以及更窄）：行程为 0，必须返回 0 而不是 NaN/Infinity。 */
    @Test
    fun testProgress_zeroTravel_returnsZeroInsteadOfNaN() {
        val same = ApprovalSliderLogic.progress(200f, 120f, 44f, 44f)
        val narrower = ApprovalSliderLogic.progress(200f, 120f, 30f, 44f)
        assertEquals(0f, same, 0f)
        assertEquals(0f, narrower, 0f)
        assertFalse(same.isNaN())
        assertFalse(narrower.isNaN())
        assertFalse(same.isInfinite())
    }

    /** progress=0 必须是拒绝红、progress=1 必须是成功绿，逐通道精确相等。 */
    @Test
    fun testColorFor_endpoints_areDangerAndSuccessTokens() {
        val red = ApprovalSliderLogic.colorFor(0f)
        val green = ApprovalSliderLogic.colorFor(1f)

        assertEquals(Wear.Palette.Danger.red, red.red, 0f)
        assertEquals(Wear.Palette.Danger.green, red.green, 0f)
        assertEquals(Wear.Palette.Danger.blue, red.blue, 0f)
        assertEquals(Wear.Palette.Danger.alpha, red.alpha, 0f)

        assertEquals(Wear.Palette.Success.red, green.red, 0f)
        assertEquals(Wear.Palette.Success.green, green.green, 0f)
        assertEquals(Wear.Palette.Success.blue, green.blue, 0f)
        assertEquals(Wear.Palette.Success.alpha, green.alpha, 0f)
    }

    /**
     * 中点精确等于 lerp 在 Oklab 空间的中点 #FFC5AD75
     * （androidx.compose.ui.graphics.lerp 对 Color 走 Oklab 插值，而不是 sRGB 分量平均，
     * 因此红→绿途中不会变成灰脏色）。
     */
    @Test
    fun testColorFor_midpoint_isExactOklabInterpolation() {
        val mid = ApprovalSliderLogic.colorFor(0.5f)

        assertEquals(0xC5 / 255f, mid.red, 0f)
        assertEquals(0xAD / 255f, mid.green, 0f)
        assertEquals(0x75 / 255f, mid.blue, 0f)
        assertEquals(1f, mid.alpha, 0f)
    }

    /** 插值必须连续单调：红通道随 progress 递减、绿通道随 progress 递增，且四分之一/四分之三处的值精确。 */
    @Test
    fun testColorFor_isContinuousAndMonotonic() {
        val quarter = ApprovalSliderLogic.colorFor(0.25f)
        val threeQuarter = ApprovalSliderLogic.colorFor(0.75f)

        assertTrue(quarter.red > threeQuarter.red)
        assertTrue(quarter.green < threeQuarter.green)
        // 0.25 -> #FFE59070, 0.75 -> #FF99C77B
        assertEquals(0xE5 / 255f, quarter.red, 0f)
        assertEquals(0x90 / 255f, quarter.green, 0f)
        assertEquals(0x70 / 255f, quarter.blue, 0f)
        assertEquals(0x99 / 255f, threeQuarter.red, 0f)
        assertEquals(0xC7 / 255f, threeQuarter.green, 0f)
        assertEquals(0x7B / 255f, threeQuarter.blue, 0f)
    }

    /** 越界的 progress 也要退化到两端颜色，不允许异常色。 */
    @Test
    fun testColorFor_outOfRangeProgress_clampsToEndpoints() {
        assertEquals(Wear.Palette.Danger.red, ApprovalSliderLogic.colorFor(-0.4f).red, 0f)
        assertEquals(Wear.Palette.Success.green, ApprovalSliderLogic.colorFor(1.7f).green, 0f)
    }

    /** 0.85 是放行阈值：0.84 不放行，0.85 与 0.86 放行。 */
    @Test
    fun testShouldAllow_thresholdIs085() {
        assertFalse(ApprovalSliderLogic.shouldAllow(0f))
        assertFalse(ApprovalSliderLogic.shouldAllow(0.84f))
        assertTrue(ApprovalSliderLogic.shouldAllow(0.85f))
        assertTrue(ApprovalSliderLogic.shouldAllow(0.86f))
        assertTrue(ApprovalSliderLogic.shouldAllow(1f))
    }

    /** 0.5 是文案切换点：未滑过是"点拒绝 · 左滑放行"，0.5 与 0.51 变"松开即放行"。 */
    @Test
    fun testLabelFor_switchesAtHalf() {
        assertEquals("点拒绝 · 左滑放行", ApprovalSliderLogic.labelFor(0f))
        assertEquals("点拒绝 · 左滑放行", ApprovalSliderLogic.labelFor(0.49f))
        assertEquals("松开即放行", ApprovalSliderLogic.labelFor(0.5f))
        assertEquals("松开即放行", ApprovalSliderLogic.labelFor(0.51f))
        assertEquals("松开即放行", ApprovalSliderLogic.labelFor(1f))
    }

    /** 锁定倒计时是第三态：无论进度多少都显示"锁定"，优先于进度文案。 */
    @Test
    fun testLabelFor_lockedOverridesProgress() {
        assertEquals("锁定", ApprovalSliderLogic.labelFor(0f, true))
        assertEquals("锁定", ApprovalSliderLogic.labelFor(0.49f, true))
        assertEquals("锁定", ApprovalSliderLogic.labelFor(0.5f, true))
        assertEquals("锁定", ApprovalSliderLogic.labelFor(1f, true))
        // 未锁定时必须回落到进度文案，锁定分支不能污染常态
        assertEquals("点拒绝 · 左滑放行", ApprovalSliderLogic.labelFor(0f, false))
        assertEquals("松开即放行", ApprovalSliderLogic.labelFor(1f, false))
    }

    /** 单击拒绝的容差边界：0 / 21 / 22 都算单击，23 起算滑动。 */
    @Test
    fun testIsRejectTap_withinSlopBoundary_isTap() {
        assertTrue(ApprovalSliderLogic.isRejectTap(0f, 0f, 22f))
        assertTrue(ApprovalSliderLogic.isRejectTap(-21f, 0f, 22f))
        assertTrue(ApprovalSliderLogic.isRejectTap(21f, 0f, 22f))
        // 恰好等于容差仍算单击，边界向内取闭区间
        assertTrue(ApprovalSliderLogic.isRejectTap(22f, 0f, 22f))
        assertTrue(ApprovalSliderLogic.isRejectTap(-22f, 0f, 22f))
    }

    /** 越过容差即滑动：23 起不再是单击，不能误判成拒绝。 */
    @Test
    fun testIsRejectTap_beyondSlop_isNotTap() {
        assertFalse(ApprovalSliderLogic.isRejectTap(23f, 0f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(-23f, 0f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(0f, 23f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(0f, -23f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(120f, 0f, 22f))
    }

    /** 纯纵向位移同样按容差判定：小幅抖动仍是单击，明显上下一划就不是。 */
    @Test
    fun testIsRejectTap_pureVerticalDrift_usesSameSlop() {
        assertTrue(ApprovalSliderLogic.isRejectTap(0f, 18f, 22f))
        assertTrue(ApprovalSliderLogic.isRejectTap(0f, 22f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(0f, 40f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(0f, -40f, 22f))
    }

    /** 斜向位移两轴独立判定：任一轴越界即算滑动，两轴都不越界才算单击。 */
    @Test
    fun testIsRejectTap_diagonal_checksBothAxes() {
        assertTrue(ApprovalSliderLogic.isRejectTap(15f, 15f, 22f))
        assertTrue(ApprovalSliderLogic.isRejectTap(22f, 22f, 22f))
        assertTrue(ApprovalSliderLogic.isRejectTap(-22f, 22f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(23f, 5f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(5f, -23f, 22f))
        assertFalse(ApprovalSliderLogic.isRejectTap(-23f, -23f, 22f))
    }

    /** 默认容差就是 22：调用方不传 slop 时按 22dp 的换算结果判定。 */
    @Test
    fun testIsRejectTap_defaultSlopIs22() {
        assertEquals(22f, ApprovalSliderLogic.TAP_SLOP_PX, 0f)
        assertTrue(ApprovalSliderLogic.isRejectTap(22f, 0f))
        assertFalse(ApprovalSliderLogic.isRejectTap(23f, 0f))
    }

    /** 两个阈值常量对外暴露，UI 与测试共用同一份数值。 */
    @Test
    fun testThresholdConstants_areSingleSourceOfTruth() {
        assertEquals(0.85f, ApprovalSliderLogic.ALLOW_THRESHOLD, 0f)
        assertEquals(0.5f, ApprovalSliderLogic.LABEL_SWITCH_PROGRESS, 0f)
    }
}
