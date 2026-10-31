package com.example.myapplication.presentation

import androidx.compose.ui.graphics.Color
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ui.ApprovalTrackTone
import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 审批页等级配色单元测试：等级到强调色/标题的映射，以及滑轨"等级色 → 放行绿"的插值。
 */
class ApprovalToneTest {

    @Test
    fun riskTokensMatchDesign() {
        // 令牌本身即设计稿色值，其余 UI 只允许引用令牌
        assertEquals(Color(0xFFF5B942), Wear.Palette.Warning)
        assertEquals(Color(0xFFFB6B4B), Wear.Palette.Critical)
    }

    @Test
    fun accentFollowsRiskLevel() {
        assertEquals(Wear.Palette.Success, ApprovalTone.accentOf(RiskLevel.LOW))
        assertEquals(Wear.Palette.Warning, ApprovalTone.accentOf(RiskLevel.MEDIUM))
        assertEquals(Wear.Palette.Critical, ApprovalTone.accentOf(RiskLevel.HIGH))
        assertEquals(Wear.Palette.Danger, ApprovalTone.accentOf(RiskLevel.CRITICAL))
    }

    @Test
    fun headlineFollowsRiskLevel() {
        assertEquals("低风险", ApprovalTone.headlineOf(RiskLevel.LOW))
        assertEquals("需要确认", ApprovalTone.headlineOf(RiskLevel.MEDIUM))
        assertEquals("高风险操作", ApprovalTone.headlineOf(RiskLevel.HIGH))
        assertEquals("高风险操作", ApprovalTone.headlineOf(RiskLevel.CRITICAL))
    }

    @Test
    fun trackStartsAtLevelToneAndAlwaysEndsGreen() {
        for (tone in listOf(Wear.Palette.Warning, Wear.Palette.Critical, Wear.Palette.Danger)) {
            assertEquals(tone, ApprovalTrackTone.colorOf(tone, 0f))
            // 滑到底恒为放行绿，与等级无关
            assertEquals(Wear.Palette.Success, ApprovalTrackTone.colorOf(tone, 1f))
        }
    }

    @Test
    fun trackGetsGreenerAsSliderMoves() {
        val quarter = ApprovalTrackTone.colorOf(Wear.Palette.Danger, 0.25f)
        val threeQuarter = ApprovalTrackTone.colorOf(Wear.Palette.Danger, 0.75f)
        assertNotEquals(Wear.Palette.Danger, quarter)
        assertNotEquals(Wear.Palette.Success, threeQuarter)
        assertTrue("越滑越绿", threeQuarter.green > quarter.green)
        assertTrue("越滑越不红", threeQuarter.red < quarter.red)
    }

    @Test
    fun trackClampsOutOfRangeProgress() {
        assertEquals(Wear.Palette.Warning, ApprovalTrackTone.colorOf(Wear.Palette.Warning, -0.4f))
        assertEquals(Wear.Palette.Success, ApprovalTrackTone.colorOf(Wear.Palette.Warning, 1.7f))
    }
}
