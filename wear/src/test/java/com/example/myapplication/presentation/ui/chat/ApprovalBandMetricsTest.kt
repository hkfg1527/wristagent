package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ui.ApprovalBlockHeight
import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审批浮层纵向分带的数值钉死。
 *
 * 真机 C101X35122973（466px @2.0 密度 = 233dp 圆屏、半径 116.5dp）与 240dp 设计稿两种屏高，
 * 都必须满足：文案带底边到滑轨顶边 ≥12dp，且「记住并放行」文字最远角点落在圆内。
 * 这些数一旦被谁"顺手调小"，文字就会重新压回滑轨上，所以全部钉在测试里。
 */
class ApprovalBandMetricsTest {

    /** 审批块高不在这里另抄一份：必须等于 BottomBarReserve − Space.Md。 */
    @Test
    fun testApprovalBlockHeight_matchesBottomBarTokens() {
        assertEquals(64f, ApprovalBlockHeight.value, 0f)
        assertEquals(
            Wear.Size.BottomBarReserve.value - Wear.Space.Md.value,
            ApprovalBlockHeight.value,
            0f
        )
    }

    /** 文案带底边总预留 = 底距 12 + 块高 64 + 间隙 12 = 88dp，两组数值走同一条加法。 */
    @Test
    fun testContentBandReserve_addsInsetBlockAndGap() {
        assertEquals(
            88f,
            ApprovalBandMetrics.contentBandReserveDp(ApprovalBlockHeight.value),
            0.001f
        )
        assertEquals(88f, ApprovalBandMetrics.contentBandReserveDp(64f), 0f)
        assertEquals(30f, ApprovalBandMetrics.contentBandReserveDp(6f), 0f)
    }

    /** 文案带底边必须正好比审批块顶边高 12dp：Reserve 减掉底距与块高之后剩下的就是这一条间隙。 */
    @Test
    fun testContentBandBottom_sitsExactlyTwelveDpAboveApprovalBlockTop() {
        val blockTopFromBottom =
            ApprovalBandMetrics.BAR_BOTTOM_INSET_DP + ApprovalBlockHeight.value
        val gap = ApprovalBandMetrics.contentBandReserveDp(ApprovalBlockHeight.value) -
            blockTopFromBottom
        assertEquals(12f, gap, 0f)
        assertTrue(gap >= 12f)
    }

    /** 间隙是全模式共用的一条线，任何一边被调小都会让文字重新压到滑轨上。 */
    @Test
    fun testHardGaps_areNotBelowDesignMinimum() {
        assertTrue(ApprovalBandMetrics.CONTENT_TO_TRACK_GAP_DP >= 12f)
        assertTrue(ApprovalBandMetrics.BAR_BOTTOM_INSET_DP >= 12f)
    }

    /** 两种屏高的文案带高度：233dp 真表 145dp，240dp 设计 152dp。 */
    @Test
    fun testContentBandHeight_realDeviceAndDesignScreens() {
        assertEquals(
            145f,
            ApprovalBandMetrics.contentBandHeightDp(233f, ApprovalBlockHeight.value),
            0f
        )
        assertEquals(
            152f,
            ApprovalBandMetrics.contentBandHeightDp(240f, ApprovalBlockHeight.value),
            0f
        )
    }

    /** 屏高不足预留时夹到 0：负高度传给 padding 会直接抛异常。 */
    @Test
    fun testContentBandHeight_shorterThanReserve_clampsToZero() {
        assertEquals(0f, ApprovalBandMetrics.contentBandHeightDp(88f, 64f), 0f)
        assertEquals(0f, ApprovalBandMetrics.contentBandHeightDp(60f, 64f), 0f)
    }

    /** 最远角点半径就是勾股距离：3-4-5 精确成立，宽高互换结果一致。 */
    @Test
    fun testFarthestCornerRadius_isEuclideanDistance() {
        assertEquals(5f, ApprovalBandMetrics.farthestCornerRadiusDp(3f, 4f), 0.0001f)
        assertEquals(5f, ApprovalBandMetrics.farthestCornerRadiusDp(4f, 3f), 0.0001f)
        assertEquals(10f, ApprovalBandMetrics.farthestCornerRadiusDp(6f, 8f), 0.0001f)
    }

    /** 「记住并放行」文字盒（半宽 30dp）在两种屏高的下沿都留在 116.5dp 圆内。 */
    @Test
    fun testRememberLabel_farthestCornerStaysInsideRoundScreen() {
        // 12sp × 5 个汉字 ≈ 60dp 宽 → 半宽 30dp
        val halfWidthDp = 30f
        // 文字下沿实测：233dp 屏 217dp、240dp 屏 224dp；圆心在屏高一半，纵向偏移取绝对值后等价
        val device = ApprovalBandMetrics.farthestCornerRadiusDp(halfWidthDp, 233f / 2f - 217f)
        val design = ApprovalBandMetrics.farthestCornerRadiusDp(halfWidthDp, 240f / 2f - 224f)
        assertTrue(device <= 116.5f)
        assertTrue(design <= 116.5f)
    }

    /** 反例：把元素放大到滑轨弦长（184dp 宽）再压到块底，半径必然越界 —— 这条检查有牙。 */
    @Test
    fun testFarthestCornerRadius_oversizedWidthDetectsCircularOverflow() {
        val overflowing = ApprovalBandMetrics.farthestCornerRadiusDp(92f, 104.5f)
        assertTrue(overflowing > 116.5f)
    }
}
