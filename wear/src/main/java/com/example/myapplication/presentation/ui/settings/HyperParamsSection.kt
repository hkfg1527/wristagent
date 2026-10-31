package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.roundToInt

/**
 * 推理超参数调优：温度步进与预设、最大输出 Token、传输协议开关。
 */
@Composable
fun HyperParamsContent(
    preferences: AgentPreferences,
    onParamsChanged: ((temp: Float, maxTokens: Int, stream: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var temp by remember { mutableFloatStateOf(preferences.temperature) }
    var maxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var isStream by remember { mutableStateOf(preferences.streamMode) }

    fun notifyChange() {
        onParamsChanged?.invoke(temp, maxTokens, isStream)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            WearSectionHeader(text = "采样温度 (Temperature)", modifier = Modifier.weight(1f))
            BasicText(
                text = "%.1f".format(temp),
                style = Wear.TextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            SettingsActionButton(
                label = "− 0.1",
                modifier = Modifier.weight(1f),
                onClick = {
                    context.vibrateTick()
                    val next = (temp - 0.1f).coerceIn(0.0f, 1.5f)
                    temp = (next * 10).roundToInt() / 10f
                    preferences.temperature = temp
                    notifyChange()
                }
            )
            SettingsActionButton(
                label = "+ 0.1",
                modifier = Modifier.weight(1f),
                onClick = {
                    context.vibrateTick()
                    val next = (temp + 0.1f).coerceIn(0.0f, 1.5f)
                    temp = (next * 10).roundToInt() / 10f
                    preferences.temperature = temp
                    notifyChange()
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(
                0.2f to "极速 0.2",
                0.7f to "平衡 0.7",
                1.2f to "发散 1.2"
            ).forEach { (presetTemp, label) ->
                WearChip(
                    label = label,
                    selected = (temp * 10).roundToInt() == (presetTemp * 10).roundToInt(),
                    onClick = {
                        context.vibrateTick()
                        temp = presetTemp
                        preferences.temperature = presetTemp
                        notifyChange()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        WearSectionHeader(text = "最大输出 Token")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(150 to "150 节能", 300 to "300 适中").forEach { (tokens, label) ->
                WearChip(
                    label = label,
                    selected = maxTokens == tokens,
                    onClick = {
                        context.vibrateTick()
                        maxTokens = tokens
                        preferences.maxOutputTokens = tokens
                        notifyChange()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(600 to "600 完整", 1200 to "1200 深度", 2048 to "2048 满额").forEach { (tokens, label) ->
                WearChip(
                    label = label,
                    selected = maxTokens == tokens,
                    onClick = {
                        context.vibrateTick()
                        maxTokens = tokens
                        preferences.maxOutputTokens = tokens
                        notifyChange()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        WearSectionHeader(text = "传输协议")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            WearChip(
                label = "开启 SSE 流式",
                selected = isStream,
                onClick = {
                    context.vibrateTick()
                    isStream = true
                    preferences.streamMode = true
                    notifyChange()
                },
                modifier = Modifier.weight(1f)
            )
            WearChip(
                label = "关闭流式",
                selected = !isStream,
                onClick = {
                    context.vibrateTick()
                    isStream = false
                    preferences.streamMode = false
                    notifyChange()
                },
                modifier = Modifier.weight(1f)
            )
        }

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
