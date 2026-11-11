package com.example.myapplication.presentation.ui

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
                "第 " + (index + 1) + " 页说明按 12sp 估宽 " + estimated + "dp，超过正文列 " +
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

    /** 按 12sp 的保守估宽：中日韩字形整格 12dp，其余半格 7dp。 */
    private fun estimatedWidthDp(text: String): Float {
        var width = 0f
        text.forEach { char ->
            width += if (char.code > 0x2E80) 12f else 7f
        }
        return width
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
        assertTrue(
            "正文列 " + OnboardingGeometry.ContentWidthDp + "dp 超过 184dp 安全宽",
            OnboardingGeometry.ContentWidthDp <= 184f
        )
    }

    @Test
    fun illustrationStaysInTheSuggestedRange() {
        assertTrue(OnboardingGeometry.IllustrationDp >= 96f)
        assertTrue(OnboardingGeometry.IllustrationDp <= 120f)
    }

    @Test
    fun everyRowStaysInsideTheCircle() {
        val rows = listOf(
            Triple("示意图", OnboardingGeometry.IllustrationDp, OnboardingGeometry.IllustrationTopDp),
            Triple("标题", OnboardingGeometry.ContentWidthDp, OnboardingGeometry.TitleTopDp),
            Triple("说明", OnboardingGeometry.ContentWidthDp, OnboardingGeometry.DescriptionTopDp),
            Triple("页脚", OnboardingGeometry.FooterWidthDp, OnboardingGeometry.FooterTopDp)
        )
        rows.forEach { (name, width, top) ->
            val height = when (name) {
                "示意图" -> OnboardingGeometry.IllustrationDp
                "标题" -> OnboardingGeometry.TitleLineDp
                "说明" -> OnboardingGeometry.DescriptionLineDp
                else -> OnboardingGeometry.FooterHeightDp
            }
            assertTrue(
                name + " 的最远角点半径 " + OnboardingGeometry.farthestCornerRadiusDp(width, height, top) +
                    "dp 超过屏幕半径 " + (OnboardingGeometry.CircleDiameterDp / 2f) + "dp",
                OnboardingGeometry.fitsInCircle(width, height, top)
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
    fun footerBudgetAddsUpToTheFooterWidth() {
        val dots = OnboardingGeometry.DotSizeDp * OnboardingPager.PageCount +
            OnboardingGeometry.GapDp * (OnboardingPager.PageCount - 1)
        assertEquals(dots, OnboardingGeometry.DotsWidthDp, 0.001f)
        assertEquals(
            OnboardingGeometry.SkipWidthDp + OnboardingGeometry.GapDp + OnboardingGeometry.DotsWidthDp +
                OnboardingGeometry.GapDp + OnboardingGeometry.ActionWidthDp,
            OnboardingGeometry.FooterWidthDp,
            0.001f
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
    fun touchTargetsMeetTheTokenMinimum() {
        assertTrue(OnboardingGeometry.FooterHeightDp >= 44f)
        assertTrue(OnboardingGeometry.SkipWidthDp >= 44f)
    }
}
