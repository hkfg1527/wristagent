package com.example.myapplication.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * 设置写入的变更信号。
 *
 * 网页控制台直接写 AgentPreferences，运行中的界面收不到通知——真机上表现为"网页把动画关了，
 * 点阵还在动，必须重启应用才生效"。写入成功后递增这个版本号，界面据此重读一次偏好。
 */
object SettingsChangeBus {

    private val counter = AtomicLong(0L)
    private val _revision = MutableStateFlow(0L)

    val revision: StateFlow<Long> = _revision.asStateFlow()

    fun bump() {
        _revision.value = counter.incrementAndGet()
    }
}
