package com.example.myapplication.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/** 一次模型轮次的产物：正文 + 思考 + 要执行的工具。子代理与主循环共用这一形状。 */
data class SubAgentTurn(
    val content: String,
    val reasoning: String = "",
    val toolCalls: List<ToolCall> = emptyList()
)

/**
 * 子代理的执行回执。纯 Kotlin 结构，落 JSON 只发生在工具层边界（[toJson]）。
 * status 只有 success / error 两档：失败一律走 error + error_type，绝不把异常抛给调用方。
 */
data class SubAgentReport(
    val status: String,
    val summary: String,
    val toolCalls: Int,
    val elapsedMs: Long,
    val errorType: String? = null,
    val message: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("status", status)
        put("summary", summary)
        put("tool_calls", toolCalls)
        put("elapsed_ms", elapsedMs)
        errorType?.let { put("error_type", it) }
        message?.let { put("message", it) }
    }

    companion object {
        const val STATUS_SUCCESS = "success"
        const val STATUS_ERROR = "error"

        const val ERROR_CANCELLED = "cancelled"
        const val ERROR_TIMEOUT = "timeout"
        const val ERROR_DEPTH = "depth_exceeded"
        const val ERROR_REQUEST = "request_failed"
        const val ERROR_IDLE_SPIN = "idle_spin"
        const val ERROR_INVALID_TASK = "invalid_task"
    }
}

/**
 * 子代理的嵌套 agent 循环。
 *
 * 上下文隔离：自己持有一份 history（system + task），全程不碰主对话的 conversationHistory，
 * 只把最终结论作为 [SubAgentReport] 交回去；工具集由调用方按"去掉 run_subagent"给出。
 * 轮数：与主循环共用 [AgentLoopPolicy]，无上限，靠取消 / 能量提示 / 空转检测 / 超时退出。
 */
