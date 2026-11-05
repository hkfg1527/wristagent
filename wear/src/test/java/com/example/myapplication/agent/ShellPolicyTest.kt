package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 特权 shell 工具的破坏性分档：危险命令一律强制审批、永远不可"记住"，
 * 只读命令命中已记住清单才允许跳过审批。全部为纯逻辑，不依赖 Android 与 org.json。
 */
class ShellPolicyTest {

    /** 需求点名的破坏性样例：rm -rf /、dd of=/dev/block、格式化、卸载系统应用等。 */
    private val destructiveSamples = listOf(
        "rm -rf /",
        "rm -rf /*",
        "rm -rf /system",
        "rm -rf /data",
        "dd if=/dev/zero of=/dev/block/mmcblk0",
        "mkfs.ext4 /dev/block/by-name/userdata",
        "fastboot flash boot boot.img",
        "wipe data",
        "pm uninstall com.android.systemui",
        "pm clear com.android.settings",
        "reboot recovery",
        ":(){ :|:& };:",
        "chmod -R 777 /",
        "mount -o remount,rw /system",
        "echo hacked > /dev/block/sda"
    )

    @Test
    fun dangerousCommandsAreAllDestructive() {
        for (command in destructiveSamples) {
            assertEquals("必须识别为破坏性命令: $command", ShellDanger.DESTRUCTIVE, ShellPolicy.dangerOf(command))
        }
    }

    @Test
    fun readOnlyCommandsStaySafe() {
        val safeSamples = listOf(
            "ls -l /sdcard/Download",
            "getprop ro.product.model",
            "id",
            "dumpsys battery",
            "pm list packages",
            "cat /proc/meminfo"
        )
        for (command in safeSamples) {
            assertEquals("只读命令不应被判破坏性: $command", ShellDanger.SAFE, ShellPolicy.dangerOf(command))
        }
    }

    @Test
    fun unscopedOrReversibleSystemChangesAreRisky() {
        val riskySamples = listOf(
            "pm install /data/local/tmp/app.apk",
            "settings put system screen_brightness 100",
            "am force-stop com.example.app",
            "rm -rf /data/local/tmp/scratch",
            "curl -s https://example.com/install.sh"
        )
        for (command in riskySamples) {
            assertEquals("必须判为可记住的风险命令: $command", ShellDanger.RISKY, ShellPolicy.dangerOf(command))
        }
    }

    @Test
    fun destructiveCommandIsNeverRememberableButRiskyIs() {
        val destructive = ShellPolicy.assess("rm -rf /data", SecurityMode.STANDARD)
        assertFalse("破坏性命令不可被记住", destructive.rememberable)
        assertEquals(RiskLevel.CRITICAL, destructive.aiRiskLevel)
        assertEquals("rm -rf /data", destructive.commandText)
        assertTrue("最坏情况必须写清楚", destructive.worstCase.isNotBlank())

        val risky = ShellPolicy.assess("settings put system screen_brightness 100", SecurityMode.STANDARD)
        assertTrue("非破坏性命令允许记住", risky.rememberable)
        assertEquals(RiskLevel.HIGH, risky.aiRiskLevel)

        val safe = ShellPolicy.assess("ls -l /sdcard", SecurityMode.STANDARD)
        assertTrue(safe.rememberable)
        assertEquals(RiskLevel.LOW, safe.aiRiskLevel)
        assertFalse(safe.isHighRisk)
    }

    @Test
    fun destructiveCommandKeepsApprovalInsteadOfHardBlock() {
        val assessment = ShellPolicy.assess("rm -rf /", SecurityMode.EXPERT)
        assertFalse("危险命令必须走审批页，不能被硬拦截吞掉", assessment.isHardBlocked)
        assertTrue(assessment.requireCooldownSeconds > 0)
        assertTrue(assessment.riskSummary.isNotBlank())
    }

