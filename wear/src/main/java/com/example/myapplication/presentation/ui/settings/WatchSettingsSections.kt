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
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.theme.Wear
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
        heroLabel = stringResource(R.string.settings_sub_voice_entry),
        onBack = onBack,
        hero = {
            SettingsHeroCard(
                label = stringResource(R.string.settings_sub_voice_entry),
                value = when {
                    !asr.enabled -> stringResource(R.string.settings_asr_off)
                    asr.providerId == AsrProviderId.BAIDU && !asr.hasBaiduCredentials ->
                        stringResource(R.string.settings_asr_key_missing)
                    asr.providerId == AsrProviderId.BAIDU -> stringResource(R.string.settings_asr_baidu)
                    else -> stringResource(R.string.settings_asr_system)
                },
                accent = Wear.CardVoice,
                badge = "VOX"
            )
        }
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
 * 审计结论（docs/SETTINGS-AUDIT.md 第 45、46 行）：纯黑与主题模式在表端**没有任何消费点** ——
 * `Wear.Palette.Bg` 是编译期常量，主题枚举也只被取来渲染文案。拨它们不改变任何像素，
 * 属于用户点名的"没有什么用的选项"，因此连同偏好字段一起删除，只留一句静态说明：
 * 底色恒为纯黑是令牌集事实，不是设置项。
 */
@Composable
fun AppearanceContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var compactMode by remember { mutableStateOf(preferences.compactMode) }
    var spacing by remember { mutableFloatStateOf(preferences.matrixSpacingDp) }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_appearance),
        onBack = onBack,
        hero = {
            SettingsHeroCard(
                label = stringResource(R.string.settings_style_header),
                value = stringResource(WearAppearance.style.labelRes),
                accent = Wear.CardSystem,
                meta = stringResource(WearAppearance.style.summaryRes),
                badge = "UI"
            )
        }
    ) {
        // 页面风格：整页外壳换装，不换数据、不换行的语义。
        // 页首 hero 已经把「页面风格 + 当前值」说了，这里换一个不重复的组标题
        section(stringResource(R.string.settings_style_options))

        SettingsStyle.values().forEach { style ->
            row(subItem = true) {
                SettingsSubRadioRow(
                    label = stringResource(style.labelRes),
                    subtitle = stringResource(style.summaryRes),
                    selected = WearAppearance.style == style,
                    onSelect = {
                        applySettingsStyle(context, style)
                        context.vibrateTick()
                    }
                )
            }
        }

        // 「主题」组整组删除：网页端 ConsoleFields 里已经没有 themeMode，表端的主题枚举也随偏好一起删了，
        // 留着「请在手机网页修改」就是把人指到一个不存在的选项上。
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

        // 字号档：整体缩放现有 5 级字阶，改完立刻整站生效（读的是 WearAppearance 的快照状态）
        section(stringResource(R.string.settings_appearance_font_scale))

        FontScaleTier.values().forEach { tier ->
            row(subItem = true) {
                SettingsSubRadioRow(
                    label = stringResource(tier.labelRes),
                    subtitle = tier.percent,
                    selected = FontScaleTier.nearest(WearAppearance.fontScale) == tier,
                    onSelect = {
                        applyFontScaleTier(context, tier)
                        context.vibrateTick()
                    }
                )
            }
        }

        // 强调色档：运行时覆盖，不是新令牌；未选中的档也用小色点把颜色先给出来
        section(stringResource(R.string.settings_appearance_accent_color))

        AccentPreset.values().forEach { preset ->
            row(subItem = true) {
                SettingsSubRadioRow(
                    label = stringResource(preset.labelRes),
                    selected = AccentPreset.of(WearAppearance.accent) == preset,
                    dotColor = preset.color,
                    onSelect = {
                        applyAccentPreset(context, preset)
                        context.vibrateTick()
                    }
                )
            }
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


