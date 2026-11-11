package com.example.myapplication.presentation

import com.example.myapplication.agent.ApprovalGate
import com.example.myapplication.agent.RememberedCommandCodec
import com.example.myapplication.agent.ShellPolicy
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「记住并放行」闭环：审批页给出的键真的能让同一条命令下次不再弹审批，破坏性命令永远拿不到键。
 *
 * 这里全部走生产原语：键来自 [ApprovalRememberPolicy]，落盘走 [RememberedCommandCodec]，
 * 命中判定走 [ShellPolicy.isRemembered] + [ShellPolicy.gate] —— 与 AgentDispatcher 的闸门是同一条链路，
 * 不重写一份判定，避免测到自己造的假实现。
 */
class ApprovalRememberTest {

    private fun riskOf(command: String, rememberable: Boolean = true): RiskAssessment = RiskAssessment(
        isHardBlocked = false,
        isHighRisk = true,
        riskSummary = "特权命令：将以最高可用权限执行",
        requireCooldownSeconds = 0,
        whatItDoes = "执行 " + command,
        worstCase = "改动系统状态且无法自动回滚",
        aiRiskLevel = RiskLevel.HIGH,
        commandText = command,
        rememberable = rememberable
    )

    /** 与 ChatViewModel.approvePendingAndRemember 的落盘顺序一致：键 -> 编码 -> 再解码读回。 */
    private fun allowlistAfterRemembering(existing: List<String>, key: String?): List<String> =
        if (key == null) existing else RememberedCommandCodec.decode(
            RememberedCommandCodec.encode(existing + key)
        )

    /** AgentDispatcher 的闸门判据：记忆命中且非破坏性才跳过审批。 */
    private fun stillNeedsApproval(command: String, allowlist: List<String>): Boolean =
        ShellPolicy.gate(ShellPolicy.dangerOf(command), ShellPolicy.isRemembered(command, allowlist)) ==
            ApprovalGate.Decision.REQUIRE_APPROVAL

    @Test
    fun rememberingACommandSkipsTheNextApproval() {
        val command = "pm install -r /data/local/tmp/app.apk"
        val key = ApprovalRememberPolicy.keyOf(riskOf(command))
        assertEquals("键就是归一化后的命令原文", ShellPolicy.normalize(command), key)

        assertTrue("还没记住时必须弹审批", stillNeedsApproval(command, emptyList()))

        val allowlist = allowlistAfterRemembering(emptyList(), key)
        assertFalse("记住之后同一条命令不再弹审批", stillNeedsApproval(command, allowlist))
        assertFalse(
            "多余空白不影响命中",
            stillNeedsApproval("  pm  install -r /data/local/tmp/app.apk ", allowlist)
        )
        assertTrue("别的命令仍然要弹审批", stillNeedsApproval("pm uninstall com.example.other", allowlist))
    }

    @Test
    fun destructiveCommandsNeverGetARememberKey() {
        val destructive = "rm -rf /data"
        assertEquals(
            "前提：这条命令的本地判定确实是破坏性",
            com.example.myapplication.agent.ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf(destructive)
        )
        assertNull(
            "破坏性命令即便被哪一层标成可记住，也不许给出键",
            ApprovalRememberPolicy.keyOf(riskOf(destructive))
        )
    }

    @Test
    fun destructiveCommandsStillAskEvenIfForcedIntoTheAllowlist() {
        val destructive = "rm -rf /data"
        val forced = RememberedCommandCodec.decode(
            RememberedCommandCodec.encode(listOf(ShellPolicy.normalize(destructive)))
        )
        assertTrue("清单被强塞也照样弹审批", stillNeedsApproval(destructive, forced))
    }

    @Test
    fun blankCommandHasNoRememberKey() {
        assertNull(ApprovalRememberPolicy.keyOf(riskOf("   ")))
        assertNull(ApprovalRememberPolicy.keyOf(null))
    }

    @Test
    fun riskThatForbidsRememberingHasNoKey() {
        assertNull(
            "甲方的 rememberable=false 必须优先于命令原文",
            ApprovalRememberPolicy.keyOf(riskOf("am start -n com.example/.Main", rememberable = false))
        )
    }
}
