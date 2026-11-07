package com.example.myapplication.agent.privilege

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

/**
 * su 后端：整个进程生命周期只申请一次授权，之后所有命令都走同一个常驻会话。
 *
 * 为什么不是 `su -c` 每命令一次：会弹提示的 Magisk 上那意味着每调用一次 root 工具就打扰用户一次，
 * 且每条命令多付几十到两百毫秒的进程启动。常驻会话把授权收敛成一次。
 *
 * 兜底：命令超时或流异常时杀掉会话并下次重新申请——否则后续命令会读到错位的输出。
 * 会话的 stdin 随本进程关闭而 EOF，su 子进程会自行退出，不需要显式回收。
 */
class RootShell(
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val availableTimeoutMs: Long = AVAILABILITY_TIMEOUT_MS,
    private val opener: () -> Process = { ProcessBuilder("su").start() }
) : PrivilegedShell, AutoCloseable {

    private val lock = ReentrantLock()
    private val live = AtomicReference<Live?>(null)
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "root-shell").apply { isDaemon = true }
    }

    private class Live(val process: Process, val session: RootSession, val writer: BufferedWriter) {
        fun close() {
            try {
                writer.close()
            } catch (_: Throwable) {
            }
            ProcessExec.destroyQuietly(process)
        }
    }

    override suspend fun run(command: String): ShellResult = withContext(Dispatchers.IO) {
        lock.withLock { execute(command, timeoutMs) }
    }

    /** 任何异常、超时、非零退出都返回 false，绝不抛。 */
    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = lock.withLock { execute(ID_COMMAND, availableTimeoutMs) }
            result.isSuccess && RootUidPattern.containsMatchIn(result.stdout)
        } catch (t: Throwable) {
            false
        }
    }

    override fun close() {
        lock.withLock { live.getAndSet(null)?.close() }
        worker.shutdownNow()
    }

    private fun execute(command: String, timeout: Long): ShellResult {
        val session = live.get() ?: open() ?: return ShellResult.failure(UNAVAILABLE)

        val future = worker.submit<ShellResult> { session.session.run(command) }
        return try {
            future.get(timeout, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            dispose(session)
            future.cancel(true)
            ShellResult.failure("timeout after " + timeout + "ms")
        } catch (t: Throwable) {
            dispose(session)
            ShellResult.failure(t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
        }
    }

    private fun open(): Live? = try {
        val process = opener()
        val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
        val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        val token = "dsh" + String.format("%016x", Random.nextLong())
        val session = RootSession(
            write = { line ->
                writer.write(line)
                writer.flush()
            },
            readLine = { reader.readLine() },
            token = token
        )
        Live(process, session, writer).also { live.set(it) }
    } catch (t: Throwable) {
        null
    }

    private fun dispose(session: Live) {
        live.compareAndSet(session, null)
        session.close()
    }

    companion object {
        const val DEFAULT_TIMEOUT_MS = 15_000L
        const val AVAILABILITY_TIMEOUT_MS = 2_000L
        private const val ID_COMMAND = "id"
        private const val UNAVAILABLE = "root 不可用：未取得 su 会话"

        /** id 的输出形如 uid=0(root) gid=0(root) ...，只认 uid=0 这个标记。 */
        private val RootUidPattern = Regex("uid=0\\b")
    }
}
