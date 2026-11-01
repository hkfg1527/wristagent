package com.example.myapplication.agent.privilege

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** su 后端：可用性由 ["su", "-c", "id"] 是否在 2 秒内吐回 uid=0 判定。 */
class RootShell(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val availableTimeoutMs: Long = AVAILABILITY_TIMEOUT_MS,
) : PrivilegedShell {

    override suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        ProcessExec.exec(listOf("su", "-c", command), timeoutMs)
    }

    /** 任何异常、超时、非零退出都返回 false，绝不抛。 */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ProcessExec.exec(listOf("su", "-c", ID_COMMAND), availableTimeoutMs)
            result.isSuccess && RootUidPattern.containsMatchIn(result.stdout)
        } catch (t: Throwable) {
            false
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val AVAILABILITY_TIMEOUT_MS = 2_000L
        private const val ID_COMMAND = "id"

        /** id 的输出形如 uid=0(root) gid=0(root) ...，只认 uid=0 这个标记。 */
        private val RootUidPattern = Regex("uid=0\\b")
    }
}
