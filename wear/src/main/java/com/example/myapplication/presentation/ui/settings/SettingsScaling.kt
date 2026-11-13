package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingParams

/**
 * 设置页的列表曲率：只保留官方淡出，缩放交给卡片自身的圆弦几何。
 *
 * 官方 `edgeScale` 会把整张矩形卡等比缩小，组内行宽不同步时会出现宽度台阶与方角。
 * 设置页的“中间宽、上下窄”由 [SettingsCurveGeometry] 按屏幕弦长重建卡片轮廓，
 * 因此这里必须把 `edgeScale` 固定为 1。
 * 逐行 `edgeAlpha` 也保持 1：相邻行若透明度不同，卡片相接处会露出半透明接缝；
 * 圆屏边缘的明暗交给统一 scrim，整组曲面因此是一块封合的面。
 */
@Composable
fun settingsScalingParams(): ScalingParams = remember {
    ScalingLazyColumnDefaults.scalingParams(
        edgeScale = 1f,
        edgeAlpha = 1f
    )
}

/**
 * 大卡片档的曲率：**官方 M3E 行为** —— 上下边缘等比缩小并淡出。
 *
 * 其余三套把 edgeScale 钉成 1，因为它们的轮廓是自绘的圆弦；只有大卡片档用官方 Card，
 * 该缩就缩，这才是谷歌表上"卡片从中间浮出、往边缘退去"的手感。
 */
@Composable
fun cardStyleScalingParams(): ScalingParams = remember {
    ScalingLazyColumnDefaults.scalingParams(
        edgeScale = 0.84f,
        edgeAlpha = 0.55f
    )
}
