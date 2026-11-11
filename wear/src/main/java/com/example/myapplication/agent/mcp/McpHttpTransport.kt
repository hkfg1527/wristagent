package com.example.myapplication.agent.mcp

import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.Charset

/** MCP 传输层失败：kind 是机器可判定的错误码，detail 是给人看的一句话。 */
class McpTransportException(
    val kind: String,
    val detail: String,
    val status: Int = -1
) : Exception(detail)

data class McpHttpResponse(
    val status: Int,
    val contentType: String?,
    val body: String,
    val sessionId: String?
)

/** 三档超时：连接、读、以及整次请求的整体预算。任一档到点都必须返回，绝不允许无限等。 */
data class McpTimeouts(
    val connectMs: Int = 3_000,
    val readMs: Int = 6_000,
    val requestMs: Long = 12_000
)

/**
 * 最小 HTTP 传输：一次 POST，一条 JSON-RPC 消息。
 *
 * 重定向自己处理而不是交给 HttpURLConnection：默认实现会跟着 302 跳到任意主机，
 * 而 mcpEndpoint 是用户可填的任意地址，跳转必须按 [McpEndpointPolicy.redirect] 逐跳校验。
 * abort() 断开在途连接，是"取消能真的打断阻塞读"的唯一手段。
 */
class McpHttpTransport(private val timeouts: McpTimeouts = McpTimeouts()) {

    @Volatile
    private var active: HttpURLConnection? = null

    fun abort() {
        val connection = active ?: return
        runCatching { connection.disconnect() }
    }

    fun post(endpoint: McpEndpoint, body: String, headers: Map<String, String>): McpHttpResponse {
        var current = endpoint
        var redirects = 0
        while (true) {
            val connection = open(current, headers)
            try {
                connection.doOutput = true
                val bytes = body.toByteArray(Charsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { stream -> stream.write(bytes) }

                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    when (val decision = McpEndpointPolicy.redirect(current, status, connection.getHeaderField("Location"))) {
                        is McpRedirect.Follow -> {
                            redirects += 1
                            if (redirects > McpEndpointPolicy.MAX_REDIRECTS) {
                                throw McpTransportException(
                                    "redirect_limit",
                                    "MCP 端点重定向超过 " + McpEndpointPolicy.MAX_REDIRECTS + " 次：" + endpoint.url,
                                    status
                                )
                            }
                            current = decision.endpoint
                            continue
                        }

                        is McpRedirect.Refuse -> throw McpTransportException("redirect_refused", decision.reason, status)
                    }
                }

                val responseBody = readBody(connection, status)
                if (status !in 200..299) {
                    throw McpTransportException(
                        "http_" + status,
                        "MCP 服务端返回 HTTP " + status + snippetOf(responseBody),
                        status
                    )
                }
                return McpHttpResponse(
                    status = status,
                    contentType = connection.contentType,
                    body = responseBody,
                    sessionId = connection.getHeaderField(SESSION_HEADER)
                )
            } catch (error: SocketTimeoutException) {
                throw McpTransportException("timeout", "MCP 端点超时（" + current.url + "）")
            } catch (error: McpTransportException) {
                throw error
            } catch (error: IOException) {
                throw McpTransportException(
                    "connect",
                    "MCP 端点不可达（" + current.url + "）：" + (error.message ?: error.javaClass.simpleName)
                )
            } finally {
                active = null
                runCatching { connection.disconnect() }
            }
        }
    }

    private fun open(endpoint: McpEndpoint, headers: Map<String, String>): HttpURLConnection {
        val connection = try {
            URL(endpoint.url).openConnection() as HttpURLConnection
        } catch (error: IOException) {
            throw McpTransportException("bad_url", "MCP 端点无法打开连接：" + endpoint.url)
        }
        connection.requestMethod = "POST"
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.connectTimeout = timeouts.connectMs
        connection.readTimeout = timeouts.readMs
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.setRequestProperty("User-Agent", USER_AGENT)
        headers.forEach { entry -> connection.setRequestProperty(entry.key, entry.value) }
        active = connection
        return connection
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) {
            try {
                connection.inputStream
            } catch (error: IOException) {
                null
            }
        } else {
            connection.errorStream
        }
        val source = stream ?: return ""
        return source.bufferedReader(charsetOf(connection.contentType)).use { reader -> reader.readText() }
    }

    private fun charsetOf(contentType: String?): Charset {
        val raw = contentType ?: return Charsets.UTF_8
        for (part in raw.split(';')) {
            val trimmed = part.trim()
            if (!trimmed.startsWith("charset=", ignoreCase = true)) continue
            val name = trimmed.substringAfter('=').trim().trim('"')
            val charset = runCatching { Charset.forName(name) }.getOrNull()
            if (charset != null) return charset
        }
        return Charsets.UTF_8
    }

    private fun snippetOf(body: String): String {
        val trimmed = body.trim()
        return if (trimmed.isEmpty()) "" else "：" + trimmed.take(200)
    }

    companion object {

        const val SESSION_HEADER = "Mcp-Session-Id"

        private const val USER_AGENT = "WristAgent-MCP/1.0 (Android Wear OS)"

        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
