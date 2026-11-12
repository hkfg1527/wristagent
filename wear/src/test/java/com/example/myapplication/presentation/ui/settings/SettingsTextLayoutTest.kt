package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTextLayoutTest {
    @Test
    fun subtitleStaysInlineForAbsentBlankAndTwelveCharacters() {
        assertFalse(SettingsTextLayout.shouldPlaceSubtitleBelowRow(null))
        assertFalse(SettingsTextLayout.shouldPlaceSubtitleBelowRow("   "))
        assertFalse(SettingsTextLayout.shouldPlaceSubtitleBelowRow("123456789012"))
        assertFalse(SettingsTextLayout.shouldPlaceSubtitleBelowRow("一二三四五六七八九十甲乙"))
    }

    @Test
    fun thirteenAsciiCharactersMoveTheWholeSubtitleBelowTheControls() {
        assertTrue(SettingsTextLayout.shouldPlaceSubtitleBelowRow("1234567890123"))
    }

    @Test
    fun thirteenCjkCharactersUseTheSameExplicitThreshold() {
        assertTrue(SettingsTextLayout.shouldPlaceSubtitleBelowRow("所有敏感与系统操作均需审批"))
    }

    @Test
    fun surroundingWhitespaceDoesNotChangeTheSubtitleDecision() {
        assertFalse(SettingsTextLayout.shouldPlaceSubtitleBelowRow("  123456789012  "))
        assertTrue(SettingsTextLayout.shouldPlaceSubtitleBelowRow("\n1234567890123\t"))
    }

    @Test
    fun anUnbrokenEndpointIsTextTooAndMustNotBeSqueezedBesideARadio() {
        assertTrue(SettingsTextLayout.shouldPlaceSubtitleBelowRow("https://api.example.com/v1"))
    }

    @Test
    fun subtitleWidthUsesTheWholeCardExceptTheSubItemIndent() {
        assertEquals(148f, SettingsTextLayout.subtitleWidthDp(subItem = false), 0.001f)
        assertEquals(136f, SettingsTextLayout.subtitleWidthDp(subItem = true), 0.001f)
    }

    @Test
    fun realRootRowsDoNotWrapTheirTitlesToMakeRoomForValues() {
        assertFalse(SettingsTextLayout.shouldPlaceValueBelowRow("语音与唤醒", "系统识别"))
        assertFalse(SettingsTextLayout.shouldPlaceValueBelowRow("模型与服务", "已配置"))
        assertFalse(SettingsTextLayout.shouldPlaceValueBelowRow("模型参数", "温度 0.7"))
        assertTrue("真机已把「工具与MCP」挤成两行", SettingsTextLayout.shouldPlaceValueBelowRow("工具与MCP", "全部启用"))
        assertTrue("真机已把「语音与唤醒」挤成两行", SettingsTextLayout.shouldPlaceValueBelowRow("语音与唤醒", "密钥未填写"))
    }

    @Test
    fun anyOverflowingTitleAndValuePairMovesTheValueBelow() {
        assertTrue(SettingsTextLayout.shouldPlaceValueBelowRow("123456789", "1234567890123"))
        assertTrue(SettingsTextLayout.shouldPlaceValueBelowRow("12345678", "1234567890123"))
        assertTrue(SettingsTextLayout.shouldPlaceValueBelowRow("服务地址", "https://api.example.com/v1"))
    }

    @Test
    fun outerWhitespaceDoesNotChangeTheWidthDecision() {
        assertFalse(SettingsTextLayout.shouldPlaceValueBelowRow(" 模型与服务 ", " 已配置 "))
        assertTrue(SettingsTextLayout.shouldPlaceValueBelowRow(" 语音与唤醒 ", " 密钥未填写 "))
    }

    @Test
    fun maskedSecretsAndShortStatusWordsStayReadableInTheirOwnSlot() {
        assertTrue("密钥值与长标题合在一起超出 148dp，必须下移而不是压标题",
            SettingsTextLayout.shouldPlaceValueBelowRow("百度 Secret Key", "••••efgh"))
        assertFalse(SettingsTextLayout.shouldPlaceValueBelowRow("安全策略", "标准"))
    }

    @Test
    fun titleValueGapComesFromTheSameSpacingTokenAsTheRow() {
        assertEquals(9f, SettingsTextLayout.titleValueGapDp(), 0.001f)
        assertEquals(148f, SettingsTextLayout.rowTextWidthDp(), 0.001f)
    }
}

class SettingsSectionHeaderDedupeTest {
    @Test
    fun pageTitleReplacesAnIdenticalFirstSectionHeader() {
        assertFalse(SettingsTextLayout.shouldRenderSectionHeader("安全策略", "安全策略", isFirstSection = true))
        assertTrue(SettingsTextLayout.shouldRenderSectionHeader("安全策略", "安全策略", isFirstSection = false))
    }

    @Test
    fun aDifferentFirstSectionStillRenders() {
        assertTrue(SettingsTextLayout.shouldRenderSectionHeader("已保存配置", "供应商", isFirstSection = true))
        assertTrue(SettingsTextLayout.shouldRenderSectionHeader(" 已保存配置 ", "供应商", isFirstSection = true))
    }

    @Test
    fun whitespaceDoesNotDefeatTheDedupe() {
        assertFalse(SettingsTextLayout.shouldRenderSectionHeader(" 安全策略 ", "安全策略", isFirstSection = true))
    }
}

class SettingsSectionHeaderMarkTest {
    @Test
    fun dashIsEightByTwoAndLeavesAVisibleGapBeforeTheTitleSlot() {
        val mark = SettingsSectionHeaderMark.mark(trackHeightDp = 18f)
        assertEquals(0f, mark.xDp, 0.001f)
        assertEquals(8f, mark.yDp, 0.001f)
        assertEquals(8f, mark.widthDp, 0.001f)
        assertEquals(2f, mark.heightDp, 0.001f)
        assertEquals(14f, mark.titleStartDp, 0.001f)
        assertEquals(6f, mark.titleStartDp - mark.widthDp, 0.001f)
    }

    @Test
    fun verticalCenteringSupportsFractionalDpWithoutMovingText() {
        val mark = SettingsSectionHeaderMark.mark(trackHeightDp = 17f, leftSlotDp = 16f)
        assertEquals(7.5f, mark.yDp, 0.001f)
        assertEquals(16f, mark.titleStartDp, 0.001f)
        assertEquals(8f, mark.widthDp, 0.001f)
    }

    @Test
    fun shortTracksClampTheDashToTheirTopWithoutNegativeGeometry() {
        assertEquals(0f, SettingsSectionHeaderMark.mark(trackHeightDp = 1f).yDp, 0.001f)
        assertEquals(0f, SettingsSectionHeaderMark.mark(trackHeightDp = -4f).yDp, 0.001f)
    }
}
