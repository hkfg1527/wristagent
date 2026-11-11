package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 行序列 -> 组卡位置的可执行契约：卡外行 / 组标题断卡，连续卡片行成组。
 *
 * 判据直接用渲染路径里的 [SettingsRowComposition.rowSlots]，实现改坏这里就会红，
 * 而不是测试里另写一份 if 自说自话。
 */
class SettingsRowCompositionTest {

    @Test
    fun consecutiveCardRowsFormOneGroup() {
        assertEquals(
            listOf(
                SettingsRowSlot.FIRST,
                SettingsRowSlot.MIDDLE,
                SettingsRowSlot.LAST
            ),
            SettingsRowComposition.rowSlots(List(3) { SettingsRowEntryKind.CARD_ROW })
        )
    }

    @Test
    fun sectionHeadersAndStandaloneRowsBreakTheCard() {
        // 组标题（section）与卡外行（动作按钮）前后各起新组：完成返回不粘到上一张卡
        assertEquals(
            listOf(
                SettingsRowSlot.SINGLE,
                null,
                SettingsRowSlot.SINGLE,
                null,
                SettingsRowSlot.SINGLE
            ),
            SettingsRowComposition.rowSlots(
                listOf(
                    SettingsRowEntryKind.CARD_ROW,
                    SettingsRowEntryKind.SECTION,
                    SettingsRowEntryKind.CARD_ROW,
                    SettingsRowEntryKind.STANDALONE,
                    SettingsRowEntryKind.CARD_ROW
                )
            )
        )
    }

    @Test
    fun emptyPageYieldsNoSlots() {
        assertEquals(emptyList<SettingsRowSlot?>(), SettingsRowComposition.rowSlots(emptyList()))
    }

    @Test
    fun standalonePageYieldsNoSlots() {
        // 纯动作页（整页只有按钮）没有卡片行，全部 slot 为 null
        assertEquals(
            listOf(null, null),
            SettingsRowComposition.rowSlots(List(2) { SettingsRowEntryKind.STANDALONE })
        )
    }

    @Test
    fun hairlineOnlyBetweenRowsNeverUnderTheLastOne() {
        val slots = SettingsRowComposition.rowSlots(
            listOf(
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.STANDALONE
            )
        )
        assertTrue(SettingsRowComposition.drawsGroupHairline(slots[0]))
        assertTrue(SettingsRowComposition.drawsGroupHairline(slots[1]))
        assertFalse("组尾下面不画线", SettingsRowComposition.drawsGroupHairline(slots[2]))
        assertFalse("卡外行永远不画线", SettingsRowComposition.drawsGroupHairline(slots[3]))
        assertFalse(SettingsRowComposition.drawsGroupHairline(null))
    }

    @Test
    fun theRenderingLoopDerivesSlotsFromTheRegistryKinds() {
        // 三角验证：模拟一个真实页面的登记顺序（标题 / 行 / 标题 / 行 / 行 / 按钮），
        // 渲染循环按 kind 列表算出的 slot 必须给到每一行
        val kinds = listOf(
            SettingsRowEntryKind.SECTION,
            SettingsRowEntryKind.CARD_ROW,
            SettingsRowEntryKind.SECTION,
            SettingsRowEntryKind.CARD_ROW,
            SettingsRowEntryKind.CARD_ROW,
            SettingsRowEntryKind.STANDALONE
        )
        assertEquals(
            listOf(null, SettingsRowSlot.SINGLE, null, SettingsRowSlot.FIRST, SettingsRowSlot.LAST, null),
            SettingsRowComposition.rowSlots(kinds)
        )
    }
}
