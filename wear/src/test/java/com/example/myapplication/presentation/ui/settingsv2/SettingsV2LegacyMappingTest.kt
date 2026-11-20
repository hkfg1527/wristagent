package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.appearance.SettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R5：设置入口切到 V2 后的**旧值映射**判据。
 *
 * 手表上已经存在 `preferences.settingsStyle`（10 个旧值）。入口切换时若直接换键，
 * 所有人设置会被重置，所以这里做单向映射：旧的五套对得上就沿用，其余收敛到大卡片档。
 * 这条判据保证"用户原来的选择不会莫名其妙变成另一套"。
 */
class SettingsV2LegacyMappingTest {

    @Test
    fun theFourAlignedLegacyStylesMapToThemselves() {
        assertEquals(SettingsV2Style.M3_LIST, SettingsV2Style.fromLegacy(SettingsStyle.M3_LIST))
        assertEquals(SettingsV2Style.TERMINAL, SettingsV2Style.fromLegacy(SettingsStyle.TERMINAL))
        assertEquals(SettingsV2Style.ARC, SettingsV2Style.fromLegacy(SettingsStyle.ARC))
        assertEquals(SettingsV2Style.SQUARE, SettingsV2Style.fromLegacy(SettingsStyle.SQUARE))
    }

    @Test
    fun theRemovedLegacyStylesCollapseToTheBigCardStyle() {
        // 被砍掉的六套（BALLOON/GOOGLE_CARD/M3_TRANSFORM/NEON/PAPER/GLASS）统一收敛到大卡片
        listOf(
            SettingsStyle.BALLOON,
            SettingsStyle.GOOGLE_CARD,
            SettingsStyle.M3_TRANSFORM,
            SettingsStyle.NEON,
            SettingsStyle.PAPER,
            SettingsStyle.GLASS,
        ).forEach { legacy ->
            assertEquals(
                "旧风格 $legacy 应收敛到 M3_CARD",
                SettingsV2Style.M3_CARD,
                SettingsV2Style.fromLegacy(legacy)
            )
        }
    }

    @Test
    fun everyLegacyStyleMapsToSomethingAndOnlyFiveTargetsExist() {
        val targets = SettingsStyle.values().map { SettingsV2Style.fromLegacy(it) }.toSet()
        assertEquals("映射目标只能落在五套里，实际 $targets", 5, targets.size)
    }
}
