package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「每组一张卡」的可执行规格：位置 -> 圆角、行间 -> 一根线、卡外行不进卡。
 *
 * 判据全部走渲染路径里的纯函数（[SettingsRowComposition.rowSlots] / [SettingsRowComposition.shapeOf]
 * / [SettingsRowComposition.drawsGroupHairline]），实现改坏这里就会红，而不是测试里另写一份 if。
 */
class SettingsGroupCardTest {

    // ===== 位置：卡外行（动作按钮）不占卡内位置，组由连续的卡片行推出 =====

    @Test
    fun singleRowGroupGetsTheAllRoundShape() {
        val slots = SettingsRowComposition.rowSlots(
            listOf(SettingsRowEntryKind.CARD_ROW)
        )
        assertEquals(SettingsRowSlot.SINGLE, slots[0])
        assertEquals(SettingsRowShape(true, true), SettingsRowComposition.shapeOf(slots[0]))
    }

    @Test
    fun twoRowGroupRoundsTopAndBottomOnly() {
        val slots = SettingsRowComposition.rowSlots(
            listOf(SettingsRowEntryKind.CARD_ROW, SettingsRowEntryKind.CARD_ROW)
        )
        assertEquals(SettingsRowSlot.FIRST, slots[0])
        assertEquals(SettingsRowSlot.LAST, slots[1])
        assertEquals(SettingsRowShape(true, false), SettingsRowComposition.shapeOf(slots[0]))
        assertEquals(SettingsRowShape(false, true), SettingsRowComposition.shapeOf(slots[1]))
    }

    @Test
    fun fiveRowGroupKeepsMiddleRowsSquare() {
        val slots = SettingsRowComposition.rowSlots(
            List(5) { SettingsRowEntryKind.CARD_ROW }
        )
        assertEquals(
            listOf(
                SettingsRowSlot.FIRST,
                SettingsRowSlot.MIDDLE,
                SettingsRowSlot.MIDDLE,
                SettingsRowSlot.MIDDLE,
                SettingsRowSlot.LAST
            ),
            slots
        )
        // 组首只圆上两角、组中四角皆方、组尾只圆下两角
        assertEquals(SettingsRowShape(true, false), SettingsRowComposition.shapeOf(slots[0]))
        assertEquals(SettingsRowShape(false, false), SettingsRowComposition.shapeOf(slots[2]))
        assertEquals(SettingsRowShape(false, true), SettingsRowComposition.shapeOf(slots[4]))
    }

    @Test
    fun groupBoundariesResetAcrossASectionHeader() {
        val slots = SettingsRowComposition.rowSlots(
            listOf(
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.SECTION,
                SettingsRowEntryKind.CARD_ROW
            )
        )
        // 标题两侧各是单行组：两张独立卡，不跨标题粘连
        assertEquals(SettingsRowSlot.SINGLE, slots[0])
        assertEquals(null, slots[1])
        assertEquals(SettingsRowSlot.SINGLE, slots[2])
    }

    @Test
    fun standaloneActionRowBreaksTheCardAndTakesNoSlot() {
        val slots = SettingsRowComposition.rowSlots(
            listOf(
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.STANDALONE,
                SettingsRowEntryKind.CARD_ROW
            )
        )
        // 动作按钮（完成返回 / 清空记忆）不并卡：前后两行各是单行组
        assertEquals(SettingsRowSlot.SINGLE, slots[0])
        assertEquals(null, slots[1])
        assertEquals(SettingsRowSlot.SINGLE, slots[2])
    }

    // ===== 线：同卡相邻行之间一根，组尾不画 =====

    @Test
    fun hairlineIsDrawnBetweenRowsButNeverUnderTheLastOne() {
        val slots = SettingsRowComposition.rowSlots(
            List(3) { SettingsRowEntryKind.CARD_ROW }
        )
        assertTrue(SettingsRowComposition.drawsGroupHairline(slots[0]))
        assertTrue(SettingsRowComposition.drawsGroupHairline(slots[1]))
        assertFalse(SettingsRowComposition.drawsGroupHairline(slots[2]))
    }

    @Test
    fun singleRowGroupDrawsNoHairline() {
        assertFalse(
            SettingsRowComposition.drawsGroupHairline(
                SettingsRowComposition.rowSlots(listOf(SettingsRowEntryKind.CARD_ROW))[0]
            )
        )
    }

