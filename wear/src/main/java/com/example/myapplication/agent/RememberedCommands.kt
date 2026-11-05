package com.example.myapplication.agent

import com.example.myapplication.agent.remote.ConsoleJson

/**
 * 会话级"记住此命令"清单的 JSON 编解码。
 *
 * 与自写工具同一套理由不用 org.json：android.jar 的桩会让 JVM 单测里的解析恒为默认值。
 * 键是 [ShellPolicy.normalize] 归一化后的命令原文，破坏性命令在写入前就被挡掉。
 */
object RememberedCommandCodec {
    private const val KeyCommands = "commands"

    fun encode(commands: List<String>): String {
        if (commands.isEmpty()) return ""
        return ConsoleJson.write(mapOf(KeyCommands to commands))
    }

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = ConsoleJson.parseObject(raw) ?: return emptyList()
        val entries = root[KeyCommands] as? List<*> ?: return emptyList()
        return entries.mapNotNull { entry ->
            (entry as? String)?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct()
    }
}
