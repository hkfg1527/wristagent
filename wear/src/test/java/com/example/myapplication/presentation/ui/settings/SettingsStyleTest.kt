package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.presentation.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 外观预设的存取：写坏了要能落回默认档，不能让设置页变空壳。 */
class SettingsStyleTest {

    @Test
    fun defaultIsTheBalloonSoOldUsersKeepTheirPixels() {
        assertEquals(SettingsStyle.BALLOON, SettingsStyle.Default)
        assertEquals(SettingsStyle.BALLOON, SettingsStyle.fromStorage(null))
        assertEquals(SettingsStyle.BALLOON, SettingsStyle.fromStorage(""))
        assertEquals(SettingsStyle.BALLOON, SettingsStyle.fromStorage("GOOGLE_CARDS"))
    }

    @Test
    fun everyTierRoundTripsItsOwnName() {
        SettingsStyle.values().forEach { style ->
            assertEquals(style, SettingsStyle.fromStorage(style.name))
        }
        assertEquals(3, SettingsStyle.values().size)
    }

    @Test
    fun storePersistsTheChosenStyle() {
        val store = FakeSharedPreferences()
        val appearance = AppearanceStore(store)
        assertEquals(SettingsStyle.BALLOON, appearance.style())

        appearance.saveStyle(SettingsStyle.GOOGLE_CARD)
        assertEquals(SettingsStyle.GOOGLE_CARD, AppearanceStore(store).style())

        appearance.saveStyle(SettingsStyle.SQUARE)
        assertEquals(SettingsStyle.SQUARE, AppearanceStore(store).style())
    }

    @Test
    fun everyTierHasItsOwnLabelAndSummary() {
        val labels = SettingsStyle.values().map { it.labelRes }
        val summaries = SettingsStyle.values().map { it.summaryRes }
        assertEquals(labels.size, labels.toSet().size)
        assertEquals(summaries.size, summaries.toSet().size)
        labels.forEach { assertNotEquals(0, it) }
        summaries.forEach { assertNotEquals(0, it) }
    }
}
