package com.example.myapplication.voice

/**
 * 录音循环的退出判定。真实录音无法在 JVM 上复现，把"何时继续读、何时退出"抽成纯函数，
 * 让取消优先于读错误这条规则可以被单测固定住。
 */
internal object CaptureLoop {

    enum class Decision { CONTINUE, CANCELLED, READ_ERROR }

    fun decide(active: Boolean, read: Int): Decision = when {
        !active -> Decision.CANCELLED
        read < 0 -> Decision.READ_ERROR
        else -> Decision.CONTINUE
    }
}
