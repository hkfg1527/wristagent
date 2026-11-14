package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingParams

/**
 * 设置页的列表曲率：只保留官方淡出，缩放交给卡片自身的圆弦几何。
 *
 * 官方 `edgeScale` 会把整张矩形卡等比缩小，组内行宽不同步时会出现宽度台阶与方角。
 * 设置页的「中间宽、上下窄」由 [SettingsCurveGeometry] 按屏幕弦长重建卡片轮廓，
 * 因此这里必须把 `edgeScale` 固定为 1。
 * 逐行 `edgeAlpha` 也保持 1：相邻行若透明度不同，卡片相接处会露出半透明接缝；
 * 圆屏边缘的明暗交给统一 scrim，整组曲面因此是一块封合的面。
 */
@Composable
fun settingsScalingParams(): ScalingParams = remember { flatScalingParams() }

/**
 * 大卡片档的曲率：**也是平坦的**。
 *
 * 边缘缩放由 [CardEdgeZoom] 在每一张卡上做（官方 0.70 / 0.50 与官方那条对称缓动）。
 * 交给库来做有两个代价，实测都成立：
 *
 * 1. **缩不动**：库的触发量是「项最远边到对侧屏幕边的距离」，而卡片档把全部分区行放在
 *    同一个 lazy item 里，那个 item 高 3000+ px，最远边一开始就在屏幕 20% 处，算出来只有百分之几；
 * 2. **白挂图层**：库给**每个 item** 挂一个 `graphicsLayer`，于是那块 3000+ px 的 item 变成
 *    一张 `432×3113` 的位图每帧重画一次（`atrace` 切片 `drawLayer [graphicsLayer] 432.0 x 3113.0`
 *    实测 15 次 / 160ms）。参数取平坦后库不再对 item 做变换，这层就没了。
 */
@Composable
fun cardStyleScalingParams(): ScalingParams = remember { flatScalingParams() }

/** 两套风格共用的平坦参数：只做布局，不把缩放/淡出交给库。 */
private fun flatScalingParams(): ScalingParams = ScalingLazyColumnDefaults.scalingParams(
    edgeScale = 1f,
    edgeAlpha = 1f
)
