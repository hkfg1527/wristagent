package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityMode

/**
 * 一条 shell 命令的破坏性分档。
 *
 * 只有 [DESTRUCTIVE] 是绝对档：一律弹审批、永远不可"记住此命令"、清单命中也不放行。
 * [RISKY] 与 [SAFE] 都允许被用户显式记住后跳过审批。
 */
enum class ShellDanger { SAFE, RISKY, DESTRUCTIVE }

/**
 * 特权 shell 工具的安全策略（纯 Kotlin，零 Android / 零 org.json 依赖）。
 *
 * 三件事收敛在这里：
 * 1. 危险命令识别（黑名单全文见 [DESTRUCTIVE_RULES]）：危险命令只降级成"必须审批"，不做静默硬拦截；
 * 2. 审批文案（命令原文 + 风险等级 + 最坏情况）；
 * 3. 会话级"记住此命令"清单的归一化与命中判定。
 */
object ShellPolicy {
    const val TOOL_RUN_SHELL = "run_shell"
    const val TOOL_DEFINE_TOOL = "define_tool"
    const val TOOL_LIST_TOOLS = "list_tools"
    const val TOOL_REMOVE_TOOL = "remove_tool"

    /** 长期记忆写入方：不执行任何命令，但会把内容持久化成之后每一轮都注入的上下文。 */
    const val TOOL_REMEMBER_FACT = "remember_fact"

    const val DEFAULT_TIMEOUT_MS = 15_000L
    const val MIN_TIMEOUT_MS = 1_000L
    const val MAX_TIMEOUT_MS = 120_000L
    const val MAX_COMMAND_LENGTH = 2_000

    /** 危险命令的强制冷却：破坏性命令在任何安全模式下都要压住 3 秒防误触。 */
    const val DESTRUCTIVE_COOLDOWN_SECONDS = 3

    private const val IMPACT_RISKY = "命令以能拿到的最高权限执行，可能改动系统状态且无法自动回滚"
    private const val IMPACT_SAFE = "只读命令，理论上不改变任何系统状态"

    /** 一条破坏性规则：id 用于报告与回归定位，pattern 在归一化后的命令上匹配。 */
    private data class DangerRule(val id: String, val pattern: Regex, val worstCase: String)

    /**
     * 破坏性命令黑名单全文。rm 的目标路径另行按 [classifyRmTarget] 判定，
     * 因此「删 /data/local/tmp/x」是风险而非破坏，只有整棵树/系统树才是破坏。
     */
    private val DESTRUCTIVE_RULES: List<DangerRule> = listOf(
        DangerRule(
            "dd_block_device",
            Regex("""\bdd\b[^;&|]*\bof=/dev/(?:block|by-name|mmcblk|sda|sdb|sdc|nvme)"""),
            "向块设备裸写数据，分区表与文件系统会被直接覆盖"
        ),
        DangerRule(
            "mkfs",
            Regex("""\b(?:mkfs(?:\.\w+)?|mke2fs|make_f2fs)\b"""),
            "格式化分区，分区内所有数据立即丢失且不可恢复"
        ),
        DangerRule(
            "fastboot",
            Regex("""\bfastboot\s+(?:flash|erase|format|oem)\b"""),
            "刷写或擦除闪存分区，操作失败会让设备变砖"
        ),
        DangerRule(
            "format",
            Regex("""\bformat\s+(?:/|/data|/system|/sdcard|userdata|system|cache|emmc)\b"""),
            "格式化存储，目标分区数据全部清空"
        ),
        DangerRule(
            "wipe",
            Regex("""\bwipe\s+(?:data|system|cache|dalvik|all|/data|/system)\b"""),
            "擦除数据分区，全部用户数据不可恢复"
        ),
        DangerRule(
            "pm_system_package",
            Regex("""\bpm\s+(?:uninstall|clear|disable(?:-user)?|suspend)\b[^;&|]*\s(?:android|com\.android[\w.]*|com\.google\.android[\w.]*|system)\b"""),
            "卸载或停用系统应用，系统组件会崩溃甚至无法进入桌面"
        ),
        DangerRule(
            "reboot_bootloader",
            Regex("""\breboot\s+(?:recovery|bootloader|fastboot|edl|sideload)\b"""),
            "重启进入引导/恢复模式，异常时无法回到系统"
        ),
        DangerRule(
            "fork_bomb",
            Regex(""":\(\)\s*\{\s*:\s*\|\s*:\s*&\s*\}\s*;\s*:"""),
            "派生无限进程，设备会瞬间卡死并重启"
        ),
        DangerRule(
            "chmod_root",
            Regex("""\bchmod\b[^;&|]*\s[0-7]{3,4}\s+(?:/|/\*)(?:\s|$)"""),
            "放开根目录权限，任何进程都能改写系统文件"
        ),
        DangerRule(
            "remount_rw",
            Regex("""\bmount\b[^;&|]*\bremount\b[^;&|]*\brw\b"""),
            "把分区重挂为可写，后续改动会破坏系统完整性与校验"
        ),
        DangerRule(
            "raw_block_redirect",
            Regex("""(?:>>?|tee)\s*/dev/(?:block|by-name|mmcblk)"""),
            "直接覆写块设备，分区数据即刻损坏"
        )
    )

