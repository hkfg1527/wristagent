package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置页版式法则的可执行断言：槽线、亲密性比例、字号阶梯、线的长度。
 *
 * 这些数字一旦被"顺手调一下"，页面就会退回"整体居中 + 到处强调色"的旧样子，
 * 所以钉在单测里，而不是靠肉眼看真机。
 */
class SettingsDesignMathTest {

    @Test
    fun contentBoxIsNarrowerThanTheSafetyWidthSoTheCircleEdgeBreathes() {
        assertEquals(176f, SettingsDesignMath.ContentWidthDp, 0.001f)
        assertTrue("内容盒必须窄于 184dp 安全宽", SettingsDesignMath.ContentWidthDp < 184f)
        assertTrue("内容盒不该窄到放不下一行取值", SettingsDesignMath.ContentWidthDp >= 168f)
    }

    @Test
    fun sideInsetCentersTheWholeBlockOnEveryScreen() {
        // 233dp 真机：(233 − 176) / 2 = 28.5dp
        assertEquals(28.5f, SettingsDesignMath.sideInsetDp(233f), 0.001f)
        // 左右内缩 + 内容盒 == 屏宽：整块居中，左右两条槽线全页一致
        listOf(200f, 233f, 260f, 320f).forEach { screen ->
            val box = screen - SettingsDesignMath.sideInsetDp(screen) * 2f
            assertEquals("screen=" + screen, SettingsDesignMath.ContentWidthDp, box, 0.5f)
        }
    }

    @Test
    fun sideInsetNeverGoesBelowTheSafetyMargin() {
        assertEquals(SettingsLayoutMath.SideInsetDp, SettingsDesignMath.sideInsetDp(0f), 0.001f)
        assertTrue(SettingsDesignMath.sideInsetDp(176f) >= SettingsLayoutMath.SideInsetDp)
    }

    @Test
    fun sectionGapIsAtLeastTwiceTheRowGap() {
        assertEquals(24f, SettingsDesignMath.SectionGapDp, 0.001f)
        assertEquals(SettingsSpacingMath.ItemGapDp, SettingsDesignMath.RowGapDp, 0.001f)
        assertTrue("组间必须 >= 组内两倍", SettingsDesignMath.isSectionRhythmReadable())
        // 实际比值：24 / 4 = 6（并卡后卡内行距为 0，组内分界只是一根 1px 线，比例只会更悬殊）
        assertEquals(6f, SettingsDesignMath.sectionToRowRatio(), 0.001f)
    }

    @Test
    fun headerToFirstRowIsTighterThanTheSectionGap() {
        // 组标题 → 本组首行 8dp：同组内更亲密，且明显小于组间 24dp
        assertTrue(SettingsSpacingMath.SectionHeaderBottomDp < SettingsDesignMath.SectionGapDp)
        assertTrue(
            "组间 24dp 必须 >= 标题到首行 8dp 的两倍",
            SettingsDesignMath.SectionGapDp >= SettingsSpacingMath.SectionHeaderBottomDp * 2f
        )
    }

    @Test
    fun fontLadderHasExactlyThreeTiers() {
        val tiers = listOf(
            SettingsDesignMath.SectionLabelSp.toInt(),
            SettingsDesignMath.RowTextSp.toInt(),
            SettingsDesignMath.FocusSp.toInt()
        )
        assertEquals(listOf(12, 15, 18), tiers)
        assertEquals(SettingsDesignMath.MaxFontTiers, SettingsDesignMath.fontTierCount(tiers))
        // 堆字号的反例：再加一档就超限
        assertFalse(
            SettingsDesignMath.fontTierCount(tiers + 21) <= SettingsDesignMath.MaxFontTiers
        )
    }

    @Test
    fun subItemIndentOnlyMovesTheLabelSlot() {
        // 子项只右移左槽线 12dp，字号与右槽线都不动
        assertEquals(12f, SettingsDesignMath.SubItemIndentDp, 0.001f)
        assertTrue(SettingsDesignMath.SubItemIndentDp < SettingsDesignMath.ContentWidthDp / 4f)
        assertTrue("子项右移量必须落在 4dp 网格上", SettingsSpacingMath.isOnGrid(SettingsDesignMath.SubItemIndentDp))
    }

    @Test
    fun subItemDividerIsShorterThanTheRowWidth() {
        assertEquals(24f, SettingsDesignMath.DividerRightInsetDp, 0.001f)
        val lineLength = SettingsDesignMath.ContentWidthDp -
            SettingsDesignMath.SubItemIndentDp - SettingsDesignMath.DividerRightInsetDp
        assertEquals(140f, lineLength, 0.001f)
        assertTrue("线必须短于行宽", lineLength < SettingsDesignMath.ContentWidthDp)
        assertTrue("线也不能短到读不出来", lineLength > SettingsDesignMath.ContentWidthDp / 2f)
    }

    @Test
    fun promptBodyLineHeightIsTheDocumentedOne() {
        assertEquals(22f, SettingsDesignMath.BodyLineHeightSp, 0.001f)
        assertEquals(8f, SettingsDesignMath.ParagraphGapDp, 0.001f)
        assertEquals(12f, SettingsDesignMath.FadeDp, 0.001f)
        // 行高比正文大 1.4 倍以上，中文墨距才不挤
        assertTrue(
            SettingsDesignMath.BodyLineHeightSp / SettingsDesignMath.RowTextSp >= 1.4f
        )
    }

    @Test
    fun onlyOneStatusDotPerPage() {
        assertEquals(1, SettingsDesignMath.StatusDotsPerPage)
    }
}
