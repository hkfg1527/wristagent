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
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityGuardrail
import com.example.myapplication.agent.contract.SecurityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
 * 3. 动态三级安全护栏审查 (硬黑名单、严格确认 3s 倒计时锁、风险后果说明) + AI 风险等级分级闸门
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

    // 审批挂起函数：返回 true 为允许，false 为拒绝。参数包含工具名、动作说明、风险评估
    var onRequireConfirmation: (suspend (toolName: String, actionText: String, risk: RiskAssessment) -> Boolean)? = null

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
            你是运行在 Android Wear OS 智能手表上的 AI 助手。
            
            【功能职责】
            - 宿主环境为圆形表盘穿戴设备，适配手表快速浏览场景。
            - 回答务必简短、精练、直接，避免冗长铺垫和多余客套。
            
            【可用工具】
            你可以根据用户需求按需调用系统与网络工具（ReAct Function Calling）：
            - get_device_status: 获取手表电量、充电状态与温度；
            - query_time_weather: 获取当前时间与天气信息；
            - vibrate_device: 触发震动反馈（short/double/alert）；
            - set_system_power_mode: 设置省电模式；
            - adjust_matrix_density: 调整表盘点阵间距；
            - web_search: 联网检索实时资讯与知识事实。
            
            【行动准则】
            - 目标明确时按需调用工具，工具返回结果后直接给出精简结论。
            - 涉及系统设置修改时简述操作目的。
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

            val turnResult = streamChatCompletions(
                blocks = blocks,
                currentStateProvider = { currentState },
                onStateUpdate = { newState ->
                    currentState = newState
                    onStateUpdate(currentState)
                }
            )

            if (turnResult.isFailure) {
                val err = turnResult.exceptionOrNull()?.message ?: "网络通信异常"
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

            val modelTurn = turnResult.getOrNull()!!
            val content = modelTurn.content
            val currentCalls = modelTurn.toolCalls

            // 检查是否有工具调用
            if (currentCalls.isNotEmpty()) {

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

                    // B. 分级审批闸门：AI 风险等级 + 安全模式共同决定是否弹审批。
                    // 先用启发式判一次：硬拦截/本来就不需要审批的快路径不必花 4 秒去请 AI 定级；
                    // 只有"可能要人来批"时才请 AI 定级，定级结果可能把操作降为自动放行（例如 LOW）。
                    val actionDesc = "执行 [${call.name}]"
                    val heuristicGate = ApprovalGate.decide(risk, preferences.securityMode)
                    val evaluatedRisk = if (heuristicGate == ApprovalGate.Decision.REQUIRE_APPROVAL) {
                        applyNarration(call.name, actionDesc, risk)
                    } else {
                        risk
                    }
                    val gate = if (heuristicGate == ApprovalGate.Decision.REQUIRE_APPROVAL) {
                        ApprovalGate.decide(evaluatedRisk, preferences.securityMode)
                    } else {
                        heuristicGate
                    }
                    val autoAllowNote = if (gate == ApprovalGate.Decision.AUTO_ALLOW) {
                        Log.i(
                            TAG,
                            "分级闸门自动放行：tool=${call.name} level=${evaluatedRisk.aiRiskLevel} mode=${preferences.securityMode}"
                        )
                        buildAutoAllowNote(call.name, actionDesc, evaluatedRisk)
                    } else {
                        null
                    }

                    if (gate == ApprovalGate.Decision.REQUIRE_APPROVAL) {
                        // 触发审批态与红瀑光相变
                        currentState = currentState.copy(
                            approvalVisualState = ApprovalVisualState.PENDING_CASCADE,
                            pendingRiskAssessment = evaluatedRisk
                        )
                        onStateUpdate(currentState)
                        val allowed = onRequireConfirmation?.invoke(call.name, actionDesc, evaluatedRisk) ?: true

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
                        resultSummary = autoAllowNote,
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
                            resultSummary = listOfNotNull(autoAllowNote, summaryText)
                                .joinToString(" · "),
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

    /**
     * 自动放行时的说明：把"调用了什么 / 改了什么"与最坏情况落进会话状态（工具小胶囊），
     * 走启发式而不额外发一次 LLM 请求，只读工具自动放行不该多等一个网络往返。
     */
    private fun buildAutoAllowNote(toolName: String, actionText: String, risk: RiskAssessment): String? {
        val narration = RiskNarrator.fallback(toolName, actionText, risk)
        return listOfNotNull(
            narration.whatItDoes.takeIf { it.isNotBlank() },
            narration.worstCase.takeIf { it.isNotBlank() }?.let { "最坏 $it" }
        ).joinToString(" · ").takeIf { it.isNotBlank() }
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

private data class ToolCallAccumulator(
        var id: String = "",
        var name: String = "",
        val arguments: java.lang.StringBuilder = java.lang.StringBuilder()
    )

    private data class ModelTurnResult(
        val content: String,
        val reasoning: String,
        val toolCalls: List<ToolCall>
    )

    /**
     * 为审批页生成 AI 三段说明（做什么 / 最坏情况 / 风险等级）。
     * 任何异常、超时或解析失败都回退到启发式；riskSummary 与 requireCooldownSeconds 原样保留。
     */
    private suspend fun applyNarration(
        toolName: String,
        actionText: String,
        risk: RiskAssessment
    ): RiskAssessment {
        val fallbackNarration = RiskNarrator.fallback(toolName, actionText, risk)
        val narration = if (preferences.apiKey.isBlank()) {
            fallbackNarration
        } else {
            try {
                val raw = withTimeoutOrNull(NARRATION_TIMEOUT_MS) {
                    requestNarration(RiskNarrator.buildPrompt(toolName, actionText))
                }
                raw?.let { RiskNarrator.parse(it) } ?: fallbackNarration
            } catch (e: Exception) {
                Log.w(TAG, "AI 风险说明生成失败，回退启发式: ${e.javaClass.simpleName}")
                fallbackNarration
            }
        }
        return risk.copy(
            whatItDoes = narration.whatItDoes,
            worstCase = narration.worstCase,
            aiRiskLevel = narration.level
        )
    }

    /** 轻量非流式补全：temperature 0、max_tokens 200、不携带工具定义，避免模型转去调工具。 */
    private suspend fun requestNarration(prompt: String): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = if (preferences.baseUrl.endsWith("/")) {
                preferences.baseUrl + "chat/completions"
            } else {
                preferences.baseUrl + "/chat/completions"
            }
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Authorization", "Bearer ${preferences.apiKey.trim()}")
                connectTimeout = NARRATION_SOCKET_TIMEOUT_MS
                readTimeout = NARRATION_SOCKET_TIMEOUT_MS
                doOutput = true
            }
            val body = JSONObject().apply {
                put("model", preferences.modelName)
                put("temperature", 0)
                put("max_tokens", 200)
                put("stream", false)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }
            if (conn.responseCode !in 200..299) return@withContext null
            val payload = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(payload).optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
        } catch (e: Exception) {
            Log.w(TAG, "AI 风险说明请求失败: ${e.javaClass.simpleName}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun streamChatCompletions(
        blocks: MutableList<ConversationBlock>,
        currentStateProvider: () -> AgentExecutionState,
        onStateUpdate: (AgentExecutionState) -> Unit
    ): Result<ModelTurnResult> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            sanitizeConversationHistory()

            val endpoint = if (preferences.baseUrl.endsWith("/")) {
                preferences.baseUrl + "chat/completions"
            } else {
                preferences.baseUrl + "/chat/completions"
            }
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", if (preferences.streamMode) "text/event-stream" else "application/json")
                setRequestProperty("Authorization", "Bearer ${preferences.apiKey.trim()}")
                connectTimeout = 15000
                readTimeout = 40000
                doOutput = true
            }

            val requestBody = JSONObject().apply {
                put("model", preferences.modelName)
                put("temperature", preferences.temperature)
                put("max_tokens", preferences.maxOutputTokens)
                if (preferences.streamMode) {
                    put("stream", true)
                }
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
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return@withContext Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }

            val accumulatedContent = java.lang.StringBuilder()
            val accumulatedReasoning = java.lang.StringBuilder()
            val toolCallsMap = mutableMapOf<Int, ToolCallAccumulator>()
            var textBlockId: String? = null
            var thoughtBlockId: String? = null

            if (preferences.streamMode) {
                // 真正的 SSE 实时流式读取 (零 delay 字节到达即刻上屏)
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (isCancelled) break
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty() || currentLine.startsWith(":")) continue
                    if (!currentLine.startsWith("data:")) continue

                    val data = currentLine.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    try {
                        val chunkJson = JSONObject(data)
                        val choices = chunkJson.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val choice = choices.getJSONObject(0)
                        val delta = choice.optJSONObject("delta") ?: continue

                        // 1. 深度思考链流式输出
                        if (delta.has("reasoning_content")) {
                            val rPiece = delta.optString("reasoning_content", "")
                            if (rPiece.isNotEmpty()) {
                                accumulatedReasoning.append(rPiece)
                                if (thoughtBlockId == null) {
                                    thoughtBlockId = UUID.randomUUID().toString()
                                    blocks.add(
                                        ConversationBlock.Thought(
                                            id = thoughtBlockId!!,
                                            reasoning = accumulatedReasoning.toString()
                                        )
                                    )
                                } else {
                                    val idx = blocks.indexOfFirst { it.id == thoughtBlockId }
                                    if (idx != -1) {
                                        blocks[idx] = ConversationBlock.Thought(
                                            id = thoughtBlockId!!,
                                            reasoning = accumulatedReasoning.toString()
                                        )
                                    }
                                }
                                val updated = currentStateProvider().copy(
                                    thoughtChain = accumulatedReasoning.toString(),
                                    conversationBlocks = blocks.toList()
                                )
                                onStateUpdate(updated)
                            }
                        }

                        // 2. 正文 Token 实时上屏 (彻底废除伪造 delay)
                        if (delta.has("content")) {
                            val cPiece = delta.optString("content", "")
                            if (cPiece.isNotEmpty()) {
                                accumulatedContent.append(cPiece)
                                if (textBlockId == null) {
                                    textBlockId = UUID.randomUUID().toString()
                                    blocks.add(
                                        ConversationBlock.Text(
                                            id = textBlockId!!,
                                            content = accumulatedContent.toString(),
                                            isStreaming = true
                                        )
                                    )
                                } else {
                                    val idx = blocks.indexOfFirst { it.id == textBlockId }
                                    if (idx != -1) {
                                        blocks[idx] = ConversationBlock.Text(
                                            id = textBlockId!!,
                                            content = accumulatedContent.toString(),
                                            isStreaming = true
                                        )
                                    }
                                }
                                val updated = currentStateProvider().copy(
                                    finalResponse = accumulatedContent.toString(),
                                    conversationBlocks = blocks.toList()
                                )
                                onStateUpdate(updated)
                            }
                        }

                        // 3. 工具调用分片拼接
                        val tcArr = delta.optJSONArray("tool_calls")
                        if (tcArr != null) {
                            for (i in 0 until tcArr.length()) {
                                val item = tcArr.getJSONObject(i)
                                val idx = item.optInt("index", i)
                                val acc = toolCallsMap.getOrPut(idx) { ToolCallAccumulator() }
                                if (item.has("id")) acc.id = item.optString("id")
                                val fn = item.optJSONObject("function")
                                if (fn != null) {
                                    if (fn.has("name")) acc.name += fn.optString("name")
                                    if (fn.has("arguments")) acc.arguments.append(fn.optString("arguments"))
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                reader.close()

                if (textBlockId != null) {
                    val idx = blocks.indexOfFirst { it.id == textBlockId }
                    if (idx != -1) {
                        blocks[idx] = ConversationBlock.Text(
                            id = textBlockId!!,
                            content = accumulatedContent.toString(),
                            isStreaming = false
                        )
                        val updated = currentStateProvider().copy(conversationBlocks = blocks.toList())
                        onStateUpdate(updated)
                    }
                }
            } else {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val sb = java.lang.StringBuilder()
                var l: String?
                while (reader.readLine().also { l = it } != null) {
                    sb.append(l)
                }
                reader.close()

                val json = JSONObject(sb.toString())
                val choice = json.optJSONArray("choices")?.optJSONObject(0)
                val msg = choice?.optJSONObject("message")
                if (msg != null) {
                    val rc = msg.optString("reasoning_content", "")
                    val c = msg.optString("content", "")
                    if (rc.isNotBlank()) {
                        accumulatedReasoning.append(rc)
                        blocks.add(ConversationBlock.Thought(id = UUID.randomUUID().toString(), reasoning = rc))
                    }
                    if (c.isNotBlank()) {
                        accumulatedContent.append(c)
                        blocks.add(ConversationBlock.Text(id = UUID.randomUUID().toString(), content = c, isStreaming = false))
                    }
                    val tArr = msg.optJSONArray("tool_calls")
                    if (tArr != null) {
                        for (i in 0 until tArr.length()) {
                            val obj = tArr.getJSONObject(i)
                            val acc = toolCallsMap.getOrPut(i) { ToolCallAccumulator() }
                            acc.id = obj.optString("id")
                            val fn = obj.optJSONObject("function")
                            acc.name = fn?.optString("name") ?: ""
                            acc.arguments.append(fn?.optString("arguments") ?: "")
                        }
                    }
                    onStateUpdate(currentStateProvider().copy(
                        finalResponse = accumulatedContent.toString(),
                        thoughtChain = accumulatedReasoning.toString(),
                        conversationBlocks = blocks.toList()
                    ))
                }
            }

            val finalToolCalls = toolCallsMap.values.filter { it.name.isNotBlank() }.map { acc ->
                ToolCall(
                    id = acc.id.ifBlank { "call_${UUID.randomUUID().toString().take(8)}" },
                    name = acc.name,
                    arguments = acc.arguments.toString()
                )
            }

            Result.success(ModelTurnResult(
                content = accumulatedContent.toString(),
                reasoning = accumulatedReasoning.toString(),
                toolCalls = finalToolCalls
            ))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        // 审批说明的轻量调用上限：整体 4 秒，套接字单次 3.5 秒，超时即回退启发式
        private const val NARRATION_TIMEOUT_MS = 4000L
        private const val NARRATION_SOCKET_TIMEOUT_MS = 3500

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

/**
 * 分级审批闸门：AI 风险等级决定要不要弹审批，安全模式决定无风险操作能否自动放行。
 * 判定顺序固定为 硬拦截 > 安全底线 > 风险等级，安全底线永远压过"低风险自动放行"。
 */
object ApprovalGate {
    enum class Decision {
        HARD_BLOCK,      // 命中硬黑名单：拦截且不执行，没有审批可谈
        AUTO_ALLOW,      // 无风险且当前模式允许：不弹审批直接执行
        REQUIRE_APPROVAL // 弹审批，由人滑动决定
    }

    fun decide(risk: RiskAssessment, mode: SecurityMode): Decision {
        // 1. 硬黑名单优先级最高：任何模式、任何等级一律拦截
        if (risk.isHardBlocked) return Decision.HARD_BLOCK

        // 2. 无风险操作：LOW 且护栏没判高危
        if (risk.aiRiskLevel == RiskLevel.LOW && !risk.isHighRisk) {
            // 安全底线：严格确认模式（SecurityMode 未命名 STRICT，严格模式即 NOVICE）不允许自动放行
            return when (mode) {
                SecurityMode.NOVICE -> Decision.REQUIRE_APPROVAL
                SecurityMode.STANDARD, SecurityMode.EXPERT -> Decision.AUTO_ALLOW
            }
        }

        // 3. 自动执行模式沿用原契约：除硬黑名单与护栏高危外不弹审批
        if (mode == SecurityMode.EXPERT && !risk.isHighRisk) return Decision.AUTO_ALLOW

        // 4. 其余情况（MEDIUM/HIGH/CRITICAL 或护栏判了高危）：照常弹审批
        return Decision.REQUIRE_APPROVAL
    }
}
