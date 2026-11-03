package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
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
 *
 * 模型列表不再是一份写死的名字：进页先渲染缓存，同时后台拉一次 /v1/models；
 * 成功即回写并换上新列表，失败保留缓存并把原因（DNS / 超时 / 401 / 非 JSON）写进行内提示。
 * 缓存里没有远端结果时，内置默认名只以「Built-in default」标记出现，不冒充用户真实可用的模型。
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

    var entries by remember {
        mutableStateOf(
            ModelCatalog.entriesFromCache(preferences.cachedRemoteModels, preferences.modelCatalogFromRemote)
        )
    }
    var isFetching by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<ModelFetchFailure?>(null) }
    var failureStatus by remember { mutableStateOf<String?>(null) }
    var fetchResultText by remember { mutableStateOf<String?>(null) }

    val fetcher = remember { ModelCatalogFetcher() }

    suspend fun refresh(baseUrl: String, apiKey: String) {
        isFetching = true
        failure = null
        failureStatus = null
        fetchResultText = context.getString(R.string.settings_probe_running)
        when (val outcome = fetcher.fetch(baseUrl, apiKey)) {
            is ModelFetchOutcome.Success -> {
                val ids = outcome.modelIds
                // 回写：缓存必须反映端点此刻真实的清单，而不是上一次或内置默认
                preferences.cachedRemoteModels = ids.joinToString(",")
                preferences.modelCatalogFromRemote = true
                entries = ModelCatalog.merge(ids, emptyList())
                fetchResultText = context.getString(R.string.settings_probe_ready, outcome.latencyMs, ids.size)
            }
            is ModelFetchOutcome.Failure -> {
                failure = outcome.reason
                failureStatus = outcome.statusCode?.toString()
                fetchResultText = null
            }
        }
        isFetching = false
    }

    // 进入本子页即自动刷新一次（子页每次打开都会重新组合，等价于"每次进页拉一次"）
    LaunchedEffect(Unit) {
        refresh(inputBaseUrl, inputApiKey)
    }

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

        if (entries.isEmpty()) {
            row { SettingsCaptionRow(text = stringResource(R.string.settings_no_models)) }
        } else {
            entries.forEach { entry ->
                row {
                    SettingsRadioRow(
                        label = ModelLabels.label(entry.id),
                        selected = currentModel == entry.id,
                        subtitle = modelSourceLabel(entry.source),
                        onSelect = {
                            onSelect(entry.id, inputBaseUrl)
                        }
                    )
                }
            }
            if (ModelCatalog.isCurrentModelMissing(entries, currentModel)) {
                row {
                    SettingsCaptionRow(
                        text = stringResource(R.string.settings_model_not_in_list, currentModel)
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
                    scope.launch { refresh(inputBaseUrl, inputApiKey) }
                }
            )
        }

        failure?.let { reason ->
            val reasonText = if (reason == ModelFetchFailure.HTTP) {
                stringResource(R.string.settings_model_error_http, failureStatus.orEmpty())
            } else {
                stringResource(ModelCatalog.errorTextRes(reason))
            }
            row {
                SettingsCaptionRow(
                    text = stringResource(R.string.settings_model_refresh_failed, reasonText)
                )
            }
        }

        fetchResultText?.let { text ->
            row { SettingsCaptionRow(text = text) }
        }
    }
}

/** 列表项的来源标签：远端实拉还是内置默认，一眼能分。 */
@Composable
private fun modelSourceLabel(source: ModelSource): String = stringResource(
    when (source) {
        ModelSource.REMOTE -> R.string.settings_model_source_remote
        ModelSource.BUILT_IN -> R.string.settings_model_source_builtin
    }
)
