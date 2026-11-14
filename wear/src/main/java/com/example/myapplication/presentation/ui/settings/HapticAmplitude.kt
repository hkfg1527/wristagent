package com.example.myapplication.presentation.ui.settings

import androidx.annotation.StringRes
import com.example.myapplication.R

/**
 * 触觉幅度三档。
 *
 * 用官方 **primitive 缩放**（[android.os.VibrationEffect.Composition.addPrimitive] 的 scale 参数），
 * 而不是换一段自造波形 —— 这样"滴"的形状仍是系统的 TICK/CLICK 语义，只改强度。
 * minSdk 33 已经够 [android.os.VibrationEffect.Composition] 的单 primitive 组合。
 */
enum class HapticAmplitude(
    @StringRes val labelRes: Int,
    /** primitive 的缩放系数，取值 (0, 1]：1 = 系统原强度。 */
    val scale: Float
) {
    LIGHT(R.string.settings_haptic_light, 0.4f),
    STANDARD(R.string.settings_haptic_standard, 0.7f),
    STRONG(R.string.settings_haptic_strong, 1f);

    companion object {
        fun fromStorage(raw: String?): HapticAmplitude =
            entries.firstOrNull { it.name == raw } ?: STANDARD
    }
}
