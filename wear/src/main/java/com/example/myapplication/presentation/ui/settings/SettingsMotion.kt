package com.example.myapplication.presentation.ui.settings

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 动画特效总开关的唯一读取入口。
 *
 * 全应用只有 AgentPreferences.animationsEnabled 一个真值，由聊天页根容器与设置页把它投进这里；
 * 点阵、底栏、审批浮层、流式光标都只认这一个 local，不再各自持有开关，
 * 避免出现"点阵停了按钮还在弹"的半开状态。
 *
 * 默认 true：没被 provider 包住的预览/测试保持原有动效。
 */
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

/**
 * 开关关闭时的唯一降级手段：把调用方原本的弹性规格换成 snap，时长归零、直接落到目标值。
 *
 * 之所以不写死静态值，是因为这些动画的终点由布局算出来（胶囊宽度、录音缩放、滑块进度），
 * 写死静态值会在切换瞬间留下尺寸错位或残影。
 */
fun <T> motionSpec(enabled: Boolean, animated: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (enabled) animated else snap()
