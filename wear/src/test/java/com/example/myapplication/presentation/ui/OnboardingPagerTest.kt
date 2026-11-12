package com.example.myapplication.presentation.ui

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 引导页纯逻辑守门：页数、按钮文案、页码点、跳过入口、文案是否放得下一行。
 * 这些不是排版偏好，是"少一页文案被截断 / 末页按钮还是继续"这类会直接放走的错。
 */
class OnboardingPagerTest {

    @Test
    fun fivePagesAndFiveDots() {
        assertEquals(5, OnboardingPager.PageCount)
        assertEquals(
            "页码点个数必须与页数一致",
            OnboardingPager.PageCount,
            OnboardingPager.Pages.size
        )
    }

    @Test
    fun everyTitleFitsTheEightCharacterLimit() {
        OnboardingPager.Pages.forEachIndexed { index, page ->
            assertTrue("第 " + (index + 1) + " 页标题为空", page.title.isNotBlank())
            assertTrue(
                "第 " + (index + 1) + " 页标题超过 8 字：" + page.title,
                page.title.length <= 8
            )
        }
    }

    @Test
    fun everyDescriptionFitsTheTwentyCharacterLimit() {
        OnboardingPager.Pages.forEachIndexed { index, page ->
            assertTrue("第 " + (index + 1) + " 页说明为空", page.description.isNotBlank())
            assertTrue(
                "第 " + (index + 1) + " 页说明超过 20 字：" + page.description,
                page.description.length <= 20
            )
        }
    }

    @Test
    fun everyDescriptionFitsOneLineOfTheContentColumn() {
        OnboardingPager.Pages.forEachIndexed { index, page ->
            val estimated = estimatedWidthDp(page.description)
            assertTrue(
                "第 " + (index + 1) + " 页说明按 15sp 估宽 " + estimated + "dp，超过正文列 " +
                    OnboardingGeometry.ContentWidthDp + "dp 会被省略号截断：" + page.description,
                estimated <= OnboardingGeometry.ContentWidthDp
            )
        }
    }

    @Test
    fun titlesAreAllDistinct() {
        val titles = OnboardingPager.Pages.map { it.title }
        assertEquals("五页标题重复，等于只有四页内容", titles.size, titles.toSet().size)
    }

    @Test
    fun nextAdvancesAndStopsOnTheLastPage() {
        assertEquals(1, OnboardingPager.next(0))
        assertEquals(4, OnboardingPager.next(3))
        assertEquals("末页不许再前进到不存在的第 6 页", 4, OnboardingPager.next(4))
    }

    @Test
    fun isLastOnlyOnTheFinalPage() {
        assertFalse(OnboardingPager.isLast(0))
        assertFalse(OnboardingPager.isLast(3))
        assertTrue(OnboardingPager.isLast(4))
    }

    @Test
    fun actionLabelStartsOnTheLastPageAndContinuesBefore() {
        assertEquals("继续", OnboardingPager.actionLabel(0))
        assertEquals("继续", OnboardingPager.actionLabel(3))
        assertEquals("开始", OnboardingPager.actionLabel(4))
    }

    @Test
    fun skipIsAvailableFromTheFirstPageAndGoneOnTheLast() {
        assertTrue("第 1 页必须能看到跳过入口", OnboardingPager.canSkip(0))
        assertTrue(OnboardingPager.canSkip(3))
        assertFalse("末页按钮已经是开始，不再并排第二个出口", OnboardingPager.canSkip(4))
    }

    @Test
    fun exactlyOneDotIsActivePerPage() {
        for (page in 0 until OnboardingPager.PageCount) {
            val active = (0 until OnboardingPager.PageCount).filter {
                OnboardingPager.isDotActive(it, page)
            }
            assertEquals("第 " + (page + 1) + " 页应当只点亮一个点，实际 " + active, listOf(page), active)
        }
    }

    @Test
    fun skipLabelIsAlwaysAvailable() {
        assertTrue(OnboardingPager.SkipLabel.isNotBlank())
    }

    /** 按 15sp 的保守估宽：中日韩字形整格 15dp，其余半格 9dp。 */
    private fun estimatedWidthDp(text: String): Float {
        var width = 0f
        text.forEach { char ->
            width += if (char.code > 0x2E80) 15f else 9f
        }
        return width
    }

