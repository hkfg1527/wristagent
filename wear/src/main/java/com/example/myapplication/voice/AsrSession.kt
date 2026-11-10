package com.example.myapplication.voice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 单次转写会话：持有转写 Job、取消标志与提前收尾标志。
 *
 * 三个动作语义不同，不能互相替代：
 * - [requestStop]：用户按停止 → 提前收尾，已录到的音频照常送去转写；
 * - [cancel]：丢弃这次采集 → 置位标志抑制回调，并取消 Job 打断阻塞读；
 * - 自然结束：录满窗口。
 */
internal class AsrSession(private val scope: CoroutineScope) {

    private var job: Job? = null

    @Volatile
    private var cancelled = false

    @Volatile
    private var stopRequested = false

    val isCancelled: Boolean get() = cancelled

    val isStopRequested: Boolean get() = stopRequested

    val isRunning: Boolean get() = job?.isActive == true

    fun launch(block: suspend () -> Unit) {
        // 重开必须先掐掉上一次的 Job：只覆盖引用的话，上一次的采集协程还活着，
        // 两条录音同时读麦克风，被丢弃那次的回调仍会把结果送上屏。
        job?.cancel()
        cancelled = false
        stopRequested = false
        job = scope.launch { block() }
    }

    fun requestStop() {
        stopRequested = true
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }
}
