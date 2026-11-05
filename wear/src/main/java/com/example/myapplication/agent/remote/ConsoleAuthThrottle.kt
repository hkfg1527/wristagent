package com.example.myapplication.agent.remote

/**
 * 访问验证码的失败限流：连续 5 次错误锁定 60 秒。
 *
 * 4 位数字只有 10000 种可能，没有限流的话同一 Wi-Fi 下的脚本几秒就能撞开，
 * 而控制台能改 API Key、能跑 run_shell —— 所以限流是 4 位验证码的配套前提，不是可选项。
 * 纯逻辑、无系统依赖，时间由调用方传入，便于单测。
 */
class ConsoleAuthThrottle(
    private val maxFailures: Int = MAX_FAILURES,
    private val lockDurationMs: Long = LOCK_DURATION_MS,
) {

    private var failures: Int = 0
    private var lockedUntilMs: Long = 0L

    @Synchronized
    fun isLocked(nowMs: Long): Boolean = nowMs < lockedUntilMs

    @Synchronized
    fun remainingLockMs(nowMs: Long): Long = (lockedUntilMs - nowMs).coerceAtLeast(0L)

    @Synchronized
    fun onFailure(nowMs: Long) {
        failures += 1
        if (failures >= maxFailures) {
            lockedUntilMs = nowMs + lockDurationMs
            failures = 0
        }
    }

    @Synchronized
    fun onSuccess() {
        failures = 0
        lockedUntilMs = 0L
    }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_DURATION_MS = 60_000L
    }
}
