package com.example.myapplication.agent.mcp

import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * 用户可填的 MCP 端点。
 *
 * 显式拆出协议、主机、端口、路径与查询串：判断"是不是 http(s)"、"是不是跨主机重定向"
 * 都必须基于解析结果，而不是对原始字符串做前缀判断。
 */
data class McpEndpoint(
    val scheme: String,
    val host: String,
    val port: Int,
    val path: String,
    val query: String?
) {

    val isDefaultPort: Boolean
        get() = (scheme == "http" && port == 80) || (scheme == "https" && port == 443)

    val authority: String
        get() = if (isDefaultPort) host else host + ":" + port

    val url: String
        get() = scheme + "://" + authority + path + (query?.let { "?" + it } ?: "")

    /** 明文 http 只有对回环地址算正常用法，其它情况要在报告里显式说明。 */
    val isLoopback: Boolean
        get() {
            val bare = host.removePrefix("[").removeSuffix("]")
            return bare == "localhost" || bare == "::1" || bare.startsWith("127.")
        }
}

/** 端点解析结论：要么给出可用端点，要么给一句人能读的拒绝理由。 */
sealed class McpEndpointVerdict {
    data class Accept(val endpoint: McpEndpoint) : McpEndpointVerdict()
    data class Reject(val reason: String) : McpEndpointVerdict()
}

/** 重定向结论：同主机同源才放行，跨主机一律拒绝并记下两端主机名。 */
sealed class McpRedirect {
    data class Follow(val endpoint: McpEndpoint) : McpRedirect()
    data class Refuse(val reason: String) : McpRedirect()
}

object McpEndpointPolicy {

    /** 单次请求最多跟几次重定向：MCP 是本地服务，链式跳转只能说明端点配错了。 */
    const val MAX_REDIRECTS = 2

    fun parse(raw: String): McpEndpointVerdict {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return McpEndpointVerdict.Reject("MCP 端点为空白：未配置就不连接")
        }
        val uri: URI = try {
            URI(trimmed)
        } catch (error: URISyntaxException) {
            return McpEndpointVerdict.Reject("MCP 端点不是合法 URL：" + trimmed)
        }
        val scheme = uri.scheme
        if (scheme == null) {
            return McpEndpointVerdict.Reject("MCP 端点缺少协议，只接受 http/https：" + trimmed)
        }
        val normalizedScheme = scheme.lowercase()
        if (normalizedScheme != "http" && normalizedScheme != "https") {
            return McpEndpointVerdict.Reject("MCP 端点协议 [" + scheme + "] 不被允许，只接受 http/https")
        }
        val host = uri.host
        if (host == null || host.isBlank()) {
            return McpEndpointVerdict.Reject("MCP 端点缺少主机名：" + trimmed)
        }
        val port = when {
            uri.port > 0 -> uri.port
            normalizedScheme == "https" -> 443
            else -> 80
        }
        val rawPath = uri.rawPath
        val path = if (rawPath.isNullOrEmpty()) "/" else rawPath
        return McpEndpointVerdict.Accept(
            McpEndpoint(
                scheme = normalizedScheme,
                host = host,
                port = port,
                path = path,
                query = uri.rawQuery
            )
        )
    }

    /**
     * 重定向判定。
     *
     * 拒绝三类跳转：跨主机（防 SSRF 到内网其它主机）、协议降级（https -> http）、跨端口。
     * 同主机 http -> https 的升级放行，这是唯一被允许的协议变化。
     */
    fun redirect(origin: McpEndpoint, status: Int, location: String?): McpRedirect {
        if (location.isNullOrBlank()) {
            return McpRedirect.Refuse("HTTP " + status + " 重定向缺少 Location 头")
        }
        val resolved = try {
            URL(URL(origin.url), location).toString()
        } catch (error: Exception) {
            return McpRedirect.Refuse("重定向目标无法解析：" + location)
        }
        val target = when (val verdict = parse(resolved)) {
            is McpEndpointVerdict.Accept -> verdict.endpoint
            is McpEndpointVerdict.Reject -> return McpRedirect.Refuse("重定向目标不是合法 http(s) 地址：" + verdict.reason)
        }
        if (!target.host.equals(origin.host, ignoreCase = true)) {
            return McpRedirect.Refuse(
                "拒绝跨主机重定向：" + origin.host + " -> " + target.host + "（" + target.url + "）"
            )
        }
        val upgrade = origin.scheme == "http" && target.scheme == "https"
        if (target.scheme != origin.scheme && !upgrade) {
            return McpRedirect.Refuse("拒绝降级重定向：" + origin.scheme + " -> " + target.scheme)
        }
        val samePort = target.port == origin.port
        val upgradePort = upgrade && origin.port == 80 && target.port == 443
        if (!samePort && !upgradePort) {
            return McpRedirect.Refuse("拒绝跨端口重定向：" + origin.port + " -> " + target.port)
        }
        return McpRedirect.Follow(target)
    }
}
