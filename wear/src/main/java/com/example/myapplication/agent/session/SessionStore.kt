package com.example.myapplication.agent.session

import com.example.myapplication.agent.ChatSession
import java.io.File

/**
 * 会话落盘。写在 filesDir 下，进程重启后据此恢复上次对话。
 *
 * 读失败返回 null（等同于"第一次启动"，用户看到空对话，还能接着聊）；
 * 写失败必须交回调用方 —— 空 catch 的结局是会话**静默丢失**：这次没写进去，
 * 下次启动只能看到更早的一份快照，而没有任何人知道中间发生了什么。
 */
class SessionStore(private val file: File) {

    fun load(): SessionSnapshot? = try {
        if (file.exists()) SessionCodec.decode(file.readText()) else null
    } catch (t: Throwable) {
        null
    }

    /**
     * 落盘全部会话。失败不抛异常（崩掉聊天比丢一次写更糟），而是返回 [Result.failure] 带上原因，
     * 调用方据此提示用户或重试。**丢弃返回值就等于回到空 catch**，那正是这次要消灭的状态。
     */
    fun save(sessions: List<ChatSession>, activeSessionId: String): Result<Unit> = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(SessionCodec.encode(sessions, activeSessionId))
    }
}
