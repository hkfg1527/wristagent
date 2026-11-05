package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.ThemeMode
import com.example.myapplication.voice.AsrProviderId
import kotlin.math.roundToInt

/** 点阵间距的可调区间：与网页端 matrixSpacingDp 的 min/max 一致。 */
private const val MatrixSpacingMinDp = 4f
private const val MatrixSpacingMaxDp = 16f
private const val MatrixSpacingSteps = 11

/**
 * 语音与唤醒。
 *
 * 这一页就是"语音入口收敛"的设置面：两个显式入口写死成说明行，两条自动唤醒写死成只读「已移除」，
 * 长按电源键单独说明它为什么需要系统按键映射 —— 应用收不到 KEYCODE_POWER。
 */
@Composable
fun VoiceEntryContent(
    preferences: AgentPreferences,
    onBack: () -> Unit,
    onOpenAsr: () -> Unit
) {
    val context = LocalContext.current
    var autoSpeak by remember { mutableStateOf(preferences.autoSpeakReplies) }
    var liveTranscript by remember { mutableStateOf(preferences.asrLiveTranscript) }
    val asr = remember { preferences.asrSettings() }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_voice_entry),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_voice_switch_header))

        row {
            SettingsNavRow(
                title = stringResource(R.string.settings_voice_asr_enabled),
                value = when {
                    !asr.enabled -> stringResource(R.string.settings_asr_off)
                    asr.providerId == AsrProviderId.BAIDU && !asr.hasBaiduCredentials ->
                        stringResource(R.string.settings_asr_key_missing)
                    asr.providerId == AsrProviderId.BAIDU -> stringResource(R.string.settings_asr_baidu)
                    else -> stringResource(R.string.settings_asr_system)
                },
                onClick = {
                    context.vibrateTick()
                    onOpenAsr()
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_voice_auto_speak),
                secondary = stringResource(R.string.settings_voice_auto_speak_secondary),
                checked = autoSpeak,
                onCheckedChange = { enabled ->
                    autoSpeak = enabled
                    preferences.autoSpeakReplies = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_voice_live_transcript),
                secondary = stringResource(R.string.settings_voice_live_transcript_secondary),
                checked = liveTranscript,
                onCheckedChange = { enabled ->
                    liveTranscript = enabled
                    preferences.asrLiveTranscript = enabled
                }
            )
        }

        // 唤醒方式：两条自动入口由 VoiceEntryPolicy 判为已移除，这里只回显结论，不给可写开关
        section(stringResource(R.string.settings_voice_wake_header))

        VoiceEntryPolicy.removed.mapNotNull { wakeLabelResOf(it) }.forEach { labelRes ->
            row {
                SettingsInfoRow(
                    title = stringResource(labelRes),
                    value = stringResource(R.string.settings_voice_wake_removed)
                )
            }
        }

        row { SettingsCaptionRow(text = stringResource(R.string.settings_voice_wake_removed_hint)) }

        section(stringResource(R.string.settings_voice_entry_header))

        row { SettingsCaptionRow(text = stringResource(R.string.settings_voice_entry_mic)) }
        row { SettingsCaptionRow(text = stringResource(R.string.settings_voice_entry_main_key)) }

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_voice_power),
                value = stringResource(R.string.settings_voice_power_value)
            )
        }

        row { SettingsCaptionRow(text = stringResource(R.string.settings_voice_power_hint)) }
    }
}

/** 已移除的唤醒入口对应的展示文案；非唤醒类来源不在此列出。 */
private fun wakeLabelResOf(source: VoiceEntrySource): Int? = when (source) {
    VoiceEntrySource.SCREEN_ON_WAKE -> R.string.settings_voice_wake_screen
    VoiceEntrySource.LOCK_SCREEN_WAKE -> R.string.settings_voice_wake_lock
    else -> null
}

/**
 * 外观与点阵。
 *
 * 纯黑与主题模式在表端是只读回显：当前令牌集只有一套深色值（Bg 恒为 0xFF000000），
 * 给出可写开关只会得到"拨了没反应"的假开关，因此真正的切换落在网页控制台。
 */
@Composable
fun AppearanceContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var compactMode by remember { mutableStateOf(preferences.compactMode) }
    var spacing by remember { mutableFloatStateOf(preferences.matrixSpacingDp) }
    val themeMode = remember { preferences.themeMode }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_appearance),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_appearance_header))

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_appearance_pure_black),
                value = stringResource(R.string.settings_appearance_pure_black_value)
            )
        }

        row {
            SettingsInfoRow(
                title = stringResource(R.string.settings_appearance_theme),
                value = stringResource(themeModeLabelRes(themeMode))
            )
        }

        row { SettingsCaptionRow(text = stringResource(R.string.settings_appearance_web_only)) }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_appearance_compact),
                secondary = stringResource(R.string.settings_appearance_compact_secondary),
                checked = compactMode,
                onCheckedChange = { enabled ->
                    compactMode = enabled
                    preferences.compactMode = enabled
                }
            )
        }

        section(stringResource(R.string.settings_appearance_matrix_header))

        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_appearance_matrix_spacing),
                value = spacing,
                valueRange = MatrixSpacingMinDp..MatrixSpacingMaxDp,
                steps = MatrixSpacingSteps,
                valueText = spacing.roundToInt().toString() + "dp",
                onValueChange = { raw ->
                    val snapped = raw.roundToInt().toFloat()
                    if (snapped == spacing) return@SettingsSliderRow
                    spacing = snapped
                    preferences.matrixSpacingDp = snapped
                    context.vibrateTick()
                }
            )
        }
    }
}

/** 主题模式三档的展示文案。 */
private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.DARK -> R.string.settings_theme_dark
    ThemeMode.LIGHT -> R.string.settings_theme_light
}
