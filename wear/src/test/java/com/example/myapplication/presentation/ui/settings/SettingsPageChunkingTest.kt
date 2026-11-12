package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPageChunkingTest {
    @Test
    fun consecutiveCardRowsBecomeOneBalloon() {
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
    fun everyPageUsesExactlyOneMainBalloon() {
        val chunks = SettingsPageChunking.chunks(
            listOf(
                SettingsRowEntryKind.SECTION,
                SettingsRowEntryKind.CARD_ROW,
                SettingsRowEntryKind.STANDALONE,
                SettingsRowEntryKind.SECTION,
                SettingsRowEntryKind.CARD_ROW
            )
        )
        assertEquals(1, chunks.size)
        assertTrue(chunks[0].grouped)
        assertEquals(listOf(0, 1, 2, 3, 4), chunks[0].indices)
    }
}
