package com.example.myapplication.agent.mcp

/**
 * 远端工具名 -> 本地工具名。
 *
 * 前缀 mcp_ 是来源标记：模型一眼能看出这个工具来自远端 MCP 服务，
 * 同时前缀也把远端命名空间和内置工具（get_device_status / run_shell ...）隔开。
 * 远端名可能含点、斜杠、大写，这里逐字符清洗成 [a-z0-9_]，
 * 再按"已被占用的名字"补序号，保证同一份清单内不撞名。
 */
object McpToolNaming {

    const val PREFIX = "mcp_"

    /** OpenAI / Anthropic 的函数名上限是 64 个字符。 */
    const val MAX_LENGTH = 64

    private const val MAX_SUFFIX = 100

    fun isMcpToolName(name: String): Boolean = name.startsWith(PREFIX)

    fun sanitize(remoteName: String): String {
        val builder = StringBuilder()
        for (char in remoteName.lowercase()) {
            val kept = char in 'a'..'z' || char in '0'..'9' || char == '_'
            builder.append(if (kept) char else '_')
        }
        val cleaned = builder.toString().trim('_')
        val usable = if (cleaned.isEmpty()) "tool" else cleaned
        return usable.take(MAX_LENGTH - PREFIX.length)
    }

    fun localName(remoteName: String, taken: Set<String>): String {
        val base = PREFIX + sanitize(remoteName)
        if (base !in taken) return base
        var index = 2
        while (index <= MAX_SUFFIX) {
            val suffix = "_" + index
            val candidate = base.take(MAX_LENGTH - suffix.length) + suffix
            if (candidate !in taken) return candidate
            index += 1
        }
        return base
    }
}
