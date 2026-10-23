package com.example.myapplication.agent

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

    private fun buildDynamicTelemetryBlock(): String {
        val nowStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.getDefault()).format(Date())
        val batteryLevel = runCatching {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val isCharging = (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) == BatteryManager.BATTERY_STATUS_CHARGING
            if (level != -1 && scale != -1) {
                "${(level * 100 / scale.toFloat()).toInt()}% (${if (isCharging) "充电中" else "放电中"})"
            } else "未知"
        }.getOrDefault("未知")

        val clipSnippet = runCatching {
            val clipManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = clipManager?.primaryClip?.getItemAt(0)
            item?.text?.toString()?.take(60) ?: "空"
        }.getOrDefault("空")

        return """
        [宿主物理环境与实时感知 Telemetry]:
        - 当前精准系统时刻: $nowStr
        - 宿主设备物理形态: Wear OS 圆屏穿戴设备 (Pixel Watch 2 / 圆形表盘)
        - 电池遥测状态: $batteryLevel
        - 剪贴板最新文字: "$clipSnippet"
        在回答主人关于时间、日期、电量或刚才复制的内容时，直接引用上述客观事实，无需重复反问。
        """.trimIndent()
    }

    private fun initSystemMessage() {
        conversationHistory.clear()
        val memoryHeader = memory.buildSystemMemoryHeader()
        val telemetryBlock = buildDynamicTelemetryBlock()
        val basePrompt = if (preferences.customSystemPrompt.isNotBlank()) {
            preferences.customSystemPrompt
        } else {
            """
            你是运行在 Android Wear OS 智能手表上的个人全自主伴生智能体（Autonomous Wearable Companion Agent）。
            
            【环境与存在形态】
            - 宿主环境为圆形表盘智能穿戴设备，是用户贴身常驻的随身参谋、技术助理与日常管家。
            - 拥有高度开放的理解、反思、规划与自主执行权限。不要自我设限，充分发挥大模型的深层推理与洞察力，为用户提供切实、高效、有深度的建议与行动闭环。
            
            【感知与物理行动能力】
            你可以根据上下文需要，自主调用穿戴设备底层硬件与云端工具链（ReAct Function Calling）：
            - get_device_status: 感知手表电量、充电状态、芯片温度与硬件健康；
            - query_time_weather: 获取精准系统时刻、星期、节假日与气象环境；
            - vibrate_device: 调度线性马达执行触觉反馈（short/double/alert 脉冲）；
            - set_system_power_mode: 自主协同系统省电策略与性能配置；
            - adjust_matrix_density: 协同微晶点阵网格物理间距，营造适宜视觉或节能环境；
            - web_search: 穿透广域互联网检索即时资讯、知识深度考据与生活事实。
            
            【最高权限与协作准则】
            - 遇到复杂目标时，主动对任务进行多步拆解，自主编排工具调用链路（先查感知、再搜网络、最终交织分析）。
            - 输出表达清晰利落、信息密度高，直击要害，适配智能手表快速扫视与移动交互场景。
            - 在高危或涉及系统持久修改的操作前，向主人通报意图与后果，由内置安全护栏协同守护。
            """.trimIndent()
        }
        val systemPrompt = "$memoryHeader\n\n$telemetryBlock\n\n$basePrompt"

        conversationHistory.add(ChatMessage(role = AgentRole.SYSTEM, content = systemPrompt))
    }

    fun clearLongTermMemory() {
        memory.longTermNotes = ""
        initSystemMessage()
    }

    fun getMemoryFactCount(): Int {
        val notes = memory.longTermNotes
        if (notes.isBlank()) return 0
        return notes.split("\n", ";", "；").count { it.isNotBlank() }
    }

    fun getRegisteredTools(): List<ToolDefinition> = toolRegistry.getToolDefinitions()

    fun getConversationHistory(): List<ChatMessage> = conversationHistory.toList()

    fun loadConversationHistory(history: List<ChatMessage>) {
        conversationHistory.clear()
        if (history.isEmpty()) {
            initSystemMessage()
        } else {
            conversationHistory.addAll(history)
        }
    }

    /**
     * 发送用户指令并启动真实多轮 Agent 交错流循环 (支持连贯追加历史对话)
     */
    suspend fun executeUserPrompt(
        prompt: String,
        existingBlocks: List<ConversationBlock> = emptyList(),
        onStateUpdate: (AgentExecutionState) -> Unit
    ) = withContext(Dispatchers.IO) {
        isCancelled = false
        val initialBlocks = existingBlocks.toMutableList().apply {
            add(ConversationBlock.Text(id = UUID.randomUUID().toString(), content = prompt, isUser = true))
        }

        var state = AgentExecutionState(
            prompt = prompt,
            thoughtChain = "正在自主规划...",
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
     * 真实网络 API 循环调用回路 (放开上下文上限，自主决策完成为止)
     */
    private suspend fun executeInterleavedApiLoop(
        initialState: AgentExecutionState,
        onStateUpdate: (AgentExecutionState) -> Unit
    ) {
        var currentState = initialState
        val blocks = currentState.conversationBlocks.toMutableList()
        val maxTurns = 5 // 穿戴设备严格限额 5 轮，防止死循环大量烧电发热

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

                    val result = try {
                        toolRegistry.executeTool(call)
                    } catch (e: Exception) {
                        Log.e(TAG, "工具执行异常自愈处理: ${e.message}", e)
                        ToolResult(
                            toolCallId = call.id,
                            toolName = call.name,
                            output = JSONObject().apply {
                                put("status", "error")
                                put("error_type", e.javaClass.simpleName)
                                put("message", e.message ?: "执行故障")
                                put("recovery_hint", "请根据错误提示调整入参重试或直接向主人汇报。")
                            }.toString()
                        )
                    }

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
                put("max_tokens", preferences.maxOutputTokens)
                val msgs = JSONArray()
                val turnsLimit = preferences.maxContextTurns
                val systemMsg = conversationHistory.firstOrNull { it.role == AgentRole.SYSTEM }
                val dialogMsgs = conversationHistory.filter { it.role != AgentRole.SYSTEM }.takeLast(turnsLimit * 2)
                if (systemMsg != null) msgs.put(systemMsg.toJson())
                dialogMsgs.forEach { msgs.put(it.toJson()) }
                put("messages", msgs)

                val toolDefs = toolRegistry.getToolDefinitions()
                if (toolDefs.isNotEmpty()) {
                    val tools = JSONArray()
                    toolDefs.forEach { tools.put(it.toJson()) }
                    put("tools", tools)
                    put("tool_choice", "auto")
                }
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

    companion object {
        suspend fun fetchRemoteModels(baseUrl: String, apiKey: String): Pair<Long, List<String>> = withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val cleanBase = baseUrl.trim().trimEnd('/')
            val urlStr = if (cleanBase.endsWith("/v1/models")) cleanBase else "$cleanBase/v1/models"
            try {
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (apiKey.isNotBlank()) {
                    conn.setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                }
                conn.setRequestProperty("Content-Type", "application/json")

                val code = conn.responseCode
                val latency = System.currentTimeMillis() - startTime
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(body)
                    val dataArr = json.optJSONArray("data")
                    val result = mutableListOf<String>()
                    if (dataArr != null) {
                        for (i in 0 until dataArr.length()) {
                            val id = dataArr.optJSONObject(i)?.optString("id")
                            if (!id.isNullOrBlank()) {
                                result.add(id)
                            }
                        }
                    }
                    latency to result
                } else {
                    -1L to emptyList()
                }
            } catch (e: Exception) {
                -1L to emptyList()
            }
        }
    }
}
