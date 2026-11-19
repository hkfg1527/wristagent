package com.example.myapplication.agent.privilege

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 假的 `su` 进程：让 [RootShell] 的超时/回收路径能在纯 JVM 上被测到。
 *
 * 为什么需要它：真机上"一条命令超时后，线程池被死任务永久占住、之后每条命令都超时"
 * 这个故障只能在超时真的发生时复现；没有这个假进程就只能靠人肉上机点。
 *
 * 行为：
 * - [send] 往 stdout 推一行；不推就代表"命令不返回"，读线程会一直阻塞（就是真机上的卡死场景）；
 * - [destroyForcibly]/[destroy] 会把流关掉 —— 真机上杀进程也是同样的效果（管道关闭 → readLine 返回 null）。
 */
internal class FakeSuProcess : Process() {

    private val lines = LinkedBlockingQueue<String>()
    private val closed = AtomicBoolean(false)
    private val alive = AtomicBoolean(true)
    private val writes = ArrayList<String>()

    val writtenLines: List<String> get() = synchronized(writes) { writes.toList() }

    /**
     * 自动应答：**收到命令行就立刻按同一 token 回哨兵**。
     *
     * 这样测试不需要后台线程去解析 token，也就没有"应答线程和写入谁先谁后"的竞态。
     */
    @Volatile
    var autoRespond: Boolean = false

    /**
     * 模拟"真机上杀不干净"：`destroy()` 之后读端**仍然阻塞**。
     *
     * 真实场景是 `su` 被杀、但它的子孙进程（`sleep 100 &`、`logcat`）还握着 stdout 管道 ——
     * 于是阻塞在 `readLine()` 的那个线程永远不会醒。这正是"一条超时命令把整个线程池占死"的成因，
     * 不打开这个开关就复现不了。
     */
    @Volatile
    var survivesDestroy: Boolean = false

    /** 推一行到 stdout（不含换行，内部补）。 */
    fun send(line: String) {
        lines.offer(line)
    }

    private val stdin = object : OutputStream() {
        private val pending = StringBuilder()
        override fun write(b: Int) {
            val completed: String?
            synchronized(writes) {
                pending.append(b.toChar())
                completed = if (b == '\n'.code) {
                    pending.toString().also {
                        writes += it
                        pending.setLength(0)
                    }
                } else {
                    null
                }
            }
            if (autoRespond && completed != null) replyTo(completed)
        }

        override fun close() {
            closed.set(true)
        }
    }

    /** 从命令行里解析会话 token，然后把"输出 + 哨兵"推进 stdout。 */
    private fun replyTo(commandLine: String) {
        // 哨兵形态是 `echo "dsh…:$__rc"`（退出码先存进 __rc，见 RootShellProtocol.buildCommand）。
        // 这里两种形态都认：旧形态 `token:$?` 只用于对照历史行为。
        // 哨兵形态是 `echo "dsh…:$__rc"`（退出码先存进 __rc，见 RootShellProtocol.buildCommand）。
        // 用 Regex.escape 拼，避免在正则里把 $ 当成行尾锚点。
        val newStyle = "(dsh[0-9a-f]+):" + Regex.escape("$" + "__rc")
        val token = Regex(newStyle).find(commandLine)?.groupValues?.get(1)
            ?: Regex("""(dsh[0-9a-f]+):\$\?""").find(commandLine)?.groupValues?.get(1)
            ?: return
        send("uid=0(root) gid=0(root)")
        send("$token:0")
    }

    /** 阻塞式读：没有新行就等；进程被 destroy 后返回 EOF。 */
    private val stdout = object : InputStream() {
        private var buffer: ByteArray = ByteArray(0)
        private var cursor = 0

        override fun read(): Int {
            if (cursor >= buffer.size) {
                while (true) {
                    val line = try {
                        lines.poll(20, TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        // 真正的管道读**不响应** Thread.interrupt：这里也照做。
                        // 否则 `future.cancel(true)` 能在假进程上"意外生效"，测试就失去了意义。
                        continue
                    }
                    if (line != null) {
                        buffer = (line + "\n").toByteArray()
                        cursor = 0
                        break
                    }
                    // survivesDestroy = 子孙进程还握着管道：杀掉 su 也不会 EOF
                    if (!survivesDestroy && (closed.get() || !alive.get())) return -1
                }
            }
            return buffer[cursor++].toInt() and 0xFF
        }

        /**
         * **必须覆盖**：默认实现会一直调 [read] 直到填满 [len] 字节 ——
         * 我们手上只有一两行，它就会永远等下去，`BufferedReader.readLine()` 因此永远返回不了。
         * 这里"有多少给多少"：读完当前缓冲就立刻返回。
         */
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val first = read()
            if (first < 0) return -1
            b[off] = first.toByte()
            var count = 1
            while (count < len && cursor < buffer.size) {
                b[off + count] = buffer[cursor++].toByte()
                count++
            }
            return count
        }
    }

    override fun getOutputStream(): OutputStream = stdin
    override fun getInputStream(): InputStream = stdout
    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))
    override fun waitFor(): Int = 0
    override fun exitValue(): Int = 0
    override fun isAlive(): Boolean = alive.get()
    override fun destroy() {
        alive.set(false)
        closed.set(true)
    }

    override fun destroyForcibly(): Process {
        destroy()
        return this
    }
}

/** 便捷：把 [RootShell] 指向一个假进程，并返回那个假进程。 */
internal fun rootShellWithFakeProcess(timeoutMs: Long = 300L): Pair<RootShell, FakeSuProcess> {
    val fake = FakeSuProcess()
    val shell = RootShell(timeoutMs = timeoutMs, availableTimeoutMs = timeoutMs, opener = { fake })
    return shell to fake
}
