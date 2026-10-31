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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ModelCycle
import com.example.myapplication.presentation.ui.ModelLabels
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.launch

/**
 * 模型与端点配置内容：模型直选、端点切换、自定义 BaseUrl/Key 与握手自检。
 */
@Composable
fun ModelConfigContent(
    preferences: AgentPreferences,
    currentModel: String,
    currentBaseUrl: String,
    onSelect: (model: String, baseUrl: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputBaseUrl by remember { mutableStateOf(currentBaseUrl) }
    var inputApiKey by remember { mutableStateOf(preferences.apiKey) }
    var showCustomInput by remember { mutableStateOf(false) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchResultText by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = "模型直选")

        val models = ModelCycle.parse(preferences.cachedRemoteModels)
        if (ModelLabels.isEmpty(models)) {
            BasicText(
                text = "未发现可用模型，请先点击下方按钮拉取模型列表",
                style = Wear.TextCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            ModelLabels.rows(models, perRow = 2).forEach { rowModels ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                ) {
                    rowModels.forEach { modelId ->
                        WearChip(
                            label = ModelLabels.label(modelId),
                            selected = currentModel == modelId,
                            onClick = {
                                context.vibrateTick()
                                onSelect(modelId, inputBaseUrl)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowModels.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        WearSectionHeader(text = "API 端点")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(
                "https://api.deepseek.com" to "DeepSeek",
                "https://generativelanguage.googleapis.com" to "Google"
            ).forEach { (url, name) ->
                WearChip(
                    label = name,
                    selected = inputBaseUrl == url,
                    onClick = {
                        context.vibrateTick()
                        inputBaseUrl = url
                        preferences.baseUrl = url
                        onSelect(currentModel, url)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SettingsActionButton(
            label = if (showCustomInput) "收起自定义输入" else "手动修改 BaseUrl / Key",
            onClick = {
                context.vibrateTick()
                showCustomInput = !showCustomInput
            }
        )

        if (showCustomInput) {
            WearSectionHeader(text = "BaseUrl")
            SettingsInput(
                value = inputBaseUrl,
                onValueChange = {
                    inputBaseUrl = it
                    preferences.baseUrl = it
                },
                placeholder = "https://api.example.com"
            )
            WearSectionHeader(text = "API Key")
            SettingsInput(
                value = inputApiKey,
                onValueChange = {
                    inputApiKey = it
                    preferences.apiKey = it
                },
                placeholder = "sk-..."
            )
        }

        SettingsActionButton(
            label = if (isFetching) "正在 Ping 测试..." else "端点测速与实时握手 Ping",
            enabled = !isFetching,
            tone = Tone.ACCENT,
            onClick = {
                context.vibrateTick()
                isFetching = true
                fetchResultText = "正在握手探测..."
                scope.launch {
                    val (timeMs, models) = AgentDispatcher.fetchRemoteModels(inputBaseUrl, inputApiKey)
                    isFetching = false
                    if (timeMs >= 0) {
                        fetchResultText = "端点就绪 • 延迟 ${timeMs}ms (${models.size} 个可用模型)"
                        if (models.isNotEmpty()) {
                            preferences.cachedRemoteModels = models.joinToString(",")
                        }
                    } else {
                        fetchResultText = "端点握手失败，网络超时"
                    }
                }
            }
        )

        fetchResultText?.let {
            BasicText(
                text = it,
                style = Wear.TextCaption,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
