package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.WearAppearance

/**
 * V2 设置页的宿主：把"旧偏好里的风格"映射成 V2 的五套之一，然后交给 [SettingsV2Screen]。
 *
 * 为什么保留映射而不是新建偏好键：手表上已经有一份用户设置（`preferences.settingsStyle`，10 个旧值），
 * 直接换键会让所有人"设置被重置"。这里做**单向映射**：旧值 → 五套之一，越迁越稳。
 * 等 R6 真机验收通过后，再考虑把偏好键正式收敛到五套。
 */
fun SettingsV2Style.Companion.fromLegacy(legacy: SettingsStyle): SettingsV2Style = when (legacy) {
    SettingsStyle.M3_LIST -> SettingsV2Style.M3_LIST
    SettingsStyle.TERMINAL -> SettingsV2Style.TERMINAL
    SettingsStyle.ARC -> SettingsV2Style.ARC
    SettingsStyle.SQUARE -> SettingsV2Style.SQUARE
    // 其余旧值（BALLOON / GOOGLE_CARD / M3_TRANSFORM / NEON / PAPER / GLASS）统一收敛到大卡片档
    else -> SettingsV2Style.M3_CARD
}

@Composable
fun SettingsV2Host(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = SettingsV2Style.fromLegacy(WearAppearance.style)
    MaterialTheme {
        SettingsV2Screen(style = style, modifier = modifier.fillMaxSize())
    }
}