    @Test
    fun copyStillNamesTheRealGestureThresholds() {
        assertEquals("按住说话", OnboardingPager.Pages[0].title)
        assertEquals("长按 0.34 秒，松手发送", OnboardingPager.Pages[0].description)
        assertEquals("轻触主键", OnboardingPager.Pages[1].title)
        assertEquals("单击恒为语音起录", OnboardingPager.Pages[1].description)
        assertEquals("上滑设置", OnboardingPager.Pages[2].title)
        assertEquals("上滑越过 38dp 进设置", OnboardingPager.Pages[2].description)
        assertEquals("横滑两翼", OnboardingPager.Pages[3].title)
        assertEquals("左翼打字，右翼开会话", OnboardingPager.Pages[3].description)
        assertEquals("滑动放行", OnboardingPager.Pages[4].title)
        assertEquals("左滑放行，点按拒绝", OnboardingPager.Pages[4].description)
    }
}

/**
 * 引导页几何守门：每一块元素的最远角点都必须落在圆屏内。
 * 圆屏 233dp（466px @2x），内容宽 184dp 只在圆心附近成立，越往下弦长收得越快。
 */
class OnboardingGeometryTest {

    @Test
    fun contentWidthRespectsTheSafeWidth() {
        assertEquals(233f, OnboardingGeometry.CircleDiameterDp, 0.001f)
        assertEquals(176f, OnboardingGeometry.ContentWidthDp, 0.001f)
        assertTrue(
            "正文列 " + OnboardingGeometry.ContentWidthDp + "dp 超过 184dp 安全宽",
            OnboardingGeometry.ContentWidthDp <= 184f
        )
    }

    @Test
    fun illustrationStaysInTheSuggestedRange() {
        assertEquals(96f, OnboardingGeometry.IllustrationDp, 0.001f)
        assertTrue(OnboardingGeometry.IllustrationDp >= 96f)
        assertTrue(OnboardingGeometry.IllustrationDp <= 120f)
    }

    /** 一块元素占的四个量：名字 + 宽 + 高 + 上沿相对圆心的 y。 */
    private data class Row(
        val name: String,
        val widthDp: Float,
        val heightDp: Float,
        val topDp: Float
    )

    private val rows: List<Row> = listOf(
        Row(
            "示意图",
            OnboardingGeometry.IllustrationDp,
            OnboardingGeometry.IllustrationDp,
            OnboardingGeometry.IllustrationTopDp
        ),
        Row("标题", OnboardingGeometry.ContentWidthDp, OnboardingGeometry.TitleLineDp, OnboardingGeometry.TitleTopDp),
        Row(
            "说明",
            OnboardingGeometry.ContentWidthDp,
            OnboardingGeometry.DescriptionLineDp,
            OnboardingGeometry.DescriptionTopDp
        ),
        Row("页码点", OnboardingGeometry.DotsWidthDp, OnboardingGeometry.DotsRowHeightDp, OnboardingGeometry.DotsTopDp),
        Row("页脚", OnboardingGeometry.FooterWidthDp, OnboardingGeometry.FooterHeightDp, OnboardingGeometry.FooterTopDp)
    )

    @Test
    fun everyRowStaysInsideTheCircle() {
        rows.forEach { row ->
            assertTrue(
                row.name + " 的最远角点半径 " +
                    OnboardingGeometry.farthestCornerRadiusDp(row.widthDp, row.heightDp, row.topDp) +
                    "dp 超过屏幕半径 " + (OnboardingGeometry.CircleDiameterDp / 2f) + "dp",
                OnboardingGeometry.fitsInCircle(row.widthDp, row.heightDp, row.topDp)
            )
        }
    }

    @Test
    fun aFullSafeWidthFooterWouldLeaveTheCircle() {
        // 反面样本：页脚若按 184dp 满铺，角点半径就会越过圆周，说明这条判据不是恒真
        assertFalse(
            OnboardingGeometry.fitsInCircle(
                widthDp = 184f,
                heightDp = OnboardingGeometry.FooterHeightDp,
                topDp = OnboardingGeometry.FooterTopDp
            )
        )
        assertTrue(
            OnboardingGeometry.farthestCornerRadiusDp(184f, OnboardingGeometry.FooterHeightDp, OnboardingGeometry.FooterTopDp) >
                OnboardingGeometry.CircleDiameterDp / 2f
        )
    }

    @Test
    fun dotsWidthIsFiveDotsAndFourGaps() {
        val dots = OnboardingGeometry.DotSizeDp * OnboardingPager.PageCount +
            OnboardingGeometry.GapDp * (OnboardingPager.PageCount - 1)
        assertEquals(dots, OnboardingGeometry.DotsWidthDp, 0.001f)
    }