class SubAgentRunner(
    private val requestTurn: suspend (history: List<ChatMessage>, tools: List<ToolDefinition>) -> SubAgentTurn,
    private val executeTool: suspend (call: ToolCall) -> ToolResult,
    private val availableTools: () -> List<ToolDefinition>,
    private val isCancelled: () -> Boolean = { false },
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    suspend fun run(
        parentDepth: Int,
        systemPrompt: String,
        task: String,
        focus: String?
    ): SubAgentReport {
        val startedAt = clock()
        if (!SubAgentPolicy.depthAllowed(parentDepth)) {
            return errorReport(
                type = SubAgentReport.ERROR_DEPTH,
                message = "子代理里不能再开子代理（最大深度 " + SubAgentPolicy.MAX_DEPTH + "）",
                executed = 0,
                startedAt = startedAt
            )
        }
        val trimmedTask = task.trim()
        if (trimmedTask.isEmpty()) {
            return errorReport(
                type = SubAgentReport.ERROR_INVALID_TASK,
                message = "子任务描述不可为空",
                executed = 0,
                startedAt = startedAt
            )
        }

        // 子代理自己的上下文：system + task，主对话一条都不进来
        val history = mutableListOf(
            ChatMessage(role = AgentRole.SYSTEM, content = systemPrompt),
            ChatMessage(role = AgentRole.USER, content = taskPrompt(trimmedTask, focus))
        )
        val tools = availableTools().filter { it.name != SubAgentPolicy.TOOL_NAME }

        val report = withTimeoutOrNull(timeoutMs) { loop(history, tools, startedAt) }
        return report ?: errorReport(
            type = SubAgentReport.ERROR_TIMEOUT,
            message = "子代理超过 " + timeoutMs + "ms 未收口，已按超时结束",
            executed = 0,
            startedAt = startedAt
        )
    }

    private suspend fun loop(
        history: MutableList<ChatMessage>,
        tools: List<ToolDefinition>,
        startedAt: Long
    ): SubAgentReport {
        var executed = 0
        var consecutiveToolTurns = 0
        var energyHinted = false
        val spinDetector = IdleSpinDetector()

        // 无轮数上限：退出只看取消 / 无工具 / 异常 / 提示后空转 / 超时
        while (true) {
            if (isCancelled()) return cancelled(executed, startedAt)

            val turn = try {
                requestTurn(history.toList(), tools)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return errorReport(
                    type = SubAgentReport.ERROR_REQUEST,
                    message = e.message ?: "子代理模型请求失败",
                    executed = executed,
                    startedAt = startedAt
                )
            }

            val calls = turn.toolCalls
            if (calls.isEmpty()) {
                // 模型不再要工具：这就是子代理的最终结论
                return SubAgentReport(
                    status = SubAgentReport.STATUS_SUCCESS,
                    summary = turn.content.trim().ifEmpty { "子代理已完成任务，但没有给出文字结论" },
                    toolCalls = executed,
                    elapsedMs = clock() - startedAt
                )
            }

            history.add(
                ChatMessage(
                    role = AgentRole.ASSISTANT,
                    content = turn.content.ifBlank { null },
                    toolCalls = calls
                )
            )
            val spin = spinDetector.observe(AgentLoopPolicy.signatureOf(calls))

            for (call in calls) {
                if (isCancelled()) return cancelled(executed, startedAt)
                val result = try {
                    executeTool(call)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 单个工具炸了不掐死子代理：把错误回执喂回去，让它换路子
                    ToolResult(
                        toolCallId = call.id,
                        toolName = call.name,
                        output = toolFailureJson(e),
                        isError = true
                    )
                }
                executed += 1
                history.add(
                    ChatMessage(
                        role = AgentRole.TOOL,
                        content = result.output,
                        toolCallId = result.toolCallId,
                        name = result.toolName
                    )
                )
            }

            consecutiveToolTurns += 1
            if (AgentLoopPolicy.shouldInjectEnergyHint(consecutiveToolTurns, energyHinted)) {
                energyHinted = true
                // 与主循环同一注入方式：请求侧只取第一条 system，因此这里用带前缀的 user 消息
                history.add(
                    ChatMessage(
                        role = AgentRole.USER,
                        content = AgentLoopPolicy.energyHintText(consecutiveToolTurns)
                    )
                )
            }
            if (spin == IdleSpinDecision.STOP) {
                return errorReport(
                    type = SubAgentReport.ERROR_IDLE_SPIN,
                    message = "子代理连续 " + AgentLoopPolicy.IDLE_SPIN_STOP_AFTER +
                        " 次重复同一份调用，已收口",
                    executed = executed,
                    startedAt = startedAt
                )
            }
        }
    }

    private fun cancelled(executed: Int, startedAt: Long) = errorReport(
        type = SubAgentReport.ERROR_CANCELLED,
        message = "任务已被主人终止",
        executed = executed,
        startedAt = startedAt
    )

    private fun errorReport(type: String, message: String, executed: Int, startedAt: Long) =
        SubAgentReport(
            status = SubAgentReport.STATUS_ERROR,
            summary = "",
            toolCalls = executed,
            elapsedMs = clock() - startedAt,
            errorType = type,
            message = message
        )

    private fun taskPrompt(task: String, focus: String?): String {
        val trimmedFocus = focus?.trim().orEmpty()
        return if (trimmedFocus.isEmpty()) task else task + "\n\n[只关注]\n" + trimmedFocus
    }

    private fun toolFailureJson(e: Exception): String {
        val detail = runCatching {
            JSONObject().apply {
                put("status", "error")
                put("error_type", e.javaClass.simpleName)
                put("message", e.message ?: "子代理工具执行故障")
                put("recovery_hint", "换一条路径或直接给出结论")
            }.toString()
        }.getOrNull()
        // 回执必须是非空文本：序列化拿不到内容就降级成一行纯文本，绝不把 null 塞进 ToolResult
        return detail?.takeIf { it.isNotBlank() }
            ?: ("tool_failed: " + e.javaClass.simpleName + " " + (e.message ?: ""))
    }

    companion object {
        /** 单个子代理的整体时限：超时返回结构化错误，不把调用方吊死。 */
        const val DEFAULT_TIMEOUT_MS = 120_000L
    }
}
