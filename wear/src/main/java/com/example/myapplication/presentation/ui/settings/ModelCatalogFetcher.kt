package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.agent.net.NetworkTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal data class ModelListResponse(val status: Int, val contentType: String?, val body: String)

internal fun interface ModelListTransport {
    suspend fun get(url: String, apiKey: String, timeoutMs: Int): ModelListResponse
}

/** 正文 -> 模型 id 列表。JSON 解析依赖 android 运行时，单测注入替身，不在这里验证。 */
internal fun interface ModelListParser {
    fun parse(body: String): List<String>
}

internal sealed interface ModelFetchOutcome {
    data class Success(val modelIds: List<String>, val latencyMs: Long, val attempts: Int) : ModelFetchOutcome

    data class Failure(
        val reason: ModelFetchFailure,
        val statusCode: Int?,
        val host: String?,
        val attempts: Int,
    ) : ModelFetchOutcome
}

internal object HttpModelListTransport : ModelListTransport {
    override suspend fun get(url: String, apiKey: String, timeoutMs: Int): ModelListResponse =
        withContext(Dispatchers.IO) {
            val conn = URL(url).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "GET"
                conn.connectTimeout = timeoutMs
                conn.readTimeout = timeoutMs
                if (apiKey.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                conn.setRequestProperty("Content-Type", "application/json")
                val status = conn.responseCode
                val contentType = conn.contentType
                // 失败响应体也要读：511 登录页 / 劫持页的正文形态是归类依据
                val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                ModelListResponse(status, contentType, body)
            } finally {
                conn.disconnect()
            }
        }
}

internal object JsonModelListParser : ModelListParser {
    override fun parse(body: String): List<String> {
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        val ids = mutableListOf<String>()
        for (index in 0 until data.length()) {
            val id = data.optJSONObject(index)?.optString("id")
            if (!id.isNullOrBlank()) ids.add(id)
        }
        return ids
    }
}

/**
 * /v1/models 拉取编排：单次 3 秒超时、失败重试一次（共 [FETCH_MAX_ATTEMPTS] 次），
 * 失败原因与状态码原样透出给界面，界面据此决定显示哪句文案。
 */
internal class ModelCatalogFetcher(
    private val transport: ModelListTransport = HttpModelListTransport,
    private val parseModels: ModelListParser = JsonModelListParser,
    private val timeoutMs: Long = FETCH_TIMEOUT_MS,
    private val maxAttempts: Int = FETCH_MAX_ATTEMPTS,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    suspend fun fetch(baseUrl: String, apiKey: String): ModelFetchOutcome {
        val url = modelsUrl(baseUrl)
        val host = NetworkTarget.hostOf(baseUrl)
        val startedAt = clock()
        var reason = ModelFetchFailure.UNKNOWN
        var statusCode: Int? = null

        for (attempt in 1..maxAttempts) {
            try {
                val response = withTimeout(timeoutMs) { transport.get(url, apiKey, timeoutMs.toInt()) }
                val failure = ModelFetchClassifier.classifyResponse(response.status, response.contentType, response.body)
                statusCode = response.status
                if (failure == null) {
                    val ids = parseModels.parse(response.body)
                    if (ids.isNotEmpty()) {
                        return ModelFetchOutcome.Success(ids, clock() - startedAt, attempt)
                    }
                    // 200 但空清单：端点没给可用模型，和解析失败一样算失败，绝不静默当成功
                    reason = ModelFetchFailure.NON_JSON
                } else {
                    reason = failure
                }
            } catch (timeout: TimeoutCancellationException) {
                reason = ModelFetchFailure.TIMEOUT
                statusCode = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                reason = ModelFetchClassifier.classifyThrowable(error)
                statusCode = null
            }
        }

        return ModelFetchOutcome.Failure(reason, statusCode, host, maxAttempts)
    }

    private fun modelsUrl(baseUrl: String): String {
        val clean = baseUrl.trim().trimEnd('/')
        return if (clean.endsWith("/v1/models")) clean else "$clean/v1/models"
    }

    companion object {
        const val FETCH_TIMEOUT_MS = 3000L
        const val FETCH_MAX_ATTEMPTS = 2
    }
}
