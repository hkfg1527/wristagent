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
    fun testNonShellToolsAreNotHardBlockedByTheirTextArguments() {
        // 回归：硬黑名单曾经对**整个 argsJson** 做子串匹配，
        // 于是「怎么格式化磁盘」这种正常搜索 / 记忆条目会被当成致命指令硬拦，
        // 用户看到的是"致命指令已被安全护栏拦截"，而检索/记忆什么也没做。
        val search = guardrail.assessTool(
            toolName = "web_search",
            argsJson = "{\"query\": \"how to format a disk on windows\"}",
            mode = SecurityMode.STANDARD
        )
        assertFalse("搜索词里出现 format 不该被硬拦", search.isHardBlocked)
        assertFalse("只读搜索也不该被判高风险", search.isHighRisk)

        val remember = guardrail.assessTool(
            toolName = "remember_fact",
            argsJson = "{\"fact\": \"wipe cache 的步骤：先关机再进 recovery\"}",
            mode = SecurityMode.STANDARD
        )
        assertFalse("记忆条目里出现 wipe 不该被硬拦", remember.isHardBlocked)
    }

    @Test
    fun testShellToolsAreStillHardBlockedByTheirCommandField() {
        // 收紧之后必须仍然拦得住真正的命令：run_shell 的 command 字段
        val direct = guardrail.assessTool(
            toolName = "run_shell",
            argsJson = "{\"command\": \"rm -rf /\"}",
            mode = SecurityMode.STANDARD
        )
        assertTrue("run_shell 的致命命令仍要硬拦", direct.isHardBlocked)

        // 自写工具把参数绑进命令模板时，也带 command 字段
        val selfDefined = guardrail.assessTool(
            toolName = "my_factory_reset",
            argsJson = "{\"command\": \"mkfs.ext4 /dev/block/sda\"}",
            mode = SecurityMode.EXPERT
        )
        assertTrue("自写工具的致命命令也要拦", selfDefined.isHardBlocked)

        // 转义引号不影响判定
        val escaped = guardrail.assessTool(
            toolName = "run_shell",
            argsJson = "{\"command\": \"dd if=/dev/zero of=/dev/block/mmcblk0\"}",
            mode = SecurityMode.STANDARD
        )
        assertTrue("dd 写块设备仍要硬拦", escaped.isHardBlocked)
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
