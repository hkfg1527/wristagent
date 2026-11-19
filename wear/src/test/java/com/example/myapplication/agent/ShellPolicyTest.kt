package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    /**
     * 命令替换与路径变体不能把破坏性命令洗成「有风险」。
     *
     * 回归背景（0.0.0.113 审查，高）：`rmInvocation` 的正则会把收尾的 `)` / 反引号一起吞进目标，
     * 于是 `echo $(rm -rf /data)` 的目标是 `/data)`、`` `rm -rf /` `` 的目标是 ``/` ``，
     * 都比不上 `/data` / `/` —— 判成 RISKY，而 RISKY 可以被「记住此命令」永久免审批，
     * 并且能过 WORKSPACE 沙箱（只有 DESTRUCTIVE 被挡）。`/storage/..` 同理，它执行时就是 `/`。
     */
    @Test
    fun commandSubstitutionAndDotDotCannotLaunderADestructiveCommand() {
        val launderable = listOf(
            "echo \$(rm -rf /data)",
            "echo `rm -rf /`",
            "rm -rf /storage/..",
            "rm -rf /data)",
            "rm -rf /data`"
        )
        for (command in launderable) {
            assertEquals(
                "命令替换/路径变体必须仍判破坏性: $command",
                ShellDanger.DESTRUCTIVE,
                ShellPolicy.dangerOf(command)
            )
        }
    }

    /**
     * 用 `$IFS` 之类的变量拼分隔符绕过关键词匹配。
     *
     * `rm$IFS-rf$IFS/data` 在静态正则里连 `rm ` 都不匹配 → 以前落到 RISKY（可记住免审批、过 WORKSPACE 沙箱），
     * 而 `sh -c` 执行时会真的展开成 `rm -rf /data`。这类写法没有正当用途，命中即按破坏性处理。
     */
    @Test
    fun ifsBasedSeparatorEvasionIsTreatedAsDestructive() {
        val evasions = listOf(
            "rm\$IFS-rf\$IFS/data",
            "rm\${IFS}-rf\${IFS}/data",
            "rm\$@-rf\$@/data",
            "su -c 'rm\$IFS-rf\$IFS/data'"
        )
        for (command in evasions) {
            assertEquals(
                "变量拼分隔符必须判破坏性: $command",
                ShellDanger.DESTRUCTIVE,
                ShellPolicy.dangerOf(command)
            )
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
    fun aNewlineCannotHideASecondCommandBehindAReadOnlyHead() {
        // normalize 会把 \n 折成空格，于是 `ls\nreboot` 只剩首词 `ls` —— 只看首词就会判 SAFE，
        // 而 sh -c 实际上会把两条都跑掉。这里钉住"第二条也参与判档"。
        assertNotEquals(
            "换行后的第二条命令不能被首词掩埋",
            ShellDanger.SAFE,
            ShellPolicy.dangerOf("ls\nreboot -p")
        )
        assertEquals(
            "换行后的破坏性命令必须仍判破坏性",
            ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf("id\nmkfs.ext4 /dev/block/sda")
        )
    }

    @Test
    fun chainedCommandsTakeTheWorstSeverity() {
        assertEquals(
            ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf("ls; rm -rf /data")
        )
        assertEquals(
            ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf("echo hi && wipe data")
        )
        assertEquals(
            "前半只读、后半改系统 = 可记住的风险档",
            ShellDanger.RISKY,
            ShellPolicy.dangerOf("ls; settings put system screen_brightness 10")
        )
    }

    @Test
    fun everyRmTargetIsCheckedNotJustTheFirst() {
        assertEquals(
            "多目标 rm 必须逐个检查：/data 排在第二个也不能漏",
            ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf("rm -rf /sdcard/Download /data")
        )
        assertNotEquals(
            "安全的多目标 rm 不该被误升成破坏性",
            ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf("rm -rf /data/local/tmp/a /data/local/tmp/b")
        )
    }

    @Test
    fun harmlessChainsAreNotOverEscalated() {
        assertEquals(ShellDanger.SAFE, ShellPolicy.dangerOf("ls -l /sdcard"))
        assertNotEquals(
            "只读管道不该被升成破坏性",
            ShellDanger.DESTRUCTIVE,
            ShellPolicy.dangerOf("dumpsys battery | head -4")
        )
    }

    @Test
    fun quotedOrWrappedCommandsCannotHideTheirRealTarget() {
        // shell 执行前会吃掉引号：带引号的破坏性命令与不带引号的效果完全一样，
        // 而黑名单是字符串正则 —— 不归一的话它们全被降级成 RISKY，
        // 而 RISKY 可被「记住此命令」永久免审批，WORKSPACE 沙箱也只挡 DESTRUCTIVE。
        val quoted = listOf(
            "rm -rf \"/data\"",
            "rm -rf '/system'",
            "dd if=/dev/zero of=\"/dev/block/mmcblk0\"",
            "pm uninstall \"com.android.systemui\"",
            "sh -c 'rm -rf /data'",
            "bash -c \"mkfs.ext4 /dev/block/sda\"",
            // 路径写法与规范化：shell 眼里 /data/、//data、/./data、/system/bin/rm 都是同一件事
            "rm -rf /data/",
            "rm -rf /./data",
            "/system/bin/rm -rf /data",
            "rm --recursive --force /data"
        )
        for (command in quoted) {
            assertEquals("引号/外层解释器不能掩盖破坏性：$command", ShellDanger.DESTRUCTIVE, ShellPolicy.dangerOf(command))
        }
        // 反方向：安全目标不能因为剥引号被误升级
        assertEquals(
            ShellDanger.RISKY,
            ShellPolicy.dangerOf("rm -rf \"/data/local/tmp/scratch\"")
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
