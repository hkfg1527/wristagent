package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.R
import com.example.myapplication.agent.net.ResponseClass
import com.example.myapplication.agent.net.ResponseClassifier
import com.example.myapplication.presentation.ModelCycle
import kotlinx.coroutines.TimeoutCancellationException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** 模型条目来源：远端端点实拉 vs 内置默认名。 */
enum class ModelSource { REMOTE, BUILT_IN }

data class ModelEntry(val id: String, val source: ModelSource)

/** 拉取失败分类：只区分能把用户指向不同动作的几种。 */
enum class ModelFetchFailure { DNS, TIMEOUT, UNAUTHORIZED, NON_JSON, HTTP, UNKNOWN }

/**
 * 模型清单的纯逻辑：合并远端与内置默认、来源标记、当前模型命中判定、失败文案映射。
 *
 * 远端优先且保序：同一 id 同时出现在两侧时保留远端那条 —— 端点真的返回了它，
 * 才说明用户手上确实有这个模型。
 */
object ModelCatalog {

    /** 内置默认名：老版本把这两个名字写死成 cachedRemoteModels 的初值，不是端点实拉结果。 */
    val BUILT_IN_MODEL_IDS: List<String> = listOf("deepseek-chat", "deepseek-reasoner")

    fun merge(remote: List<String>, builtIn: List<String> = BUILT_IN_MODEL_IDS): List<ModelEntry> {
        val seen = mutableSetOf<String>()
        val entries = mutableListOf<ModelEntry>()
        val candidates = remote.map { it to ModelSource.REMOTE } + builtIn.map { it to ModelSource.BUILT_IN }
        candidates.forEach { (raw, source) ->
            val id = raw.trim()
            if (id.isNotEmpty() && seen.add(id.lowercase())) entries.add(ModelEntry(id, source))
        }
        return entries
    }

    /**
     * 缓存渲染：标记为远端实拉过就是远端；否则只有"等于内置默认名"的旧默认值算内置，
     * 其余是旧版本写进来的真实远端缓存 —— 把后者说成内置默认同样是撒谎。
     */
    fun entriesFromCache(cached: String?, markedRemote: Boolean): List<ModelEntry> {
        val ids = ModelCycle.parse(cached)
        return when {
            ids.isEmpty() -> merge(emptyList(), BUILT_IN_MODEL_IDS)
            markedRemote -> merge(ids, emptyList())
            sameAsBuiltInDefaults(ids) -> merge(emptyList(), BUILT_IN_MODEL_IDS)
            else -> merge(ids, emptyList())
        }
    }

    fun isCurrentModelMissing(entries: List<ModelEntry>, currentModel: String?): Boolean {
        if (entries.isEmpty()) return false
        val current = currentModel?.trim().orEmpty()
        if (current.isEmpty()) return false
        return entries.none { it.id.equals(current, ignoreCase = true) }
    }

    fun errorTextRes(failure: ModelFetchFailure): Int = when (failure) {
        ModelFetchFailure.DNS -> R.string.settings_model_error_dns
        ModelFetchFailure.TIMEOUT -> R.string.settings_model_error_timeout
        ModelFetchFailure.UNAUTHORIZED -> R.string.settings_model_error_unauthorized
        ModelFetchFailure.NON_JSON -> R.string.settings_model_error_non_json
        ModelFetchFailure.HTTP -> R.string.settings_model_error_http
        ModelFetchFailure.UNKNOWN -> R.string.settings_model_error_unknown
    }

    private fun sameAsBuiltInDefaults(ids: List<String>): Boolean =
        ids.map { it.lowercase() }.toSet() == BUILT_IN_MODEL_IDS.map { it.lowercase() }.toSet()
}

/**
 * 响应归类：正文形态复用 agent/net 的 [ResponseClassifier]（劫持页/网关改写页沿用 application/json），
 * 状态码这类硬信号自己判。
 */
object ModelFetchClassifier {

    /** 2xx 且正文是 JSON 才算成功；返回 null 表示成功。 */
    fun classifyResponse(status: Int, contentType: String?, body: String): ModelFetchFailure? {
        if (status in 200..299) {
            return if (ResponseClassifier.classify(status, contentType, body) == ResponseClass.JSON_OK) {
                null
            } else {
                ModelFetchFailure.NON_JSON
            }
        }
        if (status == 401 || status == 403) return ModelFetchFailure.UNAUTHORIZED
        return ModelFetchFailure.HTTP
    }

    fun classifyThrowable(error: Throwable): ModelFetchFailure = when (error) {
        is UnknownHostException -> ModelFetchFailure.DNS
        is SocketTimeoutException -> ModelFetchFailure.TIMEOUT
        is TimeoutCancellationException -> ModelFetchFailure.TIMEOUT
        is InterruptedIOException -> ModelFetchFailure.TIMEOUT
        else -> ModelFetchFailure.UNKNOWN
    }
}
