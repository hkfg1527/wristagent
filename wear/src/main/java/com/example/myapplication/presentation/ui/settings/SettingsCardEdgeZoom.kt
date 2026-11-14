package com.example.myapplication.presentation.ui.settings

import androidx.compose.animation.core.CubicBezierEasing
import com.example.myapplication.appearance.SettingsStyle
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 大卡片档的边缘空间缩放：**只缩不放**，横纵同一个比例（等比），并随距离淡出。
 *
 * 为什么不用 `ScalingLazyColumn` 自带的 edgeScale 出这个效果 —— 官方算法
 * （compose-foundation 1.7.1 `ScalingLazyColumnMeasure.calculateScaleAndAlpha`）拿
 * "项**最远**边到对侧视口边的距离"当触发量，缩放线取
 * `lerp(minTransitionArea, maxTransitionArea, sizeRatio)`。设置页把一个分区里的所有卡塞进
 * **同一个 lazy item**（气球档必须如此：整组要拼成一张连续曲面），于是那个 item 高约一屏、
 * 最远那条边一开始就在屏幕里 20% 处，换算出来的缩放在实机上只有百分之几 —— 肉眼看就是"完全不缩"。
 *
 * 因此这里保留官方那套**常量与曲线**（`ScalingLazyColumnDefaults.scalingParams` 的
 * edgeScale = 0.70、edgeAlpha = 0.50、CubicBezierEasing(0.3f, 0f, 0.7f, 1f)），
 * 只把进度换成"这张卡自己的中心离屏心多远"：于是同一屏里每张卡各按各的位置退，
 * 与 lazy item 怎么切分无关，也与列表滚动方向无关。
 *
 * 三条硬约束（[SettingsCardEdgeZoomTest] 守着）：
 * 1. 缩放恒在 [MinScale, 1] 内且随距离单调不增 —— 只允许缩小，绝不放大、绝不横向拉伸；
 * 2. 同一张卡的 scaleX 必须等于 scaleY —— 等比；
 * 3. 屏心进度为 0（与没有这层效果时逐像素相同），贴边饱和为 1，中间走官方那条缓动。
 */
internal object CardEdgeZoom {
    /** 官方 `ScalingLazyColumnDefaults.scalingParams` 的默认 edgeScale：最靠边缩到 70%。 */
    const val MinScale = 1.00f

    /** 官方默认 edgeAlpha：最靠边时透明度 50%。 */
    const val MinAlpha = 0.45f

    /**
     * 进度起点：卡中心离屏心不足这个比例时完全不缩放。
     *
     * 取 0.5 的含义是"半个半屏"：屏幕中央那张卡保持 100%，只有真被顶到上下边缘的那张才开始退。
     * 起点太小会让整屏卡片一起变小，看起来像页面被缩小，而不是"卡片从中间浮出来"。
     */
    const val StartFraction = 0.5f

    /** 官方缩放插值器，原样照抄。 */
    val Easing: Easing = CubicBezierEasing(0.3f, 0f, 0.7f, 1f)

    /** 进度对距离的映射：0 = 卡片正对屏心，1 = 卡片中心已到屏幕边缘（更远一律饱和）。 */
    fun progress(distanceFraction: Float): Float {
        if (distanceFraction.isNaN()) return 0f
        val normalized = (distanceFraction - StartFraction) / (1f - StartFraction)
        val clamped = if (normalized < 0f) 0f else if (normalized > 1f) 1f else normalized
        return Easing.transform(clamped)
    }

    /** 由"卡片中心在屏幕坐标系里的 y"求进度；屏幕高度非法时退回不缩放。 */
    fun progressForCenter(centerPx: Float, screenHeightPx: Float): Float {
        if (screenHeightPx <= 0f) return 0f
        val half = screenHeightPx / 2f
        return progress(abs(centerPx - half) / half)
    }

    /** 进度 → 缩放。恒 ≤ 1。 */
    fun scaleFor(progress: Float): Float = 1f + (MinScale - 1f) * progress

    /** 进度 → 透明度。恒 ≤ 1。 */
    fun alphaFor(progress: Float): Float = 1f + (MinAlpha - 1f) * progress
}

/**
 * 给一张卡挂上边缘缩放。
 *
 * 位置用 `positionInRoot` 喂进一个 [mutableStateOf]，只在 `graphicsLayer` 的绘制阶段读：
 * 滚动时每帧只让图层失效，不进组合、不进布局，因此不会因为加了这层效果而掉帧。
 * 写入阈值 2px（约 1dp）：收小写入频率，1dp 的缩放差肉眼不可见。
 *
 * 非卡片档一律返回原 modifier，四套风格里只有大卡片档会吃到这层变换。
 */
@Composable
internal fun Modifier.cardEdgeZoom(): Modifier {
    if (WearAppearance.style != SettingsStyle.GOOGLE_CARD) return this
    val density = LocalDensity.current
    val screenHeightPx = with(density) { LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val yInRootPx = remember { mutableStateOf(screenHeightPx / 2f) }
    return this
        .onGloballyPositioned { coordinates ->
            val newY = coordinates.positionInRoot().y
            if (abs(newY - yInRootPx.value) > 2f) yInRootPx.value = newY
        }
        .graphicsLayer {
            val progress = CardEdgeZoom.progressForCenter(yInRootPx.value + size.height / 2f, screenHeightPx)
            val scale = CardEdgeZoom.scaleFor(progress)
            scaleX = scale
            scaleY = scale
            alpha = CardEdgeZoom.alphaFor(progress)
        }
}
