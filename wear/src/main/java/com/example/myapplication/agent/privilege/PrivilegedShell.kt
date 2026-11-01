package com.example.myapplication.agent.privilege

/** 特权执行后端，优先级 ROOT > SHIZUKU > DIRECT。 */
enum class PrivilegeBackend { DIRECT, ROOT, SHIZUKU }

/** 一次 shell 调用的结果；失败路径统一用 [failure]，绝不抛给调用方。 */
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) {
    val isSuccess: Boolean get() = exitCode == 0

    companion object {
        fun failure(reason: String): ShellResult = ShellResult(-1, "", reason)
    }
}

/** 可切换的特权 shell：同一命令在三种后端下的执行方式一致。 */
interface PrivilegedShell {
    suspend fun run(command: String): ShellResult
}

/** 异常收敛成一行原因文本，用于填进 [ShellResult.stderr]。 */
internal fun Throwable.describe(): String =
    message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
