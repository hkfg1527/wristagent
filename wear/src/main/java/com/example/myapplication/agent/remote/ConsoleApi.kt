package com.example.myapplication.agent.remote

import java.nio.charset.StandardCharsets

/** 与传输层解耦的请求模型：NanoHTTPD 的 session 只在这里被翻译一次。 */
data class ConsoleRequest(
    val method: String,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val bodyBytes: Int = 0,
    val bodyTooLarge: Boolean = false,
) {
    fun header(name: String): String? =
        headers[name.lowercase()] ?: headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    fun queryParam(name: String): String? =
        query[name] ?: query.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

class ConsoleResponse(
    val status: Int,
    val body: ByteArray,
    val contentType: String,
) {
    fun text(): String = String(body, StandardCharsets.UTF_8)

    companion object {
        const val JSON_CONTENT_TYPE = "application/json; charset=utf-8"

        fun json(status: Int, payload: String): ConsoleResponse =
            ConsoleResponse(status, payload.toByteArray(StandardCharsets.UTF_8), JSON_CONTENT_TYPE)

        fun bytes(status: Int, payload: ByteArray, contentType: String): ConsoleResponse =
            ConsoleResponse(status, payload, contentType)
    }
}

/**
 * 控制台接口的全部纯逻辑：路由匹配、token 校验、settings 读写与校验、action 分发、静态资源。
 * 不引用任何 Android 或传输层类型，因此可以完整地在 JVM 单测里驱动。
 */
class ConsoleApi(
    private val settings: ConsoleSettingsPort,
    private val capabilities: () -> Map<String, Any?>,
    private val actions: (String) -> String,
    private val assets: (String) -> ByteArray?,
    private val token: () -> String,
    private val serverPort: () -> Int,
    private val uptime: () -> Long,
    private val throttle: ConsoleAuthThrottle = ConsoleAuthThrottle(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    fun handle(request: ConsoleRequest): ConsoleResponse = try {
        route(request)
    } catch (_: Throwable) {
        errorJson(500, "internal")
    }

    private fun route(request: ConsoleRequest): ConsoleResponse {
        val method = request.method.uppercase()
        val path = normalizePath(request.path)
        if (path == "/api" || path.startsWith("/api/")) {
            // 鉴权先于一切：缺 token/错 token 一律 401，不泄露路径是否存在
            val nowMs = clock()
            if (throttle.isLocked(nowMs)) return errorJson(429, "too_many_attempts")
            if (!authorized(request)) {
                throttle.onFailure(nowMs)
                return errorJson(401, "unauthorized")
            }
            throttle.onSuccess()
            return when {
                path == "/api/ping" ->
                    if (method == "GET") ping() else errorJson(405, "method_not_allowed")

                path == "/api/state" ->
                    if (method == "GET") state() else errorJson(405, "method_not_allowed")

                path == "/api/settings" ->
                    if (method == "POST") updateSettings(request) else errorJson(405, "method_not_allowed")

                path.startsWith("/api/action/") ->
                    if (method == "POST") runAction(path.removePrefix("/api/action/"), request)
                    else errorJson(405, "method_not_allowed")

                else -> errorJson(404, "not_found")
            }
        }
        if (method != "GET" && method != "HEAD") return errorJson(405, "method_not_allowed")
        return serveStatic(path)
    }

    private fun normalizePath(raw: String): String {
        val withoutQuery = raw.trim().substringBefore('?')
        if (withoutQuery.isEmpty()) return "/"
        if (withoutQuery.length <= 1) return "/"
        val trimmed = withoutQuery.trimEnd('/')
        return trimmed.ifEmpty { "/" }
    }

    private fun authorized(request: ConsoleRequest): Boolean {
        val expected = token()
        return ConsoleToken.matches(expected, request.queryParam("t")) ||
            ConsoleToken.matches(expected, request.header("X-Console-Token"))
    }

    private fun ping(): ConsoleResponse = jsonResponse(
        200,
        linkedMapOf<String, Any?>("ok" to true, "version" to API_VERSION, "app" to APP_NAME),
    )

    private fun state(): ConsoleResponse = jsonResponse(
        200,
        linkedMapOf<String, Any?>(
            "ok" to true,
            "settings" to snapshotWithAliases(),
            "capabilities" to capabilitiesSnapshot(),
            "runtime" to linkedMapOf<String, Any?>("serverPort" to serverPort(), "uptimeMs" to uptime()),
        ),
    )

    /** 完整快照：规范键按注册表顺序输出，别名镜像同一份值。 */
    private fun snapshotWithAliases(): Map<String, Any?> {
        val live = runCatching { settings.snapshot() }.getOrNull().orEmpty()
        val out = LinkedHashMap<String, Any?>()
        for (field in ConsoleFields.ALL) out[field.key] = live[field.key]
        for ((alias, real) in ConsoleFields.ALIASES) {
            if (out.containsKey(real)) out[alias] = out[real]
        }
        return out
    }

    private fun capabilitiesSnapshot(): Map<String, Any?> {
        val live = runCatching { capabilities() }.getOrNull().orEmpty()
        val backend = (live["activeBackend"] as? String)?.trim()?.uppercase()?.takeIf { it in BACKENDS } ?: "DIRECT"
        return linkedMapOf<String, Any?>(
            "root" to (live["root"] as? Boolean ?: false),
            "shizukuInstalled" to (live["shizukuInstalled"] as? Boolean ?: false),
            "shizukuReady" to (live["shizukuReady"] as? Boolean ?: false),
            "activeBackend" to backend,
            "internet" to (live["internet"] as? Boolean ?: false),
            "tokenMasked" to ConsoleToken.mask(token()),
        )
    }

    private fun updateSettings(request: ConsoleRequest): ConsoleResponse {
        if (request.bodyTooLarge || request.bodyBytes > MAX_BODY_BYTES) return errorJson(413, "body_too_large")
        val body = request.body
        if (body.isNullOrBlank()) return errorJson(400, "bad_json")
        val parsed = ConsoleJson.parseObject(body) ?: return errorJson(400, "bad_json")

        val ignored = ArrayList<String>()
        val pending = ArrayList<Pair<String, Any>>()
        // 先全量校验再落库：任何一个字段类型不符都整单拒绝，避免半套设置生效
        for ((key, value) in parsed) {
            val field = ConsoleFields.field(key)
            if (field == null || field.readOnly) {
                ignored += key
                continue
            }
            when (val coerced = ConsoleFields.coerce(field, value)) {
                is ConsoleValue.Valid -> pending += field.key to coerced.value
                ConsoleValue.BadType -> return errorJson(400, "bad_type", field.key)
                is ConsoleValue.BadValue -> return jsonResponse(
                    400,
                    linkedMapOf<String, Any?>(
                        "ok" to false,
                        "error" to "bad_value",
                        "field" to field.key,
                        "allowed" to coerced.allowed,
                    ),
                )
            }
        }
        for ((key, value) in pending) settings.write(key, value)

        val payload = linkedMapOf<String, Any?>("ok" to true, "settings" to snapshotWithAliases())
        if (ignored.isNotEmpty()) payload["ignored"] = ignored
        return jsonResponse(200, payload)
    }

    private fun runAction(name: String, request: ConsoleRequest): ConsoleResponse {
        if (request.bodyTooLarge || request.bodyBytes > MAX_BODY_BYTES) return errorJson(413, "body_too_large")
        if (name !in ACTION_NAMES) return errorJson(404, "not_found")
        val result = runCatching { actions(name) }.getOrNull() ?: return errorJson(500, "action_failed")
        return jsonResponse(200, linkedMapOf<String, Any?>("ok" to true, "result" to result))
    }

    private fun serveStatic(path: String): ConsoleResponse {
        val name = when (path) {
            "/", "/index.html" -> "index.html"
            "/app.js" -> "app.js"
            "/app.css" -> "app.css"
            else -> return errorJson(404, "not_found")
        }
        val payload = runCatching { assets(name) }.getOrNull() ?: return errorJson(404, "not_found")
        return ConsoleResponse.bytes(200, payload, STATIC_RESOURCES[name] ?: "application/octet-stream")
    }

    private fun errorJson(status: Int, error: String, field: String? = null): ConsoleResponse {
        val payload = linkedMapOf<String, Any?>("ok" to false, "error" to error)
        if (field != null) payload["field"] = field
        return jsonResponse(status, payload)
    }

    private fun jsonResponse(status: Int, payload: Map<String, Any?>): ConsoleResponse =
        ConsoleResponse.json(status, ConsoleJson.write(payload))

    companion object {
        const val APP_NAME = "wristagent"
        const val API_VERSION = 1
        const val MAX_BODY_BYTES = 64 * 1024

        private val BACKENDS = setOf("ROOT", "SHIZUKU", "DIRECT")

        val ACTION_NAMES: List<String> =
            listOf("probe-privilege", "test-asr", "clear-memory", "refresh-models", "export-settings")

        val STATIC_RESOURCES: Map<String, String> = linkedMapOf(
            "index.html" to "text/html; charset=utf-8",
            "app.js" to "text/javascript; charset=utf-8",
            "app.css" to "text/css; charset=utf-8",
        )
    }
}
