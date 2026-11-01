package com.example.myapplication.agent.privilege

/**
 * 跨进程返回值的封帧规则：exitCode + 两段长度前缀 + 原文。
 * 用长度而不是分隔符，命令输出里出现任何字符都不会串段。
 *
 * 任何解析异常都变成 ShellResult(-1, "", 原因)，绝不抛：这条路径跑在 binder 回调里，
 * 抛出去会把进程搞挂，而用户只会看到"什么都没发生"。
 */
internal object UserServiceProtocol {

    fun format(exitCode: Int, stdout: String, stderr: String): String = buildString {
        append(exitCode).append('\n')
        append(stdout.length).append('\n')
        append(stderr.length).append('\n')
        append(stdout)
        append(stderr)
    }

    fun parse(raw: String): ShellResult {
        val first = raw.indexOf('\n')
        if (first < 0) return fail()
        val second = raw.indexOf('\n', first + 1)
        if (second < 0) return fail()
        val third = raw.indexOf('\n', second + 1)
        if (third < 0) return fail()

        val exitCode = raw.substring(0, first).toIntOrNull() ?: return fail()
        val stdoutLength = raw.substring(first + 1, second).toIntOrNull() ?: return fail()
        val stderrLength = raw.substring(second + 1, third).toIntOrNull() ?: return fail()
        if (stdoutLength < 0 || stderrLength < 0) return fail()

        val payload = raw.substring(third + 1)
        if (payload.length < stdoutLength + stderrLength) return fail()

        return ShellResult(
            exitCode = exitCode,
            stdout = payload.substring(0, stdoutLength),
            stderr = payload.substring(stdoutLength, stdoutLength + stderrLength)
        )
    }

    private fun fail(): ShellResult =
        ShellResult(exitCode = -1, stdout = "", stderr = "特权服务返回了无法解析的载荷")
}