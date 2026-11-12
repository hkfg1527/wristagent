package com.example.myapplication.presentation.ui.settings

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.ReasoningEffort
import com.example.myapplication.agent.ReasoningEffortPolicy
import com.example.myapplication.agent.ReplyStyle
import kotlin.math.roundToInt

/**
 * 推理超参数调优：温度滑轨与三档预设、最大输出 Token 滑轨、传输协议单选，
 * 外加思考过程开关与推理努力三档。
 * 读写键与副作用与旧版逐条一致：改温度立即落盘并回调，改 Token 同理。
 */
@Composable
fun HyperParamsContent(
    preferences: AgentPreferences,
    onBack: () -> Unit,
    onParamsChanged: ((temp: Float, maxTokens: Int, stream: Boolean) -> Unit)? = null
) {
    var temp by remember { mutableFloatStateOf(preferences.temperature) }
    var maxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var isStream by remember { mutableStateOf(preferences.streamMode) }
    var showReasoning by remember { mutableStateOf(preferences.showThoughtChain) }
    var defaultThinking by remember { mutableStateOf(preferences.defaultThinkingEnabled) }
    var effort by remember { mutableStateOf(ReasoningEffort.fromStored(preferences.reasoningEffort)) }
    var replyStyle by remember { mutableStateOf(ReplyStyle.fromStored(preferences.replyStyle)) }
    // 自定义 Token 输入框的开合：与阶梯滑轨并存，阶梯给常用值，输入框管任意值
    var editingTokens by remember { mutableStateOf(false) }

    fun notifyChange() {
        onParamsChanged?.invoke(temp, maxTokens, isStream)
    }

    fun applyTemperature(next: Float) {
        val quantized = SettingsValueMath.quantizeTemperature(next)
        if (quantized == temp) return
        temp = quantized
        preferences.temperature = quantized
        notifyChange()
    }

    fun applyMaxTokens(next: Int) {
        if (next == maxTokens) return
        maxTokens = next
        preferences.maxOutputTokens = next
        notifyChange()
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_hyper_params),
        onBack = onBack,
        overlay = {
            if (editingTokens) {
                SettingsTextDialog(
                    title = stringResource(R.string.settings_max_tokens),
                    initial = maxTokens.toString(),
                    placeholder = stringResource(
                        R.string.settings_max_tokens_range_hint,
                        SettingsValueMath.MaxTokensMin,
                        SettingsValueMath.MaxTokensMax
                    ),
                    // 非数字 / 非正数：解析返回 null，原值一个字符都不动
                    onValueChange = { raw ->
                        SettingsValueMath.parseMaxTokens(raw)?.let { applyMaxTokens(it) }
                    },
                    onDismiss = { editingTokens = false }
                )
            }
        }
    ) {
        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_temperature),
                value = temp,
                valueRange = SettingsValueMath.TemperatureMin..SettingsValueMath.TemperatureMax,
                steps = SettingsValueMath.TemperatureSteps,
                valueText = "%.1f".format(temp),
                onValueChange = { raw -> applyTemperature(raw) }
            )
        }

        section(stringResource(R.string.settings_temp_presets))

        row {
            SettingsRadioRow(
                label = stringResource(R.string.settings_temp_fast),
                selected = SettingsValueMath.temperatureIndex(temp) ==
                    SettingsValueMath.temperatureIndex(TempFast),
                onSelect = {
                    applyTemperature(TempFast)
                }
            )
        }

        row {
            SettingsRadioRow(
                label = stringResource(R.string.settings_temp_balanced),
                selected = SettingsValueMath.temperatureIndex(temp) ==
                    SettingsValueMath.temperatureIndex(TempBalanced),
                onSelect = {
                    applyTemperature(TempBalanced)
                }
            )
        }

        row {
            SettingsRadioRow(
                label = stringResource(R.string.settings_temp_creative),
                selected = SettingsValueMath.temperatureIndex(temp) ==
                    SettingsValueMath.temperatureIndex(TempCreative),
                onSelect = {
                    applyTemperature(TempCreative)
                }
            )
        }

        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_max_tokens),
                value = SettingsValueMath.tokenIndex(maxTokens).toFloat(),
                valueRange = 0f..SettingsValueMath.TokenLadder.lastIndex.toFloat(),
                steps = SettingsValueMath.TokenSteps,
                valueText = maxTokens.toString(),
                onValueChange = { raw ->
                    applyMaxTokens(SettingsValueMath.tokenAt(raw.roundToInt()))
                }
            )
        }

        // 阶梯之外必须还能落到任意值：否则用户被锁在 5 个档位上，属于"只能调到一半"的假开关
        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_max_tokens_custom),
                value = maxTokens.toString(),
                onClick = { editingTokens = true }
            )
        }

        section(stringResource(R.string.settings_transport))

        row {
            SettingsSplitToggleRow(
                label = stringResource(R.string.settings_sse_on),
                checked = isStream,
                onCheckedChange = { enabled ->
                    isStream = enabled
                    preferences.streamMode = enabled
                    notifyChange()
                }
            )
        }

        section(stringResource(R.string.settings_thought_group))

        row {
            SettingsSplitToggleRow(
                label = stringResource(R.string.settings_show_thought_process),
                checked = showReasoning,
                onCheckedChange = { enabled ->
                    showReasoning = enabled
                    preferences.showThoughtChain = enabled
                }
            )
        }

        row {
            SettingsSplitToggleRow(
                label = stringResource(R.string.settings_model_default_thinking),
                secondary = stringResource(R.string.settings_model_default_thinking_secondary),
                subtitleBelowRow = true,
                checked = defaultThinking,
                onCheckedChange = { enabled ->
                    defaultThinking = enabled
                    preferences.defaultThinkingEnabled = enabled
                }
            )
        }


        section(stringResource(R.string.settings_reasoning_effort))

        ReasoningEffort.values().forEach { level ->
            row {
                SettingsRadioRow(
                    label = stringResource(reasoningEffortLabelRes(level)),
                    selected = effort == level,
                    onSelect = {
                        effort = level
                        preferences.reasoningEffort = level.name
                    }
                )
            }
        }

        if (!ReasoningEffortPolicy.supports(preferences.modelName)) {
            // 非推理模型不发 reasoning_effort：这里先把"会被忽略"讲清楚，不等到请求失败
            row {
                SettingsCaptionRow(text = stringResource(R.string.settings_reasoning_effort_unsupported))
            }
        }

        section(stringResource(R.string.settings_reply_style_header))

        ReplyStyle.values().forEach { style ->
            row {
                SettingsRadioRow(
                    label = stringResource(replyStyleLabelRes(style)),
                    subtitle = stringResource(replyStyleSecondaryRes(style)),
                    selected = replyStyle == style,
                    onSelect = {
                        replyStyle = style
                        preferences.replyStyle = style.storageKey
                    }
                )
            }
        }
    }
}

