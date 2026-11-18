package com.example.myapplication.agent.privilege

import com.example.myapplication.agent.ShellPolicy

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
    }

    /**
     * 每条命令一个**一次性**线程，不再共用单线程池。
     *
     * 旧实现用 `newSingleThreadExecutor` 常驻：命令超时后 `future.cancel(true)` 打断不了阻塞在
     * `readLine()` 上的任务（管道读不响应 interrupt），这个线程就永久卡住单线程池 ——
     * 之后每条命令都排在死任务后面、逐个超时，`isAvailable()` 恒 false，root 静默降级到 DIRECT。
     * 一次性线程 + 超时即杀进程（管道随之关闭、阻塞读立即返回）才是收敛的。
     */
    private fun execute(command: String, timeout: Long): ShellResult {
        val session = live.get() ?: open() ?: return ShellResult.failure(UNAVAILABLE)

        val task = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "root-shell-cmd").apply { isDaemon = true }
        }
        return try {
            val future = task.submit<ShellResult> { session.session.run(command) }
            val result = future.get(timeout, TimeUnit.MILLISECONDS)
            // 会话自己读到 EOF（su 被撤权 / 被杀）：进程已死，别把它留在 live 里反复复用。
            // 旧实现不清缓存 —— 之后每条命令都返回同一句 "root shell closed"，root 永久失效。
            if (result.stderr.contains(SESSION_CLOSED_MARKER)) dispose(session)
            result
        } catch (e: TimeoutException) {
            dispose(session)
            ShellResult.failure("timeout after " + timeout + "ms")
        } catch (t: Throwable) {
            dispose(session)
            ShellResult.failure(t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName)
        } finally {
            task.shutdownNow()
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
        const val DEFAULT_TIMEOUT_MS = ShellPolicy.DEFAULT_TIMEOUT_MS
        const val AVAILABILITY_TIMEOUT_MS = 2_000L
        private const val ID_COMMAND = "id"
        private const val UNAVAILABLE = "root 不可用：未取得 su 会话"

        /** RootSession 读到 EOF 时的失败文案：见到它就说明会话已死，必须丢弃。 */
        private const val SESSION_CLOSED_MARKER = "root shell closed"

        /** id 的输出形如 uid=0(root) gid=0(root) ...，只认 uid=0 这个标记。 */
        private val RootUidPattern = Regex("uid=0\\b")
    }
}
