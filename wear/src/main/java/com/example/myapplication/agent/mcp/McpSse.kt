package com.example.myapplication.agent.mcp

/**
 * SSE 帧解析。
 *
 * MCP 的 Streamable HTTP 传输允许服务端用 text/event-stream 回一条 JSON-RPC 响应，
 * 也允许直接回 application/json。两种都得认，并且要能按 id 挑出本次请求的响应帧
 * （服务端可能先推 notifications/progress）。
 */
object McpSse {

    private const val DATA_FIELD = "data"

    fun isEventStream(contentType: String?): Boolean =
        contentType?.lowercase()?.contains("text/event-stream") == true

    /** 按帧取全部 data 载荷：多行 data 用 \n 拼接，注释行（: 开头）与其它字段丢弃。 */
    fun frames(body: String): List<String> {
        val payloads = mutableListOf<String>()
        val current = StringBuilder()
        var hasData = false
        for (rawLine in body.split("\n")) {
            val line = rawLine.removeSuffix("\r")
            if (line.isEmpty()) {
                if (hasData) {
                    payloads.add(current.toString())
                    current.setLength(0)
                    hasData = false
                }
                continue
            }
            if (line.startsWith(":")) continue
            val colon = line.indexOf(':')
            val field = if (colon < 0) line else line.substring(0, colon)
            if (field != DATA_FIELD) continue
            var value = if (colon < 0) "" else line.substring(colon + 1)
            if (value.startsWith(" ")) value = value.substring(1)
            if (hasData) current.append('\n')
            current.append(value)
            hasData = true
        }
        if (hasData) payloads.add(current.toString())
        return payloads
    }

    /** 响应体里的全部候选 JSON 文本：SSE 帧优先，裸 JSON 直接原样返回。 */
    fun payloads(body: String, contentType: String?): List<String> {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return emptyList()
        if (!isEventStream(contentType) && (trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return listOf(trimmed)
        }
        val fromFrames = frames(body)
        if (fromFrames.isNotEmpty()) return fromFrames
        return listOf(trimmed)
    }

    /** 挑出 id 匹配的那一条 JSON-RPC 响应原文；一条都不匹配时返回 null。 */
    fun findResponse(candidates: List<String>, id: Long): String? {
        for (candidate in candidates) {
            val parsed = try {
                McpJson.parse(candidate)
            } catch (error: McpJson.McpJsonException) {
                continue
            }
            val responseId = parsed.field("id")?.numberValue() ?: continue
            if (responseId.toLong() == id) return candidate
        }
        return null
    }
}
