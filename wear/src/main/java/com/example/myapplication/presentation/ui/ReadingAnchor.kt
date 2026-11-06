package com.example.myapplication.presentation.ui

import kotlin.math.max

/**
 * 镜头跟随的唯一纯逻辑入口：把"正在读的那一行"钉在"阅读带"里。
 *
 * 为什么不是"对齐一次 + 恒速下滚"：提词器只有两个动作（先对齐、再匀速推），
 * 任何一次布局变化都会让锚点离开原位，而它没有第二次纠错的机会 —— 真机上表现为
 * 点完按钮回答开始不跟随、思考胶囊不在看的区域、工具胶囊一出现镜头就撒手。
 * 这里改成每帧重新判定：锚点在带内就一动不动，出界才用同一个限速器拉回来，
 * 于是工具胶囊插入、思考胶囊折叠、底栏形变、逐字吐字这四类变化走的是同一条路径。
 */
object ReadingAnchor {

    /** 阅读带下沿与底栏之间留的一条缝（dp）：正文紧贴按钮会读成一整块。 */
    const val BAND_BOTTOM_GAP_DP = 8f

    /** 锚点这一帧该往哪边走。ContentUp = 正文上移（等价于 dispatchRawDelta 给正值）。 */
    enum class Move { Hold, ContentUp, ContentDown }

    /** 判定结果：[deltaPx] 带符号，正 = 正文上移（把下方内容读进带内）。 */
    data class Plan(val move: Move, val deltaPx: Float) {
        val isHold: Boolean get() = move == Move.Hold

        companion object {
            val Hold = Plan(Move.Hold, 0f)
        }
    }

    /** 阅读带上沿（px）：顶部曲面时钟占位 + 一点点余量。 */
    fun bandTopPx(topInsetPx: Float, marginPx: Float): Float = topInsetPx + marginPx

    /** 阅读带下沿（px）：视口高 − 底栏实际占用 − 底缝。底栏一形变，它立刻跟着变。 */
    fun bandBottomPx(viewportHeightPx: Float, barFootprintPx: Float, gapPx: Float): Float =
        viewportHeightPx - barFootprintPx - gapPx

    /**
     * 锚点 vs 阅读带的判定。
     *
     * - 锚点低于带下沿（内容被按钮/屏边吃掉）→ 正文上移，位移恰好等于超出量；
     * - 锚点高于带上沿（内容被顶到时钟底下）→ 正文下移，位移为负；
     * - 差量落在死区内 → 不滚（layoutInfo 反推的位置永远有几像素抖动）；
     * - 列表已经顶到没法再往那个方向滚 → 不滚，绝不空转抢镜头。
     *
     * [bandBottomPx] 与 [bandTopPx] 越界（底栏比屏幕还高这种退化输入）时按一条线处理，
     * 不让两条边互相打架。
     */
    fun plan(
        anchorBottomPx: Float,
        bandTopPx: Float,
        bandBottomPx: Float,
        deadZonePx: Float,
        canScrollForward: Boolean,
        canScrollBackward: Boolean
    ): Plan {
        val dead = deadZonePx.coerceAtLeast(0f)
        val bottom = max(bandBottomPx, bandTopPx)
        if (anchorBottomPx > bottom + dead) {
            return if (canScrollForward) Plan(Move.ContentUp, anchorBottomPx - bottom) else Plan.Hold
        }
        if (anchorBottomPx < bandTopPx - dead) {
            return if (canScrollBackward) Plan(Move.ContentDown, anchorBottomPx - bandTopPx) else Plan.Hold
        }
        return Plan.Hold
    }

    /**
     * 让路：用户正在拖/转表冠，或刚滚完还在暂停窗口内。
     * 这些时刻镜头一律不动 —— 谁在动手指，谁就是当前方向。
     */
    fun shouldYield(isScrollInProgress: Boolean, nowMs: Long, pauseUntilMs: Long): Boolean =
        isScrollInProgress || nowMs < pauseUntilMs
}
