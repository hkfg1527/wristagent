package com.example.myapplication.presentation.ui

import kotlin.math.abs

/**
 * 镜头（列表跟随）的速度曲线，纯逻辑可单测。
 *
 * 目标不是"固定速度跑到底"，而是：
 *  1. **速度由剩余距离决定**：离末端越远目标速度越快，越近越慢（远快近慢）；
 *  2. **加速有上限**：从静止起步时速度是渐进上去的（ease-in），不会一上来就窜；
 *  3. **减速是自然发生的**：接近末端时剩余距离变小 → 目标速度随之变小 → 自动减速（ease-out），
 *     而不是匀速冲到底再突然停住或瞬移。
 */
object CameraCurve {

    /** 每 1px 剩余距离对应的目标速度（px/s per px）。 */
    const val SPEED_PER_PX = 5.5f

    /** 最慢速度（px/s）：很近的距离也保持缓慢挪动，避免最后一像素抖动。 */
    const val MIN_SPEED = 45f

    /** 最快速度（px/s）：只有非常远的距离才会跑到这个上限。 */
    const val MAX_SPEED = 1400f

    /** 加速度上限（px/s²）：起步渐进的来源。 */
    const val ACCEL = 6000f

    /** 由剩余距离算目标速度：远快近慢，并夹在 [MIN_SPEED] 与 [MAX_SPEED] 之间。 */
    fun targetSpeed(remainingPx: Float): Float {
        if (remainingPx <= 0f) return 0f
        return (remainingPx * SPEED_PER_PX).coerceIn(MIN_SPEED, MAX_SPEED)
    }

    /** 当前速度向目标速度靠拢，每帧最多变化 [ACCEL] × dt（这就是 ease-in 与 ease-out 的实现）。 */
    fun nextSpeed(currentSpeed: Float, targetSpeed: Float, dtSeconds: Float): Float {
        if (dtSeconds <= 0f) return currentSpeed
        val maxDelta = ACCEL * dtSeconds
        val delta = targetSpeed - currentSpeed
        return if (abs(delta) <= maxDelta) targetSpeed else currentSpeed + maxDelta * if (delta > 0f) 1f else -1f
    }

    /** 这一帧该走多远：速度 × 时间，且绝不越过剩余距离（不会冲出去再弹回）。 */
    fun stepPx(speed: Float, dtSeconds: Float, remainingPx: Float): Float =
        (speed * dtSeconds).coerceIn(0f, remainingPx.coerceAtLeast(0f))

    /**
     * 剩余距离（px）：最后一条可见项的底部到"目标位置"（视口底部再减去底部留白）的距离。
     * Wear 的 ScalingLazyList 不提供内容总高，只能由 visibleItemsInfo 推。
     */
    fun remainingPx(
        lastItemOffsetPx: Float,
        lastItemSizePx: Float,
        viewportHeightPx: Float,
        bottomPaddingPx: Float
    ): Float {
        val contentBottom = lastItemOffsetPx + lastItemSizePx
        val targetBottom = viewportHeightPx - bottomPaddingPx
        return (contentBottom - targetBottom).coerceAtLeast(0f)
    }
}
