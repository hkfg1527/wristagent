package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import kotlin.math.roundToInt

/**
 * 推理超参数调优：温度滑轨与三档预设、最大输出 Token 滑轨、传输协议单选。
 * 读写键与副作用与旧版逐条一致：改温度立即落盘并回调，改 Token 同理。
 */
@Composable
fun HyperParamsContent(
    preferences: AgentPreferences,
    onBack: () -> Unit,
    onParamsChanged: ((temp: Float, maxTokens: Int, stream: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var temp by remember { mutableFloatStateOf(preferences.temperature) }
    var maxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var isStream by remember { mutableStateOf(preferences.streamMode) }

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
        onBack = onBack
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
                    context.vibrateTick()
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
                    context.vibrateTick()
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
                    context.vibrateTick()
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

        section(stringResource(R.string.settings_transport))

        row {
            SettingsRadioRow(
                label = stringResource(R.string.settings_sse_on),
                selected = isStream,
                onSelect = {
                    context.vibrateTick()
                    isStream = true
                    preferences.streamMode = true
                    notifyChange()
                }
            )
        }

        row {
            SettingsRadioRow(
                label = stringResource(R.string.settings_sse_off),
                selected = !isStream,
                onSelect = {
                    context.vibrateTick()
                    isStream = false
                    preferences.streamMode = false
                    notifyChange()
                }
            )
        }
    }
}

/** 三档温度预设：与旧版 chip 完全相同的取值。 */
private const val TempFast = 0.2f
private const val TempBalanced = 0.7f
private const val TempCreative = 1.2f
