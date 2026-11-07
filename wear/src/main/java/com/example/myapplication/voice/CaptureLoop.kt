package com.example.myapplication.voice

/**
 * 录音循环的退出判定。真实录音无法在 JVM 上复现，把"何时继续读、何时退出"抽成纯函数，
 * 让两条优先级规则可以被单测固定住：取消压过失常，取消也压过提前收尾。
 */
internal object CaptureLoop {

    enum class Decision { CONTINUE, CANCELLED, READ_ERROR, FINISH }

    fun decide(active: Boolean, finish: Boolean, read: Int): Decision = when {
        !active -> Decision.CANCELLED
        finish -> Decision.FINISH
        read < 0 -> Decision.READ_ERROR
        else -> Decision.CONTINUE
    }
}
