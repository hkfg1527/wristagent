package com.example.myapplication.agent

import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.agent.privilege.ShellResult
import kotlinx.coroutines.withTimeoutOrNull

/** 一次特权命令的完整回执：除了退出码与输出，还报出真实后端（ROOT / SHIZUKU / DIRECT）。 */
data class ShellRun(
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val backend: String,
    val timedOut: Boolean = false
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * 工具层可替换的特权执行入口：单测注入假实现，真机接 [PrivilegeManager]。
 * 真正的权限判定与三种后端选择全部留在特权层，这里只做超时与结果收敛。
 */
fun interface PrivilegedCommandRunner {
    suspend fun run(command: String, timeoutMs: Long): ShellRun
}

/**
 * [PrivilegeManager] 适配器：执行前重新探测后端（ROOT > SHIZUKU > DIRECT），
 * 超时按工具层给的 [PrivilegedCommandRunner.run] 时限硬切，绝不把异常抛给模型。
 */
class PrivilegeShellRunner(private val privilege: PrivilegeManager) : PrivilegedCommandRunner {

    @Volatile
    private var started = false

    override suspend fun run(command: String, timeoutMs: Long): ShellRun {
        if (!started) {
            started = true
            // 只注册一次 Shizuku 监听并触发首次探测；之后每次执行前再同步确认一次后端
            privilege.start()
        }
        val status = try {
            privilege.refresh()
        } catch (t: Throwable) {
            privilege.status.value
        }
        val shell = privilege.shellFor(status.activeBackend)
        val result = try {
            withTimeoutOrNull(timeoutMs) { shell.run(command) }
                ?: ShellResult.failure("timeout after " + timeoutMs + "ms")
        } catch (t: Throwable) {
            ShellResult.failure(t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
        }
        return ShellRun(
            command = command,
            exitCode = result.exitCode,
            stdout = result.stdout,
            stderr = result.stderr,
            backend = status.activeBackend.name,
            timedOut = result.stderr.startsWith("timeout after ")
        )
    }
}
