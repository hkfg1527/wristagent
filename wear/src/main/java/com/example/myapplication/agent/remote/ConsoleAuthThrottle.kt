package com.example.myapplication.agent.remote

/**
 * 访问验证码的失败限流：同一个来源连续 5 次错误锁定 60 秒。
 *
 * 4 位数字只有 10000 种可能，没有限流的话同一 Wi-Fi 下的脚本几秒就能撞开，
 * 而控制台能改 API Key、能跑 run_shell —— 所以限流是 4 位验证码的配套前提，不是可选项。
 *
 * 计数按来源分桶：全局锁会让局域网里任意一台机器用 5 个错码把合法用户关在门外，
 * 那本身就是个 DoS。桶表有上界，伪造大量来源也不会把内存撑爆。
 *
 * 纯逻辑、无系统依赖，时间由调用方传入，便于单测。
 */
class ConsoleAuthThrottle(
    private val maxFailures: Int = MAX_FAILURES,
    private val lockDurationMs: Long = LOCK_DURATION_MS,
    private val maxPeers: Int = MAX_PEERS,
) {

    private class PeerState {
        var failures: Int = 0
        var lockedUntilMs: Long = 0L
    }

    private val peers = LinkedHashMap<String, PeerState>(16, 0.75f, true)

    @Synchronized
    fun isLocked(nowMs: Long): Boolean = isLocked(nowMs, DEFAULT_PEER)

    @Synchronized
    fun isLocked(nowMs: Long, peer: String): Boolean = nowMs < state(peer).lockedUntilMs

    @Synchronized
    fun remainingLockMs(nowMs: Long): Long = remainingLockMs(nowMs, DEFAULT_PEER)

    @Synchronized
    fun remainingLockMs(nowMs: Long, peer: String): Long =
        (state(peer).lockedUntilMs - nowMs).coerceAtLeast(0L)

    @Synchronized
    fun onFailure(nowMs: Long) = onFailure(nowMs, DEFAULT_PEER)

    @Synchronized
    fun onFailure(nowMs: Long, peer: String) {
        val entry = state(peer)
        entry.failures += 1
        if (entry.failures >= maxFailures) {
            entry.lockedUntilMs = nowMs + lockDurationMs
            entry.failures = 0
        }
    }

    @Synchronized
    fun onSuccess() = onSuccess(DEFAULT_PEER)

    @Synchronized
    fun onSuccess(peer: String) {
        val entry = state(peer)
        entry.failures = 0
        entry.lockedUntilMs = 0L
    }

    /** 当前跟踪的来源数，供单测断言上界。 */
    @Synchronized
    fun trackedPeers(): Int = peers.size

    private fun state(peer: String): PeerState {
        val key = peer.trim().ifEmpty { DEFAULT_PEER }
        val existing = peers[key]
        if (existing != null) return existing
        while (peers.size >= maxPeers) {
            val oldest = peers.keys.firstOrNull() ?: break
            peers.remove(oldest)
        }
        val fresh = PeerState()
        peers[key] = fresh
        return fresh
    }

    companion object {
        const val MAX_FAILURES = 5
        const val LOCK_DURATION_MS = 60_000L

        /** 桶表上界：一个局域网不会有 128 个合法来源。 */
        const val MAX_PEERS = 128

        const val DEFAULT_PEER = ""
    }
}