    /** rm 的整棵树目标：删掉就是破坏性。 */
    private val wholeTreeTargets = setOf(
        "/", "/*", "/data", "/data/*", "/storage", "/storage/*", "/sdcard", "/sdcard/*"
    )

    /** 系统自有的目录树：删掉其中任何一段都算破坏性。 */
    private val systemTreePrefixes = listOf(
        "/system", "/vendor", "/boot", "/persist", "/etc", "/proc", "/sys", "/dev",
        "/data/system", "/data/adb"
    )

    private val rmInvocation = Regex("""(?:^|[\s;&|(])rm\s+((?:-{1,2}[A-Za-z-]+\s+)*)([^\s;&|]+)""")

    /** 只读命令白名单：命中且整条命令不含 shell 元字符才算 SAFE。 */
    private val readOnlyCommands = setOf(
        "ls", "cat", "head", "tail", "grep", "wc", "stat", "id", "whoami", "uname", "date",
        "uptime", "ps", "df", "du", "free", "getprop", "dumpsys", "which", "echo", "printf",
        "pwd", "file", "md5sum", "sha1sum", "sha256sum", "logcat", "printenv", "nproc", "pgrep",
        "pidof", "netstat", "ss", "ip", "top", "vmstat", "lsblk"
    )

    /** 出现任何一个就直接失去只读资格：反引号与美元符用转义写法，避免与 Kotlin 模板冲突。 */
    private val shellMetaCharacters = listOf("|", "&", ";", ">", "<", "\u0060", "\u0024")

    /** 归一化即"记住此命令"的键：折叠空白并去掉首尾空格，不做任何语义改写。 */
    fun normalize(command: String): String = command.trim().replace(Regex("\\s+"), " ")

    fun dangerOf(command: String): ShellDanger {
        val normalized = normalize(command)
        if (normalized.isEmpty()) return ShellDanger.SAFE
        if (matchedRule(normalized) != null) return ShellDanger.DESTRUCTIVE
        if (classifyRmTarget(normalized) == ShellDanger.DESTRUCTIVE) return ShellDanger.DESTRUCTIVE
        return if (isReadOnly(normalized)) ShellDanger.SAFE else ShellDanger.RISKY
    }

    /** 审批页"最坏情况"整段文案：破坏性命令用命中的规则原文，其余用分档兜底。 */
    fun impactOf(command: String): String {
        val normalized = normalize(command)
        matchedRule(normalized)?.let { return it.worstCase }
        return when (dangerOf(normalized)) {
            ShellDanger.DESTRUCTIVE -> "命令会不可逆地破坏系统数据"
            ShellDanger.RISKY -> IMPACT_RISKY
            ShellDanger.SAFE -> IMPACT_SAFE
        }
    }

    fun isRemembered(command: String, remembered: Collection<String>): Boolean {
        if (dangerOf(command) == ShellDanger.DESTRUCTIVE) return false
        val key = normalize(command)
        if (key.isEmpty()) return false
        return remembered.any { normalize(it) == key }
    }

    /** 记住清单命中且非破坏性才跳过审批；破坏性命令无论清单如何都要人批。 */
    fun gate(danger: ShellDanger, remembered: Boolean): ApprovalGate.Decision =
        if (remembered && danger != ShellDanger.DESTRUCTIVE) ApprovalGate.Decision.AUTO_ALLOW
        else ApprovalGate.Decision.REQUIRE_APPROVAL

    fun assess(command: String, mode: SecurityMode): RiskAssessment {
        val normalized = normalize(command)
        val danger = dangerOf(normalized)
        val impact = impactOf(normalized)
        val action = when (danger) {
            ShellDanger.DESTRUCTIVE -> "以最高可用权限执行一条破坏性 shell 命令"
            ShellDanger.RISKY -> "以最高可用权限执行一条 shell 命令"
            ShellDanger.SAFE -> "以最高可用权限执行一条只读 shell 命令"
        }
        return when (danger) {
            ShellDanger.DESTRUCTIVE -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = true,
                riskSummary = "危险命令：" + impact,
                requireCooldownSeconds = DESTRUCTIVE_COOLDOWN_SECONDS,
                whatItDoes = action,
                worstCase = impact,
                aiRiskLevel = RiskLevel.CRITICAL,
                commandText = normalized,
                rememberable = false
            )

            ShellDanger.RISKY -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = true,
                riskSummary = "特权命令：将以最高可用权限执行",
                requireCooldownSeconds = 0,
                whatItDoes = action,
                worstCase = impact,
                aiRiskLevel = RiskLevel.HIGH,
                commandText = normalized,
                rememberable = true
            )

