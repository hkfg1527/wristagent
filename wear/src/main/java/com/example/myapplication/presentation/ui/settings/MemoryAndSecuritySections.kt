package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone

/**
 * 上下文与长记忆管理：保留轮数改单选行，清空动作改用官方 Button 的错误配色。
 * 清空仍然直接落盘 [AgentPreferences.resetAll]，点击语义与旧版一致。
 */
@Composable
fun MemoryManagerContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var memoryCleared by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(preferences.maxContextTurns) }
    var memoryEnabled by remember { mutableStateOf(preferences.memoryEnabled) }
    var autoCompress by remember { mutableStateOf(preferences.autoCompressContext) }

    val turnOptions = listOf(
        4 to R.string.settings_turns_4,
        10 to R.string.settings_turns_10,
        30 to R.string.settings_turns_30
    )

    SettingsSubPage(
        title = stringResource(R.string.settings_memory_management),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_turns_header))

        turnOptions.forEach { (count, labelRes) ->
            row {
                SettingsRadioRow(
                    label = stringResource(labelRes),
                    selected = turns == count,
                    onSelect = {
                        turns = count
                        preferences.maxContextTurns = count
                    }
                )
            }
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_memory_enabled),
                secondary = stringResource(R.string.settings_memory_enabled_secondary),
                checked = memoryEnabled,
                onCheckedChange = { enabled ->
                    memoryEnabled = enabled
                    preferences.memoryEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_context_compress),
                secondary = stringResource(R.string.settings_context_compress_secondary),
                checked = autoCompress,
                onCheckedChange = { enabled ->
                    autoCompress = enabled
                    preferences.autoCompressContext = enabled
                }
            )
        }

        // 上下文用量只读回显：轮数上限与自动压缩都是本页真实取值，不做任何估算
        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_context_usage),
                value = stringResource(
                    R.string.settings_context_usage_value,
                    turns,
                    stringResource(
                        if (autoCompress) R.string.settings_flag_on else R.string.settings_flag_off
                    )
                )
            )
        }

        section(stringResource(R.string.settings_web_only_header))

        row { SettingsCaptionRow(text = stringResource(R.string.settings_context_window_web)) }
        row { SettingsCaptionRow(text = stringResource(R.string.settings_context_truncation_web)) }

        section(stringResource(R.string.settings_memory_clear_header))

        row {
            SettingsActionButton(
                label = if (memoryCleared) {
                    stringResource(R.string.settings_memory_cleared)
                } else {
                    stringResource(R.string.settings_memory_clear_all)
                },
                tone = if (memoryCleared) Tone.MUTED else Tone.DANGER,
                onClick = {
                    context.vibrateConfirm()
                    preferences.resetAll()
                    memoryCleared = true
                }
            )
        }
    }
}

/**
 * 两翼副键说明。
 * 横向手势已被 Wear 系统占用，右副键固定为打开会话抽屉，不再提供可绑定项。
 */
@Composable
fun WingShortcutContent(
    currentAction: RightButtonAction,
    onBack: () -> Unit,
    onSelect: (RightButtonAction) -> Unit
) {
    SettingsSubPage(
        title = stringResource(R.string.settings_sub_wing_shortcut),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_side_key_header))

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_side_key),
                value = stringResource(R.string.settings_side_key_value)
            )
        }
    }
}

/**
 * AI 安全护栏模式选择：三档改单选行，选中态取主色，说明文字作为次行最多两行。
 */
@Composable
fun SecurityGuardrailContent(
    currentMode: SecurityMode,
    onBack: () -> Unit,
    onSelect: (SecurityMode) -> Unit
) {
    val modes = listOf(
        Triple(SecurityMode.NOVICE, R.string.settings_security_novice_title, R.string.settings_security_novice_desc),
        Triple(SecurityMode.STANDARD, R.string.settings_security_standard_title, R.string.settings_security_standard_desc),
        Triple(SecurityMode.EXPERT, R.string.settings_security_expert_title, R.string.settings_security_expert_desc)
    )

    SettingsSubPage(
        title = stringResource(R.string.settings_security_policy),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_security_policy))

        modes.forEach { (mode, titleRes, descRes) ->
            row {
                SettingsRadioRow(
                    label = stringResource(titleRes),
                    subtitle = stringResource(descRes),
                    selected = currentMode == mode,
                    onSelect = {
                        onSelect(mode)
                    }
                )
            }
        }
    }
}
