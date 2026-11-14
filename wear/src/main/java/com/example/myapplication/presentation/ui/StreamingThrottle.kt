package com.example.myapplication.presentation.ui

/**
 * 流式刷新节流（纯逻辑，无 Android 依赖，可单测）。
 *
 * 模型 token 的到达节奏远高于屏幕刷新：逐 token 提交状态会让整列正文每来一个字就重新测量排版，
 * 手表上表现为整块文字抖动、光标闪成噪点。这里把"提交 UI 更新"收敛成固定节奏（默认 70ms ≈ 12~16fps），
 * 并顺手判定一次提交是否属于"过快突发"，交给渲染侧关掉逐字动画。
 */
object StreamingThrottle {

    /**
     * 相邻两次 UI 更新的最小间隔的出厂默认值：32ms ≈ 30fps。
     * 太长（如 70ms）会让"卡片长高"和"列表跟随"之间出现肉眼可见的滞后，
     * 表现为文字被往下顶一下再跳回来；太快又会逐 token 重排导致抖动。
     *
     * 真实生效的窗口由设置项 `stream_emit_interval_ms` 决定
     * （[com.example.myapplication.agent.AgentPreferences.streamEmitIntervalMs]，夹在 8..200）。
     */
    const val DEFAULT_INTERVAL_MS = 32L

    /** 单次刷新增量超过该字数即视为"突发"（模型一次吐出很大一段），此时不做逐字动画。 */
    const val DEFAULT_CHARS_PER_EMIT_THRESHOLD = 12

    /**
     * 距上次提交是否已满 [intervalMs]。
     * [lastEmitMs] <= 0 表示还没有提交过：首帧必须立刻可见，否则开屏会空等一个窗口。
     * 时钟回拨（now < last）按"未满"处理，避免时间源异常导致疯狂刷新。
     */
    fun shouldEmit(lastEmitMs: Long, nowMs: Long, intervalMs: Long = DEFAULT_INTERVAL_MS): Boolean {
        if (lastEmitMs <= 0L || intervalMs <= 0L) return true
        return nowMs - lastEmitMs >= intervalMs
    }

    /**
     * 单次增量是否过大：只有"文本变长且增长超过 [charsPerEmitThreshold]"才算突发。
     * 文本变短（切会话、清屏、回退）或不变一律不算，避免误关光标动画。
     */
    fun isBurst(
        prevText: String,
        newText: String,
        charsPerEmitThreshold: Int = DEFAULT_CHARS_PER_EMIT_THRESHOLD
    ): Boolean {
        if (newText.length <= prevText.length) return false
        return newText.length - prevText.length > charsPerEmitThreshold
    }
}

/**
 * 节流状态机：记录"上一次真正提交到屏幕"的时刻与文本。
 * 被节流掉的更新不推进基线，因此 [isBurst] 衡量的始终是屏幕上可见的增量。
 */
class StreamingThrottleState(
    private var intervalMs: Long = StreamingThrottle.DEFAULT_INTERVAL_MS,
    private val charsPerEmitThreshold: Int = StreamingThrottle.DEFAULT_CHARS_PER_EMIT_THRESHOLD
) {
    var lastEmitMs: Long = 0L
        private set

    var lastText: String = ""
        private set

    /** 最近一次放行的更新是否为突发增量。 */
    var isBurst: Boolean = false
        private set

    /** 当前生效的合帧窗口：测试与设置页用它确认"偏好真的落到了节流器上"。 */
    val currentIntervalMs: Long get() = intervalMs

    /**
     * 换一个合帧窗口（毫秒）：设置项 `stream_emit_interval_ms` 的唯一落点。
     * 只换窗口、不动基线，因此改完设置不需要重建状态机，也不会凭空补放或吞掉一帧。
     */
    fun updateInterval(intervalMs: Long) {
        this.intervalMs = intervalMs
    }

    /**
     * 放行则记录本次时刻/文本/突发标记并返回 true；被节流则原样返回 false。
     * [force] 用于终态（一轮结束、报错）：终态必须立刻落地，不能被 70ms 窗口挡掉。
     */
    fun accept(newText: String, nowMs: Long, force: Boolean = false): Boolean {
        if (!force && !StreamingThrottle.shouldEmit(lastEmitMs, nowMs, intervalMs)) return false
        isBurst = StreamingThrottle.isBurst(lastText, newText, charsPerEmitThreshold)
        lastEmitMs = nowMs
        lastText = newText
        return true
    }

    /** 新一轮开始：清空基线，保证首帧立即可提交、突发判定从空文本起算。 */
    fun reset() {
        lastEmitMs = 0L
        lastText = ""
        isBurst = false
    }
}
