package com.example.myapplication.agent.contract

import java.util.regex.Pattern

/**
 * AI 三级安全护栏审查引擎
 * 1. 致命指令硬黑名单绝对拦截 (rm -rf, 格式化, 擦除数据等)
 * 2. 小白模式 (默认)：全部审批 + 风险命令 3 秒冷却锁 + 大白话人话说明
 * 3. 普通人模式：无风险只读工具静默放行，有风险工具触发审批并给出最严重后果说明
 * 4. 大佬模式：除致命黑名单外全部放行
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
                riskSummary = "大佬模式已放行此命令",
                requireCooldownSeconds = 0
            )
            SecurityMode.STANDARD -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = true,
                riskSummary = "此命令将直接在系统底层执行，最高可能导致系统服务中断或应用闪退。",
                requireCooldownSeconds = 0
            )
            SecurityMode.NOVICE -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = true,
                riskSummary = "警告：此命令将修改手表底层运行状态，可能导致系统不稳定。请仔细确认后再滑动！",
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
                riskSummary = "工具参数中检测到致命高危指令，已被安全机制强制拒死！",
                requireCooldownSeconds = 0
            )
        }

        // 判定该工具是否属于高危系统修改类
        val isSystemControlTool = toolName in listOf("set_system_power_mode", "adjust_matrix_density")

        return when (mode) {
            SecurityMode.EXPERT -> RiskAssessment(
                isHardBlocked = false,
                isHighRisk = false,
                riskSummary = "大佬模式：自动放行执行",
                requireCooldownSeconds = 0
            )

            SecurityMode.STANDARD -> {
                if (isSystemControlTool) {
                    val summary = if (toolName == "set_system_power_mode") {
                        "该操作将变更系统功耗。开启极致省电会挂起后台同步并降低刷新率，可能导致来电提醒延迟。"
                    } else {
                        "该操作将实时重构手表屏幕底层的物理点阵网格。"
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
                    val summary = "高危提醒：将调整手表的核心电源策略！可能导致后台通知无法及时接收，最严重会导致网络断开。"
                    RiskAssessment(
                        isHardBlocked = false,
                        isHighRisk = true,
                        riskSummary = summary,
                        requireCooldownSeconds = 3 // 必须强制锁 3 秒！
                    )
                } else {
                    // 普通读数工具在小白模式下也提示确认，但无需 3 秒锁
                    val friendlyName = when (toolName) {
                        "get_device_status" -> "获取手表电量与硬件温度"
                        "query_time_weather" -> "获取当前精准时间与本地天气"
                        "vibrate_device" -> "触发表盘马达振动"
                        "web_search" -> "连接互联网搜索实时资讯"
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