            ShellDanger.SAFE -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = false,
                riskSummary = "只读命令：不改变系统状态",
                requireCooldownSeconds = 0,
                whatItDoes = action,
                worstCase = impact,
                aiRiskLevel = RiskLevel.LOW,
                commandText = normalized,
                rememberable = true
            )
        }
    }

    /**
     * AI 定级只允许加严，不允许把特权命令的等级降下来；命令原文与"可否记住"永远以本地判定为准。
     * 否则一次"低风险"的 AI 判断就能让危险命令在审批页上显示成低危。
     */
    fun enforceRiskFloor(assessed: RiskAssessment, narrated: RiskAssessment): RiskAssessment {
        val level = if (narrated.aiRiskLevel.ordinal >= assessed.aiRiskLevel.ordinal) {
            narrated.aiRiskLevel
        } else {
            assessed.aiRiskLevel
        }
        return narrated.copy(
            aiRiskLevel = level,
            isHighRisk = assessed.isHighRisk || narrated.isHighRisk,
            isHardBlocked = assessed.isHardBlocked,
            commandText = assessed.commandText,
            rememberable = assessed.rememberable
        )
    }

    /** null 表示参数合法；非 null 是要回给模型的错误说明。 */
    fun validate(command: String): String? {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return "命令不可为空"
        if (trimmed.length > MAX_COMMAND_LENGTH) return "命令过长（上限 " + MAX_COMMAND_LENGTH + " 字符）"
        return null
    }

    fun clampTimeout(requested: Long?): Long =
        (requested ?: DEFAULT_TIMEOUT_MS).coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)

    /** run_shell 与全部自写工具都是特权工具：执行前必须过审批闸门。 */
    fun isPrivilegedTool(toolName: String, selfDefinedNames: Set<String>): Boolean =
        toolName == TOOL_RUN_SHELL || toolName in selfDefinedNames

    /**
     * 强制审批档（无视安全模式与审批策略，一律先问人）。
     *
     * 判据不是"这一下危险不危险"，而是"之后会不会一直生效"：
     * - run_shell / 自写工具：以最高可用权限执行任意命令；
     * - define_tool：扩大能力面，新工具会留在清单里；
     * - remember_fact：**持久化数据**，写进去的条目之后每一轮都进系统提示词 ——
     *   这是唯一一条能长期影响后续对话的隐式通道，因此与 define_tool 同档，
     *   而不是退回按 AI 风险等级分的通用闸门（那条闸门会按低风险自动放行）。
     * 元数据类工具（list_tools / remove_tool / 设备状态等）仍走通用分级闸门。
     */
    fun requiresApprovalAlways(toolName: String, selfDefinedNames: Set<String>): Boolean =
        isPrivilegedTool(toolName, selfDefinedNames) ||
            toolName == TOOL_DEFINE_TOOL ||
            toolName == TOOL_REMEMBER_FACT

    /** 长期记忆写入不是 shell 命令：它持久化数据，但不进沙箱分档、也不执行任何命令。 */
    fun isMemoryTool(toolName: String): Boolean = toolName == TOOL_REMEMBER_FACT

    private fun matchedRule(normalized: String): DangerRule? =
        DESTRUCTIVE_RULES.firstOrNull { it.pattern.containsMatchIn(normalized) }

    private fun classifyRmTarget(normalized: String): ShellDanger {
        var destructive = false
        for (match in rmInvocation.findAll(normalized)) {
            val flags = match.groupValues[1].lowercase()
            val target = match.groupValues[2]
            val recursive = flags.contains('r') || flags.contains('f')
            if (!recursive && target !in wholeTreeTargets) continue
            if (target in wholeTreeTargets) return ShellDanger.DESTRUCTIVE
            if (systemTreePrefixes.any { target == it || target.startsWith(it + "/") }) {
                destructive = true
            }
        }
        return if (destructive) ShellDanger.DESTRUCTIVE else ShellDanger.RISKY
    }

    private fun isReadOnly(normalized: String): Boolean {
        if (shellMetaCharacters.any { normalized.contains(it) }) return false
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        val head = tokens.firstOrNull() ?: return false
        return when (head) {
            "pm" -> tokens.getOrNull(1) == "list"
            "cmd" -> tokens.getOrNull(1) == "-l"
            else -> head in readOnlyCommands
        }
    }
}
