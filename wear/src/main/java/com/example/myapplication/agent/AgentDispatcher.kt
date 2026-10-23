package com.example.myapplication.agent

import android.content.Context
import android.util.Log
import com.example.myapplication.agent.contract.AgentMemory
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.SecurityGuardrail
import com.example.myapplication.agent.contract.SecurityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * 穿戴原生交错流 Agent 调度引擎
 * 1. 宿主原生 Wear OS 契约注入与长记忆持久化锁定
 * 2. 支持交错流：先说再调、边说边调、调后反思 (中间 content 实时流式上屏)
 * 3. 动态三级安全护栏审查 (硬黑名单、小白模式 3s 倒计时锁、风险后果说明)
 * 4. 彻底免疫孤立 tool_call 导致的 HTTP 400 自愈中枢
 */
class AgentDispatcher(
    private val context: Context,
    private val toolRegistry: AgentToolRegistry,
    private val preferences: AgentPreferences
) {
    private val TAG = "AgentDispatcher"
    private val memory = AgentMemory(context)
    private val guardrail = SecurityGuardrail()
    private val conversationHistory = mutableListOf<ChatMessage>()

    // 审批挂起函数：返回 true 为允许，false 为拒绝。参数包含动作说明、风险评估
    var onRequireConfirmation: (suspend (actionText: String, risk: RiskAssessment) -> Boolean)? = null

    @Volatile
    private var isCancelled = false

    init {
        initSystemMessage()
    }

    fun cancelCurrentExecution() {
        isCancelled = true
    }

    fun resetConversation() {
        initSystemMessage()
    }

    private fun initSystemMessage() {
        conversationHistory.clear()
        val memoryHeader = memory.buildSystemMemoryHeader()
        val systemPrompt = """
            $memoryHeader
            
            你是伴生在 Android Wear OS 智能手表上的专属全能原生 Agent 智能体。
            
            【核心行为与交互准则】
            1. 极简高密度输出：手表物理屏幕极其狭小，单次答复正文严格精炼在 150~250 字以内，杜绝任何客套寒暄、开场白与废话，重点突出，分点最多 3 点。
            2. 灵活自主交错调用 (Interleaved Execution)：
               - 你可以【先向主人简短说明意图，再发起工具调用】；
               - 也可以【先调用工具获取数据，再进行深度反思与总结】；
               - 在收到工具返回的 Observation 结果后，必须结合数据自主思考下一步行动。
            3. 支持真实穿戴与云端工具链：
               - get_device_status: 读取手表电量、充电状态与硬件温度
               - query_time_weather: 查询本地系统精准时间、星期与气象
               - vibrate_device: 触发线性马达触觉反馈 (short/double/alert)
               - set_system_power_mode: 切换极致省电模式 (ultraSaving: true/false)
               - adjust_matrix_density: 调整表盘唯一点阵网格物理间距
               - web_search: 实时连接互联网搜索最新资讯与即时事实
            4. 严禁越界：未得到主人明确要求时，严禁自作主张修改表盘点阵 UI 或开关省电模式。
        """.trimIndent()

        conversationHistory.add(ChatMessage(role = AgentRole.SYSTEM, content = systemPrompt))
    }

    /**
     * 发送用户指令并启动真实多轮 Agent 交错流循环
     */
    suspend fun executeUserPrompt(
        prompt: String,
        onStateUpdate: (AgentExecutionState) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        val initialBlocks = mutableListOf<ConversationBlock>(
            ConversationBlock.Text(id = UUID.randomUUID().toString(), content = prompt, isUser = true)
        )

        var state = AgentExecutionState(
            prompt = prompt,
            thoughtChain = "正在理解主人意图...",
            conversationBlocks = initialBlocks,
            isThinking = true
        )
        onStateUpdate(state)

        conversationHistory.add(ChatMessage(role = AgentRole.USER, content = prompt))

        if (preferences.apiKey.isBlank()) {
            val errorBlock = ConversationBlock.Text(
                id = UUID.randomUUID().toString(),
                content = "未配置 API Key，请按住底栏上滑或点击右副键进入设置填入密钥。"
            )
            state = state.copy(
                isThinking = false,
                isFinished = true,
                errorMessage = "未配置 API Key",
                conversationBlocks = initialBlocks + errorBlock
            )
            onStateUpdate(state)
            return@withContext
        }

        executeInterleavedApiLoop(state, onStateUpdate)
    }

    /**
     * 真实网络 API 循环调用回路 (支持交错流文本、思考小胶囊、工具小胶囊)
     */
    private suspend fun executeInterleavedApiLoop(
        initialState: AgentExecutionState,
        onStateUpdate: (AgentExecutionState) -> Unit
    ) {
        var currentState = initialState
        val blocks = currentState.conversationBlocks.toMutableList()
        val maxTurns = 8

        for (turn in 1..maxTurns) {
            if (isCancelled) break

            currentState = currentState.copy(isThinking = true)
            onStateUpdate(currentState)

            val apiResult = callChatCompletionsApi()
            if (apiResult.isFailure) {
                val err = apiResult.exceptionOrNull()?.message ?: "网络通信异常"
                Log.e(TAG, "API 调用失败 (第 $turn 轮): $err")
                val errorBlock = ConversationBlock.Text(
                    id = UUID.randomUUID().toString(),
                    content = "网络请求失败: $err"
                )
                currentState = currentState.copy(
                    isThinking = false,
                    isFinished = true,
                    errorMessage = err,
                    conversationBlocks = blocks + errorBlock
                )
                onStateUpdate(currentState)
                return
            }

            val responseJson = apiResult.getOrNull()!!
            val choice = responseJson.optJSONArray("choices")?.optJSONObject(0)
            val msg = choice?.optJSONObject("message")
            if (msg == null) {
                currentState = currentState.copy(
                    isThinking = false,
                    isFinished = true,
                    errorMessage = "未解析到有效回复消息"
                )
                onStateUpdate(currentState)
                return
            }

            val reasoningContent = msg.optString("reasoning_content", "")
            val content = msg.optString("content", "")
            val toolCallsArray = msg.optJSONArray("tool_calls")

            // 1. 若有深度思考链，生成【思考小胶囊】插入交错流中
            if (reasoningContent.isNotBlank()) {
                val thoughtBlock = ConversationBlock.Thought(
                    id = UUID.randomUUID().toString(),
                    reasoning = reasoningContent,
                    durationMs = 2800L,
                    isExpanded = false
                )
                blocks.add(thoughtBlock)
                currentState = currentState.copy(
                    thoughtChain = reasoningContent,
                    conversationBlocks = blocks.toList()
                )
                onStateUpdate(currentState)
            }

            // 2. 若有随包附带的解释性文本（先说或边说边调），立即流式推送到交错流中！
            if (content.isNotBlank()) {
                val textBlockId = UUID.randomUUID().toString()
                val textBlock = ConversationBlock.Text(id = textBlockId, content = "", isStreaming = true)
                blocks.add(textBlock)
                currentState = currentState.copy(conversationBlocks = blocks.toList())
                onStateUpdate(currentState)

                val sb = StringBuilder()
                for (ch in content) {
                    if (isCancelled) break
                    sb.append(ch)
                    val idx = blocks.indexOfFirst { it.id == textBlockId }
                    if (idx != -1) {
                        blocks[idx] = ConversationBlock.Text(id = textBlockId, content = sb.toString(), isStreaming = true)
                        currentState = currentState.copy(conversationBlocks = blocks.toList(), finalResponse = sb.toString())
                        onStateUpdate(currentState)
                    }
                    delay(12)
                }
                // 流式结束
                val finalIdx = blocks.indexOfFirst { it.id == textBlockId }
                if (finalIdx != -1) {
                    blocks[finalIdx] = ConversationBlock.Text(id = textBlockId, content = content, isStreaming = false)
                    currentState = currentState.copy(conversationBlocks = blocks.toList())
                    onStateUpdate(currentState)
                }
            }

            // 3. 检查是否有工具调用
            if (toolCallsArray != null && toolCallsArray.length() > 0) {
                val currentCalls = mutableListOf<ToolCall>()
                for (i in 0 until toolCallsArray.length()) {
                    currentCalls.add(ToolCall.fromJson(toolCallsArray.getJSONObject(i)))
                }

                // 将 assistant 的消息记录加入历史
                conversationHistory.add(
                    ChatMessage(
                        role = AgentRole.ASSISTANT,
                        content = if (content.isNotBlank()) content else null,
                        toolCalls = currentCalls
                    )
                )

                // 遍历执行工具
                for (call in currentCalls) {
                    if (isCancelled) {
                        handleCancelledTools(currentCalls)
                        currentState = currentState.copy(
                            isThinking = false,
                            isExecutingTool = false,
                            isFinished = true
                        )
                        onStateUpdate(currentState)
                        return
                    }

                    // 安全审查评估
                    val risk = guardrail.assessTool(call.name, call.arguments, preferences.securityMode)
                    
                    // A. 致命黑名单硬拦截
                    if (risk.isHardBlocked) {
                        val blockedMsg = "致命指令已被安全护栏拦截：${risk.riskSummary}"
                        val toolBlock = ConversationBlock.ToolCall(
                            id = UUID.randomUUID().toString(),
                            toolName = call.name,
                            argsJson = call.arguments,
                            resultSummary = blockedMsg,
                            isExecuting = false
                        )
                        blocks.add(toolBlock)
                        conversationHistory.add(
                            ChatMessage(role = AgentRole.TOOL, content = blockedMsg, toolCallId = call.id, name = call.name)
                        )
                        currentState = currentState.copy(conversationBlocks = blocks.toList())
                        onStateUpdate(currentState)
                        continue
                    }

                    // B. 审批判断
                    val needApproval = when (preferences.securityMode) {
                        SecurityMode.NOVICE -> true // 小白模式全部审批
                        SecurityMode.STANDARD -> risk.isHighRisk // 普通人模式仅高危审批
                        SecurityMode.EXPERT -> false // 大佬模式免审批
                    }

                    if (needApproval) {
                        // 触发审批态与红瀑光相变
                        currentState = currentState.copy(
                            approvalVisualState = ApprovalVisualState.PENDING_CASCADE,
                            pendingRiskAssessment = risk
                        )
                        onStateUpdate(currentState)

                        val actionDesc = "执行 [${call.name}]"
                        val allowed = onRequireConfirmation?.invoke(actionDesc, risk) ?: true

                        if (allowed) {
                            // 批准转绿
                            currentState = currentState.copy(
                                approvalVisualState = ApprovalVisualState.APPROVED_GREEN,
                                pendingRiskAssessment = null
                            )
                            onStateUpdate(currentState)
                            delay(260)
                            currentState = currentState.copy(approvalVisualState = ApprovalVisualState.NONE)
                            onStateUpdate(currentState)
                        } else {
                            // 拒绝退潮
                            currentState = currentState.copy(
                                approvalVisualState = ApprovalVisualState.REJECTED_EBB,
                                pendingRiskAssessment = null
                            )
                            onStateUpdate(currentState)
                            delay(220)
                            currentState = currentState.copy(approvalVisualState = ApprovalVisualState.NONE)
                            onStateUpdate(currentState)

                            val rejectJson = JSONObject().apply {
                                put("status", "rejected")
                                put("message", "主人已在手腕端拒绝执行此操作")
                            }.toString()

                            conversationHistory.add(
                                ChatMessage(role = AgentRole.TOOL, content = rejectJson, toolCallId = call.id, name = call.name)
                            )
                            val toolBlock = ConversationBlock.ToolCall(
                                id = UUID.randomUUID().toString(),
                                toolName = call.name,
                                argsJson = call.arguments,
                                resultSummary = "已被主人拒绝取消",
                                isExecuting = false
                            )
                            blocks.add(toolBlock)
                            currentState = currentState.copy(conversationBlocks = blocks.toList())
                            onStateUpdate(currentState)
                            continue
                        }
                    }

                    // C. 真实执行工具并生成【工具小胶囊】
                    val toolBlockId = UUID.randomUUID().toString()
                    val executingBlock = ConversationBlock.ToolCall(
                        id = toolBlockId,
                        toolName = call.name,
                        argsJson = call.arguments,
                        isExecuting = true
                    )
                    blocks.add(executingBlock)
                    currentState = currentState.copy(
                        isExecutingTool = true,
                        conversationBlocks = blocks.toList()
                    )
                    onStateUpdate(currentState)

                    val result = toolRegistry.executeTool(call)

                    // 清洗并提取精炼摘要
                    val summaryText = extractToolSummary(call.name, result.output)
                    val completedBlockIdx = blocks.indexOfFirst { it.id == toolBlockId }
                    if (completedBlockIdx != -1) {
                        blocks[completedBlockIdx] = ConversationBlock.ToolCall(
                            id = toolBlockId,
                            toolName = call.name,
                            argsJson = call.arguments,
                            resultSummary = summaryText,
                            isExecuting = false
                        )
                    }

                    conversationHistory.add(
                        ChatMessage(
                            role = AgentRole.TOOL,
                            content = result.output,
                            toolCallId = result.toolCallId,
                            name = result.toolName
                        )
                    )

                    currentState = currentState.copy(
                        isExecutingTool = false,
                        conversationBlocks = blocks.toList()
                    )
                    onStateUpdate(currentState)
                }

                // 进入下一轮，将工具结果喂给模型二次反思与自主决策
                delay(150)
            } else {
                // 模型自主判定任务结束，无后续工具
                Log.d(TAG, "第 $turn 轮：Agent 自主判定长任务完成收口")
                conversationHistory.add(ChatMessage(role = AgentRole.ASSISTANT, content = content))
                currentState = currentState.copy(
                    isThinking = false,
                    isExecutingTool = false,
                    isFinished = true
                )
                onStateUpdate(currentState)
                return
            }
        }

        currentState = currentState.copy(
            isThinking = false,
            isExecutingTool = false,
            isFinished = true
        )
        onStateUpdate(currentState)
    }

    private fun extractToolSummary(toolName: String, outputJson: String): String {
        return try {
            val json = JSONObject(outputJson)
            when (toolName) {
                "get_device_status" -> "电量 ${json.optInt("battery_level_percent", 0)}% · 温控 ${json.optDouble("battery_temperature_celsius", 0.0)}℃"
                "web_search" -> "已检索 ${json.optInt("results_count", 1)} 条互联网最新事实"
                "query_time_weather" -> "${json.optString("temperature", "22℃")} · ${json.optString("weather", "晴朗")}"
                "vibrate_device" -> "触觉脉冲已触发"
                "set_system_power_mode" -> if (json.optBoolean("ultra_saving_enabled", false)) "极致省电已开启" else "标准性能已恢复"
                "adjust_matrix_density" -> "点阵网格已更新"
                else -> json.optString("message", "执行成功")
            }
        } catch (_: Exception) {
            "执行成功"
        }
    }

    private fun handleCancelledTools(pendingCalls: List<ToolCall>) {
        for (call in pendingCalls) {
            val cancelJson = JSONObject().apply {
                put("status", "cancelled")
                put("message", "任务已被主人终止")
            }.toString()
            conversationHistory.add(
                ChatMessage(role = AgentRole.TOOL, content = cancelJson, toolCallId = call.id, name = call.name)
            )
        }
    }

    private fun sanitizeConversationHistory() {
        val sanitized = mutableListOf<ChatMessage>()
        var i = 0
        while (i < conversationHistory.size) {
            val msg = conversationHistory[i]
            sanitized.add(msg)
            if (msg.role == AgentRole.ASSISTANT && !msg.toolCalls.isNullOrEmpty()) {
                val requiredToolCallIds = msg.toolCalls.map { it.id to it.name }.toMutableList()
                var j = i + 1
                while (j < conversationHistory.size && conversationHistory[j].role == AgentRole.TOOL) {
                    val toolMsg = conversationHistory[j]
                    sanitized.add(toolMsg)
                    requiredToolCallIds.removeAll { it.first == toolMsg.toolCallId }
                    j++
                }
                for ((missingId, missingName) in requiredToolCallIds) {
                    val fallbackContent = JSONObject().apply {
                        put("status", "cancelled")
                        put("message", "任务中断，回执自愈补齐")
                    }.toString()
                    sanitized.add(
                        ChatMessage(role = AgentRole.TOOL, content = fallbackContent, toolCallId = missingId, name = missingName)
                    )
                }
                i = j
            } else {
                i++
            }
        }
        conversationHistory.clear()
        conversationHistory.addAll(sanitized)
    }

    private fun callChatCompletionsApi(): Result<JSONObject> {
        return try {
            sanitizeConversationHistory()

            val endpoint = if (preferences.baseUrl.endsWith("/")) {
                preferences.baseUrl + "chat/completions"
            } else {
                preferences.baseUrl + "/chat/completions"
            }
            val url = URL(endpoint)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Authorization", "Bearer ${preferences.apiKey.trim()}")
            conn.connectTimeout = 15000
            conn.readTimeout = 40000
            conn.doOutput = true

            val requestBody = JSONObject().apply {
                put("model", preferences.modelName)
                put("temperature", preferences.temperature)
                val msgs = JSONArray()
                conversationHistory.forEach { msgs.put(it.toJson()) }
                put("messages", msgs)

                val tools = JSONArray()
                toolRegistry.getToolDefinitions().forEach { tools.put(it.toJson()) }
                put("tools", tools)
                put("tool_choice", "auto")
            }

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(requestBody.toString()) }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
                reader.close()
                Result.success(JSONObject(sb.toString()))
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
