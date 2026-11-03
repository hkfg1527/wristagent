package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置页顶部对齐的几何约束：滚到最顶时标题上方不许再留出被 autoCentering 撑开的空白。
 *
 * 公式取自 wear compose foundation 的 ScalingLazyListState.spaceNeeded（见 [SettingsTopAnchorMath] 注释），
 * 这里用真机同量级的数值（200dp 屏、8dp 行距、86dp 标题行）把两种 itemIndex 的差别钉死。
 */
class SettingsTopAnchorMathTest {

    private val viewportPx = 400 // 200dp @ 2.0 密度
    private val gapPx = 16 // Wear.Space.Sm = 8dp
    private val contentPaddingTopPx = 16 // 根页顶部内边距 8dp
    private val titleRowPx = 172 // 34dp 时钟带 + 44dp 触摸高度 + 8dp 下留白 = 86dp
    private val firstRowPx = 80 // 一条普通设置行

    @Test
    fun legacyHeaderCenteredConfigLeavesHalfScreenBlank() {
        // itemIndex = 0：标题自己被居中，spacer 几乎半屏 —— 这正是用户看到的"字在中间、上面一大片空"
        val spacer = SettingsTopAnchorMath.topSpacerPx(
            viewportHeightPx = viewportPx,
            itemGapPx = gapPx,
            centerItemSizePx = titleRowPx
        )
        val top = SettingsTopAnchorMath.topItemTopPx(contentPaddingTopPx, spacer, gapPx)

        assertTrue("旧配置的标题顶边应被推到屏幕中下部", top >= viewportPx / 4)
    }

    @Test
    fun headerAsListHeaderMovesTitleUpByMoreThanAThird() {
        // 回归阀门：把 itemIndex 改回 0（或把标题行重新变成自动居中项）时这条必须变红
        val legacyTop = SettingsTopAnchorMath.topItemTopPx(
            contentPaddingTopPx,
            SettingsTopAnchorMath.topSpacerPx(
                viewportHeightPx = viewportPx,
                itemGapPx = gapPx,
                centerItemSizePx = titleRowPx
            ),
            gapPx
        )
        val fixedTop = SettingsTopAnchorMath.topItemTopPx(
            contentPaddingTopPx,
            SettingsTopAnchorMath.topSpacerPx(
                viewportHeightPx = viewportPx,
                itemGapPx = gapPx,
                centerItemSizePx = firstRowPx,
                sizesAboveCenterItemPx = listOf(titleRowPx)
            ),
            gapPx
        )

        assertTrue("修复后标题顶边至少要上移三分之一屏", fixedTop * 3 < legacyTop)
    }

    @Test
    fun headerAsListHeaderPutsTitleRightUnderTheClockBand() {
        // itemIndex = 1：自动居中项变成第一条内容行，它前面的标题行把 spacer 压到 0
        val spacer = SettingsTopAnchorMath.topSpacerPx(
            viewportHeightPx = viewportPx,
            itemGapPx = gapPx,
            centerItemSizePx = firstRowPx,
            sizesAboveCenterItemPx = listOf(titleRowPx)
        )
        val top = SettingsTopAnchorMath.topItemTopPx(contentPaddingTopPx, spacer, gapPx)

        assertEquals("首项之上不应再有 autoCentering 撑出的空白", 0, spacer)
        assertEquals(contentPaddingTopPx + gapPx, top)
        assertTrue("标题必须贴在上半屏", top < viewportPx / 4)
    }

    @Test
    fun titleTextTopLandsExactlyOnTheClockBandBottom() {
        // 不变量：列表顶部内边距 + 行间距 + 行内顶部留白 == 34dp 时钟带下沿
        val topInsetDp = 34f
        val paddingTopDp = 8f
        val itemGapDp = 8f

        val innerTop = SettingsTopAnchorMath.titleTopPaddingDp(topInsetDp, paddingTopDp, itemGapDp)

        assertEquals(18f, innerTop, 0.001f)
        assertEquals(topInsetDp, paddingTopDp + itemGapDp + innerTop, 0.001f)
    }

    @Test
    fun titleInnerPaddingNeverGoesNegative() {
        // 令牌变化导致"行顶边已经在时钟带以下"时取 0，不制造负留白
        assertEquals(0f, SettingsTopAnchorMath.titleTopPaddingDp(34f, 30f, 8f), 0.001f)
        assertEquals(4f, SettingsTopAnchorMath.titleTopPaddingDp(34f, 26f, 4f), 0.001f)
    }

    @Test
    fun spacerIsZeroBeforeTheViewportIsMeasured() {
        // 首帧还没有视口尺寸：不制造 spacer，避免入场时先被推开一帧
        assertEquals(
            0,
            SettingsTopAnchorMath.topSpacerPx(
                viewportHeightPx = 0,
                itemGapPx = gapPx,
                centerItemSizePx = titleRowPx
            )
        )
    }

    @Test
    fun rootTitleStillLandsOnTheClockBandAfterTheItemGapWidens() {
        // 行间距从旧的 8dp 加宽后会把整排往下顶：行内顶部留白必须同步收窄同样多，
        // 标题文字顶边才能继续压在 34dp 时钟带下沿。这条是"改间距不许顶动标题"的阀门。
        assertEquals(
            Wear.Size.TopContentInset.value,
            Wear.Space.Sm.value + SettingsSpacingMath.ItemGapDp + RootTitleTopPadding.value,
            0.001f
        )
        assertEquals(14f, RootTitleTopPadding.value, 0.001f)
    }

    @Test
    fun subPageHeaderStillLandsOnTheClockBandAfterTheItemGapWidens() {
        assertEquals(
            Wear.Size.TopContentInset.value,
            Wear.Space.Xs.value + SettingsSpacingMath.ItemGapDp + SubPageHeaderTopPadding.value,
            0.001f
        )
        assertEquals(18f, SubPageHeaderTopPadding.value, 0.001f)
    }

    private fun assertEquals(message: String, expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(message, expected.toLong(), actual.toLong())
    }
}
