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

    /**
     * 输入的原始文本 → 可执行命令。空串与纯空白一律返回 null（不发空命令给 shell）。
     * 只做 trim：不替换引号、不拆管道 —— 那是 shell 的事，替用户改写命令比不执行更糟。
     */
    fun parseCommand(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() }

    /** 命令历史最多留几条（与转录分开计数：转录是输出，历史是输入）。 */
    const val MAX_HISTORY = 20

    /**
     * 追加一条历史。**与最后一条相同则不重复记** ——
     * 连点两次同一条命令是常见操作，历史里堆十条一样的没有信息量。
     */
    fun appendHistory(
        history: List<String>,
        command: String,
        max: Int = MAX_HISTORY
    ): List<String> {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return history
        if (history.lastOrNull() == trimmed) return history
        return (history + trimmed).takeLast(max.coerceAtLeast(1))
    }

    /** 快捷位上限：太多会把这页变成第二个设置页。 */
    const val MAX_SAVED = 6

    /** 落盘的快捷位是"换行分隔的命令"：可读、可手改、天然去重。 */
    fun savedOf(raw: String): List<String> =
        raw.lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * 添加一个快捷位。已存在就不重复加（顺序保持用户添加顺序），超上限丢最旧的。
     * 与 [appendHistory] 同一套"重复不算数"的直觉，避免同一个命令在界面上出现两次。
     */
    fun withSaved(raw: String, command: String, max: Int = MAX_SAVED): String {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return raw
        val list = savedOf(raw)
        if (list.contains(trimmed)) return raw
        return (list + trimmed).takeLast(max.coerceAtLeast(1)).joinToString("\n")
    }

    /** 删除一个快捷位；不存在时原样返回。 */
    fun withoutSaved(raw: String, command: String): String =
        savedOf(raw).filterNot { it == command.trim() }.joinToString("\n")

    /** 只留最近 [max] 条。 */
    fun trimEntries(entries: List<String>, max: Int = MAX_ENTRIES): List<String> =
        if (entries.size <= max) entries else entries.takeLast(max)
}