    @Test
    fun footerKeepsTheChordBudgetItHadWithTheDotsInIt() {
        // 页码点搬走后页脚总宽一个像素没变：跳过 + 中段留白 + 按钮，中段留白正是原点阵那一档
        assertEquals(
            OnboardingGeometry.GapDp * 2f + OnboardingGeometry.DotsWidthDp,
            OnboardingGeometry.FooterMiddleGapDp,
            0.001f
        )
        assertEquals(
            OnboardingGeometry.SkipWidthDp + OnboardingGeometry.FooterMiddleGapDp +
                OnboardingGeometry.ActionWidthDp,
            OnboardingGeometry.FooterWidthDp,
            0.001f
        )
        assertEquals("三件套时代的页脚总宽，搬位置不许改预算", 136f, OnboardingGeometry.FooterWidthDp, 0.001f)
    }

    @Test
    fun dotsSitRightUnderTheDescriptionAndOutsideTheFooter() {
        assertTrue(
            "页码点必须落在说明文字下方：点行上沿 " + OnboardingGeometry.DotsTopDp +
                "dp，说明行下沿 " + (OnboardingGeometry.DescriptionTopDp + OnboardingGeometry.DescriptionLineDp) + "dp",
            OnboardingGeometry.DotsTopDp >=
                OnboardingGeometry.DescriptionTopDp + OnboardingGeometry.DescriptionLineDp
        )
        assertEquals(
            "页码点行的高度就是一排点，没有额外撑高",
            OnboardingGeometry.DotSizeDp,
            OnboardingGeometry.DotsRowHeightDp,
            0.001f
        )
        assertEquals(
            "点阵与页脚保留独立 8dp 间隔",
            OnboardingGeometry.FooterTopDp,
            OnboardingGeometry.DotsTopDp + OnboardingGeometry.DotsRowHeightDp +
                OnboardingGeometry.FooterGapDp,
            0.001f
        )
        assertTrue(
            "页码点不许再回到页脚那一行里",
            OnboardingGeometry.DotsTopDp < OnboardingGeometry.FooterTopDp
        )
        assertEquals(4f, OnboardingGeometry.TextGroupGapDp, 0.001f)
        assertEquals(8f, OnboardingGeometry.FooterGapDp, 0.001f)
    }

    @Test
    fun aRowShiftedTwelveDpDownLeavesTheCircle() {
        // 第二组反面样本：把页脚整体下移 12dp 就出圆 —— 判据跟着几何参数动，不是恒真
        assertFalse(
            OnboardingGeometry.fitsInCircle(
                OnboardingGeometry.FooterWidthDp,
                OnboardingGeometry.FooterHeightDp,
                OnboardingGeometry.FooterTopDp + 12f
            )
        )
    }

    @Test
    fun theContentColumnIsVerticallyCenteredOnItsOwnNumbers() {
        // 内容盒上沿 + 半个内容高 = Column 的居中偏移，两者必须同源，否则版面会整体偏位
        assertEquals(
            OnboardingGeometry.ContentTopDp + OnboardingGeometry.ContentHeightDp / 2f,
            OnboardingGeometry.ContentCenterOffsetDp,
            0.001f
        )
        assertEquals(
            OnboardingGeometry.ContentTopDp + OnboardingGeometry.ContentHeightDp,
            OnboardingGeometry.FooterTopDp + OnboardingGeometry.FooterHeightDp,
            0.001f
        )
    }

    @Test
    fun entryShiftIsSmallAndStillInsideTheCircle() {
        assertEquals(4f, OnboardingGeometry.EntryShiftDp, 0.001f)
        assertTrue(OnboardingGeometry.EntryShiftDp in 2f..4f)
        rows.take(3).forEach { row ->
            assertTrue(
                row.name + "在 4dp 入场偏移时出圆",
                OnboardingGeometry.fitsInCircle(
                    row.widthDp,
                    row.heightDp,
                    row.topDp + OnboardingGeometry.EntryShiftDp
                )
            )
        }
    }

