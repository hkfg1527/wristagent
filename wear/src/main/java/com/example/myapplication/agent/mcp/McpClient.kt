package com.example.myapplication.agent.mcp

import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class McpServerInfo(
    val name: String,
    val version: String,
    val protocolVersion: String
)

data class McpRemoteTool(
    val name: String,
    val description: String,
    val inputSchema: McpJson.Obj?
)

data class McpCallResult(
    val isError: Boolean,
    val text: String,
    val content: List<McpJson>
)

/**
 * 最小 MCP 客户端：JSON-RPC 2.0 over HTTP，响应体可能是裸 JSON 也可能是 SSE 帧。
 *
 * 只做三件事：initialize 握手、tools/list 拉清单、tools/call 执行远端工具。
 * 失败一律以 [McpTransportException]（带可判定的 kind）抛出，由调用层翻译成给模型看的回执；
 * 这里不吞异常，也不做任何重试 —— 重试由 [McpToolBinding] 的冷却时间统一决定。
 *
 * 超时三档（连接/读/整体预算）保证任何一次调用都有硬边界；阻塞调用跑在 Dispatchers.IO 上，
 * 取消则通过 [abort] 断开在途连接立即生效（[McpToolBinding.close] 与失败清场都会调用它），
 * 未主动 abort 时最多等一个读超时。
 */
