package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingParams

/**
 * 设置页与官方 M3E 圆屏列表共用同一份曲率参数。
 *
 * 不再写死 edgeScale / edgeAlpha：外观、缩放曲线、过渡区和视口偏移全部交给
 * [ScalingLazyColumnDefaults.scalingParams]，库升级后与官方 Wear 观感一起更新。
 * 根页和所有子页必须使用这一份实例，避免同一屏出现两种边缘缩放。
 */
@Composable
fun settingsScalingParams(): ScalingParams = remember {
    ScalingLazyColumnDefaults.scalingParams()
}
