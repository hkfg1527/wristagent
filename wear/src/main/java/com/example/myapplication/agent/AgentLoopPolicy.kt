package com.example.myapplication.agent

/** 空转检测的判定结果：继续、先提示一次、收口停手。 */
enum class IdleSpinDecision { CONTINUE, NUDGE, STOP }

/**
 * 工具轮数策略。轮数没有上限（主人明确要求），防烧电只靠三件事：
 * 1) 取消：顶层 isCancelled 置位后循环立刻退出；
 * 2) 能量提示：连续工具轮达到 [ENERGY_HINT_AFTER_TURNS] 时注入一次"立即总结"提示，不做硬中断；
 * 3) 空转检测：连续重复同一份调用先提示、再收口，避免"无上限"退化成真死循环。
 */
object AgentLoopPolicy {
    /** 连续工具轮数的软阈值：到这里注入一次收尾提示。 */
    const val ENERGY_HINT_AFTER_TURNS = 40

    /** 同一份调用连续重复到这一轮先注入一次"别再重复"提示。 */
    const val IDLE_SPIN_NUDGE_AFTER = 3

    /** 提示之后仍然重复到这一轮才收口：这是空转，不是轮数上限。 */
    const val IDLE_SPIN_STOP_AFTER = 5

    /** 设置项可调范围：2（激进）到 8（宽容）。 */
    const val MIN_IDLE_SPIN_STOP = 2
    const val MAX_IDLE_SPIN_STOP = 8

    fun shouldInjectEnergyHint(consecutiveToolTurns: Int, alreadyInjected: Boolean): Boolean =
        !alreadyInjected && consecutiveToolTurns >= ENERGY_HINT_AFTER_TURNS

    fun energyHintText(turns: Int): String =
        "已连续执行 " + turns + " 轮工具，请立即总结当前结果并结束。"

    /**
     * 空转判定。[stopAfter] 来自设置项 idle_spin_stop_after，夹在 2..8：
     * 小于 2 会在第二次相同调用就收口（正常重试也被打断），大于 8 等于放任死循环。
     */
    fun idleSpinDecision(
        consecutiveSameCall: Int,
        stopAfter: Int = IDLE_SPIN_STOP_AFTER,
        nudgeAfter: Int = IDLE_SPIN_NUDGE_AFTER
    ): IdleSpinDecision {
        val stop = stopAfter.coerceIn(MIN_IDLE_SPIN_STOP, MAX_IDLE_SPIN_STOP)
        // 提示永远落在停手前两轮：用户把收口调早/调晚时，提示跟着一起移动
        val nudge = if (stopAfter == IDLE_SPIN_STOP_AFTER) {
            nudgeAfter.coerceIn(2, stop)
        } else {
            (stop - (IDLE_SPIN_STOP_AFTER - IDLE_SPIN_NUDGE_AFTER)).coerceIn(2, stop)
        }
        return when {
            consecutiveSameCall >= stop -> IdleSpinDecision.STOP
            consecutiveSameCall >= nudge -> IdleSpinDecision.NUDGE
            else -> IdleSpinDecision.CONTINUE
        }
    }

    fun idleSpinText(toolName: String, repeats: Int): String =
        "同一份调用 [" + toolName + "] 已连续重复 " + repeats +
            " 次且入参完全一致，结果不会因为再调一次而变化：请立刻换策略或直接总结收尾。"

    /** 工具调用的"同一份"签名：名字与入参都相同才算同一次调用。 */
    fun signatureOf(calls: List<ToolCall>): String =
        calls.joinToString("|") { it.name + "#" + it.arguments }
}

/** 空转检测器：只追踪"连续同一份调用"，一旦换了调用立即归零。 */
class IdleSpinDetector(
    /** 空转收口阈值，来自设置项 idle_spin_stop_after。 */
    private val stopAfter: Int = AgentLoopPolicy.IDLE_SPIN_STOP_AFTER
) {
    private var lastSignature: String? = null
    private var repeats = 0

    fun observe(signature: String): IdleSpinDecision {
        if (signature == lastSignature) {
            repeats += 1
        } else {
            lastSignature = signature
            repeats = 1
        }
        return AgentLoopPolicy.idleSpinDecision(repeats, stopAfter = stopAfter)
    }

    fun reset() {
        lastSignature = null
        repeats = 0
    }
}
