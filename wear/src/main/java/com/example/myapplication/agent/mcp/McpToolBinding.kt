package com.example.myapplication.agent.mcp

import com.example.myapplication.agent.ToolDefinition
import com.example.myapplication.agent.ToolResult
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class McpRegisteredTool(
    val localName: String,
    val remote: McpRemoteTool
)

data class McpSnapshot(
    val endpoint: McpEndpoint? = null,
    val tools: List<McpRegisteredTool> = emptyList(),
    val fetchedAtMs: Long = 0L,
    val serverName: String = "",
    val protocolVersion: String = "",
    val lastError: String? = null
)

data class McpRefreshReport(
    val status: String,
    val endpoint: String,
    val tools: List<String>,
    val serverName: String,
    val protocolVersion: String,
    val errorKind: String,
    val error: String
) {
    val isOk: Boolean get() = status == "ok"
}

/**
 * 把远端 MCP 工具接进本地工具清单的装配点。
 *
 * 三条硬约束决定了这个类的形状：
 * 1. 工具清单是在请求构建路径上同步取的，绝不能在那里做网络 IO —— 只读缓存，
 *    首次/过期时丢一个后台刷新（[ensureFresh]），清单下一轮就位；
 * 2. 开关关闭时既不注册也不连接（[toolDefinitions] 直接返回空、[refresh] 直接返回 disabled）；
 * 3. 任何失败都必须变成一条可读的 [ToolResult]，绝不向上抛 —— 远端挂了不能拖死工具循环。
 */
