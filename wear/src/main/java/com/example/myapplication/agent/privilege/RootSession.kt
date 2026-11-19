package com.example.myapplication.agent.privilege

/**
 * 常驻 su 会话的协议与行循环。
 *
 * 一次性 `su -c` 每执行一条命令都要重新申请一次授权，于是用户在会弹提示的 Magisk 上每条 root 命令都被打扰一次；
 * 常驻会话把授权收敛到一次，顺带省掉每条命令几十到两百毫秒的进程启动开销。
 */
internal object RootShellProtocol {

    /**
     * 哨兵行形如 `<token>:<exitCode>`；token 每会话随机，避免命令输出自己撞上哨兵。
     *
     * 两处细节都是为了"会话不错位"：
     * - `</dev/null`：命令若读 stdin（`cat`、`read`、嵌套 `sh`）会**吞掉下一条命令**，
     *   把整个会话后续全部错位；
     * - 哨兵前先 `printf '\n'`：命令输出不带尾换行时（`printf hi`、`echo -n x`），
     *   哨兵会粘成 `hitoken:0`，`classify` 认不出，读循环卡到超时。
     *
     * 退出码必须**先存再补换行**：`printf` 会把 `$?` 覆盖成它自己的退出码（恒 0），
     * 于是所有 root 命令都以 `exit_code=0` 回给模型 —— 失败的命令会被当成"已经改成功了"。
     */
    fun buildCommand(command: String, token: String): String =
        "{ " + command + " ; } </dev/null 2>&1 ; __rc=\$? ; printf '\\n' ; echo \"" + token + ":\$__rc\"\n"

    sealed interface Line {
        /** 哨兵行：命令结束，[exitCode] 是 shell 给出的退出码。 */
        data class Done(val exitCode: Int) : Line

        /** 普通输出行。 */
        data class Output(val text: String) : Line
    }

    fun classify(line: String, token: String): Line {
        if (!line.startsWith(token + ":")) return Line.Output(line)
        val code = line.removePrefix(token + ":").trim().toIntOrNull()
        return if (code == null) Line.Output(line) else Line.Done(code)
    }
}

/** 一次常驻会话：写一行命令，读到哨兵行为止。不持有进程，读写由调用方注入。 */
internal class RootSession(
    private val write: (String) -> Unit,
    private val readLine: () -> String?,
    private val token: String
) {

    fun run(command: String): ShellResult {
        try {
            write(RootShellProtocol.buildCommand(command, token))
        } catch (t: Throwable) {
            return ShellResult.failure(describe(t))
        }
        val output = StringBuilder()
        var truncated = false
        while (true) {
            val line = readLine() ?: return ShellResult.failure("root shell closed")
            when (val classified = RootShellProtocol.classify(line, token)) {
                is RootShellProtocol.Line.Done ->
                    return ShellResult(
                        classified.exitCode,
                        output.toString().trim() + if (truncated) "\n" + TRUNCATED_MARK else "",
                        ""
                    )

                is RootShellProtocol.Line.Output -> {
                    // 输出封顶：root 下一条 logcat / dumpsys 15 秒能产数十 MB，手表堆只有数百 MB。
                    // 超出后继续读干（否则写满的管道会卡住子进程），但不再入内存。
                    if (output.length < MAX_OUTPUT_CHARS) {
                        output.appendLine(classified.text)
                    } else {
                        truncated = true
                    }
                }
            }
        }
    }

    private companion object {
        /** 与 ProcessExec 的同名上限保持一致（256K 字符）。 */
        const val MAX_OUTPUT_CHARS = 256 * 1024
        const val TRUNCATED_MARK = "…[truncated]"
    }

    private fun describe(t: Throwable): String =
        t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
}
