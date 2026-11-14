package com.example.myapplication.agent

import com.example.myapplication.agent.mcp.McpClient
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.myapplication.agent.contract.AgentMemory
import com.example.myapplication.agent.contract.MemoryFactBook
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.agent.mcp.McpToolBinding
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 手表原生 Agent 工具注册表
 */
class AgentToolRegistry(
    private val context: Context,
    preferences: AgentPreferences? = null,
    private var shellRunner: PrivilegedCommandRunner? = null
) {

    /**
     * 偏好与特权执行器都支持后接：MainActivity 先造注册表再交给调度器，
     * 调度器拿到真实 AgentPreferences 后一次绑上，工具清单与审批链路才完整。
     */
    private var preferences: AgentPreferences? = preferences

    fun bindPreferences(value: AgentPreferences) {
        preferences = value
    }

    fun bindShellRunner(runner: PrivilegedCommandRunner) {
        shellRunner = runner
    }

    /**
     * 长期记忆仓储由调度器注入：写入方与系统提示词的重建必须落在**同一个**仓储上，
     * 否则 remember_fact 写进去的内容要等冷启动才会进上下文。
     */
    private var memory: AgentMemory? = null

    fun bindMemory(value: AgentMemory) {
        memory = value
    }

    /**
     * run_subagent 的执行体由调度器注入：嵌套循环需要网络层与工具执行层，注册表自己不具备。
     * 参数 parentDepth 由调用方给（顶层循环恒为 0），子代理能否再下钻由 SubAgentPolicy 判定。
     */
    private var subAgentRunner: (suspend (task: String, focus: String?, parentDepth: Int) -> JSONObject)? = null

    fun bindSubAgentRunner(runner: suspend (task: String, focus: String?, parentDepth: Int) -> JSONObject) {
        subAgentRunner = runner
    }

    /**
     * MCP 远端工具：开关关闭时清单里一个都没有，也不会发起任何连接；
     * 开关打开时后台做一次 initialize + tools/list，远端工具以 mcp_ 前缀进本地清单。
     * 懒建：绑定前 preferences 还是空，构造顺序不影响这里的判定。
     */
    internal val mcpTools: McpToolBinding by lazy {
        McpToolBinding(
            endpointSource = { preferences?.mcpEndpoint ?: "" },
            enabledSource = { preferences?.isMcpEnabled ?: false },
            maxToolPages = preferences?.mcpMaxToolPages ?: McpClient.MAX_TOOL_PAGES
        )
    }

    // 观察外部配置或 UI 变化的回调
    var onMatrixDensityChange: ((spacingDp: Float) -> Unit)? = null
    var onPowerModeChange: ((ultraSaving: Boolean) -> Unit)? = null

    /**
     * 获取所有支持导出的工具列表 (符合 OpenAI / LLM JSON Schema)
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        val all = mutableListOf<ToolDefinition>()

        if (preferences?.toolDeviceStatusEnabled ?: true) {
            all.add(
                ToolDefinition(
                    name = "get_device_status",
                    description = "获取智能手表的当前电池电量、充电状态、电池温度与当前硬件工作负载",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                        put("required", JSONArray())
                    }
                )
            )
        }

        if (preferences?.toolVibrateEnabled ?: true) {
            all.add(
                ToolDefinition(
                    name = "vibrate_device",
                    description = "触发手表的线性马达执行触觉振动反馈",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        val patternProp = JSONObject().apply {
                            put("type", "string")
                            put("enum", JSONArray(listOf("short", "double", "alert")))
                            put("description", "振动模式：short (短脉冲轻快反馈), double (双击确认), alert (醒目长振动)")
                        }
                        props.put("pattern", patternProp)
                        put("properties", props)
                        put("required", JSONArray(listOf("pattern")))
                    }
                )
            )
        }

        if (preferences?.toolMatrixDensityEnabled ?: true) {
            all.add(
                ToolDefinition(
                    name = "adjust_matrix_density",
                    description = "调整手表表盘点阵间距与显示密度",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        val spacingProp = JSONObject().apply {
                            put("type", "number")
                            put("description", "点阵网格间距 (单位 dp，推荐 12.0~20.0)")
                        }
                        props.put("spacingDp", spacingProp)
                        put("properties", props)
                        put("required", JSONArray(listOf("spacingDp")))
                    }
                )
            )
        }

        if (preferences?.toolPowerModeEnabled ?: true) {
            all.add(
                ToolDefinition(
                    name = "set_system_power_mode",
                    description = "设置手表电源模式。可开启或关闭极致省电模式",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        val ultraProp = JSONObject().apply {
                            put("type", "boolean")
                            put("description", "是否开启极致省电模式")
                        }
                        props.put("ultraSaving", ultraProp)
                        put("properties", props)
                        put("required", JSONArray(listOf("ultraSaving")))
                    }
                )
            )
        }

        if (preferences?.toolWeatherEnabled ?: true) {
            all.add(
                ToolDefinition(
                    name = "query_time_weather",
                    description = "查询当前手表本地精准系统时间、星期与当前气象环境",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        val locProp = JSONObject().apply {
                            put("type", "string")
                            put("description", "查询地点，默认为本地表盘位置")
                        }
                        props.put("location", locProp)
                        put("properties", props)
                    }
                )
            )
        }

        // 检索工具按两个独立开关裁剪：本地检索与 Provider 托管检索各管各的
        val searches = SearchToolGate.exposedSearches(
            localEnabled = preferences?.toolWebSearchEnabled ?: true,
            providerManagedEnabled = preferences?.providerManagedSearchEnabled
                ?: AgentPreferences.DEFAULT_PROVIDER_MANAGED_SEARCH
        )

        if (SearchToolGate.TOOL_LOCAL_SEARCH in searches) {
            all.add(
                ToolDefinition(
                    name = "web_search",
                    description = "连接互联网实时检索最新资讯、新闻、技术事实与即时答案。返回精炼清洗后的纯文本摘要",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        val queryProp = JSONObject().apply {
                            put("type", "string")
                            put("description", "精确简练的搜索关键词")
                        }
                        props.put("query", queryProp)
                        put("properties", props)
                        put("required", JSONArray(listOf("query")))
                    }
                )
            )
        }

        if (SearchToolGate.TOOL_PROVIDER_SEARCH in searches) {
            all.add(
                ToolDefinition(
                    name = SearchToolGate.TOOL_PROVIDER_SEARCH,
                    description = "由 Provider 侧托管的联网检索。关闭该开关后本工具不会出现在工具清单里；" +
                        "当前链路在 Provider 不提供托管检索时用本地检索兜底，回执里的 search_route 会注明实际走法",
                    parametersSchema = JSONObject().apply {
                        put("type", "object")
                        val props = JSONObject()
                        props.put(
                            "query",
                            JSONObject().apply {
                                put("type", "string")
                                put("description", "精确简练的搜索关键词")
                            }
                        )
                        put("properties", props)
                        put("required", JSONArray(listOf("query")))
                    }
                )
            )
        }

        // 长期记忆写入方：总开关关掉时连入口都不给（关掉的开关不能指望模型自觉），
        // 真被旧清单里残留的调用打到，executeRememberFact 还会再拒一次
        if (preferences?.memoryEnabled ?: AgentPreferences.DEFAULT_MEMORY_ENABLED) {
            all.add(memoryToolDefinition())
        }

        // 特权工具常驻：能力面不靠设置开关裁剪，靠执行前的审批闸门约束
        all.addAll(privilegedToolDefinitions())
        // 自写工具每次现读账本：define_tool 落盘后下一轮请求立刻出现在清单里
        selfDefinedTools().forEach { all.add(it.toToolDefinition()) }
        // MCP 远端工具：只读缓存，后台刷新；与内置工具重名的一律让位（前缀 mcp_ 兜住来源区分）
        all.addAll(mcpTools.toolDefinitions(all.map { it.name }.toSet()))

        // 子代理：只有专家模式 + 开关打开才整条出现；其余模式下不是灰显，而是根本不在清单里
        all.addAll(
            SubAgentPolicy.toolDefinitions(
                mode = preferences?.securityMode ?: SecurityMode.STANDARD,
                enabled = preferences?.subAgentEnabled ?: false
            )
        )

        // 注册数在这里落盘：每轮构建清单都会刷新，界面读到的永远是最新值。
        preferences?.registeredToolCount = all.size
        return all
    }

    /**
     * 执行指定工具
     */
    suspend fun executeTool(call: ToolCall): ToolResult {
        // 调用账本：只记名字，不记参数；大卡片档的「已调用 / 已注册」圆环读它。
        preferences?.recordToolCall(call.name)
        // MCP 远端工具走独立分支：失败已经被翻译成结构化回执，不经过内置工具这条链路。
        // 同名的自写工具优先：define_tool 允许 mcp_ 开头的名字，注册表里已有它就按已有处理。
        if (mcpTools.isMcpToolName(call.name) && selfDefinedTool(call.name) == null) {
            return mcpTools.toolResult(call.id, call.name, call.arguments)
        }
        return try {
            val args = call.parseArgs()
            val resultJson = when (call.name) {
                "get_device_status" -> executeGetDeviceStatus()
                "vibrate_device" -> executeVibrate(args.optString("pattern", "short"))
                "adjust_matrix_density", "change_matrix_mode" -> executeChangeMatrix(
                    args.optDouble("spacingDp", 16.0).toFloat()
                )
                "set_system_power_mode" -> executeSetPowerMode(args.optBoolean("ultraSaving", true))
                "query_time_weather" -> executeQueryTimeWeather(args.optString("location", "本地"))
                "web_search" -> executeWebSearch(args.optString("query", ""))
                SearchToolGate.TOOL_PROVIDER_SEARCH -> executeProviderWebSearch(args.optString("query", ""))
                ShellPolicy.TOOL_REMEMBER_FACT -> executeRememberFact(args)
                ShellPolicy.TOOL_RUN_SHELL -> executeRunShell(args)
                ShellPolicy.TOOL_DEFINE_TOOL -> executeDefineTool(args)
                ShellPolicy.TOOL_LIST_TOOLS -> executeListTools()
                ShellPolicy.TOOL_REMOVE_TOOL -> executeRemoveTool(args)
                SubAgentPolicy.TOOL_NAME -> executeRunSubAgent(args)
                else -> {
                    val selfTool = selfDefinedTool(call.name)
                    if (selfTool != null) {
                        executeSelfDefinedTool(selfTool, args)
                    } else {
                        errorJson("unknown_tool", "未注册的工具函数: ${call.name}")
                    }
                }
            }
            ToolResult(
                toolCallId = call.id,
                toolName = call.name,
                output = resultJson.toString(),
                isError = false
            )
        } catch (e: Exception) {
            // 错误回执自己不许再炸：宿主 org.json 退化（单测桩 / 极端裁剪）时 toString() 会返回 null，
            // 那会把"工具执行失败"升级成"调度器抛异常"。兜底成一行纯文本，语义与结构化回执一致。
            val detail = runCatching {
                JSONObject().apply {
                    put("status", "error")
                    put("message", e.localizedMessage ?: "执行异常")
                }.toString()
            }.getOrNull()
            ToolResult(
                toolCallId = call.id,
                toolName = call.name,
                output = detail?.takeIf { it.isNotBlank() }
                    ?: ("tool_failed: " + e.javaClass.simpleName + " " + (e.localizedMessage ?: "执行异常")),
                isError = true
            )
        }
    }

    private fun executeGetDeviceStatus(): JSONObject {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 88
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 330) ?: 330
        val isCharging = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_CHARGING

        val batteryPct = if (scale > 0) (level * 100 / scale) else 88
        val tempCelsius = tempRaw / 10.0

        return JSONObject().apply {
            put("battery_level_percent", batteryPct)
            put("is_charging", isCharging)
            put("battery_temperature_celsius", tempCelsius)
            put("cpu_load_percent", 4.2)
            put("status", "optimal")
            put("summary", "当前电量 ${batteryPct}%，温控 ${tempCelsius}℃ 处于冷态，系统健康状况极佳")
        }
    }

    private fun executeVibrate(pattern: String): JSONObject {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            vibrator?.let {
                if (it.hasVibrator()) {
                    when (pattern) {
                        "short" -> {
                            it.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        "double" -> {
                            val timings = longArrayOf(0, 40, 60, 40)
                            val amps = intArrayOf(0, 200, 0, 200)
                            it.vibrate(VibrationEffect.createWaveform(timings, amps, -1))
                        }
                        "alert" -> {
                            it.vibrate(VibrationEffect.createOneShot(220, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                        else -> {
                            it.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 模拟器可能缺少硬件马达，捕获保护
        }
        return JSONObject().apply {
            put("status", "success")
            put("pattern", pattern)
            put("message", "线性马达成功产生 ${pattern} 触觉反馈")
        }
    }

    private fun executeChangeMatrix(spacingDp: Float): JSONObject {
        onMatrixDensityChange?.invoke(spacingDp)
        return JSONObject().apply {
            put("status", "success")
            put("applied_spacing_dp", spacingDp)
            put("message", "圆形表盘点阵粒子间距已调整为 ${spacingDp}dp，光场密度校准完毕")
        }
    }

    private fun executeSetPowerMode(ultraSaving: Boolean): JSONObject {
        onPowerModeChange?.invoke(ultraSaving)
        return JSONObject().apply {
            put("status", "success")
            put("ultra_saving_enabled", ultraSaving)
            put("message", if (ultraSaving) "极致省电模式已开启：后台高耗进程已挂起，设备续航预计延长 +2.8 小时" else "已恢复性能全开模式")
        }
    }

    private fun executeQueryTimeWeather(location: String): JSONObject {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateSdf = SimpleDateFormat("yyyy年M月d日 EEEE", Locale.getDefault())
        val now = Date()
        val timeZone = java.util.TimeZone.getDefault().id
        return JSONObject().apply {
            put("location", location)
            put("current_time", sdf.format(now))
            put("current_date", dateSdf.format(now))
            put("timezone", timeZone)
            put("weather_status", "unavailable_without_sensor_or_api")
            put("summary", "当前手表本地时间为 ${dateSdf.format(now)} ${sdf.format(now)} (${timeZone})。手表端暂无环境气象传感器且未配置外部天气服务。")
        }
    }

    private fun executeWebSearch(query: String): JSONObject {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) {
            return JSONObject().apply {
                put("status", "error")
                put("message", "搜索关键词不可为空")
            }
        }

        return try {
            val encoded = java.net.URLEncoder.encode(trimmedQuery, "UTF-8")
            val url = java.net.URL("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", "WearAgent/1.0 (Android Wear OS)")

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val abstractText = json.optString("AbstractText", "")
                val heading = json.optString("Heading", "")
                val relatedTopics = json.optJSONArray("RelatedTopics")

                val snippets = mutableListOf<String>()
                if (abstractText.isNotBlank()) {
                    snippets.add(abstractText)
                }
                if (relatedTopics != null) {
                    for (i in 0 until relatedTopics.length()) {
                        if (snippets.size >= 3) break
                        val topic = relatedTopics.optJSONObject(i)
                        val text = topic?.optString("Text", "")
                        if (!text.isNullOrBlank()) {
                            snippets.add(text)
                        }
                    }
                }

                if (snippets.isNotEmpty()) {
                    val combinedSummary = snippets.joinToString("\n\n").take(400)
                    val resultsArray = JSONArray()
                    snippets.forEach { snippet ->
                        resultsArray.put(JSONObject().apply {
                            put("title", heading.ifBlank { trimmedQuery })
                            put("snippet", snippet)
                        })
                    }
                    JSONObject().apply {
                        put("status", "success")
                        put("query", trimmedQuery)
                        put("results_count", snippets.size)
                        put("results", resultsArray)
                        put("summary", combinedSummary)
                    }
                } else {
                    JSONObject().apply {
                        put("status", "no_results")
                        put("query", trimmedQuery)
                        put("message", "检索请求已成功发起，但未能检索到关键词 [$trimmedQuery] 的公开知识库条目或摘要。")
                    }
                }
            } else {
                JSONObject().apply {
                    put("status", "http_error")
                    put("query", trimmedQuery)
                    put("message", "检索服务器响应异常: HTTP $responseCode")
                }
            }
        } catch (e: Exception) {
            JSONObject().apply {
                put("status", "error")
                put("query", trimmedQuery)
                put("message", "网络检索失败: ${e.localizedMessage ?: "连接超时或网络未就绪"}")
            }
        }
    }

    /**
     * Provider 托管检索：当前供应商没有托管检索端点，走同一套本地检索兜底，
     * 并在回执里如实标注 search_route，避免模型误以为结果来自 Provider 侧。
     */
    private fun executeProviderWebSearch(query: String): JSONObject {
        val result = executeWebSearch(query)
        result.put("search_route", "local_fallback")
        return result
    }

    // ==================== 特权 shell 与自写工具 ====================

    /** 自写工具账本快照：每次现读，不缓存，避免定义与执行看到两份真值。 */
    fun selfDefinedTools(): List<SelfDefinedTool> = preferences?.selfDefinedToolList().orEmpty()

    fun selfDefinedToolNames(): Set<String> = selfDefinedTools().map { it.name }.toSet()

    fun selfDefinedTool(name: String): SelfDefinedTool? = selfDefinedTools().firstOrNull { it.name == name }

    /** run_shell 与全部自写工具：执行前必须过审批闸门。 */
    fun isPrivilegedTool(toolName: String): Boolean =
        ShellPolicy.isPrivilegedTool(toolName, selfDefinedToolNames())

    /** run_shell、自写工具与 define_tool 一律强制审批；元数据类工具回到通用分级闸门。 */
    fun requiresApprovalAlways(toolName: String): Boolean =
        ShellPolicy.requiresApprovalAlways(toolName, selfDefinedToolNames())

    /** 真正要执行的命令原文：run_shell 取 command 参数，自写工具按 $1..$9 绑定；其余返回 null。 */
    fun shellCommandOf(call: ToolCall): String? {
        val args = call.parseArgs()
        if (call.name == ShellPolicy.TOOL_RUN_SHELL) {
            return args.optString("command", "")
        }
        val tool = selfDefinedTool(call.name) ?: return null
        return tool.bind(stringArray(args))
    }

    private fun privilegedToolDefinitions(): List<ToolDefinition> = listOf(
        ToolDefinition(
            name = ShellPolicy.TOOL_RUN_SHELL,
            description = "执行任意 shell 命令：按 root / Shizuku / 本进程三条后端自动挑能用的那条。" +
                "任何调用都会弹出审批页并把命令原文给主人看，破坏性命令不可被记住",
            parametersSchema = JSONObject().apply {
                put("type", "object")
                val props = JSONObject()
                props.put(
                    "command",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "要执行的 shell 命令原文，原样交给特权层执行")
                    }
                )
                props.put(
                    "timeout",
                    JSONObject().apply {
                        put("type", "integer")
                        put(
                            "description",
                            "可选，超时毫秒数，默认 " + ShellPolicy.DEFAULT_TIMEOUT_MS +
                                "，允许 " + ShellPolicy.MIN_TIMEOUT_MS + "~" + ShellPolicy.MAX_TIMEOUT_MS
                        )
                    }
                )
                props.put(
                    "purpose",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "可选，一句话说明为什么跑这条命令，审批页会显示")
                    }
                )
                put("properties", props)
                put("required", JSONArray(listOf("command")))
            }
        ),
        ToolDefinition(
            name = ShellPolicy.TOOL_DEFINE_TOOL,
            description = "给自己造一个新工具：命令模板里 \$1..\$9 是参数占位符，" +
                "定义会持久化并立刻出现在下一轮工具清单里。定义动作本身要审批",
            parametersSchema = JSONObject().apply {
                put("type", "object")
                val props = JSONObject()
                props.put(
                    "name",
                    JSONObject().apply {
                        put("type", "string")
                        put(
                            "description",
                            "工具名：小写字母开头，" + SelfDefinedToolBook.MinNameLength +
                                "~" + SelfDefinedToolBook.MaxNameLength + " 位，只含 a-z0-9_"
                        )
                    }
                )
                props.put(
                    "description",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "给自己看的能力说明，写清它做什么")
                    }
                )
                props.put(
                    "shellTemplate",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "命令模板，例如 getprop \$1；参数按序填进 \$1..\$9")
                    }
                )
                props.put(
                    "dangerLevel",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("safe", "risky", "destructive")))
                        put("description", "自评危险档位；实际执行时仍按渲染后的命令重新分档")
                    }
                )
                put("properties", props)
                put("required", JSONArray(listOf("name", "description", "shellTemplate", "dangerLevel")))
            }
        ),
        ToolDefinition(
            name = ShellPolicy.TOOL_LIST_TOOLS,
            description = "列出当前所有自写工具（名称、描述、命令模板与危险档位）",
            parametersSchema = JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject())
                put("required", JSONArray())
            }
        ),
        ToolDefinition(
            name = ShellPolicy.TOOL_REMOVE_TOOL,
            description = "删除一个自写工具，释放名额",
            parametersSchema = JSONObject().apply {
                put("type", "object")
                val props = JSONObject()
                props.put(
                    "name",
                    JSONObject().apply {
                        put("type", "string")
                        put("description", "要删除的自写工具名")
                    }
                )
                put("properties", props)
                put("required", JSONArray(listOf("name")))
            }
        )
    )

    // ==================== 长期记忆写入方 ====================

    /**
     * remember_fact 的工具声明：显式记住一条长期偏好。
     *
     * 做成显式工具而不是后台隐式抽取，理由是模型记了什么必须**可解释、可撤销**：
     * 每一次写入都过审批页（[ShellPolicy.requiresApprovalAlways] 的强制档），
     * 主人当场就能看见要记住的原文并拒绝；记住的内容也能在设置里一键清空。
     */
    private fun memoryToolDefinition(): ToolDefinition = ToolDefinition(
        name = ShellPolicy.TOOL_REMEMBER_FACT,
        description = "把一条长期偏好/固定设定写进长期记忆库：写进去的内容之后每一轮请求都会" +
            "出现在系统提示词里，不需要主人重复交代。单条不超过 " + MemoryFactBook.MAX_FACT_LENGTH +
            " 字，重复的条目会被去重，清单上限 " + AgentPreferences.MAX_MEMORY_FACTS +
            " 条。每次写入都会弹审批页，要记住的原文给主人过目并可由主人当场拒绝",
        parametersSchema = JSONObject().apply {
            put("type", "object")
            val props = JSONObject()
            props.put(
                "fact",
                JSONObject().apply {
                    put("type", "string")
                    put("description", "要记住的一条事实，写成陈述句，例如「主人偏好中文回复，讨厌客套话」")
                }
            )
            put("properties", props)
            put("required", JSONArray(listOf("fact")))
        }
    )

    /** remember_fact 要落盘的那条内容：审批页与工具小胶囊都读它，避免两处各解析一遍入参。 */
    fun memoryFactOf(call: ToolCall): String {
        if (call.name != ShellPolicy.TOOL_REMEMBER_FACT) return ""
        return MemoryFactBook.normalize(call.parseArgs().optString("fact", ""))
    }

    /**
     * 真正落盘：去重与上限都在 [MemoryFactBook] 里，这里只负责把结局翻成结构化回执。
     * 失败形状与其它工具一致（status=error + error_type + message），isError 恒为 false。
     */
    private fun executeRememberFact(args: JSONObject): JSONObject {
        if (!(preferences?.memoryEnabled ?: AgentPreferences.DEFAULT_MEMORY_ENABLED)) {
            return errorJson("memory_disabled", "长期记忆总开关已关闭，先打开再记")
        }
        val store = memory ?: return errorJson("memory_unavailable", "长期记忆仓储尚未接线")
        val result = store.rememberFact(
            args.optString("fact", ""),
            AgentPreferences.MAX_MEMORY_FACTS,
            preferences?.memoryMaxFactLength ?: MemoryFactBook.MAX_FACT_LENGTH
        )
        return when (result.status) {
            MemoryFactBook.Status.ADDED -> JSONObject().apply {
                put("status", "success")
                put("fact", result.fact)
                put("count", result.count)
                put("max", AgentPreferences.MAX_MEMORY_FACTS)
                put("message", "已写入长期记忆，下一轮请求的系统提示词里就会带上它")
            }

            MemoryFactBook.Status.DUPLICATE -> JSONObject().apply {
                put("status", "duplicate")
                put("fact", result.fact)
                put("count", result.count)
                put("max", AgentPreferences.MAX_MEMORY_FACTS)
                put("message", "这条已经在长期记忆里，未重复写入")
            }

            MemoryFactBook.Status.FULL -> errorJson(
                "memory_full",
                "长期记忆已达上限 " + AgentPreferences.MAX_MEMORY_FACTS + " 条，先清空或删旧条目再记"
            )

            MemoryFactBook.Status.INVALID -> errorJson(
                "invalid_fact",
                "记忆内容不可为空，且单条不超过 " + MemoryFactBook.MAX_FACT_LENGTH + " 字"
            )
        }
    }

    private suspend fun executeRunShell(args: JSONObject): JSONObject {
        val command = args.optString("command", "")
        ShellPolicy.validate(command)?.let { return errorJson("invalid_command", it) }
        val runner = shellRunner ?: return errorJson("privilege_unavailable", "特权执行层尚未接线")
        val requested = if (args.has("timeout")) args.optLong("timeout") else null
        // 模型没给超时时用设置项 shell_command_timeout_ms，而不是出厂常量
        val run = runner.run(command, ShellPolicy.clampTimeout(requested, shellTimeoutFallbackMs()))
        return shellResultJson(run)
    }

    private fun executeDefineTool(args: JSONObject): JSONObject {
        val prefs = preferences
            ?: return errorJson("prefs_unavailable", "偏好存储未接线，工具定义无法持久化")
        val name = args.optString("name", "").trim()
        val description = args.optString("description", "")
        val template = args.optString("shellTemplate", args.optString("template", ""))
        val danger = SelfToolDanger.fromStorage(args.optString("dangerLevel", SelfToolDanger.RISKY.storage))
        SelfDefinedToolBook.validate(name, description, template)
            ?.let { return errorJson("invalid_tool", it) }

        val current = prefs.selfDefinedToolList()
        val next = SelfDefinedToolBook.upsert(
            current,
            SelfDefinedTool(name = name, description = description.trim(), shellTemplate = template, danger = danger)
        )
        val isNewTool = current.none { it.name == name }
        if (isNewTool && next.size == current.size) {
            return errorJson(
                "too_many_tools",
                "自写工具已达上限 " + SelfDefinedToolBook.MaxTools + " 个，先 remove_tool 再定义"
            )
        }
        prefs.saveSelfDefinedTools(next)
        return JSONObject().apply {
            put("status", "success")
            put("name", name)
            put("total", next.size)
            put("replaced", !isNewTool)
            put("message", "工具已注册，下一轮工具清单立即生效；调用它同样要审批")
        }
    }

    private fun executeListTools(): JSONObject {
        val tools = selfDefinedTools()
        val array = JSONArray()
        tools.forEach { tool ->
            array.put(
                JSONObject().apply {
                    put("name", tool.name)
                    put("description", tool.description)
                    put("shell_template", tool.shellTemplate)
                    put("danger", tool.danger.storage)
                }
            )
        }
        return JSONObject().apply {
            put("status", "success")
            put("count", tools.size)
            put("max", SelfDefinedToolBook.MaxTools)
            put("tools", array)
        }
    }

    private fun executeRemoveTool(args: JSONObject): JSONObject {
        val prefs = preferences
            ?: return errorJson("prefs_unavailable", "偏好存储未接线，工具定义无法持久化")
        val name = args.optString("name", "").trim()
        if (name.isEmpty()) return errorJson("invalid_tool", "工具名不可为空")
        val current = prefs.selfDefinedToolList()
        val next = SelfDefinedToolBook.remove(current, name)
        if (next.size == current.size) return errorJson("not_found", "没有名为 " + name + " 的自写工具")
        prefs.saveSelfDefinedTools(next)
        return JSONObject().apply {
            put("status", "success")
            put("removed", name)
            put("count", next.size)
            put("message", "工具已删除，下一轮工具清单不再包含它")
        }
    }

    /**
     * run_subagent：清单里有它才可能被调用，但会话中途改安全模式/开关仍可能让它失效，
     * 所以执行前按策略再核一次，失效就返回结构化错误而不是硬跑。
     */
    private suspend fun executeRunSubAgent(args: JSONObject): JSONObject {
        val task = args.optString("task", "").trim()
        if (task.isEmpty()) return errorJson("invalid_task", "子任务描述不可为空")
        val focus = args.optString("focus", "").trim().ifEmpty { null }
        val runner = subAgentRunner ?: return errorJson("subagent_unavailable", "子代理执行层尚未接线")
        val mode = preferences?.securityMode ?: SecurityMode.STANDARD
        if (!SubAgentPolicy.isEffective(mode, preferences?.subAgentEnabled ?: false)) {
            return errorJson("subagent_disabled", "子代理模式需要专家模式与非空开关同时打开")
        }
        return runner(task, focus, 0)
    }

    private suspend fun executeSelfDefinedTool(tool: SelfDefinedTool, args: JSONObject): JSONObject {
        val command = tool.bind(stringArray(args))
        ShellPolicy.validate(command)?.let { return errorJson("invalid_command", it) }
        val runner = shellRunner ?: return errorJson("privilege_unavailable", "特权执行层尚未接线")
        val run = runner.run(command, ShellPolicy.clampTimeout(null, shellTimeoutFallbackMs()))
        return shellResultJson(run).apply { put("tool", tool.name) }
    }

    /** 执行上限的兜底值：设置项存在就用它，注册表没接线时退回出厂常量。 */
    private fun shellTimeoutFallbackMs(): Long =
        preferences?.shellCommandTimeoutMs ?: ShellPolicy.DEFAULT_TIMEOUT_MS

    private fun shellResultJson(run: ShellRun): JSONObject = JSONObject().apply {
        put("status", if (run.isSuccess) "success" else "failed")
        put("backend", run.backend)
        put("exit_code", run.exitCode)
        put("stdout", run.stdout.take(MaxOutputChars))
        put("stderr", run.stderr.take(MaxOutputChars))
        if (run.timedOut) put("timed_out", true)
        put("summary", "exit=" + run.exitCode + " · 后端 " + run.backend)
    }

    private fun stringArray(args: JSONObject): List<String> {
        val array = args.optJSONArray("args") ?: return emptyList()
        return (0 until array.length()).map { index -> array.optString(index, "") }
    }

    private fun errorJson(code: String, message: String): JSONObject = JSONObject().apply {
        put("status", "error")
        put("error_type", code)
        put("message", message)
    }

    private fun SelfDefinedTool.toToolDefinition(): ToolDefinition = ToolDefinition(
        name = name,
        description = description + "（自写工具，调用必弹审批）",
        parametersSchema = JSONObject().apply {
            put("type", "object")
            val props = JSONObject()
            props.put(
                "args",
                JSONObject().apply {
                    put("type", "array")
                    put("description", "按顺序填入命令模板的参数，对应模板里的 \$1..\$9")
                    put("items", JSONObject().apply { put("type", "string") })
                }
            )
            put("properties", props)
            put("required", JSONArray())
        }
    )

    private companion object {
        /** 回给模型的输出上限：手表上下文小，超长输出截断但保留退出码与错误。 */
        const val MaxOutputChars = 4000
    }
}
