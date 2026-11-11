package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ui.ApprovalBlockHeight
import com.example.myapplication.presentation.ui.ApprovalRememberBandHeight
import com.example.myapplication.presentation.ui.ApprovalTrackTouchHeight
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.WearTypography
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审批浮层纵向分带的数值钉死。
 *
 * 真机 C101X35122973（466px @2.0 密度 = 233dp 圆屏、半径 116.5dp）与 240dp 设计稿两种屏高，都必须满足：
 * 固定头 + 内容带 + 间隙 12 + 审批块 64 + 底距 12 正好等于屏高；内容带底边到滑轨顶边 ≥12dp；
 * 「记住并放行」文字最远角点落在圆内。这些数一旦被谁"顺手调小"，文字就会重新压回滑轨上、
 * 或者固定头又会把内容带挤掉，所以全部钉在测试里。
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

    /** 内容带底边总预留 = 底距 12 + 块高 64 + 间隙 12 = 88dp，两组数值走同一条加法。 */
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

    /** 内容带底边必须正好比审批块顶边高 12dp：Reserve 减掉底距与块高之后剩下的就是这一条间隙。 */
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

    /**
     * 固定头高 = 圆屏留白 8 + 标题行 24 + 标题到条 8 + 条 8 + 段间距 10 = 58dp，五段都必须是令牌值。
     *
     * 标题行不是随手写的 24：它就是 titleMedium 的行高（WearMaterialTheme 显式覆写 18/24sp），
     * 字号档一变这条断言就红，不会留下一份对不上的"头高"。
     */
    @Test
    fun testHeaderHeight_isRoundPadPlusTitleLineAndBarsAndGap() {
        assertEquals(24f, WearTypography.titleMedium.lineHeight.value, 0.001f)
        assertEquals(
            WearTypography.titleMedium.lineHeight.value,
            ApprovalBandMetrics.HEADER_TITLE_LINE_DP,
            0.001f
        )
        assertEquals(Wear.Space.Sm.value, ApprovalBandMetrics.HEADER_TOP_PAD_DP, 0f)
        assertEquals(Wear.Space.Sm.value, ApprovalBandMetrics.HEADER_TITLE_TO_BARS_DP, 0f)
        assertEquals(8f, ApprovalBandMetrics.HEADER_BARS_DP, 0f)
        assertEquals(10f, ApprovalBandMetrics.BLOCK_GAP_DP, 0f)
        assertEquals(58f, ApprovalBandMetrics.HEADER_HEIGHT_DP, 0f)
        assertEquals(
            ApprovalBandMetrics.HEADER_TOP_PAD_DP +
                ApprovalBandMetrics.HEADER_TITLE_LINE_DP +
                ApprovalBandMetrics.HEADER_TITLE_TO_BARS_DP +
                ApprovalBandMetrics.HEADER_BARS_DP +
                ApprovalBandMetrics.BLOCK_GAP_DP,
            ApprovalBandMetrics.HEADER_HEIGHT_DP,
            0f
        )
    }

    /**
     * 圆屏留白这 8dp 是"标题不被圆周切掉"的量化依据，不是审美：233dp 圆/半径 116.5dp 上，
     * 标题字形顶边落在 y = 留白 + 行内上留白 ≈ 13dp，那里弦长 2√(116.5²−103.5²) ≈ 107dp，
     * 而「高风险操作」18sp 五字宽 90dp —— 留白归零时顶边落在 y≈5dp，弦长只有 68dp，两个字上半截会被切掉。
     */
    @Test
    fun testHeaderTopPad_keepsTitleInsideRoundScreenChord() {
        val radius = 116.5f
        val titleWidth = 90f
        fun chordAt(yFromTopDp: Float): Float {
            val dy = radius - yFromTopDp
            return 2f * sqrt(radius * radius - dy * dy)
        }
        // 留白存在时：字形顶边在 y≈13dp，弦长容得下整个标题
        assertTrue(chordAt(ApprovalBandMetrics.HEADER_TOP_PAD_DP + 5f) >= titleWidth)
        // 反例：留白归零时同一行的弦长装不下标题 —— 这条检查有牙
        assertTrue(chordAt(5f) < titleWidth)
    }

    /**
     * 守恒：固定头 + 内容带 + 间隙 12 + 审批块 64 + 底距 12 = 屏高。
     *
     * 逐项相加（不用 [ApprovalBandMetrics.stackHeightDp]，那个函数自己就算得对），
     * 233dp 与 240dp 两种屏都必须正好合上 —— 少一 dp 说明头叠在内容上挤掉了别的东西，
     * 多一 dp 说明头没从内容带里扣干净。
     */
    @Test
    fun testBandStack_headerPlusContentPlusGapPlusBlockPlusInset_equalsScreenHeight() {
        val blockHeight = ApprovalBlockHeight.value
        assertEquals(233f, screenStackDp(233f, blockHeight), 0.001f)
        assertEquals(240f, screenStackDp(240f, blockHeight), 0.001f)
        assertEquals(233f, ApprovalBandMetrics.stackHeightDp(233f, blockHeight), 0.001f)
        assertEquals(240f, ApprovalBandMetrics.stackHeightDp(240f, blockHeight), 0.001f)
    }

    /** 两种屏高的可滚动内容带：233dp 真表 87dp（233−88−58），240dp 设计 94dp（240−88−58）。 */
    @Test
    fun testContentBandHeight_realDeviceAndDesignScreens() {
        assertEquals(
            87f,
            ApprovalBandMetrics.contentBandHeightDp(233f, ApprovalBlockHeight.value),
            0f
        )
        assertEquals(
            94f,
            ApprovalBandMetrics.contentBandHeightDp(240f, ApprovalBlockHeight.value),
            0f
        )
        // 内容带 = 屏高 − 88 − 头高：头变高 1dp，内容带就少 1dp，不可能两边都满
        assertEquals(
            ApprovalBandMetrics.contentBandHeightDp(240f, ApprovalBlockHeight.value),
            240f - 88f - ApprovalBandMetrics.HEADER_HEIGHT_DP,
            0f
        )
    }

    /** 屏高不足预留 + 固定头时夹到 0：负高度传给 padding 会直接抛异常。 */
    @Test
    fun testContentBandHeight_shorterThanReserveAndHeader_clampsToZero() {
        // 预留 88 + 固定头 58 = 146dp 正好吃掉全部屏高
        assertEquals(0f, ApprovalBandMetrics.contentBandHeightDp(146f, 64f), 0f)
        assertEquals(1f, ApprovalBandMetrics.contentBandHeightDp(147f, 64f), 0f)
        assertEquals(0f, ApprovalBandMetrics.contentBandHeightDp(88f, 64f), 0f)
        assertEquals(0f, ApprovalBandMetrics.contentBandHeightDp(60f, 64f), 0f)
    }

    /**
     * 固定头只吃文字：滑轨热区与「记住并放行」条带的 y 区间只由屏高与审批块高决定，与头高无关。
     *
     * 233dp 真表：滑轨 157..201、记住 201..221；240dp 设计稿：滑轨 164..208、记住 208..228。
     * 两条带一起正好填满 64dp 的审批块，块底 = 屏幕底 − 12dp。
     */
    @Test
    fun testTrackAndRememberBand_areNotMovedByHeader() {
        assertEquals(44f, ApprovalTrackTouchHeight.value, 0f)
        assertEquals(20f, ApprovalRememberBandHeight.value, 0f)

        val screens = floatArrayOf(233f, 240f)
        val trackTops = floatArrayOf(157f, 164f)
        val trackBottoms = floatArrayOf(201f, 208f)
        val rememberBottoms = floatArrayOf(221f, 228f)

        for (index in screens.indices) {
            val screenHeight = screens[index]
            val blockTop =
                screenHeight - ApprovalBandMetrics.BAR_BOTTOM_INSET_DP - ApprovalBlockHeight.value
            val trackBottom = blockTop + ApprovalTrackTouchHeight.value
            val rememberBottom = trackBottom + ApprovalRememberBandHeight.value

            assertEquals(trackTops[index], blockTop, 0f)
            assertEquals(trackBottoms[index], trackBottom, 0f)
            assertEquals(rememberBottoms[index], rememberBottom, 0f)
            assertEquals(screenHeight - 12f, rememberBottom, 0f)

            // 头 + 内容带的底边正好落在滑轨顶边上方 12dp：头再高也只是让内容带变矮
            val textBottom = ApprovalBandMetrics.HEADER_HEIGHT_DP +
                ApprovalBandMetrics.contentBandHeightDp(screenHeight, ApprovalBlockHeight.value)
            assertEquals(trackTops[index] - 12f, textBottom, 0f)

            // 反例：若头被叠在内容上（而不是从内容带里扣），文字底边就会压进滑轨热区
            val overlayInsteadOfDeduct = textBottom + ApprovalBandMetrics.HEADER_HEIGHT_DP
            assertTrue(overlayInsteadOfDeduct > trackTops[index] - 12f)
        }
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

    /**
     * 顶边遮罩：高 12dp，出现条件读的是滚动状态而不是常量。
     *
     * 钉底必然在内容带顶边切开最上面那一行（真机 docs/device/v10/99f_approval.png：「调用 run_shell」只剩半截），
     * 遮罩把这半行压回 [Wear.Palette.Bg]。但滚动量为 0 时整条都不许画 —— 四块都放得下、
     * 整段根本不可滚的时候，画遮罩只会平白压暗第一行，把"没有内容"谎报成"上面还有内容"。
     */
    @Test
    fun testTopMask_appearsOnlyWhenContentBandIsScrolled() {
        assertEquals(12f, ApprovalTopMaskHeight.value, 0f)
        // 遮罩不能比内容带还高：233dp 真表内容带只有 87dp
        assertTrue(
            ApprovalTopMaskHeight.value <
                ApprovalBandMetrics.contentBandHeightDp(233f, ApprovalBlockHeight.value)
        )
        // 滚动量 0（含完全不可滚的短文案）：不画
        assertEquals(0f, approvalTopMaskAlphaFor(0), 0f)
        // 只要有滚动量就是满不透明度，被切开的半行不会留下半透明的残影
        assertEquals(1f, approvalTopMaskAlphaFor(1), 0f)
        assertEquals(1f, approvalTopMaskAlphaFor(96), 0f)
        // 反例：判据一旦退化成常数，"滚动量为 0 不画"这条就红
        assertTrue(approvalTopMaskAlphaFor(0) < approvalTopMaskAlphaFor(1))
    }

    /** 守恒式的逐项展开：固定头 + 内容带 + 间隙 + 审批块 + 底距。 */
    private fun screenStackDp(screenHeightDp: Float, blockHeightDp: Float): Float =
        ApprovalBandMetrics.HEADER_HEIGHT_DP +
            ApprovalBandMetrics.contentBandHeightDp(screenHeightDp, blockHeightDp) +
            ApprovalBandMetrics.CONTENT_TO_TRACK_GAP_DP +
            blockHeightDp +
            ApprovalBandMetrics.BAR_BOTTOM_INSET_DP
}
