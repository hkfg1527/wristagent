package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.ModelLabels
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 该页可编辑的两个文本项。 */
private enum class ModelTextField { BASE_URL, API_KEY }

/** 内置端点：取值与旧版 chip 完全一致。 */
private val BuiltInEndpoints = listOf(
    "https://api.deepseek.com" to "DeepSeek",
    "https://generativelanguage.googleapis.com" to "Google"
)

/**
 * 「端点测速与实时握手 Ping」按钮的可见状态。
 *
 * 三态各自都必须有屏上表现：IDLE 无状态行、RUNNING 是进行时文案 + 走动的时长、
 * DONE / FAILED 是结果行（延迟与模型数 / 失败原因）。没有"点了什么都不发生"的中间态。
 */
internal enum class ProbePhase { IDLE, RUNNING, DONE, FAILED }

/** 进行中的时长回显：整数秒，单位不参与本地化（"s" 中英文都读得懂）。 */
internal fun elapsedSecondsLabel(elapsedMs: Long): String = (elapsedMs / 1000L).toString() + "s"

/**
 * 模型与端点配置：模型单选、端点单选、BaseUrl/API Key 走输入对话框、握手自检。
 *
 * 模型列表不再是一份写死的名字：进页先渲染缓存，同时后台拉一次 /v1/models；
 * 成功即回写并换上新列表，失败保留缓存并把原因（DNS / 超时 / 401 / 非 JSON）写进行内提示。
 * 缓存里没有远端结果时，内置默认名只以「Built-in default」标记出现，不冒充用户真实可用的模型。
 *
 * 反馈位置（真机实测的缺陷修复）：Ping 的结果**不再**追加在列表末尾 ——
 * 按钮本身就在页面最底部时，新增的结果行落在视口外，看上去就是"点了没反应"。
 * 现在按钮与状态行合成同一块（同一个列表项），状态行永远紧贴按钮下方；
 * 按钮也不再因为 isFetching 而被禁用：连点时重新发起一次，任何一次点击都有即时反馈。
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
    var phase by remember { mutableStateOf(ProbePhase.IDLE) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var startedAtMs by remember { mutableStateOf(0L) }
    var elapsedMs by remember { mutableStateOf(0L) }
    var probeJob by remember { mutableStateOf<Job?>(null) }

    val fetcher = remember { ModelCatalogFetcher() }

    fun failureText(reason: ModelFetchFailure, status: String?): String =
        if (reason == ModelFetchFailure.HTTP) {
            context.getString(R.string.settings_model_error_http, status.orEmpty())
        } else {
            context.getString(ModelCatalog.errorTextRes(reason))
        }

    suspend fun refresh(baseUrl: String, apiKey: String) {
        // 一进来就改状态：点下去的那一帧屏上就有"正在测试"，不必等网络
        phase = ProbePhase.RUNNING
        statusText = null
        elapsedMs = 0L
        startedAtMs = System.currentTimeMillis()
        val outcome = fetcher.fetch(baseUrl, apiKey)
        elapsedMs = System.currentTimeMillis() - startedAtMs
        when (outcome) {
            is ModelFetchOutcome.Success -> {
                val ids = outcome.modelIds
                // 回写：缓存必须反映端点此刻真实的清单，而不是上一次或内置默认
                preferences.cachedRemoteModels = ids.joinToString(",")
                preferences.modelCatalogFromRemote = true
                entries = ModelCatalog.merge(ids, emptyList())
                phase = ProbePhase.DONE
                statusText = context.getString(R.string.settings_probe_ready, outcome.latencyMs, ids.size)
            }
            is ModelFetchOutcome.Failure -> {
                phase = ProbePhase.FAILED
                statusText = context.getString(
                    R.string.settings_model_refresh_failed,
                    failureText(outcome.reason, outcome.statusCode?.toString())
                )
            }
        }
    }

    // 进入本子页即自动刷新一次（子页每次打开都会重新组合，等价于"每次进页拉一次"）
    LaunchedEffect(Unit) {
        refresh(inputBaseUrl, inputApiKey)
    }

    // RUNNING 态自带走动的时长：网络卡住时屏上也在动，"应用死了"的怀疑立刻消失
    if (phase == ProbePhase.RUNNING) {
        LaunchedEffect(phase, startedAtMs) {
            while (true) {
                elapsedMs = System.currentTimeMillis() - startedAtMs
                delay(500L)
            }
        }
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
                    // 提示文案里带原始模型 id：折行会把这个 id 劈成两半
                    SettingsCaptionRow(
                        text = stringResource(R.string.settings_model_not_in_list, currentModel),
                        maxLines = 1
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

        // 按钮 + 状态行是同一个列表项：状态行永远紧贴按钮下方，点了必然在视线内
        standaloneRow {
            Column(
                modifier = Modifier
                    .widthIn(max = SettingsContentWidth)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
            ) {
                SettingsActionButton(
                    label = if (phase == ProbePhase.RUNNING) {
                        stringResource(R.string.settings_ping_running)
                    } else {
                        stringResource(R.string.settings_ping_idle)
                    },
                    // 不再用 enabled = false 关门：连点时取消上一次并重新发起，
                    // 任何一次点击都换来一帧可读的反馈（旧版在自动刷新那 6 秒里点击完全没反应）
                    enabled = true,
                    tone = Tone.ACCENT,
                    onClick = {
                        context.vibrateTick()
                        probeJob?.cancel()
                        probeJob = scope.launch { refresh(inputBaseUrl, inputApiKey) }
                    }
                )
                val status = when (phase) {
                    ProbePhase.IDLE -> null
                    ProbePhase.RUNNING ->
                        stringResource(R.string.settings_ping_running) +
                            " " + elapsedSecondsLabel(elapsedMs)
                    ProbePhase.DONE, ProbePhase.FAILED -> statusText
                }
                if (status != null) {
                    BasicText(
                        text = status,
                        // 状态行是 12sp 那一档：反馈要看得见，但不能和行标签抢层级
                        style = Wear.TextCaption,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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
