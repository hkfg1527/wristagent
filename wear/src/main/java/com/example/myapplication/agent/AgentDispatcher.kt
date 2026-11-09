package com.example.myapplication.agent

import com.example.myapplication.presentation.ErrorMessage

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import com.example.myapplication.R
import com.example.myapplication.agent.contract.AgentMemory
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityGuardrail
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.agent.privilege.PrivilegeManager
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
    private val preferences: AgentPreferences,
    /**
     * 特权层：run_shell 与自写工具都从这里拿到 root / Shizuku / 本进程三条后端。
     * 默认就地建一个，MainActivity 无需改接线即可获得真实权限执行链。
     */
    private val privilege: PrivilegeManager = PrivilegeManager(context)
) {
    private val TAG = "AgentDispatcher"
    private val memory = AgentMemory(context)
    private val guardrail = SecurityGuardrail()
    private val conversationHistory = mutableListOf<ChatMessage>()

    // 审批挂起函数：返回 true 为允许，false 为拒绝。参数包含工具名、动作说明、风险评估
    var onRequireConfirmation: (suspend (toolName: String, actionText: String, risk: RiskAssessment) -> Boolean)? = null

    // 供应商拒收 reasoning_effort 并已去掉该字段重试：上层据此提示"该模型已忽略推理努力"
    var onReasoningEffortIgnored: (() -> Unit)? = null

    @Volatile
    private var isCancelled = false

    /**
     * 子代理执行器：请求轮与工具执行都复用本类既有链路，但上下文与 [conversationHistory] 完全隔离
     * （子代理在自己的 history 里跑），工具集由注册表按"去掉 run_subagent"给出。
     */
    private val subAgentRunner = SubAgentRunner(
        requestTurn = { history, tools -> requestSubAgentTurn(history, tools) },
        executeTool = { call -> executeSubAgentTool(call) },
        availableTools = { toolRegistry.getToolDefinitions() },
        isCancelled = { isCancelled }
    )

    init {
        // 工具层要读自写工具账本与记住清单，也要能执行特权命令：在这里一次接好线
        toolRegistry.bindPreferences(preferences)
        toolRegistry.bindShellRunner(PrivilegeShellRunner(privilege))
        // run_subagent 的执行体要用网络层 + 工具执行层，只有调度器具备：把嵌套循环接到工具层上
        toolRegistry.bindSubAgentRunner { task, focus, parentDepth ->
            subAgentRunner.run(
                parentDepth = parentDepth,
                systemPrompt = buildSubAgentSystemPrompt(),
                task = task,
                focus = focus
            ).toJson()
        }
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
        - 宿主设备物理形态: Wear OS 圆屏穿戴设备（圆形表盘；具体型号不写死，避免告知模型错误事实）
        - 电池遥测状态: $batteryLevel
        - 剪贴板最新文字: "$clipSnippet"
        在回答主人关于时间、日期、电量或刚才复制的内容时，直接引用上述客观事实，无需重复反问。
        """.trimIndent()
    }

    private fun initSystemMessage() {
        conversationHistory.clear()
        // 身份 / 能力 / 纪律 / 边界四段放在资源里（values 与 values-zh 各一份），
        // 长记忆头与实时遥测固定在最前面；customSystemPrompt 非空时整体替换四段。
        val sections = AgentSystemPrompt.SECTION_RES_IDS.map { resId ->
            runCatching { context.getString(resId) }.getOrDefault("")
        }
        val systemPrompt = AgentSystemPrompt.compose(
            sections = sections,
            // memoryEnabled 关掉时连记忆头都不拼：长期笔记因此也不会进入上下文
            memoryHeader = MemoryInjection.header(preferences.memoryEnabled, memory.buildSystemMemoryHeader()),
            telemetryBlock = buildDynamicTelemetryBlock(),
            customPrompt = preferences.customSystemPrompt
        )
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
     *
     * 轮数没有上限：主人要求工具轮数不再卡在 5 轮，原来的 `val maxTurns = 5` +
     * `for (turn in 1..maxTurns)` 硬上限已整体删除。防烧电不靠轮数，靠四条真实退出路径：
     * 1) cancelCurrentExecution() 置位 isCancelled → while 条件立刻为 false；
     * 2) 模型不再返回 tool_calls → 走下面的"自主收口"分支 return；
     * 3) 网络 / 解析异常 → turnResult.isFailure 分支 return；
     * 4) AgentLoopPolicy：连续 40 轮工具注入一次"立即总结"提示（只提示、不硬中断），
     *    同一份调用连续重复则先提示、第 5 次才收口——那是空转，不是轮数上限。
     */
    private suspend fun executeInterleavedApiLoop(
        initialState: AgentExecutionState,
        onStateUpdate: (AgentExecutionState) -> Unit
    ) {
        var currentState = initialState
        val blocks = currentState.conversationBlocks.toMutableList()
        // 轮数无上限：循环只由取消 / 无工具 / 异常 / 能量提示 + 空转检测收口
        var turn = 0
        var consecutiveToolTurns = 0
        var energyHintInjected = false
        var spinNudgeInjected = false
        val spinDetector = IdleSpinDetector()

        while (!isCancelled) {
            turn += 1

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
                // 只说人话：err 里是 provider 的原始 JSON，绝不进气泡（映射规则见 ErrorMessage）
                val errorBlock = ConversationBlock.Text(
                    id = UUID.randomUUID().toString(),
                    content = ErrorMessage.display(err)
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

                // 空转检测：连续同一份调用（名字 + 入参全同）先提示一次，第 5 次才收口。
                // 这不是轮数上限——换了调用立刻归零。
                val spin = spinDetector.observe(AgentLoopPolicy.signatureOf(currentCalls))
                if (spin == IdleSpinDecision.NUDGE && !spinNudgeInjected) {
                    spinNudgeInjected = true
                    conversationHistory.add(
                        ChatMessage(
                            role = AgentRole.USER,
                            content = AgentLoopPolicy.idleSpinText(
                                currentCalls.first().name,
                                AgentLoopPolicy.IDLE_SPIN_NUDGE_AFTER
                            )
                        )
                    )
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
                    // 特权工具（run_shell / 自写工具 / define_tool）绕开通用分级闸门，走下面的强制审批链
                    val forcedApproval = toolRegistry.requiresApprovalAlways(call.name)
                    val shellCommand = toolRegistry.shellCommandOf(call)
                    val actionDesc = if (shellCommand.isNullOrBlank()) {
                        "执行 [" + call.name + "]"
                    } else {
                        "执行 [" + call.name + "] " + ShellPolicy.normalize(shellCommand)
                    }

                    // A. 致命黑名单硬拦截：只对普通工具生效。
                    // 特权 shell 工具的危险命令一律走审批页——命令原文要给人看见并当场决定，不能被静默拦死。
                    if (!forcedApproval && risk.isHardBlocked) {
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

                    // B. 分级审批闸门。
                    // B1 特权 shell 命令：命令原文进审批页，破坏性命令永远要人批；
                    // B2 define_tool（以及命令为空的 run_shell）：能力面变更与参数错误同样强制审批；
                    // B3 其余工具沿用原分级闸门（AI 风险等级 + 安全模式）。
                    val heuristicGate = ApprovalGate.decide(risk, preferences.securityMode)
                    val gate: ApprovalGate.Decision
                    val evaluatedRisk: RiskAssessment
                    val autoAllowNote: String?
                    when {
                        shellCommand != null -> {
                            val baseRisk = ShellPolicy.assess(shellCommand, preferences.securityMode)
                            val remembered = ShellPolicy.isRemembered(
                                shellCommand,
                                preferences.rememberedShellCommandList()
                            )
                            gate = ShellPolicy.gate(ShellPolicy.dangerOf(shellCommand), remembered)
                            // 破坏性命令用确定性文案（不为一次审批多等 4 秒 AI 定级），其余让 AI 补一段更贴切的影响说明
                            evaluatedRisk = if (
                                gate == ApprovalGate.Decision.REQUIRE_APPROVAL &&
                                baseRisk.aiRiskLevel != RiskLevel.CRITICAL
                            ) {
                                ShellPolicy.enforceRiskFloor(
                                    baseRisk,
                                    applyNarration(call.name, actionDesc, baseRisk)
                                )
                            } else {
                                baseRisk
                            }
                            autoAllowNote = if (gate == ApprovalGate.Decision.AUTO_ALLOW) {
                                Log.i(TAG, "记住清单命中，跳过审批：tool=${call.name} command=$shellCommand")
                                "命中已记住命令，跳过审批"
                            } else {
                                null
                            }
                        }

                        forcedApproval -> {
                            gate = ApprovalGate.Decision.REQUIRE_APPROVAL
                            evaluatedRisk = forcedApprovalRisk(call.name)
                            autoAllowNote = null
                        }

                        heuristicGate == ApprovalGate.Decision.REQUIRE_APPROVAL -> {
                            evaluatedRisk = applyNarration(call.name, actionDesc, risk)
                            gate = ApprovalGate.decide(evaluatedRisk, preferences.securityMode)
                            autoAllowNote = if (gate == ApprovalGate.Decision.AUTO_ALLOW) {
                                Log.i(
                                    TAG,
                                    "分级闸门自动放行：tool=${call.name} level=${evaluatedRisk.aiRiskLevel} mode=${preferences.securityMode}"
                                )
                                buildAutoAllowNote(call.name, actionDesc, evaluatedRisk)
                            } else {
                                null
                            }
                        }

                        else -> {
                            evaluatedRisk = risk
                            gate = heuristicGate
                            autoAllowNote = if (gate == ApprovalGate.Decision.AUTO_ALLOW) {
                                Log.i(
                                    TAG,
                                    "分级闸门自动放行：tool=${call.name} level=${evaluatedRisk.aiRiskLevel} mode=${preferences.securityMode}"
                                )
                                buildAutoAllowNote(call.name, actionDesc, evaluatedRisk)
                            } else {
                                null
                            }
                        }
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

                consecutiveToolTurns += 1
                // 能量保护：连续 40 轮工具只提示一次，不做硬中断（主人要的是无上限，不是 5 轮）
                if (AgentLoopPolicy.shouldInjectEnergyHint(consecutiveToolTurns, energyHintInjected)) {
                    energyHintInjected = true
                    Log.i(TAG, "连续工具轮 " + consecutiveToolTurns + "，注入一次收尾提示")
                    conversationHistory.add(
                        ChatMessage(
                            role = AgentRole.USER,
                            content = AgentLoopPolicy.energyHintText(consecutiveToolTurns)
                        )
                    )
                }
                if (spin == IdleSpinDecision.STOP) {
                    Log.w(
                        TAG,
                        "空转收口：同一份调用连续重复 " + AgentLoopPolicy.IDLE_SPIN_STOP_AFTER + " 次"
                    )
                    break
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

    /**
     * define_tool 这类"不执行命令但扩大能力面"的特权动作的固定文案：
     * 不为一次审批多等一轮 AI 定级，审批页也永远有话说。
     */
    private fun forcedApprovalRisk(toolName: String): RiskAssessment {
        val isDefineTool = toolName == ShellPolicy.TOOL_DEFINE_TOOL
        return RiskAssessment(
            isHardBlocked = false,
            isHighRisk = true,
            riskSummary = if (isDefineTool) {
                "定义新工具：Agent 会获得一个新的可执行入口"
            } else {
                "特权工具：执行前必须由人放行"
            },
            requireCooldownSeconds = 0,
            whatItDoes = if (isDefineTool) "注册一个带命令模板的新工具" else "调用特权工具 " + toolName,
            worstCase = if (isDefineTool) {
                "新工具会留在工具清单里，之后每次调用都要单独审批"
            } else {
                "命令以最高可用权限执行，可能改动系统状态"
            },
            aiRiskLevel = RiskLevel.MEDIUM,
            commandText = "",
            rememberable = false
        )
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
                ShellPolicy.TOOL_RUN_SHELL ->
                    "exit " + json.optInt("exit_code", -1) + " · 后端 " + json.optString("backend", "?")
                ShellPolicy.TOOL_DEFINE_TOOL -> "已注册工具 " + json.optString("name", "")
                ShellPolicy.TOOL_LIST_TOOLS -> "自写工具 " + json.optInt("count", 0) + " 个"
                ShellPolicy.TOOL_REMOVE_TOOL -> "已删除 " + json.optString("removed", "")
                else -> json.optString("summary", json.optString("message", "执行成功"))
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

    /**
     * autoCompressContext 打开且历史超过 maxContextTurns 时，把落盘历史压到请求侧同一上限：
     * system 头保留、最近 N 轮保留、孤立 tool 回执丢弃（缺回执的 tool_calls 由自愈补齐）。
     * 开关关闭时这里什么都不做，历史只在请求侧截断。
     */
    private fun applyAutoCompaction() {
        if (!preferences.autoCompressContext) return
        val compacted = ContextCompactor.trim(
            history = conversationHistory,
            maxTurns = preferences.maxContextTurns,
            enabled = true
        )
        if (compacted.size == conversationHistory.size) return
        Log.i(TAG, "自动压缩上下文：" + conversationHistory.size + " -> " + compacted.size + " 条")
        conversationHistory.clear()
        conversationHistory.addAll(compacted)
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
            applyAutoCompaction()
            sanitizeConversationHistory()

            val endpoint = if (preferences.baseUrl.endsWith("/")) {
                preferences.baseUrl + "chat/completions"
            } else {
                preferences.baseUrl + "/chat/completions"
            }
            val url = URL(endpoint)

            // 推理努力只在推理模型上发出去，且受 defaultThinkingEnabled 总闸约束；
            // 供应商不认这个字段时置空后重试一次
            var reasoningEffortValue: String? = ThinkingPolicy.reasoningEffortBody(
                enabled = preferences.defaultThinkingEnabled,
                modelName = preferences.modelName,
                effort = ReasoningEffort.fromStored(preferences.reasoningEffort)
            )

            fun buildRequestBody(): JSONObject = JSONObject().apply {
                put("model", preferences.modelName)
                put("temperature", preferences.temperature)
                put("max_tokens", preferences.maxOutputTokens)
                if (preferences.streamMode) {
                    put("stream", true)
                }
                reasoningEffortValue?.let { put("reasoning_effort", it) }
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

            fun openConnection(): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", if (preferences.streamMode) "text/event-stream" else "application/json")
                setRequestProperty("Authorization", "Bearer ${preferences.apiKey.trim()}")
                connectTimeout = 15000
                readTimeout = 40000
                doOutput = true
            }

            var droppedEffort = false
            // 思考耗时的起点 = 请求真正发出去的那一刻。以它为准，响应多久才回来都记作思考时间，
            // 非流式回显因此也不会退化成 0s（旧代码把起点记在了响应到达之后）。
            var requestStartedAtMs = 0L
            while (true) {
                conn?.disconnect()
                requestStartedAtMs = System.currentTimeMillis()
                val activeConn = openConnection()
                conn = activeConn
                OutputStreamWriter(activeConn.outputStream, "UTF-8").use { it.write(buildRequestBody().toString()) }

                val responseCode = activeConn.responseCode
                if (responseCode in 200..299) break

                val errorBody = activeConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                if (!droppedEffort && reasoningEffortValue != null &&
                    ReasoningEffortPolicy.isUnsupportedFieldError(responseCode, errorBody)
                ) {
                    // 供应商明确拒绝 reasoning_effort：去掉字段重试，并把"已忽略"提示交给上层
                    droppedEffort = true
                    reasoningEffortValue = null
                    onReasoningEffortIgnored?.invoke()
                    continue
                }
                return@withContext Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }

            val accumulator = StreamAccumulator()
            var textBlockId: String? = null
            // 思考胶囊的时间线：每个 reasoning 增量各自提交一次状态，起点固定在请求发出时刻
            val thoughtTimeline = ThoughtTimeline(requestStartedAtMs)
            val showReasoning = preferences.showThoughtChain

            /**
             * 思考胶囊：第一次拿到思考增量时建块，之后**每个增量都改写同一粒胶囊并提交一次状态**。
             * 提交粒度就是流式粒度：日志里能看到每块增量各自的"距请求"时刻。
             */
            fun publishReasoning() {
                if (!showReasoning) return
                val reasoning = accumulator.reasoning
                if (!thoughtTimeline.onReasoning(blocks, reasoning)) return
                Log.d(
                    TAG,
                    "思考增量 #${thoughtTimeline.incrementCount} +" +
                        "${reasoning.length} 字, 距请求 ${thoughtTimeline.elapsedMs()}ms"
                )
                onStateUpdate(
                    currentStateProvider().copy(
                        thoughtChain = reasoning,
                        conversationBlocks = blocks.toList()
                    )
                )
            }

            /** 正文块：与思考胶囊同一套增量上屏节奏。 */
            fun publishContent() {
                val content = accumulator.content
                if (textBlockId == null) {
                    textBlockId = UUID.randomUUID().toString()
                    blocks.add(
                        ConversationBlock.Text(
                            id = textBlockId!!,
                            content = content,
                            isStreaming = true
                        )
                    )
                } else {
                    val idx = blocks.indexOfFirst { it.id == textBlockId }
                    if (idx != -1) {
                        blocks[idx] = ConversationBlock.Text(
                            id = textBlockId!!,
                            content = content,
                            isStreaming = true
                        )
                    }
                }
                onStateUpdate(
                    currentStateProvider().copy(
                        finalResponse = content,
                        conversationBlocks = blocks.toList()
                    )
                )
            }

            if (preferences.streamMode) {
                // 真正的 SSE 实时流式读取 (零 delay 字节到达即刻上屏)
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (isCancelled) break
                    val currentLine = line?.trim() ?: continue
                    if (currentLine.isEmpty()) continue
                    // 一行 SSE -> 结构化事件：思考与正文各走各的通道，工具调用分片交给累加器拼接
                    val events = accumulator.applyLine(currentLine)
                    // 供应商的 [DONE] 是流结束标记：不等连接自然关闭，立刻去做收尾翻牌
                    if (events.any { it === StreamDelta.Done }) break
                    events.forEach { event ->
                        when (event) {
                            is StreamDelta.Reasoning -> publishReasoning()
                            is StreamDelta.Content -> publishContent()
                            is StreamDelta.ToolCall -> Unit
                            StreamDelta.Done -> Unit
                        }
                    }
                }
                reader.close()
            } else {
                val reader = BufferedReader(InputStreamReader(conn.inputStream, "UTF-8"))
                val sb = java.lang.StringBuilder()
                var l: String?
                while (reader.readLine().also { l = it } != null) {
                    sb.append(l)
                }
                reader.close()

                val message = JSONObject(sb.toString())
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                if (message != null) {
                    val events = ModelStreamParser.messageEvents(message)
                    accumulator.applyAll(events)
                    if (events.any { it is StreamDelta.Reasoning }) publishReasoning()
                    if (events.any { it is StreamDelta.Content }) publishContent()
                    onStateUpdate(
                        currentStateProvider().copy(
                            finalResponse = accumulator.content,
                            thoughtChain = accumulator.reasoning,
                            conversationBlocks = blocks.toList()
                        )
                    )
                }
            }

            // 收尾：思考胶囊从"思考中"翻到"思考完成 · Ns"；正文块摘掉流式光标
            run {
                val finishedAtMs = System.currentTimeMillis()
                if (!thoughtTimeline.finish(blocks, accumulator.reasoning, finishedAtMs)) return@run
                Log.d(
                    TAG,
                    "思考收尾: 共 ${thoughtTimeline.incrementCount} 块增量, 耗时 " +
                        "${thoughtTimeline.durationMs(finishedAtMs)}ms"
                )
                onStateUpdate(currentStateProvider().copy(conversationBlocks = blocks.toList()))
            }

            if (textBlockId != null) {
                val idx = blocks.indexOfFirst { it.id == textBlockId }
                if (idx != -1) {
                    blocks[idx] = ConversationBlock.Text(
                        id = textBlockId!!,
                        content = accumulator.content,
                        isStreaming = false
                    )
                    onStateUpdate(currentStateProvider().copy(conversationBlocks = blocks.toList()))
                }
            }

            val finalToolCalls = accumulator.toolCalls()

            Result.success(ModelTurnResult(
                content = accumulator.content,
                reasoning = accumulator.reasoning,
                toolCalls = finalToolCalls
            ))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 子代理里的单次工具执行：**不绕过审批**。
     *
     * 子代理执行的是同一批工具（run_subagent 之外的全集），所以这里复用主循环的同一组判定原语：
     * 护栏硬黑名单 → 特权工具强制审批 → ShellPolicy / ApprovalGate 分级闸门 → onRequireConfirmation。
     * 唯一省略的是主循环里的 AI 风险说明（applyNarration，一次外呼）：少了它只会让审批更保守，
     * 不会出现"主循环要批、子代理却静默执行"的情况。
     */
    private suspend fun executeSubAgentTool(call: ToolCall): ToolResult {
        val risk = guardrail.assessTool(call.name, call.arguments, preferences.securityMode)
        val forcedApproval = toolRegistry.requiresApprovalAlways(call.name)
        val shellCommand = toolRegistry.shellCommandOf(call)
        val actionDesc = if (shellCommand.isNullOrBlank()) {
            "执行 [" + call.name + "]"
        } else {
            "执行 [" + call.name + "] " + ShellPolicy.normalize(shellCommand)
        }

        // 致命黑名单硬拦截：与主循环同一条判定，特权工具的原文仍然走审批页
        if (!forcedApproval && risk.isHardBlocked) {
            return rejectedToolResult(call, "致命指令已被安全护栏拦截：" + risk.riskSummary)
        }

        val gate = when {
            shellCommand != null -> ShellPolicy.gate(
                ShellPolicy.dangerOf(shellCommand),
                ShellPolicy.isRemembered(shellCommand, preferences.rememberedShellCommandList())
            )
            forcedApproval -> ApprovalGate.Decision.REQUIRE_APPROVAL
            else -> ApprovalGate.decide(risk, preferences.securityMode)
        }

        if (gate == ApprovalGate.Decision.REQUIRE_APPROVAL) {
            val evaluatedRisk = if (forcedApproval) forcedApprovalRisk(call.name) else risk
            val allowed = onRequireConfirmation?.invoke(call.name, actionDesc, evaluatedRisk) ?: true
            if (!allowed) {
                Log.i(TAG, "子代理工具被拒绝：tool=" + call.name)
                return rejectedToolResult(call, "主人已在手腕端拒绝执行此操作")
            }
        }

        return toolRegistry.executeTool(call)
    }

    /** 子代理工具被拦下时回给它的结构化回执：让子代理知道这条路走不通，而不是当成功继续编。 */
    private fun rejectedToolResult(call: ToolCall, message: String): ToolResult = ToolResult(
        toolCallId = call.id,
        toolName = call.name,
        output = JSONObject().apply {
            put("status", "rejected")
            put("message", message)
        }.toString(),
        isError = true
    )

    /**
     * 子代理的模型请求：非流式、单次往返，用子代理自己的 history 与工具集组装请求。
     * 全程不碰 conversationHistory，主时间线状态也不参与。
     */
    private suspend fun requestSubAgentTurn(
        history: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): SubAgentTurn = withContext(Dispatchers.IO) {
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
                setRequestProperty("Authorization", "Bearer " + preferences.apiKey.trim())
                connectTimeout = SUB_AGENT_CONNECT_TIMEOUT_MS
                readTimeout = SUB_AGENT_READ_TIMEOUT_MS
                doOutput = true
            }
            val body = JSONObject().apply {
                put("model", preferences.modelName)
                put("temperature", preferences.temperature)
                put("max_tokens", preferences.maxOutputTokens)
                put("stream", false)
                put("messages", JSONArray().apply { history.forEach { put(it.toJson()) } })
                if (tools.isNotEmpty()) {
                    put("tools", JSONArray().apply { tools.forEach { put(it.toJson()) } })
                    put("tool_choice", "auto")
                }
            }
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body.toString()) }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                val errorBody = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw IllegalStateException("HTTP " + responseCode + ": " + errorBody.take(200))
            }
            val payload = conn.inputStream.bufferedReader().use { it.readText() }
            val message = JSONObject(payload)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?: throw IllegalStateException("响应缺少 choices[0].message")

            // 复用主链路的同一套解析器与累加器：非流式回包的分片拼装规则不需要第二份实现
            val accumulator = StreamAccumulator()
            accumulator.applyAll(ModelStreamParser.messageEvents(message))
            SubAgentTurn(
                content = accumulator.content,
                reasoning = accumulator.reasoning,
                toolCalls = accumulator.toolCalls()
            )
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 子代理的 system 头：沿用宿主四段契约与实时遥测，再补一段子代理专属说明。
     * 子代理看不到主对话，因此必须在提示里明确"自己查清事实再回结论"。
     */
    private fun buildSubAgentSystemPrompt(): String {
        val sections = AgentSystemPrompt.SECTION_RES_IDS.map { resId ->
            runCatching { context.getString(resId) }.getOrDefault("")
        }
        val base = AgentSystemPrompt.compose(
            sections = sections,
            memoryHeader = "",
            telemetryBlock = buildDynamicTelemetryBlock(),
            customPrompt = preferences.customSystemPrompt
        )
        val subAgentRules = runCatching {
            context.getString(R.string.agent_subagent_system_prompt)
        }.getOrDefault("")
        return listOf(base, subAgentRules.trim()).filter { it.isNotEmpty() }.joinToString("\n\n")
    }

    companion object {
        // 审批说明的轻量调用上限：整体 4 秒，套接字单次 3.5 秒，超时即回退启发式
        private const val NARRATION_TIMEOUT_MS = 4000L
        private const val NARRATION_SOCKET_TIMEOUT_MS = 3500

        // 子代理单次请求的连接 / 读取上限；整体时限由 SubAgentRunner.DEFAULT_TIMEOUT_MS 兜底
        private const val SUB_AGENT_CONNECT_TIMEOUT_MS = 15000
        private const val SUB_AGENT_READ_TIMEOUT_MS = 60000

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
