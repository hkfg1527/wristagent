package com.example.myapplication.agent.privilege

import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * 子进程收口：stdout/stderr 各由一条守护线程读干，主线程按超时等待退出。
 * 超时、流异常、中断都返回 [ShellResult.failure]，不向上抛。
 */
internal object ProcessExec {

    /** 读干剩余输出的等待上限，防止子进程异常时线程悬挂。 */
    private const val DrainJoinMs = 500L

    fun exec(argv: List<String>, timeoutMs: Long): ShellResult = try {
        collect(ProcessBuilder(argv).start(), timeoutMs)
    } catch (t: Throwable) {
        ShellResult.failure(t.describe())
    }

    fun collect(process: Process, timeoutMs: Long): ShellResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val outReader = drain(process.inputStream, stdout)
        val errReader = drain(process.errorStream, stderr)

        val exited = try {
            process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        } catch (t: Throwable) {
            destroyQuietly(process)
            joinQuietly(outReader)
            joinQuietly(errReader)
            return ShellResult.failure(t.describe())
        }

        if (!exited) {
            destroyQuietly(process)
            joinQuietly(outReader)
            joinQuietly(errReader)
            return ShellResult.failure("timeout after ${timeoutMs}ms")
        }

        joinQuietly(outReader)
        joinQuietly(errReader)
        val code = try {
            process.exitValue()
        } catch (t: Throwable) {
            -1
        }
        return ShellResult(code, stdout.toString().trim(), stderr.toString().trim())
    }

    fun destroyQuietly(process: Process) {
        try {
            process.destroy()
        } catch (_: Throwable) {
        }
        try {
            process.inputStream.close()
        } catch (_: Throwable) {
        }
        try {
            process.errorStream.close()
        } catch (_: Throwable) {
        }
        try {
            process.outputStream.close()
        } catch (_: Throwable) {
        }
    }

    private fun drain(stream: InputStream, sink: StringBuilder): Thread =
        Thread {
            try {
                stream.bufferedReader().use { reader -> sink.append(reader.readText()) }
            } catch (_: Throwable) {
            }
        }.apply {
            isDaemon = true
            start()
        }

    private fun joinQuietly(thread: Thread) {
        try {
            thread.join(DrainJoinMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
