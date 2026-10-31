package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分级审批闸门单元测试：AI 风险等级决定要不要弹审批，安全模式决定无风险操作能否自动放行。
 */
class ApprovalGateTest {

    private fun riskOf(
        level: RiskLevel,
        isHighRisk: Boolean = false,
        isHardBlocked: Boolean = false
    ) = RiskAssessment(
        isHardBlocked = isHardBlocked,
        isHighRisk = isHighRisk,
        riskSummary = "测试说明",
        aiRiskLevel = level
    )

    @Test
    fun lowRiskWithoutHighRiskFlag_autoAllowsInsteadOfAsking() {
        // 无风险：不弹审批直接放行，标准模式与自动执行模式都不打扰
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ApprovalGate.decide(riskOf(RiskLevel.LOW), SecurityMode.STANDARD)
        )
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ApprovalGate.decide(riskOf(RiskLevel.LOW), SecurityMode.EXPERT)
        )
    }

    @Test
    fun lowRiskButHighRiskTool_stillAsksForApproval() {
        // 等级低但护栏判了高危：必须弹审批，不能被"低风险"标签放过去
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ApprovalGate.decide(riskOf(RiskLevel.LOW, isHighRisk = true), SecurityMode.STANDARD)
        )
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ApprovalGate.decide(riskOf(RiskLevel.LOW, isHighRisk = true), SecurityMode.EXPERT)
        )
    }

    @Test
    fun strictModeNeverAutoAllows() {
        // 安全底线：严格确认模式（NOVICE）下再低的风险也必须由人滑
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ApprovalGate.decide(riskOf(RiskLevel.LOW), SecurityMode.NOVICE)
        )
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ApprovalGate.decide(
                riskOf(RiskLevel.LOW, isHighRisk = true),
                SecurityMode.NOVICE
            )
        )
    }

    @Test
    fun hardBlockedAlwaysWins() {
        // 硬黑名单优先级最高：等级 LOW、模式 EXPERT、非高危也一律拦截
        assertEquals(
            ApprovalGate.Decision.HARD_BLOCK,
            ApprovalGate.decide(riskOf(RiskLevel.LOW, isHardBlocked = true), SecurityMode.EXPERT)
        )
        assertEquals(
            ApprovalGate.Decision.HARD_BLOCK,
            ApprovalGate.decide(
                riskOf(RiskLevel.CRITICAL, isHighRisk = true, isHardBlocked = true),
                SecurityMode.NOVICE
            )
        )
    }

    @Test
    fun mediumHighCriticalAlwaysAskInStandardMode() {
        // 中度 / 重度 / 致命：标准模式下逐级都要审批
        for (level in listOf(RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL)) {
            assertEquals(
                "$level 必须弹审批",
                ApprovalGate.Decision.REQUIRE_APPROVAL,
                ApprovalGate.decide(riskOf(level), SecurityMode.STANDARD)
            )
        }
    }

    @Test
    fun strictModeAsksForEveryLevel() {
        // 严格模式下任何等级（含非高危）都要审批
        for (level in listOf(RiskLevel.LOW, RiskLevel.MEDIUM, RiskLevel.HIGH, RiskLevel.CRITICAL)) {
            assertEquals(
                "$level 在严格模式必须审批",
                ApprovalGate.Decision.REQUIRE_APPROVAL,
                ApprovalGate.decide(riskOf(level), SecurityMode.NOVICE)
            )
        }
    }
}
