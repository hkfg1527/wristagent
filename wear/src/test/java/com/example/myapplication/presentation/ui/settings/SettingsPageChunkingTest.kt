package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分组规则：**连续的卡片行拼成一张官方 Card**，分区标题与卡外动作行把卡片断开。
 *
 * 这条规则在 0.0.0.46 变过一次，方向是"更像 M3"：
 * 旧规则是"一页只用一张主气球"（`everyPageUsesExactlyOneMainBalloon`），
 * 整页连标题一起裹进一张手搓超椭圆 —— 真机上一屏就是一坨，用户直接判"假 M3E、很丑"。
 * 现在标题在外当列表小标题、行在内当卡片内容，断点由行类型本身决定。
 */
class SettingsPageChunkingTest {
    @Test
    fun consecutiveCardRowsBecomeOneCard() {
        val chunks = SettingsPageChunking.chunks(
            listOf(
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.CARD_ROW
            )
        )
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].grouped)
        assertEquals(listOf(0, 1, 2), chunks[0].indices)
    }

    @Test
    fun sectionHeadersCutTheCardInPieces() {
        val chunks = SettingsPageChunking.chunks(
            listOf(
                SettingsRowEntryKind.SECTION,
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.STANDALONE,
                SettingsRowEntryKind.SECTION,
                SettingsRowEntryKind.CARD_ROW
            )
        )
        assertEquals(5, chunks.size)
        // 标题与卡外行动各自成段，且都不套卡片
        assertFalse(chunks[0].grouped)
        assertEquals(listOf(0), chunks[0].indices)
        assertTrue(chunks[1].grouped)
        assertEquals(listOf(1), chunks[1].indices)
        assertFalse(chunks[2].grouped)
        assertFalse(chunks[3].grouped)
        assertTrue(chunks[4].grouped)
    }

    @Test
    fun twoSectionsInARowProduceTwoSeparateChunks() {
        val chunks = SettingsPageChunking.chunks(
            listOf(SettingsRowEntryKind.SECTION, SettingsRowEntryKind.SECTION)
        )
        assertEquals(2, chunks.size)
        assertFalse(chunks[0].grouped)
        assertFalse(chunks[1].grouped)
    }

    @Test
    fun emptyPageHasNoChunks() {
        assertTrue(SettingsPageChunking.chunks(emptyList()).isEmpty())
    }
}
