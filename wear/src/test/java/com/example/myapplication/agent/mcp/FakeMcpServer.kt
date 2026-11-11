package com.example.myapplication.agent.mcp

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/**
 * 本地假 MCP 服务器：真起一个 HTTP 端口、真收发 JSON-RPC，端到端测试不 mock 客户端任何逻辑。
 *
 * 请求体按 Content-Length 精确读取，而不是交给 NanoHTTPD.parseBody：
 * 后者只认 multipart 与 urlencoded，application/json 的请求体读进 files 里一个字段都不会有。
 */
internal class FakeMcpServer : NanoHTTPD(0) {

    val requestCount = AtomicInteger(0)

    val methods: MutableList<String> = Collections.synchronizedList(mutableListOf<String>())

    /** 响应走 text/event-stream 而不是裸 JSON：验证 SSE 传输。 */
    @Volatile
    var useSse: Boolean = false

    /** tools/call 前的人为延迟，用来验证读超时。 */
    @Volatile
    var callDelayMs: Long = 0L

    /** tools/call 直接回 JSON-RPC error（不是工具自身的 isError）。 */
    @Volatile
    var failCallWithRpcError: Boolean = false

    /** 所有请求一律 302 到这个 Location：验证跨主机重定向被拒绝。 */
    @Volatile
    var redirectLocation: String? = null

    fun startServer() {
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, true)
    }

    val port: Int
        get() = listeningPort

    val endpointUrl: String
        get() = "http://127.0.0.1:" + listeningPort + "/mcp"

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        requestCount.incrementAndGet()
        redirectLocation?.let { location ->
            val redirected = NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.REDIRECT,
                "text/plain",
                "redirect"
            )
            redirected.addHeader("Location", location)
            return redirected
        }
        val body = readBody(session) ?: return respond(400, "application/json", "{}")
        val request = runCatching { McpJson.parse(body) as? McpJson.Obj }.getOrNull()
            ?: return respond(400, "application/json", "{}")
        val method = request.field("method")?.stringValue() ?: ""
        methods.add(method)
        val id = request.field("id")
        return when (method) {
            "initialize" -> reply(id, initializeResult())
            "notifications/initialized" -> respond(202, "application/json", "")
            "tools/list" -> reply(id, toolsResult())
            "tools/call" -> {
                if (callDelayMs > 0L) Thread.sleep(callDelayMs)
                if (failCallWithRpcError) {
                    rpcError(id, -32603, "fake server boom")
                } else {
                    reply(id, callResult(request.field("params")))
                }
            }

            else -> rpcError(id, -32601, "method not found: " + method)
        }
    }

    private fun readBody(session: NanoHTTPD.IHTTPSession): String? {
        val length = session.headers["content-length"]?.trim()?.toIntOrNull() ?: return ""
        if (length <= 0) return ""
        val buffer = ByteArray(length)
        var read = 0
        val stream = session.inputStream
        while (read < length) {
            val count = stream.read(buffer, read, length - read)
            if (count <= 0) break
            read += count
        }
        return String(buffer, 0, read, StandardCharsets.UTF_8)
    }

    private fun reply(id: McpJson?, result: McpJson.Obj): NanoHTTPD.Response {
        val fields = linkedMapOf<String, McpJson>("jsonrpc" to McpJson.Str("2.0"))
        if (id != null) fields["id"] = id
        fields["result"] = result
        val payload = McpJson.write(McpJson.Obj(fields))
        if (!useSse) return respond(200, "application/json", payload)
        return respond(200, "text/event-stream", "event: message\ndata: " + payload + "\n\n")
    }

    private fun rpcError(id: McpJson?, code: Int, message: String): NanoHTTPD.Response {
        val fields = linkedMapOf<String, McpJson>("jsonrpc" to McpJson.Str("2.0"))
        if (id != null) fields["id"] = id
        fields["error"] = McpJson.obj(
            "code" to McpJson.Num(code.toDouble()),
            "message" to McpJson.Str(message)
        )
        return respond(200, "application/json", McpJson.write(McpJson.Obj(fields)))
    }

    private fun respond(status: Int, mime: String, body: String): NanoHTTPD.Response {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        val nanoStatus = when (status) {
            200 -> NanoHTTPD.Response.Status.OK
            202 -> NanoHTTPD.Response.Status.ACCEPTED
            else -> NanoHTTPD.Response.Status.BAD_REQUEST
        }
        val response = NanoHTTPD.newFixedLengthResponse(
            nanoStatus,
            mime,
            ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
        response.addHeader("Mcp-Session-Id", "fake-session-1")
        return response
    }

    private fun initializeResult(): McpJson.Obj = McpJson.obj(
        "protocolVersion" to McpJson.Str("2024-11-05"),
        "capabilities" to McpJson.obj("tools" to McpJson.obj("listChanged" to McpJson.Bool(false))),
        "serverInfo" to McpJson.obj(
            "name" to McpJson.Str("fake-mcp"),
            "version" to McpJson.Str("1.0.0")
        )
    )

    private fun toolsResult(): McpJson.Obj = McpJson.obj(
        "tools" to McpJson.Arr(
            listOf(
                McpJson.obj(
                    "name" to McpJson.Str("echo"),
                    "description" to McpJson.Str("回显传入的文本"),
                    "inputSchema" to McpJson.obj(
                        "type" to McpJson.Str("object"),
                        "properties" to McpJson.obj("text" to McpJson.obj("type" to McpJson.Str("string"))),
                        "required" to McpJson.Arr(listOf(McpJson.Str("text")))
                    )
                ),
                McpJson.obj(
                    "name" to McpJson.Str("add"),
                    "description" to McpJson.Str("两数相加"),
                    "inputSchema" to McpJson.obj(
                        "type" to McpJson.Str("object"),
                        "properties" to McpJson.obj(
                            "a" to McpJson.obj("type" to McpJson.Str("number")),
                            "b" to McpJson.obj("type" to McpJson.Str("number"))
                        )
                    )
                ),
                McpJson.obj(
                    "name" to McpJson.Str("fail-now"),
                    "description" to McpJson.Str("自报失败的工具"),
                    "inputSchema" to McpJson.obj("type" to McpJson.Str("object"))
                )
            )
        )
    )

    private fun callResult(params: McpJson?): McpJson.Obj {
        val name = params?.field("name")?.stringValue() ?: ""
        val arguments = params?.field("arguments") ?: McpJson.Obj(emptyMap())
        val text = when (name) {
            "echo" -> "echo:" + (arguments.field("text")?.stringValue() ?: "")
            "add" -> "sum=" + ((arguments.field("a")?.numberValue() ?: 0.0) + (arguments.field("b")?.numberValue() ?: 0.0)).toInt()
            else -> "tool exploded"
        }
        val isError = name != "echo" && name != "add"
        return McpJson.obj(
            "content" to McpJson.Arr(
                listOf(
                    McpJson.obj(
                        "type" to McpJson.Str("text"),
                        "text" to McpJson.Str(text)
                    )
                )
            ),
            "isError" to McpJson.Bool(isError)
        )
    }
}

/** 取一个当前没人监听的端口：绑定后立刻释放，用于验证"连接被拒"。 */
internal fun freePort(): Int {
    val socket = ServerSocket(0)
    return try {
        socket.localPort
    } finally {
        socket.close()
    }
}
