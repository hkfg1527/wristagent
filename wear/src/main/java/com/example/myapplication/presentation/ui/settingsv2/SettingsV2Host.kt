package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
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
            // 行点击：**五套风格共用**。此前只有 TERMINAL 接通，另外四套点了没反应
            // （M3_CARD 甚至是 `onClick = {}` 的假按钮），用户看到的就是"设置页全都用不了"。
            onRowClick = { row ->
                when (val trailing = row.trailing) {
                    is RowTrailing.Toggle -> {
                        // 每个开关都写回**对应的真实偏好字段**，不再只改本地 UI。
                        // key -> setter 表：新增的 11 个开关（工具 6 + MCP + ASR 3 + 原有 2）全部有落点。
                        when (row.key) {
                            "tool_device" -> preferences.toolDeviceStatusEnabled = !preferences.toolDeviceStatusEnabled
                            "tool_vibrate" -> preferences.toolVibrateEnabled = !preferences.toolVibrateEnabled
                            "tool_matrix" -> preferences.toolMatrixDensityEnabled = !preferences.toolMatrixDensityEnabled
                            "tool_power" -> preferences.toolPowerModeEnabled = !preferences.toolPowerModeEnabled
                            "tool_weather" -> preferences.toolWeatherEnabled = !preferences.toolWeatherEnabled
                            "tool_web" -> preferences.toolWebSearchEnabled = !preferences.toolWebSearchEnabled
                            "mcp" -> preferences.isMcpEnabled = !preferences.isMcpEnabled
                            "asr_enabled" -> preferences.asrEnabled = !preferences.asrEnabled
                            "asr_live" -> preferences.asrLiveTranscript = !preferences.asrLiveTranscript
                            "tts" -> preferences.autoSpeakReplies = !preferences.autoSpeakReplies
                            "compact" -> preferences.compactMode = !preferences.compactMode
                            else -> trailing.onChange(!trailing.checked)
                        }
                        com.example.myapplication.agent.SettingsChangeBus.bump()
                    }
                    // V2 已无"点了没反应"的跳转行：Chevron 只剩「页面风格」（Screen 层已分流）
                    is RowTrailing.Chevron -> Unit
                    else -> row.onClick?.invoke()
                }
            },
            // 选项行选中后写回偏好：value 是落库键（枚举 storageKey / 数值字符串）。
            onChoiceSelected = { key, value ->
                when (key) {
                    "temperature" -> preferences.temperature = value.toFloat()
                    "reply_style" -> preferences.replyStyle = value
                    "security" -> preferences.securityMode = SecurityMode.valueOf(value)
                    "root" -> preferences.sandboxLevel = SandboxLevel.fromStorage(value)
                    "asr_lang" -> preferences.asrDevPid = value.toInt()
                }
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
