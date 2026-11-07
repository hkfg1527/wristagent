package com.example.myapplication.agent.session

import com.example.myapplication.agent.ChatSession
import java.io.File

/**
 * 会话落盘。写在 filesDir 下，进程重启后据此恢复上次对话。
 * 写失败一律吞掉：存不下会话是遗憾，崩掉整个聊天是灾难。
 */
class SessionStore(private val file: File) {

    fun load(): SessionSnapshot? = try {
        if (file.exists()) SessionCodec.decode(file.readText()) else null
    } catch (t: Throwable) {
        null
    }

    fun save(sessions: List<ChatSession>, activeSessionId: String) {
        try {
            file.parentFile?.mkdirs()
            file.writeText(SessionCodec.encode(sessions, activeSessionId))
        } catch (_: Throwable) {
        }
    }
}
