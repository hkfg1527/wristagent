package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingParams

/**
 * 设置页滚动缩放的取值：缩放与淡出全部钉在 1。
 *
 * 库默认边缘行缩到 0.75 并淡出，行随滚动位置变大变小 —— 真机 uiautomator 量到同一子页两条
 * 同文案节点的 254x45 px 与 236x41 px（差 7.6% / 9.8%），手指快速滑动时就是"行随着位置缩放"。
 * 设置页要的是"同一行在任何滚动位置尺寸一致"，所以这两个系数必须是 1（恒等变换）。
 */
internal object SettingsScalingMath {
    /** 边缘行缩放倍数：1 = 不缩放。 */
    const val EdgeScale = 1f

    /** 边缘行不透明度：1 = 不淡出。 */
    const val EdgeAlpha = 1f
}

/**
 * 关掉曲率缩放的唯一实现：根页与所有子页共用同一份 [ScalingParams]。
 *
 * remember 住是因为 [ScalingLazyColumn] 会按参数是否变化决定要不要重新测量列表。
 */
@Composable
fun settingsScalingParams(): ScalingParams = remember {
    ScalingLazyColumnDefaults.scalingParams(
        edgeScale = SettingsScalingMath.EdgeScale,
        edgeAlpha = SettingsScalingMath.EdgeAlpha
    )
}