/** 推理努力三档的展示文案：取值与持久化枚举一一对应。 */
@StringRes
private fun reasoningEffortLabelRes(effort: ReasoningEffort): Int = when (effort) {
    ReasoningEffort.LOW -> R.string.settings_reasoning_effort_low
    ReasoningEffort.MEDIUM -> R.string.settings_reasoning_effort_medium
    ReasoningEffort.HIGH -> R.string.settings_reasoning_effort_high
}

@StringRes
private fun replyStyleLabelRes(style: ReplyStyle): Int = when (style) {
    ReplyStyle.CONCISE -> R.string.settings_reply_style_concise
    ReplyStyle.BALANCED -> R.string.settings_reply_style_balanced
    ReplyStyle.DETAILED -> R.string.settings_reply_style_detailed
}

@StringRes
private fun replyStyleSecondaryRes(style: ReplyStyle): Int = when (style) {
    ReplyStyle.CONCISE -> R.string.settings_reply_style_concise_secondary
    ReplyStyle.BALANCED -> R.string.settings_reply_style_balanced_secondary
    ReplyStyle.DETAILED -> R.string.settings_reply_style_detailed_secondary
}

/** 三档温度预设：与旧版 chip 完全相同的取值。 */
private const val TempFast = 0.2f
private const val TempBalanced = 0.7f
private const val TempCreative = 1.2f
