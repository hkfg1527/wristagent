package com.example.myapplication.presentation.ui

/** 底栏按钮形态：完整按钮 / 收起成小白条。 */
enum class BottomBarMode { Expanded, Collapsed }

/** 底栏可见性的输入事件。 */
enum class BottomBarEvent {
    /** 用户开始滚动（表冠刻度或手指拖拽）。 */
    UserScroll,

    /** 点击收起态的小白条。 */
    StripTap,

    /**
     * 提词器自动下滚持续超过 [AUTO_COLLAPSE_DELAY_MS] 且用户没有任何操作。
     * 此时说明用户在专心读，按钮就应该让位收起；3 秒之内仍保持按钮可见（给"想自己动手"的人留窗口）。
     */
    AutoPanQuiet
}

/** 新回合进来后，多久没人动就自动收起底栏。 */
const val AUTO_COLLAPSE_DELAY_MS = 3000L

/** 用户点弧条唤醒按钮后的"挂起期"：这段时间内滚动/静默都不能再把它收起来。 */
const val WAKE_LATCH_MS = 3000L

/**
 * 底栏可见性状态机（纯逻辑，可单测）。
 *
 * 用户拍板的规则：
 *  - 回复完成后按钮正常展开，**不存在按空闲时间自动收起**；
 *  - 用户一旦开始滚动（表冠或手指）就收起成小白条；
 *  - 收起之后**只有点击小白条**才恢复按钮，不自动展开；
 *  - 强制态（执行中、审批、录音、输入弹窗、抽屉）一律强制展开且不可收起。
 */
object BottomBarVisibility {

    /**
     * 一次状态迁移。[current] 是迁移起点，[event] 是本次输入，[forceExpanded] 表示当前是否处于强制态。
     * 映射是全定义的：强制态吞掉一切事件，否则滚动只收、点条只开（幂等，不会来回抖）。
     */
    /**
     * 挂起期内（用户刚点弧条唤醒）任何"自动收起"都要被否决。
     * 否则用户刚把按钮叫回来、手一碰就被收走，等于白点。
     */
    fun collapseAllowed(nowMs: Long, wakeLatchUntilMs: Long): Boolean = nowMs >= wakeLatchUntilMs

    /**
     * 新回合到达后是否该因为"3 秒没人动"而自动收起：
     *  - 必须过了挂起期；
     *  - 且这 3 秒内用户没有滚动过（滚过就说明他正在读，别抢）。
     */
    fun shouldAutoCollapseAfterTurn(
        nowMs: Long,
        turnStartMs: Long,
        wakeLatchUntilMs: Long,
        lastUserScrollMs: Long,
        forceExpanded: Boolean
    ): Boolean {
        if (forceExpanded) return false
        if (!collapseAllowed(nowMs, wakeLatchUntilMs)) return false
        if (nowMs - turnStartMs < AUTO_COLLAPSE_DELAY_MS) return false
        return lastUserScrollMs < turnStartMs
    }

    /**
     * 用户滚动时是否允许收起底栏。
     *
     * 关键一条：**已经滚到底部就不许收**。底部的语义是"读到最新了、随时要操作"，
     * 此处收走按钮会让用户既看不到内容下方留白、又找不到入口；
     * 而且"滚到底"本身就是要恢复按钮的信号（见 [shouldRestoreAtBottom]）。
     */
    fun shouldCollapseOnScroll(nowMs: Long, wakeLatchUntilMs: Long, atBottom: Boolean): Boolean {
        if (atBottom) return false
        return collapseAllowed(nowMs, wakeLatchUntilMs)
    }

    /**
     * 是否应该因为"到达底部"而恢复按钮：到底了就恢复，无条件。
     * 与 [shouldRestoreAtOutputEnd] 同义（一个由用户滚动触发，一个由提词器触发），
     * 都只约束"恢复"方向，不受唤醒挂起期影响。
     */
    fun shouldRestoreAtBottom(atBottom: Boolean): Boolean = atBottom

    /**
     * 提词器滚到输出结尾后，按钮是否应该自动回来。
     * 结论是**无条件恢复**：此刻正文下方刚好空出位置，且"读完了"就是最强的操作意图信号。
     * 注意它与 [collapseAllowed] 无关 —— 挂起期只约束"收起"，从不约束"恢复"。
     */
    fun shouldRestoreAtOutputEnd(): Boolean = true

    fun next(current: BottomBarMode, event: BottomBarEvent, forceExpanded: Boolean): BottomBarMode {
        if (forceExpanded) return BottomBarMode.Expanded
        return when (event) {
            BottomBarEvent.UserScroll -> BottomBarMode.Collapsed
            BottomBarEvent.AutoPanQuiet -> BottomBarMode.Collapsed
            BottomBarEvent.StripTap -> BottomBarMode.Expanded
        }
    }
}
