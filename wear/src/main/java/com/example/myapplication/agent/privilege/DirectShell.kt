package com.example.myapplication.agent.privilege

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 本进程权限的兜底后端：未 root、未安装 Shizuku 时唯一可用的一条路。 */
class DirectShell(private val timeoutMs: Long = DEFAULT_TIMEOUT_MS) : PrivilegedShell {

    override suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        ProcessExec.exec(listOf("sh", "-c", command), timeoutMs)
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
