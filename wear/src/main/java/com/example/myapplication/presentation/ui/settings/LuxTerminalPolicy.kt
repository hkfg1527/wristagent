package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.agent.privilege.ShellResult

/**
 * Lux 终端的纯逻辑：转录渲染、条数收口、快捷命令清单。
 *
 * "极轻量"的具体含义（本版刻意划的边界）：
 * - **没有 PTY、没有 ANSI 解析、没有交互式 stdin** —— 只发一条命令、收一次输出；
 * - 输出按用户已经设置过的 `tool_result_max_chars` 截断（同一个预算，不再新增旋钮）；
 * - 转录只保留最近 [MAX_ENTRIES] 条，手表内存里不该攒一整份 shell 历史。
 */
object LuxTerminalPolicy {

    /** 转录最多留几条：超过就丢最旧的。 */
    const val MAX_ENTRIES = 12

    /**
     * 快捷命令：都能在手表上说明"这台机器现在什么样"，而且全都无副作用。
     * 自由输入留到有真实需求再加 —— 先保证点两下就能拿到可信输出。
     */
    val QuickCommands = listOf(
        "echo lux",
        "getprop ro.build.version.release",
        "uptime",
        "dumpsys battery | head -4",
    )

    /** 一条转录：> 命令名 + 输出（失败时带上 stderr）。 */
    fun renderEntry(command: String, result: ShellResult, cap: Int): String {
        val body = when {
            result.stdout.isNotBlank() -> result.stdout
            result.stderr.isNotBlank() -> result.stderr
            result.isSuccess -> "(无输出)"
            else -> "exit " + result.exitCode
        }
        val limit = cap.coerceAtLeast(200)
        val trimmed = if (body.length <= limit) body else body.take(limit) + "\n……[truncated " + (body.length - limit) + " chars]"
        return "❯ " + command + "\n" + trimmed.trimEnd()
    }

    /** 只留最近 [max] 条。 */
    fun trimEntries(entries: List<String>, max: Int = MAX_ENTRIES): List<String> =
        if (entries.size <= max) entries else entries.takeLast(max)
}
