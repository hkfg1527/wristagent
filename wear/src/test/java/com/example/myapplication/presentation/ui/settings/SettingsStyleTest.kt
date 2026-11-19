package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
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
        assertEquals(
            "十套风格：气泡/方角/大卡片/终端 + 官方列表 + 官方转场 + 霓虹/手帐/磨砂/弧列" +
                "（每套必须独立，不许靠同一套控件糊过去）",
            10,
            SettingsStyle.values().size
        )
    }

    @Test
    fun thePreferenceRepositoryIsTheSourceOfTruth() {
        val store = FakeSharedPreferences()
        val preferences = AgentPreferences(store)
        assertEquals(SettingsStyle.BALLOON, preferences.settingsStyle)

        preferences.settingsStyle = SettingsStyle.GOOGLE_CARD
        assertEquals(SettingsStyle.GOOGLE_CARD, AgentPreferences(store).settingsStyle)

        preferences.settingsStyle = SettingsStyle.SQUARE
        assertEquals(SettingsStyle.SQUARE, AgentPreferences(store).settingsStyle)
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
