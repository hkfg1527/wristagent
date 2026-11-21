package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    preferences: com.example.myapplication.agent.AgentPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 风格**只从偏好读**：控制台 POST 写的就是偏好，`/api/state` 读的也是偏好。
    // 之前读内存态 `WearAppearance.style`，结果真机上怎么切都不变（0.0.0.153 实测：重启后仍是默认档），
    // 因为那条内存态在进程启动时并没有从偏好回填。
    val revision by com.example.myapplication.agent.SettingsChangeBus.revision.collectAsState()
    val style = remember(revision, preferences.settingsStyle) {
        SettingsV2Style.fromLegacy(preferences.settingsStyle)
    }
    // 内容也随 revision 重读：控制台改完偏好会 bump，于是模型名/温度等实值行会跟着变
    val sections = remember(revision) { SettingsV2SampleData.fromPreferences(preferences) }
    MaterialTheme {
        SettingsV2Screen(
            style = style,
            sections = sections,
            modifier = modifier.fillMaxSize(),
            onStyleSelected = { picked ->
                // 落库 + 广播：preferences 是唯一真源，bump 之后宿主会按新风格重组
                preferences.settingsStyle = picked.toLegacy()
                com.example.myapplication.agent.SettingsChangeBus.bump()
            },
        )
    }
}

/** V2 风格 -> 旧枚举（偏好里存的是旧枚举）。 */
fun SettingsV2Style.toLegacy(): SettingsStyle = when (this) {
    SettingsV2Style.M3_CARD -> SettingsStyle.BALLOON
    SettingsV2Style.M3_LIST -> SettingsStyle.M3_LIST
    SettingsV2Style.ARC -> SettingsStyle.ARC
    SettingsV2Style.SQUARE -> SettingsStyle.SQUARE
    SettingsV2Style.TERMINAL -> SettingsStyle.TERMINAL
}