    @Test
    fun sectionHeaderAndStandaloneRowsDrawNoHairline() {
        assertFalse(SettingsRowComposition.drawsGroupHairline(null))
        val slots = SettingsRowComposition.rowSlots(
            listOf(SettingsRowEntryKind.STANDALONE, SettingsRowEntryKind.SECTION)
        )
        assertFalse(SettingsRowComposition.drawsGroupHairline(slots[0]))
        assertFalse(SettingsRowComposition.drawsGroupHairline(slots[1]))
    }

    // ===== 线的长度：子项组上沿短一截（并入原 SettingsSubItemDivider 的语义）=====

    @Test
    fun hairlineStartsAtTheSubItemSlotLineForASubItemGroupStart() {
        // 子项组上沿：从子项槽线（12dp）起笔；普通行间：从卡片左内边距（14dp）起笔
        assertEquals(
            SettingsDesignMath.SubItemIndentDp,
            SettingsRowComposition.groupHairlineStartIndentDp(isSubItem = true, previousIsSubItem = false),
            0.001f
        )
        assertEquals(
            SettingsDesignMath.RowSidePaddingDp,
            SettingsRowComposition.groupHairlineStartIndentDp(isSubItem = false, previousIsSubItem = false),
            0.001f
        )
        // 组内第二行起回到普通槽线
        assertEquals(
            SettingsDesignMath.RowSidePaddingDp,
            SettingsRowComposition.groupHairlineStartIndentDp(isSubItem = true, previousIsSubItem = true),
            0.001f
        )
    }

    // ===== 常量钉：防止回漂 =====

    @Test
    fun cardConstantsArePinned() {
        assertEquals(16f, SettingsDesignMath.GroupCardRadiusDp, 0.001f)
        assertEquals(14f, SettingsDesignMath.RowSidePaddingDp, 0.001f)
        assertEquals(0f, SettingsSpacingMath.CardRowGapDp, 0.001f)
    }

    @Test
    fun sliderConstantsArePinned() {
        assertEquals(6f, SettingsSliderMath.TrackHeightDp, 0.001f)
        assertEquals(18f, SettingsSliderMath.ThumbSizeDp, 0.001f)
        assertEquals(2f, SettingsSliderMath.ThumbRingDp, 0.001f)
    }

    @Test
    fun sectionGapStaysTwentyFourAndDwarfsTheCardRowGap() {
        assertEquals(24f, SettingsDesignMath.SectionGapDp, 0.001f)
        // 组间 24dp / 组内 0（只有一根 1px 线）：亲密性差别必须一眼可辨
        assertTrue(SettingsDesignMath.SectionGapDp >= SettingsSpacingMath.CardRowGapDp * 4f)
    }

    // ===== 滑轨纯函数：指针位置 -> 取值 =====

    @Test
    fun sliderValueMapsTheFullWidthWithThumbInset() {
        // 滑块中心在 [r, w-r] 之间移动：左端 = 区间起点，右端 = 区间终点
        assertEquals(
            0f,
            settingsSliderValueAt(9f, 100f, 9f, 0f..1f, steps = 0),
            0.001f
        )
        assertEquals(
            1f,
            settingsSliderValueAt(91f, 100f, 9f, 0f..1f, steps = 0),
            0.001f
        )
        assertEquals(
            0.5f,
            settingsSliderValueAt(50f, 100f, 9f, 0f..1f, steps = 0),
            0.001f
        )
    }

    @Test
    fun sliderValueClampsOutOfRangeDrags() {
        assertEquals(0f, settingsSliderValueAt(-50f, 100f, 9f, 0f..1f, steps = 0), 0.001f)
        assertEquals(1f, settingsSliderValueAt(150f, 100f, 9f, 0f..1f, steps = 0), 0.001f)
    }

    @Test
    fun sliderValueSnapsToStepsAndHandlesDegenerateInput() {
        // 0..1 三段（steps=2）：拖到 0.4 处吸附到 1/3
        assertEquals(
            1f / 3f,
            settingsSliderValueAt(41f, 100f, 9f, 0f..1f, steps = 2),
            0.001f
        )
        // 零宽 / 反向区间：不炸，回落起点
        assertEquals(5f, settingsSliderValueAt(50f, 100f, 9f, 5f..5f, steps = 0), 0.001f)
        assertEquals(5f, settingsSliderValueAt(50f, 0f, 9f, 5f..9f, steps = 0), 0.001f)
    }
}