    @Test
    fun everyIllustrationKeepsItsStrokesInsideTheCanvas() {
        assertEquals(40f, OnboardingGeometry.FrameRadiusDp, 0.001f)
        val halfCanvas = OnboardingGeometry.IllustrationDp / 2f
        val pageExtents = listOf(
            33f + 1.25f,
            33f + 1.25f,
            maxOf(31f + 1.5f, 18f + 15f),
            32f + 11f + 0.75f,
            72f / 2f,
            OnboardingGeometry.FrameRadiusDp + 2.5f
        )
        pageExtents.forEachIndexed { index, extreme ->
            assertTrue("第 " + (index + 1) + " 页图形越过 96dp 画布", extreme <= halfCanvas)
        }
    }

    @Test
    fun touchTargetsMeetTheTokenMinimum() {
        assertTrue(OnboardingGeometry.FooterHeightDp >= 44f)
        assertTrue(OnboardingGeometry.SkipWidthDp >= 44f)
    }
}

/**
 * 引导页源码守护：页码点搬了位置，读屏描述与"页脚只剩两件"这两条约束不会由编译器检查。
 * 只有扫真实源文件才能钉住 —— 否则下一次改版面会把「第 N 页，共 5 页」连同位置一起丢掉。
 */
class OnboardingSourceGuardTest {

    private val source: String = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/OnboardingScreen.kt"
    )

    @Test
    fun pageDotsKeepTheirAccessibilityDescription() {
        val dots = source
            .substringAfter("private fun PageDots(")
            .substringBefore("private fun GestureIllustration(")
        assertTrue(
            "页码点那一行必须保留「第 N 页，共 5 页」的读屏描述",
            dots.contains("contentDescription")
        )
        assertTrue("页码点数量仍取页数常量，不许各自漂移", dots.contains("OnboardingPager.PageCount"))
    }

    @Test
    fun theFooterOnlyKeepsSkipAndTheActionButton() {
        val footer = source
            .substringAfter("private fun OnboardingFooter(")
            .substringBefore("private fun PageDots(")
        assertFalse("页码点必须已经搬出页脚", footer.contains("PageDots("))
        assertTrue(
            "页脚两件左右分置：跳过贴左、按钮贴右，中段留空",
            footer.contains("Arrangement.SpaceBetween")
        )
    }

    @Test
    fun titleAndDescriptionKeepTheirRequiredFontSizes() {
        val body = source
            .substringAfter("private fun PageBody(")
            .substringBefore("private fun OnboardingFooter(")
        assertTrue(body.contains("Wear.TextTitle"))
        assertTrue(body.contains("Wear.TextBody"))
        assertTrue(body.contains("fontSize = 15.sp"))
        assertTrue("说明必须降为次级文本，不能与标题抢焦点", body.contains("color = Wear.Palette.TextSecondary"))
        assertTrue(body.contains("maxLines = 1"))
    }

    @Test
    fun pageTransitionUsesSharedMotionAndOnlyASmallEntryShift() {
        val screen = source
            .substringAfter("fun OnboardingScreen(")
            .substringBefore("private fun PageBody(")
        assertTrue(screen.contains("slideInVertically"))
        assertTrue(screen.contains("motionSpec(animationsEnabled, Wear.Motion.page())"))
        assertTrue(screen.contains("OnboardingGeometry.EntryShiftDp"))
        assertFalse("引导页不再有持续循环动画", source.contains("rememberInfiniteTransition"))
        assertFalse(source.contains("infiniteRepeatable"))
        assertFalse("审批示意不得用大面积红绿拼色", source.contains("Wear.Palette.Danger"))
        assertFalse("审批示意不得用大面积红绿拼色", source.contains("Wear.Palette.Success"))
    }

    @Test
    fun gestureCopyStillMatchesTheRealHandlers() {
        val orb = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/BottomInteractiveBlock.kt")
        assertTrue(orb.contains("delay(340)"))
        assertTrue(orb.contains("onVoiceStopAndTrigger()"))
        assertTrue(orb.contains("totalDeltaY < -38.dp.toPx()"))
        assertTrue(orb.contains("abs(deltaX) > 22.dp.toPx()"))
        assertTrue(orb.contains("onLeftKeyboardClick()"))
        assertTrue(orb.contains("onRightActionClick()"))
        assertTrue(orb.contains("ApprovalSliderLogic.isRejectTap"))
        assertTrue(orb.contains("ApprovalSliderLogic.shouldAllow"))

        val approval = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/ApprovalSliderState.kt")
        assertTrue(approval.contains("ALLOW_THRESHOLD = 0.85f"))
        assertTrue(approval.contains("fun shouldAllow(progress: Float): Boolean"))
        assertTrue(approval.contains("progress >= ALLOW_THRESHOLD"))
    }
}
