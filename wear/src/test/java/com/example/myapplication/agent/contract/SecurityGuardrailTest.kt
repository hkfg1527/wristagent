package com.example.myapplication.agent.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * AI 三级安全护栏与硬黑名单单元测试 (TDD 严格红绿循环)
 */
class SecurityGuardrailTest {

    private lateinit var guardrail: SecurityGuardrail

    @Before
    fun setUp() {
        guardrail = SecurityGuardrail()
    }

    @Test
    fun testHardBlacklistDetection_blocksFatalCommands() {
        // 致命全盘抹除或格式化指令：必须 100% 绝对拦截拒死
        val fatalCases = listOf(
            "rm -rf /",
            "rm -rf /system",
            "mkfs.ext4 /dev/block/bootdevice",
            "format data",
            "dd if=/dev/zero of=/dev/block/mmcblk0",
            ":(){ :|:& };:"
        )

        for (cmd in fatalCases) {
            val result = guardrail.assessCommand(cmd, SecurityMode.EXPERT)
            assertTrue("致命命令 [$cmd] 必须被硬黑名单拦截", result.isHardBlocked)
            assertTrue(result.isHighRisk)
            assertTrue("说明必须指出致命高危", result.riskSummary.contains("致命"))
        }
    }

    @Test
    fun testNoviceMode_requiresApprovalForAllTools_withCooldownForRisky() {
        // 小白模式 (默认)：所有操作必须审批，中高风险操作必须带 3 秒倒计时锁
        val riskyResult = guardrail.assessTool(
            toolName = "set_system_power_mode",
            argsJson = "{\"ultraSaving\": true}",
            mode = SecurityMode.NOVICE
        )
        assertFalse(riskyResult.isHardBlocked)
        assertTrue(riskyResult.isHighRisk)
        assertEquals(3, riskyResult.requireCooldownSeconds)
        assertTrue("必须提供大白话解释", riskyResult.riskSummary.isNotBlank())

        // 哪怕是无害的查电量，小白模式也需要确认，但无 3 秒冷却
        val safeResult = guardrail.assessTool(
            toolName = "get_device_status",
            argsJson = "{}",
            mode = SecurityMode.NOVICE
        )
        assertFalse(safeResult.isHardBlocked)
        assertEquals(0, safeResult.requireCooldownSeconds)
    }

    @Test
    fun testStandardMode_allowsSafeTools_blocksRiskyToolsWithExplanation() {
        // 普通人模式：无害工具直接放行 (isHighRisk = false)
        val safeResult = guardrail.assessTool(
            toolName = "web_search",
            argsJson = "{\"query\": \"今日天气\"}",
            mode = SecurityMode.STANDARD
        )
        assertFalse("只读搜索直接放行", safeResult.isHighRisk)

        // 涉及系统控制的工具触发审批
        val riskyResult = guardrail.assessTool(
            toolName = "set_system_power_mode",
            argsJson = "{\"ultraSaving\": true}",
            mode = SecurityMode.STANDARD
        )
        assertTrue("系统省电调节需审批", riskyResult.isHighRisk)
        assertTrue(riskyResult.riskSummary.contains("后台"))
    }

    @Test
    fun testExpertMode_allowsAllExceptHardBlacklist() {
        // 大佬模式：除硬黑名单外全部放行
        val result = guardrail.assessTool(
            toolName = "set_system_power_mode",
            argsJson = "{\"ultraSaving\": true}",
            mode = SecurityMode.EXPERT
        )
        assertFalse("大佬模式无需审批", result.isHighRisk)
        assertEquals(0, result.requireCooldownSeconds)
    }
}
