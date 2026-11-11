package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ui.ReadingAnchor
import kotlin.math.max

/**
 * 聊天列表跟随滚动的唯一策略（纯逻辑，无 Android 依赖，可单测）。
 *
 * 真机逐帧证据（59fps 录制解出来的纵向位移 Δcy）：一次 243px 的自动滚动里 Δ 是
 * 9.5 / 16.5 / 22.5 / 31 / 36.5 / 40.5 / **19.5 / 32 / 12.5 / 22** —— 减速不单调。
 * 这一串不可能出自限速器：CameraCurve.ACCEL 每帧最多改 6000 × 0.0169 ≈ 101px/s（≈1.7px），
 * 而 40.5 → 19.5 一步就是 1240px/s。问题在**目标本身**：
 * 锚点取的是回合卡片的**底边**，而这条边正是流式正文每 32ms 提交一次就生长、
 * 离开中线后又被 ScalingLazyColumn 按缩放收回去的那条边 —— 逐帧"剩余距离"在 ±20px 内来回跳，
 * 而单帧位移被 stepPx 夹到剩余距离，夹取量就是肉眼看到的硬跳。
 *
 * 所以策略只收在三件事上：
 *  1. 目标只在**收缩方向**限速（[limitTarget]）：增长（新内容出现）立刻承认，收缩走斜坡，
 *     于是单帧位移单调收敛，不会被一个跳变的目标直接夹断；
 *  2. 追加只登记「内容又长了」，由镜头每帧取一次（[FollowRequests]）：**同一帧内**连续 50 次
 *     追加只落成 1 条目标更新（跨帧不合并，每帧各一条），不会每 token 起一次动画；
 *  3. 该不该跟随只有一个判据（[shouldFollow]）：用户手势优先，且未处于沉浸态。
 */
object ScrollFollow {

    /** 目标每秒最多收缩多少像素：340px/s ≈ 60Hz 下 5.7px/帧，小于任何人眼可分辨的跳变。 */
    const val TARGET_SHRINK_PX_PER_SEC = 340f

    /** 这一帧允许目标收缩多少像素（[dtSeconds] 为帧间隔，退化输入一律给 0）。 */
    fun maxShrinkPx(dtSeconds: Float, ratePerSec: Float = TARGET_SHRINK_PX_PER_SEC): Float =
        ratePerSec.coerceAtLeast(0f) * dtSeconds.coerceAtLeast(0f)

    /**
     * 目标位置限速：变大立刻承认，变小每帧最多 [maxShrinkPx]。
     *
     * 只限"收缩"方向是刻意的。新内容出现时目标变大，单帧位移仍被速度上限
     * （[com.example.myapplication.presentation.ui.CameraCurve.targetSpeed]）夹住，本来就不会跳；
     * 而目标突然变小会把同一个速度直接夹成小位移，那就是"急停一下再走"。
     * 限了收缩方向，减速才是一条单调斜坡。
     */
    fun limitTarget(previousPx: Float, nextPx: Float, maxShrinkPx: Float): Float {
        if (nextPx >= previousPx) return nextPx
        return max(nextPx, previousPx - maxShrinkPx.coerceAtLeast(0f))
    }

    /**
     * 这一帧该不该让镜头动列表。四个条件同时成立才允许，缺一个就一帧都不动：
     *  - [followEnabled]：本轮已被授权跟随（用户原本就在底部，且没被自己的手势叫停）；
     *  - 用户没在拖/转表冠（[userScrolling]）—— 谁在动手指，谁就是当前方向；
     *  - 不在用户手势后的让路窗口里（[yieldUntilMs]）；
     *  - 不处于沉浸态（审批/录音/输入弹窗/抽屉）。
     */
    fun shouldFollow(
        followEnabled: Boolean,
        userScrolling: Boolean,
        nowMs: Long,
        yieldUntilMs: Long,
        immersive: Boolean
    ): Boolean = followEnabled &&
        !immersive &&
        !ReadingAnchor.shouldYield(userScrolling, nowMs, yieldUntilMs)

    /**
     * 追加请求的一格邮箱（合并器）。
     *
     * 流式正文每 32ms 提交一次，一帧里可能落好几次"内容又长了"。这里只留一格：
     * 置位期间再来多少次都合并成同一条，[take] 一帧取一次，于是
     * **同一帧内连续 50 次追加只产生 1 条滚动目标更新**（跨帧不合并：十帧十条，见 ScrollFollowTest）。
     * 不做队列是刻意的：滚动的目标位置每帧从 layoutInfo 现算，历史请求没有任何意义。
     */
    class FollowRequests {

        private var pending: Boolean = false

        /** 是否还有未被取走的请求。 */
        val hasPending: Boolean get() = pending

        /** 登记一次追加。已在置位时不改变任何状态 —— 这就是合并。 */
        fun request() {
            pending = true
        }

        /** 取走请求：只有真正从"有"变"无"的那一次返回 true。 */
        fun take(): Boolean {
            if (!pending) return false
            pending = false
            return true
        }

        /** 丢弃未取走的请求（回合切换时用：上一回合的追加不该在新回合补滚一次）。 */
        fun clear() {
            pending = false
        }
    }
}
