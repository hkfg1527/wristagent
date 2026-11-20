package com.example.myapplication.presentation.ui.settingsv2.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import kotlinx.coroutines.delay

/**
 * V2 的三件动效参数：**入场逐行错峰**、**边缘淡出**、**边缘缩放**。
 *
 * 每套风格一份，互不相同（这是"每套像另一个 App"的一部分，不是全局统一节奏）。
 * 这里只放**纯参数与纯函数**，渲染侧自己决定怎么用；纯函数便于用判据钉住
 * "错峰真的是 index×stagger"、"缩放真的随距离单调递减且不小于下限"。
 */
data class V2Motion(
    /** 相邻两行入场的时间差。 */
    val staggerMs: Long,
    /** 单行入场时长。 */
    val enterMs: Int,
    /** 入场时的竖直位移（从下方浮上来）。 */
    val enterSlide: Dp,
    /** 贴到屏幕边缘时缩到多小（1f = 不缩）。 */
    val edgeScaleMin: Float,
    /** 贴到屏幕边缘时淡到多透明（1f = 不淡）。 */
    val edgeAlphaMin: Float,
)

object SettingsV2Motions {

    /** 五套的动效参数：数值互不相同，避免"十套风格只有一套动效"。 */
    fun of(style: SettingsV2Style): V2Motion = when (style) {
        SettingsV2Style.M3_CARD -> V2Motion(staggerMs = 40, enterMs = 260, enterSlide = 12.dp, edgeScaleMin = 0.90f, edgeAlphaMin = 0.55f)
        SettingsV2Style.M3_LIST -> V2Motion(staggerMs = 26, enterMs = 200, enterSlide = 8.dp, edgeScaleMin = 0.94f, edgeAlphaMin = 0.65f)
        SettingsV2Style.ARC -> V2Motion(staggerMs = 50, enterMs = 300, enterSlide = 16.dp, edgeScaleMin = 0.88f, edgeAlphaMin = 0.50f)
        SettingsV2Style.SQUARE -> V2Motion(staggerMs = 0, enterMs = 160, enterSlide = 4.dp, edgeScaleMin = 0.97f, edgeAlphaMin = 0.80f)
        SettingsV2Style.TERMINAL -> V2Motion(staggerMs = 18, enterMs = 120, enterSlide = 0.dp, edgeScaleMin = 1.00f, edgeAlphaMin = 0.90f)
    }

    /** 第 index 行开始入场的延迟：`index × staggerMs`。 */
    fun enterDelayMs(index: Int, motion: V2Motion): Long =
        (index.coerceAtLeast(0) * motion.staggerMs)

    /**
     * 边缘缩放：`distance` 是"离屏幕中心的比例"（0=正中，1=最边上）。
     * 结果从 1f 单调降到 [V2Motion.edgeScaleMin]，绝不小于下限。
     */
    fun edgeScale(distance: Float, motion: V2Motion): Float {
        val d = distance.coerceIn(0f, 1f)
        return 1f - (1f - motion.edgeScaleMin) * d
    }

    /** 边缘淡出：与缩放同构，从 1f 降到 [V2Motion.edgeAlphaMin]。 */
    fun edgeAlpha(distance: Float, motion: V2Motion): Float {
        val d = distance.coerceIn(0f, 1f)
        return 1f - (1f - motion.edgeAlphaMin) * d
    }
}

/**
 * 入场动画的进度：0f→1f。
 *
 * 用 `Animatable` 而不是 `animateFloatAsState`，是为了让判据能"等它真跑完"；
 * 延迟 = index × staggerMs，这就是"逐行错峰"的全部实现。
 */
@Composable
fun rememberEnterProgress(index: Int, motion: V2Motion): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(index, motion) {
        val delayMs = SettingsV2Motions.enterDelayMs(index, motion)
        if (delayMs > 0) delay(delayMs)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = motion.enterMs, easing = LinearOutSlowInEasing),
        )
    }
    return anim.value
}

/** 把"入场进度"用到一行上：透明度 + 竖直位移。 */
fun Modifier.enterAppearance(progress: Float, motion: V2Motion): Modifier = this.graphicsLayer {
    alpha = progress.coerceIn(0f, 1f)
    translationY = (1f - progress.coerceIn(0f, 1f)) * motion.enterSlide.toPx()
}

/** 把"边缘距离"用到一行上：等比缩放 + 淡出（两轴同值，**不做横向形变**）。 */
fun Modifier.edgeAppearance(distance: Float, motion: V2Motion): Modifier = this.graphicsLayer {
    val scale = SettingsV2Motions.edgeScale(distance, motion)
    scaleX = scale
    scaleY = scale
    alpha = SettingsV2Motions.edgeAlpha(distance, motion)
}