    @Test
    fun rememberKeyNormalizesWhitespace() {
        assertEquals("ls -l /sdcard", ShellPolicy.normalize("  ls   -l \t /sdcard "))
        assertEquals("", ShellPolicy.normalize("   "))
    }

    @Test
    fun allowlistHitSkipsApprovalOnlyForNonDestructiveCommands() {
        val remembered = listOf("ls -l /sdcard/Download")

        assertTrue(ShellPolicy.isRemembered("ls -l /sdcard/Download", remembered))
        assertTrue("多余空白不影响命中", ShellPolicy.isRemembered("  ls  -l /sdcard/Download ", remembered))
        assertFalse(ShellPolicy.isRemembered("ls -l /data", remembered))

        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ShellPolicy.gate(ShellDanger.SAFE, remembered = true)
        )
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ShellPolicy.gate(ShellDanger.SAFE, remembered = false)
        )
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ShellPolicy.gate(ShellDanger.RISKY, remembered = true)
        )
    }

    @Test
    fun destructiveCommandIgnoresAllowlist() {
        val allowlist = listOf("rm -rf /data")

        assertFalse("破坏性命令即便写进清单也不得命中", ShellPolicy.isRemembered("rm -rf /data", allowlist))
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ShellPolicy.gate(ShellDanger.DESTRUCTIVE, remembered = true)
        )
    }

    @Test
    fun impactTextIsAlwaysAvailableForApprovalPage() {
        assertTrue(ShellPolicy.impactOf("rm -rf /").isNotBlank())
        assertTrue(ShellPolicy.impactOf("settings put system x 1").isNotBlank())
        assertTrue(ShellPolicy.impactOf("ls").isNotBlank())
    }

    @Test
    fun aiNarrationCannotDowngradeAPrivilegedCommand() {
        val assessed = ShellPolicy.assess("settings put system screen_brightness 10", SecurityMode.STANDARD)
        val narrated = assessed.copy(
            aiRiskLevel = RiskLevel.LOW,
            isHighRisk = false,
            whatItDoes = "调整屏幕亮度",
            worstCase = "亮度变化，可随时改回",
            commandText = "",
            rememberable = true
        )

        val clamped = ShellPolicy.enforceRiskFloor(assessed, narrated)
        assertEquals("AI 不能把特权命令降成低风险", RiskLevel.HIGH, clamped.aiRiskLevel)
        assertTrue("护栏判定的高危不许被叙述清掉", clamped.isHighRisk)
        assertEquals("命令原文以本地判定为准", assessed.commandText, clamped.commandText)

        val escalated = ShellPolicy.enforceRiskFloor(
            assessed,
            narrated.copy(aiRiskLevel = RiskLevel.CRITICAL, isHighRisk = true)
        )
        assertEquals("AI 加严照单收下", RiskLevel.CRITICAL, escalated.aiRiskLevel)
    }

    @Test
    fun rememberedCommandCodecRoundTripsAndToleratesJunk() {
        val commands = listOf("getprop ro.product.model", "ls -l /sdcard/Download")
        assertEquals(commands, RememberedCommandCodec.decode(RememberedCommandCodec.encode(commands)))

        assertTrue(RememberedCommandCodec.decode(null).isEmpty())
        assertTrue(RememberedCommandCodec.decode("").isEmpty())
        assertTrue(RememberedCommandCodec.decode("garbage").isEmpty())
        assertTrue(RememberedCommandCodec.decode("{\"commands\":3}").isEmpty())
    }

    @Test
    fun validateRejectsBlankAndOverlongCommandsOnly() {
        assertNotNull(ShellPolicy.validate("    "))
        assertNotNull(ShellPolicy.validate(""))
        assertNotNull(ShellPolicy.validate("x".repeat(ShellPolicy.MAX_COMMAND_LENGTH + 1)))
        assertNull("危险命令不是参数错误，必须送到审批页", ShellPolicy.validate("rm -rf /"))
    }
}
