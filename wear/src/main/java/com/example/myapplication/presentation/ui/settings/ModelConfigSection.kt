package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ModelCycle
import com.example.myapplication.presentation.ui.ModelLabels
import com.example.myapplication.presentation.ui.components.Tone
import kotlinx.coroutines.launch

/** 该页可编辑的两个文本项。 */
private enum class ModelTextField { BASE_URL, API_KEY }

/** 内置端点：取值与旧版 chip 完全一致。 */
private val BuiltInEndpoints = listOf(
    "https://api.deepseek.com" to "DeepSeek",
    "https://generativelanguage.googleapis.com" to "Google"
)

/**
 * 模型与端点配置：模型单选、端点单选、BaseUrl/API Key 走输入对话框、握手自检。
 * 读写键与副作用保持不变；文本项从"列表内 TextField"改为"一行入口 + 对话框"，
 * 避免眼控/表冠滚动与软键盘抢焦点。
 */
@Composable
fun ModelConfigContent(
    preferences: AgentPreferences,
    currentModel: String,
    currentBaseUrl: String,
    onBack: () -> Unit,
    onSelect: (model: String, baseUrl: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputBaseUrl by remember { mutableStateOf(currentBaseUrl) }
    var inputApiKey by remember { mutableStateOf(preferences.apiKey) }
    var editingField by remember { mutableStateOf<ModelTextField?>(null) }
    var isFetching by remember { mutableStateOf(false) }
    var fetchResultText by remember { mutableStateOf<String?>(null) }

    val models = ModelCycle.parse(preferences.cachedRemoteModels)

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_model_config),
        onBack = onBack,
        overlay = {
            when (editingField) {
                ModelTextField.BASE_URL -> SettingsTextDialog(
                    title = stringResource(R.string.settings_text_base_url),
                    initial = inputBaseUrl,
                    placeholder = stringResource(R.string.settings_base_url_hint),
                    onValueChange = { value ->
                        inputBaseUrl = value
                        preferences.baseUrl = value
                    },
                    onDismiss = { editingField = null }
                )
                ModelTextField.API_KEY -> SettingsTextDialog(
                    title = stringResource(R.string.settings_text_api_key),
                    initial = inputApiKey,
                    placeholder = stringResource(R.string.settings_api_key_hint),
                    onValueChange = { value ->
                        inputApiKey = value
                        preferences.apiKey = value
                    },
                    onDismiss = { editingField = null }
                )
                null -> Unit
            }
        }
    ) {
        section(stringResource(R.string.settings_model_pick))

        if (ModelLabels.isEmpty(models)) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_no_models)) }
        } else {
            models.forEach { modelId ->
                row {
                    SettingsRadioRow(
                        label = ModelLabels.label(modelId),
                        selected = currentModel == modelId,
                        onSelect = {
                            onSelect(modelId, inputBaseUrl)
                        }
                    )
                }
            }
        }

        section(stringResource(R.string.settings_api_endpoint))

        BuiltInEndpoints.forEach { (url, name) ->
            row {
                SettingsRadioRow(
                    label = name,
                    selected = inputBaseUrl == url,
                    onSelect = {
                        inputBaseUrl = url
                        preferences.baseUrl = url
                        onSelect(currentModel, url)
                    }
                )
            }
        }

        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_text_base_url),
                value = inputBaseUrl,
                onClick = {
                    editingField = ModelTextField.BASE_URL
                }
            )
        }

        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_text_api_key),
                value = SettingsValueMath.maskSecret(inputApiKey),
                onClick = {
                    editingField = ModelTextField.API_KEY
                }
            )
        }

        row {
            SettingsActionButton(
                label = if (isFetching) {
                    stringResource(R.string.settings_ping_running)
                } else {
                    stringResource(R.string.settings_ping_idle)
                },
                enabled = !isFetching,
                tone = Tone.ACCENT,
                onClick = {
                    isFetching = true
                    fetchResultText = context.getString(R.string.settings_probe_running)
                    scope.launch {
                        val (timeMs, remoteModels) = AgentDispatcher.fetchRemoteModels(inputBaseUrl, inputApiKey)
                        isFetching = false
                        if (timeMs >= 0) {
                            fetchResultText = context.getString(R.string.settings_probe_ready, timeMs, remoteModels.size)
                            if (remoteModels.isNotEmpty()) {
                                preferences.cachedRemoteModels = remoteModels.joinToString(",")
                            }
                        } else {
                            fetchResultText = context.getString(R.string.settings_probe_failed)
                        }
                    }
                }
            )
        }

        fetchResultText?.let { text ->
            row { SettingsCaptionRow(text = text) }
        }
    }
}
