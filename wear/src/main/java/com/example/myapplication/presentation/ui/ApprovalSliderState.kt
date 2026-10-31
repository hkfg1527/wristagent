package com.example.myapplication.presentation.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.abs

/**
 * 审批滑块的纯逻辑：进度换算、红→绿插值、放行阈值、单击容差与文案切换。
 * 与 Compose 运行时无关，便于 JVM 单测覆盖边界。
 */
object ApprovalSliderLogic {
    /** 松手放行阈值：progress 达到该值即视为放行。 */
    const val ALLOW_THRESHOLD = 0.85f

    /** 文案切换点：progress 低于该值提示"滑向放行"，达到即提示"松开即放行"。 */
    const val LABEL_SWITCH_PROGRESS = 0.5f

    /**
     * 单击容差的设计值 22（对应 22dp）：横纵位移都落在容差内即为"点了一下"，也就是拒绝。
     * UI 层按本机密度把 22dp 换算成 px 后传入 [isRejectTap]；纯逻辑默认取该值。
     */
    const val TAP_SLOP_PX = 22f

    /**
     * 拇指自右端向左拖动的完成度。
     * 可拖行程 = 轨道宽 - 拇指宽；行程为 0（轨道不比拇指宽）时固定 0，避免除零产生 NaN。
     */
    fun progress(startX: Float, currentX: Float, trackWidthPx: Float, thumbWidthPx: Float): Float {
        val travelPx = trackWidthPx - thumbWidthPx
        if (travelPx <= 0f) return 0f
        return ((startX - currentX) / travelPx).coerceIn(0f, 1f)
    }

    /** 拒绝红 → 放行绿的连续插值：progress 0 为 [Wear.Palette.Danger]，1 为 [Wear.Palette.Success]。 */
    fun colorFor(progress: Float): Color =
        lerp(Wear.Palette.Danger, Wear.Palette.Success, progress.coerceIn(0f, 1f))

    /** 松手时是否构成放行决定。 */
    fun shouldAllow(progress: Float): Boolean = progress >= ALLOW_THRESHOLD

    /**
     * 松手时是否构成"单击拒绝"：横纵位移都在容差内（按下→抬起没有真正滑动）。
     * 一旦任一方向越过容差就是滑动，交由放行阈值处理，不再算作拒绝。
     */
    fun isRejectTap(totalDxPx: Float, totalDyPx: Float, slopPx: Float = TAP_SLOP_PX): Boolean =
        abs(totalDxPx) <= slopPx && abs(totalDyPx) <= slopPx

    /** 胶囊内左侧的操作提示：锁定倒计时优先，其次在"点拒绝 · 左滑放行 / 松开即放行"之间切换。 */
    fun labelFor(progress: Float, isLocked: Boolean = false): String = when {
        isLocked -> "锁定"
        progress < LABEL_SWITCH_PROGRESS -> "点拒绝 · 左滑放行"
        else -> "松开即放行"
    }
}
