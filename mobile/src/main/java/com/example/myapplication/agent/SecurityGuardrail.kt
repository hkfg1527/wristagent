package com.example.myapplication.agent

import java.io.File

/**
 * 手机端轻量安全审查防护栏 (Security Guardrail Interceptor)
 * 对 Agent 发起的一切文件读写与命令执行进行严格的沙盒与高危特征审查
 */
class SecurityGuardrail(
    private val sandboxRootDir: File
) {
    // 危险系统命令黑名单
    private val blockedCommands = listOf(
        "rm -rf /",
        "rm -rf /*",
        "mkfs",
        "dd if=",
        ":(){ :|:& };:",
        "reboot",
        "shutdown",
        "chmod 777 /",
        "su -",
        "setenforce 0"
    )

    // 严禁越界修改的系统敏感路径
    private val restrictedPaths = listOf(
        "/system",
        "/data/system",
        "/etc",
        "/proc",
        "/sys",
        "/dev"
    )

    data class AuditResult(
        val isPassed: Boolean,
        val riskScore: Int, // 0 (安全) ~ 100 (极高危)
        val reason: String
    )

    /**
     * 审查文件路径访问是否符合沙盒边界
     */
    fun auditPathAccess(targetPath: String, isWrite: Boolean): AuditResult {
        val canonicalPath = try {
            File(targetPath).canonicalPath
        } catch (_: Exception) {
            targetPath
        }

        // 1. 检查是否触碰系统只读/核心敏感目录
        for (restricted in restrictedPaths) {
            if (canonicalPath.startsWith(restricted)) {
                return AuditResult(
                    isPassed = false,
                    riskScore = 95,
                    reason = "越界访问拦截：严禁触碰底层系统关键路径 $restricted"
                )
            }
        }

        // 2. 写操作必须严格限制在工作区沙盒或应用内部目录内
        if (isWrite) {
            val sandboxCanonical = sandboxRootDir.canonicalPath
            val isInsideSandbox = canonicalPath.startsWith(sandboxCanonical)
            if (!isInsideSandbox) {
                return AuditResult(
                    isPassed = false,
                    riskScore = 80,
                    reason = "写保护拦截：目标写入路径必须位于沙盒目录内 [$sandboxCanonical]"
                )
            }
        }

        return AuditResult(
            isPassed = true,
            riskScore = 0,
            reason = "沙盒边界合规，放行访问"
        )
    }

    /**
     * 审查命令执行安全性
     */
    fun auditCommand(commandLine: String): AuditResult {
        val cleanCmd = commandLine.trim().lowercase()

        for (blocked in blockedCommands) {
            if (cleanCmd.contains(blocked)) {
                return AuditResult(
                    isPassed = false,
                    riskScore = 100,
                    reason = "高危命令熔断：检测到破坏性高危指令 [$blocked]，严禁执行！"
                )
            }
        }

        return AuditResult(
            isPassed = true,
            riskScore = 5,
            reason = "命令合规，准予沙盒内运行"
        )
    }
}
