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
    fun shortTitleRowWouldPushTheWholeListDownUnderAutoCentering() {
        // 标题行不再为了凑 spacer = 0 而撑到 86dp（那正是顶部 41dp 黑带的来源）。
        // 行只有约 32dp 时，itemIndex = 1 会让库算出 ~60dp 的顶部 spacer ——
        // 所以设置页显式传 autoCentering = null，顶部锚点只由 contentPadding.top 决定。
        val spacerPx = SettingsTopAnchorMath.topSpacerPx(
            viewportHeightPx = 466, // 233dp @2x 真机
            itemGapPx = 8, // 4dp 行间距
            centerItemSizePx = 80, // 组标题行 ~40dp
            sizesAboveCenterItemPx = listOf(64) // 标题行 ~32dp
        )
        assertTrue("spacer=" + spacerPx + "px", spacerPx > 100)
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

    /** 根页顶部只给 24dp 渐变遮罩让位：行内不再额外留白（旧值 14dp，把顶部黑带撑到 41dp）。 */
    @Test
    fun rootTitleInnerPaddingDropsToZero() {
        assertEquals(0f, RootTitleTopPadding.value, 0.001f)
        val topDp = 24f + SettingsSpacingMath.ItemGapDp + RootTitleTopPadding.value
        assertTrue("标题顶边 " + topDp + "dp 必须越过 24dp 遮罩", topDp >= SettingsLayoutMath.TopScrimDp)
        assertTrue("标题顶边 " + topDp + "dp 不应超过 36dp", topDp <= 36f)
    }

    @Test
    fun subPageHeaderClearsTheTopScrim() {
        // 行内留白是由遮罩高度反推出来的：行内边距 + 行间距 + 留白 必须正好落在遮罩下沿，
        // 所以这里断言落点而不是某个写死的 dp 值。
        val topDp = Wear.Space.Xs.value + SettingsSpacingMath.ItemGapDp + SubPageHeaderTopPadding.value
        assertEquals(topDp, SettingsLayoutMath.TopScrimDp, 0.001f)
        assertTrue("子页顶部留白不允许为负", SubPageHeaderTopPadding.value >= 0f)
    }

    private fun assertEquals(message: String, expected: Int, actual: Int) {
        org.junit.Assert.assertEquals(message, expected.toLong(), actual.toLong())
    }
}
