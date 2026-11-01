package com.example.myapplication.presentation.ui

/**
 * 表冠刻度到列表位移的统一增益。
 *
 * 真表实测 12 格刻度只换来约 24px 位移（2px/格），滚一屏要转十几圈；而 [androidx.compose.ui.input.rotary.RotaryScrollEvent.verticalScrollPixels]
 * 本来就是"未处理的原始像素"，增益只能由消费方补。此值只放大"刻度 -> 位移"这一段：
 * 震动门限仍按原始刻度判定，手感与震动密度都不跟着变。
 */
const val RotaryScrollGain = 3f

/** 把一次表冠刻度的原始位移换算成列表实际要滚的像素。 */
fun rotaryScrollDelta(rawPixels: Float): Float = rawPixels * RotaryScrollGain
