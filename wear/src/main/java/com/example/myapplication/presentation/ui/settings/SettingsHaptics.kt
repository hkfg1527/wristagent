package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.example.myapplication.agent.AgentPreferences

/**
 * 设置页的触觉反馈：两档语义 + 一个按档位缩放的官方 primitive。
 *
 * 从 `SettingsShared.kt` 拆出来（那是 2081 行的上帝文件，2026-11-17 全量审查点名）。
 * 这三件事只依赖 `AgentPreferences` 与系统 Vibrator，与排版、卡片、行组件毫无关系，
 * 放在一起反而让"设置页长什么样"这个文件读起来更清爽。
 */

/** 滚动刻度 / 选项切换：一次轻"滴"。 */
fun Context.vibrateTick() {
    val preferences = AgentPreferences(this)
    if (!preferences.hapticsEnabled) return
    vibratePrimitive(
        primitive = VibrationEffect.Composition.PRIMITIVE_TICK,
        scale = preferences.hapticAmplitude.scale
    )
}

/** 破坏性操作与真实提交的唯一确认震动：一次、短促，与滚动刻度震动彻底分开。 */
fun Context.vibrateConfirm() {
    val preferences = AgentPreferences(this)
    if (!preferences.hapticsEnabled) return
    vibratePrimitive(
        primitive = VibrationEffect.Composition.PRIMITIVE_CLICK,
        scale = preferences.hapticAmplitude.scale
    )
}

/**
 * 按档位缩放一个官方 primitive。
 *
 * 用 Composition 而不是 createPredefined：后者不接受强度参数，想改强度只能换波形 ——
 * 那就把"滴"的系统语义丢了。scale 取值 (0,1]，越界一律夹到 [0.1, 1]。
 */
private fun Context.vibratePrimitive(primitive: Int, scale: Float) {
    try {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val composed = VibrationEffect.startComposition()
                .addPrimitive(primitive, scale.coerceIn(0.1f, 1f))
                .compose()
            vibrator.vibrate(composed)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15L)
        }
    } catch (_: Exception) {}
}
