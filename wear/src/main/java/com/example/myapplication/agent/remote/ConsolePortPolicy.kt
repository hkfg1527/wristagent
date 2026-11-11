package com.example.myapplication.agent.remote

/** 端口回退策略：默认 8080，被占用时依次尝试后续端口。 */
object ConsolePortPolicy {

    const val DEFAULT_PORT = 8080
    const val MAX_ATTEMPTS = 3
    const val MIN_PORT = 1
    const val MAX_PORT = 65535

    val FALLBACK_PORTS: List<Int> = listOf(DEFAULT_PORT, DEFAULT_PORT + 1, DEFAULT_PORT + 2)

    /** 只保留 1..65535：0 是内核临时端口（绑上去了 port 会报 0，等于对外不可见），越界端口直接绑定失败。 */
    fun candidates(preferred: Int = DEFAULT_PORT, attempts: Int = MAX_ATTEMPTS): List<Int> =
        if (attempts <= 0) {
            emptyList()
        } else {
            List(attempts) { preferred + it }.filter { it in MIN_PORT..MAX_PORT }
        }

    /** 返回第一个可绑定的端口；全部占满返回 -1。 */
    fun select(preferred: Int = DEFAULT_PORT, attempts: Int = MAX_ATTEMPTS, canBind: (Int) -> Boolean): Int =
        candidates(preferred, attempts).firstOrNull(canBind) ?: -1
}