class McpToolBinding(
    private val endpointSource: () -> String,
    private val enabledSource: () -> Boolean,
    private val timeouts: McpTimeouts = McpTimeouts(),
    private val staleAfterMs: Long = DEFAULT_STALE_MS,
    private val failureBackoffMs: Long = DEFAULT_BACKOFF_MS,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val now: () -> Long = { System.currentTimeMillis() },
    /** tasks/list 分页上限，来自设置项 mcp_max_tool_pages。 */
    private val maxToolPages: Int = McpClient.MAX_TOOL_PAGES,
    private val clientFactory: (McpEndpoint) -> McpClient = { endpoint ->
        McpClient(endpoint, timeouts, maxToolPages = maxToolPages)
    }
) : Closeable {

    @Volatile
    private var snapshotValue = McpSnapshot()

    @Volatile
    private var retryAtMs = 0L

    @Volatile
    private var session: McpClient? = null

    private val refreshLock = Mutex()

    private val sessionLock = Mutex()

    fun isEnabled(): Boolean =
        enabledSource() && McpEndpointPolicy.parse(endpointSource()) is McpEndpointVerdict.Accept

    /** 名字带 mcp_ 前缀就算远端工具：清单还没到手时也要能把"没连上"如实回给模型。 */
    fun isMcpToolName(name: String): Boolean = McpToolNaming.isMcpToolName(name)

    fun snapshot(): McpSnapshot = snapshotValue

    /** 工具清单读取口：只读缓存，需要时丢一次后台刷新，绝不在这里阻塞。 */
    fun toolDefinitions(reserved: Set<String>): List<ToolDefinition> {
        if (!enabledSource()) return emptyList()
        ensureFresh()
        val snapshot = snapshotValue
        if (snapshot.tools.isEmpty()) return emptyList()
        val host = snapshot.endpoint?.authority ?: ""
        return snapshot.tools
            .filter { tool -> tool.localName !in reserved }
            .map { tool -> tool.toDefinition(host) }
    }

    /** 后台刷新：过期 + 开关打开 + 不在失败冷却期 + 没有并发的刷新，四个条件同时满足才发起。 */
    fun ensureFresh() {
        if (!enabledSource()) return
        if (now() < retryAtMs) return
        val snapshot = snapshotValue
        val fresh = snapshot.fetchedAtMs > 0L && now() - snapshot.fetchedAtMs < staleAfterMs
        if (fresh) return
        val endpoint = when (val verdict = McpEndpointPolicy.parse(endpointSource())) {
            is McpEndpointVerdict.Accept -> verdict.endpoint
            is McpEndpointVerdict.Reject -> {
                retryAtMs = now() + failureBackoffMs
                return
            }
        }
        scope.launch {
            if (!refreshLock.tryLock()) return@launch
            try {
                refreshLocked(endpoint)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // refreshLocked 已经把业务异常转成报告；这里兜住实现之外的意外，后台协程绝不外抛
            } finally {
                refreshLock.unlock()
            }
        }
    }

    /** 显式刷新（测试与需要立刻拿到结论的调用方用）。 */
    suspend fun refresh(): McpRefreshReport {
        if (!enabledSource()) {
            return McpRefreshReport("disabled", "", emptyList(), "", "", "disabled", "MCP 开关已关闭：不会发起任何连接")
        }
        val endpoint = when (val verdict = McpEndpointPolicy.parse(endpointSource())) {
            is McpEndpointVerdict.Accept -> verdict.endpoint
            is McpEndpointVerdict.Reject ->
                return McpRefreshReport("invalid_endpoint", endpointSource(), emptyList(), "", "", "invalid_endpoint", verdict.reason)
        }
        refreshLock.lock()
        try {
            return refreshLocked(endpoint)
        } finally {
            refreshLock.unlock()
        }
    }

    /**
     * 执行一个远端工具，结果永远是一个 [ToolResult]。
     *
     * 清单还没有（首次调用、上次连接失败、或用户刚清空过缓存）时先补一次有界刷新，
     * 于是"连不上"会以 mcp_connect 之类的具体错误回到模型，而不是含糊的"没有这个工具"。
     */
    suspend fun toolResult(callId: String, localName: String, argumentsJson: String): ToolResult {
        if (!enabledSource()) {
            return failure(callId, localName, "mcp_disabled", "MCP 开关已关闭：远端工具不会注册，也不会被调用")
        }
        val endpoint = when (val verdict = McpEndpointPolicy.parse(endpointSource())) {
            is McpEndpointVerdict.Accept -> verdict.endpoint
            is McpEndpointVerdict.Reject -> return failure(callId, localName, "mcp_invalid_endpoint", verdict.reason)
        }
        val known = snapshotValue.tools.firstOrNull { tool -> tool.localName == localName }
        if (known != null) return execute(callId, localName, argumentsJson, known, endpoint)

        val report = refresh()
        if (!report.isOk) {
            return failure(callId, localName, "mcp_" + report.errorKind, "MCP 远端工具暂不可用：" + report.error)
        }
        val discovered = snapshotValue.tools.firstOrNull { tool -> tool.localName == localName }
            ?: return failure(
                callId,
                localName,
                "mcp_unknown_tool",
                "MCP 远端工具清单里没有 " + localName + "，服务端可能已经下掉这个工具"
            )
        return execute(callId, localName, argumentsJson, discovered, endpoint)
    }

    override fun close() {
        dropSession()
        scope.cancel()
    }

    private suspend fun refreshLocked(endpoint: McpEndpoint): McpRefreshReport {
        return try {
            val client = sessionFor(endpoint)
            val info = client.ensureInitialized()
            val registered = registered(client.listTools())
            snapshotValue = McpSnapshot(
                endpoint = endpoint,
                tools = registered,
                fetchedAtMs = now(),
                serverName = info.name,
                protocolVersion = info.protocolVersion,
                lastError = null
            )
            retryAtMs = 0L
            McpRefreshReport(
                status = "ok",
                endpoint = endpoint.url,
                tools = registered.map { tool -> tool.localName },
                serverName = info.name,
                protocolVersion = info.protocolVersion,
                errorKind = "",
                error = ""
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val kind = (error as? McpTransportException)?.kind ?: "unexpected"
            val detail = error.message ?: error.javaClass.simpleName
            // 远端不可用时清空清单：已经连不上的工具不该继续出现在模型能看到的定义里
            snapshotValue = snapshotValue.copy(tools = emptyList(), fetchedAtMs = 0L, lastError = detail)
            retryAtMs = now() + failureBackoffMs
            dropSession()
            McpRefreshReport("failed", endpoint.url, emptyList(), "", "", kind, detail)
        }
    }

    private suspend fun execute(
        callId: String,
        localName: String,
        argumentsJson: String,
        entry: McpRegisteredTool,
        endpoint: McpEndpoint
    ): ToolResult {
        val arguments = when (val parsed = runCatching { McpJson.parse(argumentsJson) }.getOrNull()) {
            null -> McpJson.Obj(emptyMap())
            is McpJson.Obj -> parsed
            else -> return failure(
                callId,
                localName,
                "mcp_bad_arguments",
                "MCP 工具入参必须是 JSON 对象，收到：" + argumentsJson.take(200)
            )
        }
        val started = now()
        return try {
            val outcome = sessionFor(endpoint).callTool(entry.remote.name, arguments)
            ToolResult(
                toolCallId = callId,
                toolName = localName,
                output = McpJson.write(resultJson(entry, endpoint, outcome, now() - started)),
                isError = false
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            val kind = (error as? McpTransportException)?.kind ?: "unexpected"
            val detail = error.message ?: error.javaClass.simpleName
            snapshotValue = snapshotValue.copy(tools = emptyList(), fetchedAtMs = 0L, lastError = detail)
            retryAtMs = now() + failureBackoffMs
            dropSession()
            failure(callId, localName, "mcp_" + kind, "MCP 调用失败（" + (now() - started) + "ms）：" + detail)
        }
    }

    private fun resultJson(
        entry: McpRegisteredTool,
        endpoint: McpEndpoint,
        outcome: McpCallResult,
        elapsedMs: Long
    ): McpJson.Obj {
        val isError = outcome.isError
        val summary = if (isError) {
            "MCP 工具 " + entry.remote.name + " 自报失败：" + outcome.text.take(200)
        } else {
            "MCP 工具 " + entry.remote.name + " 执行完成（" + elapsedMs + "ms）"
        }
        val fields = linkedMapOf<String, McpJson>(
            "status" to McpJson.Str(if (isError) "error" else "success"),
            "source" to McpJson.Str("mcp"),
            "server" to McpJson.Str(endpoint.authority),
            "tool" to McpJson.Str(entry.remote.name),
            "local_tool" to McpJson.Str(entry.localName),
            "elapsed_ms" to McpJson.Num(elapsedMs.toDouble()),
            "is_error" to McpJson.Bool(isError),
            "text" to McpJson.Str(outcome.text),
            "content" to McpJson.Arr(outcome.content),
            "summary" to McpJson.Str(summary)
        )
        return McpJson.Obj(fields)
    }

    private fun failure(callId: String, localName: String, kind: String, message: String): ToolResult {
        val fields = linkedMapOf<String, McpJson>(
            "status" to McpJson.Str("error"),
            "error_type" to McpJson.Str(kind),
            "message" to McpJson.Str(message),
            "source" to McpJson.Str("mcp"),
            "tool" to McpJson.Str(localName),
            "degraded" to McpJson.Bool(true)
        )
        return ToolResult(
            toolCallId = callId,
            toolName = localName,
            output = McpJson.write(McpJson.Obj(fields)),
            isError = false
        )
    }

    private fun registered(remoteTools: List<McpRemoteTool>): List<McpRegisteredTool> {
        val taken = linkedSetOf<String>()
        val out = mutableListOf<McpRegisteredTool>()
        for (tool in remoteTools) {
            if (tool.name.isBlank()) continue
            val local = McpToolNaming.localName(tool.name, taken)
            taken.add(local)
            out.add(McpRegisteredTool(local, tool))
        }
        return out
    }

    private suspend fun sessionFor(endpoint: McpEndpoint): McpClient = sessionLock.withLock {
        val existing = session
        if (existing != null && existing.endpoint.url == endpoint.url) {
            existing
        } else {
            existing?.close()
            val created = clientFactory(endpoint)
            session = created
            created
        }
    }

    @Synchronized
    private fun dropSession() {
        session?.close()
        session = null
    }

    private fun McpRegisteredTool.toDefinition(host: String): ToolDefinition = ToolDefinition(
        name = localName,
        description = describe(host, remote),
        parametersSchema = McpJsonBridge.toParametersSchema(remote.inputSchema)
    )

    private fun describe(host: String, remote: McpRemoteTool): String {
        val head = "[MCP " + host + "] " + remote.name
        val body = remote.description.trim()
        return if (body.isEmpty()) head else head + "：" + body
    }

    companion object {

        /** 清单过期时间：六十秒内不重复握手，避免每轮请求都打远端。 */
        const val DEFAULT_STALE_MS = 60_000L

        /** 失败冷却：远端挂掉后十五秒内不再重试，避免每个工具循环轮次都等一次连接超时。 */
        const val DEFAULT_BACKOFF_MS = 15_000L
    }
}
