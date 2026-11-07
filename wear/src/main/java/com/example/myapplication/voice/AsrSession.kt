package com.example.myapplication.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 单次转写会话：持有转写 Job 与取消标志。
 * 取消必须同时做两件事——置位标志（抑制后续回调）与取消 Job（打断阻塞录音）。
 */
internal class AsrSession(private val scope: CoroutineScope) {

    private var job: Job? = null

    @Volatile
    private var cancelled = false

    val isCancelled: Boolean get() = cancelled

    val isRunning: Boolean get() = job?.isActive == true

    fun launch(block: suspend () -> Unit) {
        cancelled = false
        job = scope.launch { block() }
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }
}
