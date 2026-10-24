package com.example.myapplication.agent.contract

import java.util.regex.Pattern

/**
 * AI 三级安全护栏审查引擎
 * 1. 致命指令硬黑名单绝对拦截 (rm -rf, 格式化, 擦除数据等)
 * 2. 严格确认模式：全部审批 + 风险命令 3 秒冷却锁 + 直白影响说明
 * 3. 标准模式 (默认)：无风险只读工具静默放行，有风险工具触发审批并给出风险后果说明
 * 4. 自动执行模式：除致命黑名单外全部放行
 */
class SecurityGuardrail {

    private fun isFatalCommand(command: String): Boolean {
        val lower = command.lowercase().replace("\\s+".toRegex(), " ")
        if (lower.contains("rm -rf") || lower.contains("rm -r") || lower.contains("rm -f")) {
            if (lower.contains("/") || lower.contains("~") || lower.contains("*")) return true
        }
        if (lower.startsWith("mkfs") || lower.contains("mkfs.") || lower.contains("format ") || lower.contains("wipe ")) return true
        if (lower.contains("dd ") && lower.contains("if=") && lower.contains("of=")) return true
        if (lower.contains(":(){ :|:& };:") || lower.contains(":(){:|:&};:")) return true
        if (lower.contains("pm clear com.android") || lower.contains("pm uninstall com.android")) return true
        if (lower.contains("reboot recovery") || lower.contains("reboot bootloader")) return true
        return false
    }

    /**
     * 针对底层 Shell/系统命令的审查
     */
    fun assessCommand(command: String, mode: SecurityMode): RiskAssessment {
        val trimmed = command.trim()

        // 1. 硬黑名单检查
        if (isFatalCommand(trimmed)) {
            return RiskAssessment(
                isHardBlocked = true,
                isHighRisk = true,
                riskSummary = "致命高危指令：企图破坏或擦除系统/全盘数据，已被安全机制强制拒死！",
                requireCooldownSeconds = 0
            )
        }

        // 2. 根据模式评估风险
        return when (mode) {
            SecurityMode.EXPERT -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = false,
                riskSummary = "自动执行模式：跳过二次确认",
                requireCooldownSeconds = 0
            )
            SecurityMode.STANDARD -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = true,
                riskSummary = "此命令将直接在系统底层执行，可能影响系统稳定性。",
                requireCooldownSeconds = 0
            )
            SecurityMode.NOVICE -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = true,
                riskSummary = "严格确认：此命令涉及底层操作，已锁定 3 秒，请确认后滑动放行。",
                requireCooldownSeconds = 3
            )
        }
    }

    /**
     * 针对穿戴端标准 Tool Calling 的审查
     */
    fun assessTool(toolName: String, argsJson: String, mode: SecurityMode): RiskAssessment {
        // 先检查参数里是否携带了致命注入
        if (isFatalCommand(argsJson) || isFatalCommand(toolName)) {
            return RiskAssessment(
                isHardBlocked = true,
                isHighRisk = true,
                riskSummary = "工具参数中检测到高危指令，已被安全机制拦截。",
                requireCooldownSeconds = 0
            )
        }

        // 判定该工具是否属于高危系统修改类
        val isSystemControlTool = toolName in listOf("set_system_power_mode", "adjust_matrix_density")

        return when (mode) {
            SecurityMode.EXPERT -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = false,
                riskSummary = "自动模式：直接执行",
                requireCooldownSeconds = 0
            )

            SecurityMode.STANDARD -> {
                if (isSystemControlTool) {
                    val summary = if (toolName == "set_system_power_mode") {
                        "该操作将变更系统功耗设置，可能影响后台同步频率。"
                    } else {
                        "该操作将调整手表屏幕点阵间距。"
                    }
                    RiskAssessment(
                        isHardBlocked = false,
                        isHighRisk = true,
                        riskSummary = summary,
                        requireCooldownSeconds = 0
                    )
                } else {
                    // 只读工具 (查电量、时间、天气、网络搜索) 直接放行
                    RiskAssessment(
                        isHardBlocked = false,
                        isHighRisk = false,
                        riskSummary = "安全只读操作，直接放行",
                        requireCooldownSeconds = 0
                    )
                }
            }

            SecurityMode.NOVICE -> {
                if (isSystemControlTool) {
                    val summary = "高危确认：将调整系统电源或显示策略，已锁定 3 秒防误触。"
                    RiskAssessment(
                        isHardBlocked = false,
                        isHighRisk = true,
                        riskSummary = summary,
                        requireCooldownSeconds = 3 // 锁定 3 秒
                    )
                } else {
                    // 普通读数工具在严格模式下也提示确认，但无需 3 秒锁
                    val friendlyName = when (toolName) {
                        "get_device_status" -> "获取手表电量与硬件温度"
                        "query_time_weather" -> "获取当前时间与本地天气"
                        "vibrate_device" -> "触发表盘马达振动"
                        "web_search" -> "连接互联网搜索资讯"
                        else -> toolName
                    }
                    RiskAssessment(
                        isHardBlocked = false,
                        isHighRisk = false,
                        riskSummary = "AI 请求执行 [$friendlyName]，请确认是否允许。",
                        requireCooldownSeconds = 0
                    )
                }
            }
        }
    }
}