class McpClient(
    val endpoint: McpEndpoint,
    private val timeouts: McpTimeouts = McpTimeouts(),
    private val transport: McpHttpTransport = McpHttpTransport(timeouts),
    /** tools/list 分页上限，来自设置项 mcp_max_tool_pages；越界在构造时就夹好。 */
    maxToolPages: Int = MAX_TOOL_PAGES
) : Closeable {

    private val toolPageLimit: Int = maxToolPages.coerceIn(MIN_TOOL_PAGES, MAX_TOOL_PAGE_LIMIT)

    private val idSequence = AtomicLong(0)

    private val initLock = Mutex()

    @Volatile
    private var sessionId: String? = null

    @Volatile
    private var serverInfo: McpServerInfo? = null

    /** 握手只做一次；并发调用会等在同一个 Mutex 上，不会发出两次 initialize。 */
    suspend fun ensureInitialized(): McpServerInfo {
        serverInfo?.let { return it }
        return initLock.withLock {
            val ready = serverInfo
            if (ready != null) {
                ready
            } else {
                val params = McpJson.obj(
                    "protocolVersion" to McpJson.Str(PROTOCOL_VERSION),
                    "capabilities" to McpJson.obj(),
                    "clientInfo" to McpJson.obj(
                        "name" to McpJson.Str(CLIENT_NAME),
                        "version" to McpJson.Str(CLIENT_VERSION)
                    )
                )
                val result = rpc("initialize", params, timeouts.requestMs)
                val protocol = result.field("protocolVersion")?.stringValue() ?: PROTOCOL_VERSION
                val info = result.field("serverInfo")?.objectFields().orEmpty()
                val value = McpServerInfo(
                    name = info["name"]?.stringValue() ?: "unknown",
                    version = info["version"]?.stringValue() ?: "",
                    protocolVersion = protocol
                )
                serverInfo = value
                notify("notifications/initialized", null)
                value
            }
        }
    }

    suspend fun listTools(): List<McpRemoteTool> {
        ensureInitialized()
        val tools = mutableListOf<McpRemoteTool>()
        var cursor: String? = null
        var page = 0
        while (page < toolPageLimit) {
            val currentCursor = cursor
            val params = if (currentCursor == null) null else McpJson.obj("cursor" to McpJson.Str(currentCursor))
            val result = rpc("tools/list", params, timeouts.requestMs)
            result.field("tools")?.arrayItems()?.forEach { item ->
                val name = item.field("name")?.stringValue()
                if (!name.isNullOrBlank()) {
                    tools.add(
                        McpRemoteTool(
                            name = name,
                            description = item.field("description")?.stringValue() ?: "",
                            inputSchema = item.field("inputSchema") as? McpJson.Obj
                        )
                    )
                }
            }
            page += 1
            val next = result.field("nextCursor")?.stringValue()
            if (next.isNullOrBlank()) break
            cursor = next
        }
        return tools
    }

    suspend fun callTool(remoteName: String, arguments: McpJson.Obj): McpCallResult {
        ensureInitialized()
        val params = McpJson.obj(
            "name" to McpJson.Str(remoteName),
            "arguments" to arguments
        )
        val result = rpc("tools/call", params, timeouts.requestMs)
        val content = result.field("content")?.arrayItems().orEmpty()
        val text = content.mapNotNull { block -> block.field("text")?.stringValue() }.joinToString("\n")
        val isError = result.field("isError")?.boolValue() ?: false
        return McpCallResult(isError = isError, text = text, content = content)
    }

    /** 取消/关闭时断开在途连接：阻塞读会立刻返回，工具循环不会被一个远端端点拖住。 */
    fun abort() {
        transport.abort()
    }

    override fun close() {
        abort()
    }

    private suspend fun rpc(method: String, params: McpJson.Obj?, budgetMs: Long): McpJson.Obj {
        val id = idSequence.incrementAndGet()
        val fields = linkedMapOf<String, McpJson>(
            "jsonrpc" to McpJson.Str(JSONRPC_VERSION),
            "id" to McpJson.Num(id.toDouble()),
            "method" to McpJson.Str(method)
        )
        if (params != null) fields["params"] = params
        val response = send(McpJson.write(McpJson.Obj(fields)), budgetMs)
        val candidates = McpSse.payloads(response.body, response.contentType)
        val payload = McpSse.findResponse(candidates, id)
            ?: throw McpTransportException(
                "bad_response",
                "MCP 服务端没有返回 id=" + id + " 的 " + method + " 结果"
            )
        val parsed = try {
            McpJson.parse(payload)
        } catch (error: McpJson.McpJsonException) {
            throw McpTransportException("bad_response", "MCP 响应不是合法 JSON：" + error.message)
        }
        val error = parsed.field("error")
        if (error != null && error !is McpJson.Null) {
            val code = error.field("code")?.intValue() ?: -1
            val message = error.field("message")?.stringValue() ?: "未提供错误说明"
            throw McpTransportException("rpc_error", "MCP 服务端返回错误 " + code + "：" + message)
        }
        return parsed.field("result") as? McpJson.Obj ?: McpJson.Obj(emptyMap())
    }

    private suspend fun notify(method: String, params: McpJson.Obj?) {
        val fields = linkedMapOf<String, McpJson>(
            "jsonrpc" to McpJson.Str(JSONRPC_VERSION),
            "method" to McpJson.Str(method)
        )
        if (params != null) fields["params"] = params
        try {
            send(McpJson.write(McpJson.Obj(fields)), timeouts.requestMs)
        } catch (error: CancellationException) {
            throw error
        } catch (_: McpTransportException) {
            // 初始化通知是单向的：对端不认也只是少一条通知，握手本身已经完成，不该让会话失败
        }
    }

    private suspend fun send(body: String, budgetMs: Long): McpHttpResponse = withContext(Dispatchers.IO) {
        val response = withTimeoutOrNull(budgetMs) {
            transport.post(endpoint, body, requestHeaders())
        } ?: throw McpTransportException(
            "timeout",
            "MCP 请求超过 " + budgetMs + "ms 预算：" + endpoint.url
        )
        response.sessionId?.let { sessionId = it }
        response
    }

    private fun requestHeaders(): Map<String, String> {
        val headers = linkedMapOf("MCP-Protocol-Version" to PROTOCOL_VERSION)
        sessionId?.let { headers[McpHttpTransport.SESSION_HEADER] = it }
        return headers
    }

    companion object {

        const val JSONRPC_VERSION = "2.0"

        /** 与 2024-11-05 版 MCP 规范对齐：服务端不认就退回它自己声明的版本。 */
        const val PROTOCOL_VERSION = "2024-11-05"

        const val CLIENT_NAME = "wrist-agent"

        const val CLIENT_VERSION = "0.0.2"

        /** tools/list 分页上限的出厂值：远端用游标无限翻页时不能把工具循环拖住。 */
        const val MAX_TOOL_PAGES = 5

        /** 设置项可调范围：1 页（只拉第一屏）到 10 页。 */
        const val MIN_TOOL_PAGES = 1
        const val MAX_TOOL_PAGE_LIMIT = 10
    }
}
