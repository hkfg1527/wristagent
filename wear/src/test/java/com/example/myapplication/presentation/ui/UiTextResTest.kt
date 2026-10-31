package com.example.myapplication.presentation.ui

import com.example.myapplication.R
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.settings.SettingsStatusTextRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 审批等级标题：等级 -> 资源 id 的映射必须区分三档，并且同档共享同一个资源。 */
class ApprovalTextResTest {
    @Test
    fun lowRiskHeadlineMapsToLowRiskResource() {
        assertEquals(R.string.ui_approval_headline_low, ApprovalTextRes.headlineResOf(RiskLevel.LOW))
    }

    @Test
    fun mediumRiskHeadlineMapsToConfirmationResource() {
        assertEquals(R.string.ui_approval_headline_confirm, ApprovalTextRes.headlineResOf(RiskLevel.MEDIUM))
    }

    @Test
    fun highAndCriticalShareTheHighRiskHeadline() {
        assertEquals(R.string.ui_approval_headline_high, ApprovalTextRes.headlineResOf(RiskLevel.HIGH))
        assertEquals(R.string.ui_approval_headline_high, ApprovalTextRes.headlineResOf(RiskLevel.CRITICAL))
    }

    @Test
    fun everyRiskGroupUsesItsOwnHeadlineResource() {
        val low = ApprovalTextRes.headlineResOf(RiskLevel.LOW)
        val medium = ApprovalTextRes.headlineResOf(RiskLevel.MEDIUM)
        val high = ApprovalTextRes.headlineResOf(RiskLevel.HIGH)
        assertNotEquals(low, medium)
        assertNotEquals(low, high)
        assertNotEquals(medium, high)
    }

    @Test
    fun lockCountdownWinsOverProgressLabel() {
        assertEquals(R.string.ui_approval_locked, ApprovalTextRes.sliderLabelResOf(0f, isLocked = true))
        assertEquals(R.string.ui_approval_locked, ApprovalTextRes.sliderLabelResOf(0.9f, isLocked = true))
    }

    @Test
    fun sliderLabelSwitchesAtTheHalfwayPoint() {
        assertEquals(R.string.ui_approval_slider_hint, ApprovalTextRes.sliderLabelResOf(0.49f, isLocked = false))
        assertEquals(R.string.ui_approval_release_to_allow, ApprovalTextRes.sliderLabelResOf(0.5f, isLocked = false))
    }

    @Test
    fun actionWordSwitchesBetweenRejectAndAllow() {
        assertEquals(R.string.ui_approval_reject_tap, ApprovalTextRes.actionLabelResOf(0.2f))
        assertEquals(R.string.ui_approval_allow, ApprovalTextRes.actionLabelResOf(0.8f))
    }
}

/** 设置页徽章：已配置与未配置必须落到不同资源，并带各自的色调。 */
class SettingsStatusTextResTest {
    @Test
    fun configuredModelBadgeUsesConfiguredTextAndAccentTone() {
        assertEquals(R.string.settings_badge_configured to Tone.ACCENT, SettingsStatusTextRes.modelServiceBadge(true))
    }

    @Test
    fun unconfiguredModelBadgeUsesMissingTextAndDangerTone() {
        assertEquals(
            R.string.settings_badge_unconfigured to Tone.DANGER,
            SettingsStatusTextRes.modelServiceBadge(false)
        )
    }

    @Test
    fun theTwoBadgesNeverShareOneResource() {
        val configured = SettingsStatusTextRes.modelServiceBadge(true).first
        val unconfigured = SettingsStatusTextRes.modelServiceBadge(false).first
        assertNotEquals(configured, unconfigured)
    }
}
