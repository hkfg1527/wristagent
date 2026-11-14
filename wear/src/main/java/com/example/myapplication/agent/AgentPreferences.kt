package com.example.myapplication.agent

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.voice.AsrLanguage
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.AsrSettings

/**
 * Agent 与表盘偏好设置持久化仓库
 */
class AgentPreferences internal constructor(private val prefs: SharedPreferences) {

    /** 生产入口：真机只有这一个存储后端，[prefs] 注入仅为单测提供纯 JVM 接缝。 */
    constructor(context: Context) : this(context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE))

    var securityMode: SecurityMode
        get() {
            val stored = prefs.getString("security_mode", SecurityMode.STANDARD.name)
            return SecurityMode.values().firstOrNull { it.name == stored } ?: SecurityMode.STANDARD
        }
        set(value) = prefs.edit().putString("security_mode", value.name).apply()

    /** 唯一的密钥来源：用户在应用设置里填写，绝不参与构建。 */
    var apiKey: String
        get() = prefs.getString("api_key", "") ?: ""
        set(value) = prefs.edit().putString("api_key", value).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        set(value) = prefs.edit().putString("base_url", value).apply()

    var modelName: String
        get() = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString("model_name", value).apply()

    var matrixSpacingDp: Float
        get() = prefs.getFloat("matrix_spacing_dp", 16f)
        set(value) = prefs.edit().putFloat("matrix_spacing_dp", value).apply()

    /** 显示思考过程：关掉后思考胶囊不再出现在时间线上（推理请求本身不变） */
    var showThoughtChain: Boolean
        get() = prefs.getBoolean("show_thought_chain", true)
        set(value) = prefs.edit().putBoolean("show_thought_chain", value).apply()

    /** 回答风格：CONCISE / BALANCED / DETAILED，注入系统提示词的表达约束。 */
    var replyStyle: String
        get() = prefs.getString(KEY_REPLY_STYLE, ReplyStyle.DEFAULT_STORAGE)
            ?: ReplyStyle.DEFAULT_STORAGE
        set(value) = prefs.edit().putString(KEY_REPLY_STYLE, value).apply()

    /** 推理努力：LOW / MEDIUM / HIGH，映射到请求体 reasoning_effort；默认中档 */
    var reasoningEffort: String
        get() = prefs.getString("reasoning_effort", ReasoningEffort.DEFAULT_STORAGE)
            ?: ReasoningEffort.DEFAULT_STORAGE
        set(value) = prefs.edit().putString("reasoning_effort", value).apply()

    /** 单次请求的读超时（秒）：默认 40，夹在 10..180。表上网络抖，给得比手机宽。 */
    var requestTimeoutSeconds: Int
        get() = prefs.getInt(KEY_REQUEST_TIMEOUT, DEFAULT_REQUEST_TIMEOUT)
            .coerceIn(MinRequestTimeoutSeconds, MaxRequestTimeoutSeconds)
        set(value) = prefs.edit()
            .putInt(KEY_REQUEST_TIMEOUT, value.coerceIn(MinRequestTimeoutSeconds, MaxRequestTimeoutSeconds))
            .apply()

    /** 网络失败自动重试次数：默认 1 次，0..3。 */
    var requestRetries: Int
        get() = prefs.getInt(KEY_REQUEST_RETRIES, DEFAULT_REQUEST_RETRIES).coerceIn(0, MaxRequestRetries)
        set(value) = prefs.edit().putInt(KEY_REQUEST_RETRIES, value.coerceIn(0, MaxRequestRetries)).apply()

    /** 重试前的退避（毫秒）：默认 800，0..5000。 */
    var retryBackoffMs: Int
        get() = prefs.getInt(KEY_RETRY_BACKOFF, DEFAULT_RETRY_BACKOFF).coerceIn(0, MaxRetryBackoffMs)
        set(value) = prefs.edit().putInt(KEY_RETRY_BACKOFF, value.coerceIn(0, MaxRetryBackoffMs)).apply()

    // ===================== 四个真实可调的数值项 =====================
    // 每一项都有真实消费点（流式节流 / run_shell 上限 / 审批滑杆 / TTS 语速）；
    // 读写两侧都夹取 —— 越界值落在盘上会让消费点永远为真或永远为假，等于把功能关死。

    /** 流式合帧间隔（毫秒）：默认 32，夹在 8..200。 */
    var streamEmitIntervalMs: Int
        get() = prefs.getInt(KEY_STREAM_EMIT_INTERVAL_MS, DEFAULT_STREAM_EMIT_INTERVAL_MS)
            .coerceIn(MinStreamEmitIntervalMs, MaxStreamEmitIntervalMs)
        set(value) = prefs.edit()
            .putInt(
                KEY_STREAM_EMIT_INTERVAL_MS,
                value.coerceIn(MinStreamEmitIntervalMs, MaxStreamEmitIntervalMs)
            )
            .apply()

    /** run_shell 的默认执行上限（毫秒）：默认 15000，夹在 [ShellPolicy] 已有的 MIN/MAX 上。 */
    var shellCommandTimeoutMs: Long
        get() = prefs.getLong(KEY_SHELL_COMMAND_TIMEOUT_MS, DEFAULT_SHELL_COMMAND_TIMEOUT_MS)
            .coerceIn(ShellPolicy.MIN_TIMEOUT_MS, ShellPolicy.MAX_TIMEOUT_MS)
        set(value) = prefs.edit()
            .putLong(
                KEY_SHELL_COMMAND_TIMEOUT_MS,
                value.coerceIn(ShellPolicy.MIN_TIMEOUT_MS, ShellPolicy.MAX_TIMEOUT_MS)
            )
            .apply()

    /** 审批滑杆的放行阈值：默认 0.85，夹在 0.5..0.98。 */
    var approvalAllowThreshold: Float
        get() = prefs.getFloat(KEY_APPROVAL_ALLOW_THRESHOLD, DEFAULT_APPROVAL_ALLOW_THRESHOLD)
            .coerceIn(MinApprovalAllowThreshold, MaxApprovalAllowThreshold)
        set(value) = prefs.edit()
            .putFloat(
                KEY_APPROVAL_ALLOW_THRESHOLD,
                value.coerceIn(MinApprovalAllowThreshold, MaxApprovalAllowThreshold)
            )
            .apply()

    /** 播报语速：默认 1.0（引擎原始语速），夹在 0.5..2.0。 */
    var ttsSpeechRate: Float
        get() = prefs.getFloat(KEY_TTS_SPEECH_RATE, DEFAULT_TTS_SPEECH_RATE)
            .coerceIn(MinTtsSpeechRate, MaxTtsSpeechRate)
        set(value) = prefs.edit()
            .putFloat(KEY_TTS_SPEECH_RATE, value.coerceIn(MinTtsSpeechRate, MaxTtsSpeechRate))
            .apply()

    /**
     * 播报音量（0..1）：每次合成前交给 TextToSpeech.setVolume。
     *
     * 手表外放还要再乘系统媒体音量；这里的偏好是给这一条播报再压一层 —— 夜里降到 0.3 不会吵到人。
     */
    var ttsVolume: Float
        get() = prefs.getFloat(KEY_TTS_VOLUME, DEFAULT_TTS_VOLUME)
            .coerceIn(MIN_TTS_VOLUME, MAX_TTS_VOLUME)
        set(value) = prefs.edit()
            .putFloat(KEY_TTS_VOLUME, value.coerceIn(MIN_TTS_VOLUME, MAX_TTS_VOLUME))
            .apply()

    /** 仅 WiFi 下发起请求：默认关。开着时蜂窝网络会直接给出可读回执。 */
    var wifiOnlyRequests: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY, DEFAULT_WIFI_ONLY)
        set(value) = prefs.edit().putBoolean(KEY_WIFI_ONLY, value).apply()

    /** 触觉反馈：默认开。关掉后所有震动（含确认震动）都不再触发。 */
    var hapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS, DEFAULT_HAPTICS)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS, value).apply()

    /**
     * 表冠滚动增益：一格刻度换算成多少像素位移的倍数。
     *
     * 1.0 = 库默认手感；调大转一格走得更远（适合习惯快滚的人），调小更细腻。
     */
    var rotaryScrollGain: Float
        get() = prefs.getFloat(KEY_ROTARY_SCROLL_GAIN, DEFAULT_ROTARY_SCROLL_GAIN)
            .coerceIn(MIN_ROTARY_SCROLL_GAIN, MAX_ROTARY_SCROLL_GAIN)
        set(value) = prefs.edit()
            .putFloat(KEY_ROTARY_SCROLL_GAIN, value.coerceIn(MIN_ROTARY_SCROLL_GAIN, MAX_ROTARY_SCROLL_GAIN))
            .apply()
    /** 设置页外观预设：界面外壳读它，导入导出与控制台也按名字搬运。 */
    var settingsStyle: SettingsStyle
        get() = SettingsStyle.fromStorage(prefs.getString(KEY_SETTINGS_STYLE, DEFAULT_SETTINGS_STYLE))
        set(value) = prefs.edit().putString(KEY_SETTINGS_STYLE, value.name).apply()

    /**
     * 已经真正执行过的工具名：大卡片档用它算「已调用 / 已注册」的圆环比例。
     *
     * 存成一串逗号分隔的名字而不是字符串集合：默认值因此是一个命名常量，
     * 与其它字段的 DEFAULT_* 约定一致，也便于导入导出时逐字段搬运。
     */
    var calledToolNames: Set<String>
        get() {
            val raw = prefs.getString(KEY_CALLED_TOOLS, DEFAULT_CALLED_TOOLS) ?: DEFAULT_CALLED_TOOLS
            return raw.split(",").filter { it.isNotBlank() }.toSet()
        }
        set(value) = prefs.edit().putString(KEY_CALLED_TOOLS, value.joinToString(",")).apply()

    /** 最近一次构建出来的工具清单条数：注册数由注册表自己写，界面不重复数一遍。 */
    var registeredToolCount: Int
        get() = prefs.getInt(KEY_REGISTERED_TOOLS, DEFAULT_REGISTERED_TOOLS)
        set(value) = prefs.edit().putInt(KEY_REGISTERED_TOOLS, value).apply()

    /** 记一次工具调用：同名工具只留一个名字，重复调用不会让圆环虚高。 */
    fun recordToolCall(name: String) {
        if (name.isBlank()) return
        val current = calledToolNames
        if (name in current) return
        calledToolNames = current + name
    }

    var ultraSaving: Boolean
        get() = prefs.getBoolean("ultra_saving", false)
        set(value) = prefs.edit().putBoolean("ultra_saving", value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("has_completed_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_completed_onboarding", value).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value).apply()

    /**
     * 核采样 top_p（0.05..1.0）。0.9 以下会明显收窄候选词，答得更稳但更死板。
     *
     * 默认 1.0 = 不裁剪候选集（与 temperature 正交：温度改形状，top_p 改范围）。
     */
    var samplingTopP: Float
        get() = SamplingPolicy.clampTopP(prefs.getFloat(KEY_SAMPLING_TOP_P, DEFAULT_SAMPLING_TOP_P))
        set(value) = prefs.edit()
            .putFloat(KEY_SAMPLING_TOP_P, SamplingPolicy.clampTopP(value))
            .apply()

    var mcpEndpoint: String
        get() = prefs.getString("mcp_endpoint", "http://127.0.0.1:8000/sse") ?: "http://127.0.0.1:8000/sse"
        set(value) = prefs.edit().putString("mcp_endpoint", value).apply()

    var isMcpEnabled: Boolean
        get() = prefs.getBoolean("is_mcp_enabled", true)
        set(value) = prefs.edit().putBoolean("is_mcp_enabled", value).apply()

    var customSystemPrompt: String
        get() = prefs.getString("custom_system_prompt", "") ?: ""
        set(value) = prefs.edit().putString("custom_system_prompt", value).apply()

    var maxContextTurns: Int
        get() = prefs.getInt("max_context_turns", 30)
        set(value) = prefs.edit().putInt("max_context_turns", value).apply()

    /** 默认 2048：300 会把正常长度的回复硬截断 */
    var maxOutputTokens: Int
        get() = prefs.getInt("max_output_tokens", DEFAULT_MAX_OUTPUT_TOKENS)
        set(value) = prefs.edit().putInt("max_output_tokens", value).apply()

    var streamMode: Boolean
        get() = prefs.getBoolean("stream_mode", true)
        set(value) = prefs.edit().putBoolean("stream_mode", value).apply()

    var cachedRemoteModels: String
        get() = prefs.getString("cached_remote_models", "deepseek-chat,deepseek-reasoner") ?: "deepseek-chat,deepseek-reasoner"
        set(value) = prefs.edit().putString("cached_remote_models", value).apply()

    /**
     * 缓存里的模型清单是否来自端点实拉。
     *
     * false 时 [cachedRemoteModels] 只可能是内置默认名或旧版本写入的值，
     * 界面据此把内置默认名标成「Built-in default」，不当成用户真实可用的模型。
     */
    var modelCatalogFromRemote: Boolean
        get() = prefs.getBoolean("model_catalog_from_remote", false)
        set(value) = prefs.edit().putBoolean("model_catalog_from_remote", value).apply()

    var toolDeviceStatusEnabled: Boolean
        get() = prefs.getBoolean("tool_device_status_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_device_status_enabled", value).apply()

    var toolVibrateEnabled: Boolean
        get() = prefs.getBoolean("tool_vibrate_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_vibrate_enabled", value).apply()

    var toolMatrixDensityEnabled: Boolean
        get() = prefs.getBoolean("tool_matrix_density_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_matrix_density_enabled", value).apply()

    var toolPowerModeEnabled: Boolean
        get() = prefs.getBoolean("tool_power_mode_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_power_mode_enabled", value).apply()

    var toolWeatherEnabled: Boolean
        get() = prefs.getBoolean("tool_weather_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_weather_enabled", value).apply()

    var toolWebSearchEnabled: Boolean
        get() = prefs.getBoolean("tool_web_search_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_web_search_enabled", value).apply()

    /** Agent 自写工具账本：整块 JSON 落盘，define_tool 写入、每次拼工具清单时读回。 */
    var selfDefinedTools: String
        get() = prefs.getString(KEY_SELF_DEFINED_TOOLS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELF_DEFINED_TOOLS, value).apply()

    /** 会话级"记住此命令"清单：整块 JSON 落盘，命中即跳过审批；破坏性命令永不写入。 */
    var rememberedShellCommands: String
        get() = prefs.getString(KEY_REMEMBERED_SHELL_COMMANDS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REMEMBERED_SHELL_COMMANDS, value).apply()

    fun selfDefinedToolList(): List<SelfDefinedTool> = SelfDefinedToolCodec.decode(selfDefinedTools)

    fun saveSelfDefinedTools(tools: List<SelfDefinedTool>) {
        selfDefinedTools = SelfDefinedToolCodec.encode(tools)
    }

    fun rememberedShellCommandList(): List<String> = RememberedCommandCodec.decode(rememberedShellCommands)

    /**
     * 记住一条命令。返回是否真的记住了：破坏性命令、空命令与满额清单都拒绝写入，
     * 于是"记住"永远不可能成为绕过危险命令的入口。
     */
    fun rememberShellCommand(command: String): Boolean {
        val key = ShellPolicy.normalize(command)
        if (key.isEmpty()) return false
        if (ShellPolicy.dangerOf(key) == ShellDanger.DESTRUCTIVE) return false
        val current = rememberedShellCommandList()
        if (current.any { ShellPolicy.normalize(it) == key }) return true
        if (current.size >= MaxRememberedShellCommands) return false
        rememberedShellCommands = RememberedCommandCodec.encode(current + key)
        return true
    }

    /** 清空记住清单：设置页的显式"忘掉全部"入口与手势动作共用。 */
    fun forgetRememberedShellCommands() {
        rememberedShellCommands = ""
    }

    /**
     * 已记住的命令条数。
     *
     * 存在的理由是"上限不可见"：清单满 [MaxRememberedShellCommands] 条之后
     * [rememberShellCommand] 会静默返回 false，用户以为记住了、其实命令照旧走审批。
     * 设置面把当前条数与上限一起显示，满额时用 Warning 色点明。
     */
    val rememberedShellCommandCount: Int
        get() = rememberedShellCommandList().size

    /** 记住清单是否已满：满额后新的"记住"一律被拒绝。 */
    val rememberedShellCommandsFull: Boolean
        get() = rememberedShellCommandCount >= MaxRememberedShellCommands

    /** 语音转写总开关：关闭时上层直接给出中文提示，不发起任何录音或网络请求 */
    var asrEnabled: Boolean
        get() = prefs.getBoolean(KEY_ASR_ENABLED, DEFAULT_ASR_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_ASR_ENABLED, value).apply()

    /** 语音服务厂商，持久化为 AsrProviderId.storageKey 字符串 */
    var asrProvider: String
        get() = prefs.getString(KEY_ASR_PROVIDER, DEFAULT_ASR_PROVIDER) ?: DEFAULT_ASR_PROVIDER
        set(value) = prefs.edit().putString(KEY_ASR_PROVIDER, value).apply()

    var baiduApiKey: String
        get() = prefs.getString(KEY_ASR_BAIDU_API_KEY, DEFAULT_ASR_KEY) ?: DEFAULT_ASR_KEY
        set(value) = prefs.edit().putString(KEY_ASR_BAIDU_API_KEY, value).apply()

    var baiduSecretKey: String
        get() = prefs.getString(KEY_ASR_BAIDU_SECRET_KEY, DEFAULT_ASR_KEY) ?: DEFAULT_ASR_KEY
        set(value) = prefs.edit().putString(KEY_ASR_BAIDU_SECRET_KEY, value).apply()

    /** 识别语言，存百度 dev_pid 数值（1537 普通话 / 1737 英语 / 1637 粤语 / 1837 四川话） */
    var asrDevPid: Int
        get() = prefs.getInt(KEY_ASR_DEV_PID, DEFAULT_ASR_DEV_PID)
        set(value) = prefs.edit().putInt(KEY_ASR_DEV_PID, value).apply()

    /** 动画特效总开关：关闭后点阵不再呼吸/冲击波、审批流光与按钮弹性一并降级为直接切换 */
    var animationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_ANIMATIONS_ENABLED, DEFAULT_ANIMATIONS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, value).apply()

    /**
     * 子代理模式开关。可留但不等于生效：是否真的放行由 SubAgentPolicy.isEffective 判定
     * （只有专家模式 + 本开关打开才生效），避免"开关能拨但没效果"散落成两处判断。
     */
    var subAgentEnabled: Boolean
        get() = prefs.getBoolean(KEY_SUB_AGENT_ENABLED, DEFAULT_SUB_AGENT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SUB_AGENT_ENABLED, value).apply()

    // ===================== 本轮新增的手表设置项 =====================
    // 键名全部沿用网页控制台的 camelCase 字段名：控制台写同一个键，表端只回显，两侧共用一份真值。

    /** 自动播报回答：把助手回复读出来（与「启用语音识别」独立，识别是入、播报是出）。 */
    var autoSpeakReplies: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SPEAK_REPLIES, DEFAULT_AUTO_SPEAK_REPLIES)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SPEAK_REPLIES, value).apply()

    /** 边说边显示文字：识别中间结果实时上屏；关闭后只在最终结果出现时刷新。 */
    var asrLiveTranscript: Boolean
        get() = prefs.getBoolean(KEY_ASR_LIVE_TRANSCRIPT, DEFAULT_ASR_LIVE_TRANSCRIPT)
        set(value) = prefs.edit().putBoolean(KEY_ASR_LIVE_TRANSCRIPT, value).apply()

    /** 允许模型调用 Provider 托管的联网搜索（关闭时只用内置检索）。 */
    var providerManagedSearchEnabled: Boolean
        get() = prefs.getBoolean(KEY_PROVIDER_MANAGED_SEARCH, DEFAULT_PROVIDER_MANAGED_SEARCH)
        set(value) = prefs.edit().putBoolean(KEY_PROVIDER_MANAGED_SEARCH, value).apply()

    /** 默认启用思考：新会话默认带上推理请求（与只控制显示的 [showThoughtChain] 不同）。 */
    var defaultThinkingEnabled: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_THINKING, DEFAULT_DEFAULT_THINKING)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_THINKING, value).apply()

    /** 长期记忆总开关：关闭后不读不写长期记忆库。 */
    var memoryEnabled: Boolean
        get() = prefs.getBoolean(KEY_MEMORY_ENABLED, DEFAULT_MEMORY_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_MEMORY_ENABLED, value).apply()

    /** 自动压缩上下文：占用超过阈值后自动压缩历史，关掉则只在用户手动触发时压缩。 */
    var autoCompressContext: Boolean
        get() = prefs.getBoolean(KEY_AUTO_COMPRESS_CONTEXT, DEFAULT_AUTO_COMPRESS_CONTEXT)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_COMPRESS_CONTEXT, value).apply()

    /**
     * 压缩阈值：上下文占用超过"轮数预算 × 该比例"才开始压缩历史。
     *
     * 写入时按 [CompressPolicy.clampThreshold] 夹到 0.3..0.95：网页控制台可以写任意浮点，
     * 越界值落在盘上会让 [CompressPolicy.shouldCompress] 永远为真或永远为假，等于把功能关死。
     */
    var historyCompressThreshold: Float
        get() = prefs.getFloat(KEY_HISTORY_COMPRESS_THRESHOLD, DEFAULT_HISTORY_COMPRESS_THRESHOLD)
        set(value) = prefs.edit()
            .putFloat(KEY_HISTORY_COMPRESS_THRESHOLD, CompressPolicy.clampThreshold(value))
            .apply()

    /** 工具审批策略三档：全部确认 / 只确认风险项 / 从不确认（网页 tools.toolApprovalPolicy 同键）。 */
    var toolApprovalPolicy: ToolApprovalPolicy
        get() = ToolApprovalPolicy.fromStorage(
            prefs.getString(KEY_TOOL_APPROVAL_POLICY, DEFAULT_TOOL_APPROVAL_POLICY.storageKey)
        )
        set(value) = prefs.edit().putString(KEY_TOOL_APPROVAL_POLICY, value.storageKey).apply()

    /** 沙箱等级三档：只读 / 工作区 / 全权限，决定工具调用能触碰的范围（网页 dialog.sandboxLevel 同键）。 */
    var sandboxLevel: SandboxLevel
        get() = SandboxLevel.fromStorage(
            prefs.getString(KEY_SANDBOX_LEVEL, DEFAULT_SANDBOX_LEVEL.storageKey)
        )
        set(value) = prefs.edit().putString(KEY_SANDBOX_LEVEL, value.storageKey).apply()

    /** 长按绑定：手势落点由 [GestureBindingGate] 解析，默认不绑定任何动作。 */
    var longPressAction: GestureAction
        get() = GestureAction.fromStorage(
            prefs.getString(KEY_LONG_PRESS_ACTION, DEFAULT_LONG_PRESS_ACTION.storageKey)
        )
        set(value) = prefs.edit().putString(KEY_LONG_PRESS_ACTION, value.storageKey).apply()

    var doubleTapAction: GestureAction
        get() = GestureAction.fromStorage(
            prefs.getString(KEY_DOUBLE_TAP_ACTION, DEFAULT_DOUBLE_TAP_ACTION.storageKey)
        )
        set(value) = prefs.edit().putString(KEY_DOUBLE_TAP_ACTION, value.storageKey).apply()

    var sideKeyShortAction: GestureAction
        get() = GestureAction.fromStorage(
            prefs.getString(KEY_SIDE_KEY_SHORT_ACTION, DEFAULT_SIDE_KEY_SHORT_ACTION.storageKey)
        )
        set(value) = prefs.edit().putString(KEY_SIDE_KEY_SHORT_ACTION, value.storageKey).apply()

    var sideKeyLongAction: GestureAction
        get() = GestureAction.fromStorage(
            prefs.getString(KEY_SIDE_KEY_LONG_ACTION, DEFAULT_SIDE_KEY_LONG_ACTION.storageKey)
        )
        set(value) = prefs.edit().putString(KEY_SIDE_KEY_LONG_ACTION, value.storageKey).apply()

    /** 四条手势绑定的整体快照：一次读完，避免解析出一半新一半旧的组合。 */
    fun gestureBindings(): GestureBindings = GestureBindings(
        longPress = longPressAction,
        doubleTap = doubleTapAction,
        sideKeyShort = sideKeyShortAction,
        sideKeyLong = sideKeyLongAction
    )

    /** 简洁模式：压掉次要装饰文案与次级说明行。 */
    var compactMode: Boolean
        get() = prefs.getBoolean(KEY_COMPACT_MODE, DEFAULT_COMPACT_MODE)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT_MODE, value).apply()

    /** 多供应商 Profile 账本；纯逻辑在 [ProviderProfileBook]，这里只负责整块读写。 */
    fun providerProfiles(): ProviderProfileBook =
        ProviderProfileCodec.decode(prefs.getString(KEY_PROVIDER_PROFILES, null))

    /** 整块落盘：Profile 与 activeProfileId 同存一个键，避免两处状态互相错位。 */
    fun saveProviderProfiles(book: ProviderProfileBook) {
        prefs.edit().putString(KEY_PROVIDER_PROFILES, ProviderProfileCodec.encode(book)).apply()
    }

    /** 以当前单值字段为模板复制出一套 Profile（名称留空时按序号自动命名）。 */
    fun createProviderProfileFromCurrent(name: String = ""): ProviderProfileBook {
        val book = providerProfiles()
        val next = book.addFromCurrent(
            name = name.ifBlank { "配置 " + (book.profiles.size + 1) },
            endpoint = ProviderEndpoint(baseUrl = baseUrl, apiKey = apiKey, modelName = modelName),
            id = providerProfileId()
        )
        saveProviderProfiles(next)
        return next
    }

    fun renameProviderProfile(id: String, name: String): ProviderProfileBook {
        val next = providerProfiles().rename(id, name)
        saveProviderProfiles(next)
        return next
    }

    fun deleteProviderProfile(id: String): ProviderProfileBook {
        val next = providerProfiles().remove(id)
        saveProviderProfiles(next)
        return next
    }

    /**
     * 一键套用：只把 Profile 的三项写回既有单值字段并记为 active，其余读取路径完全不变。
     * 返回实际写入的端点，null 表示该 Profile 已不存在。
     */
    fun applyProviderProfile(id: String): ProviderEndpoint? {
        val book = providerProfiles()
        val profile = book.profiles.firstOrNull { it.id == id } ?: return null
        val endpoint = profile.endpoint()
        baseUrl = endpoint.baseUrl
        apiKey = endpoint.apiKey
        modelName = endpoint.modelName
        saveProviderProfiles(book.select(id))
        return endpoint
    }

    /** 供语音链路使用的不可变配置快照，隔离 SharedPreferences 依赖 */
    fun asrSettings(): AsrSettings = AsrSettings(
        enabled = asrEnabled,
        providerId = AsrProviderId.fromStorage(asrProvider),
        baiduApiKey = baiduApiKey,
        baiduSecretKey = baiduSecretKey,
        language = AsrLanguage.fromDevPid(asrDevPid)
    )

    fun resetAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val DEFAULT_MAX_OUTPUT_TOKENS = 2048
        const val KEY_SAMPLING_TOP_P = "sampling_top_p"
        const val DEFAULT_SAMPLING_TOP_P = 1.0f
        const val MIN_SAMPLING_TOP_P = 0.05f
        const val MAX_SAMPLING_TOP_P = 1.0f
        /**
         * 长期记忆条目的硬上限。
         *
         * remember_fact 是唯一的写入方，超限一律拒绝并回 memory_full —— 没有上限的记忆库
         * 会把每一轮请求的系统提示词撑爆。控制台那条「记忆条数上限」滑杆尚未接线，
         * 真正的闸门是这里这个常量。
         */
        const val MAX_MEMORY_FACTS = 50

        const val PreferencesName = "agent_prefs"

        const val KEY_AUTO_SPEAK_REPLIES = "autoSpeakReplies"
        const val KEY_ASR_LIVE_TRANSCRIPT = "asrLiveTranscript"
        const val KEY_PROVIDER_MANAGED_SEARCH = "providerManagedSearchEnabled"
        const val KEY_REPLY_STYLE = "replyStyle"

        /** 工具调用账本与注册数：大卡片档的圆环数据源。 */
        internal const val KEY_CALLED_TOOLS = "called_tool_names"
        internal const val KEY_REGISTERED_TOOLS = "registered_tool_count"
        internal const val KEY_SETTINGS_STYLE = "settings_style"
        internal const val DEFAULT_SETTINGS_STYLE = "BALLOON"

        /** 网络与触觉四件套：键、默认值、上下限都具名，避免四处散落字面量。 */
        const val KEY_REQUEST_TIMEOUT = "requestTimeoutSeconds"
        const val KEY_REQUEST_RETRIES = "requestRetries"
        const val KEY_RETRY_BACKOFF = "retryBackoffMs"
        const val KEY_WIFI_ONLY = "wifiOnlyRequests"
        const val KEY_HAPTICS = "hapticsEnabled"
        const val KEY_ROTARY_SCROLL_GAIN = "rotary_scroll_gain"
        const val DEFAULT_ROTARY_SCROLL_GAIN = 1f
        const val MIN_ROTARY_SCROLL_GAIN = 0.5f
        const val MAX_ROTARY_SCROLL_GAIN = 3f
        const val DEFAULT_REQUEST_TIMEOUT = 40
        const val DEFAULT_REQUEST_RETRIES = 1
        const val DEFAULT_RETRY_BACKOFF = 800
        const val DEFAULT_WIFI_ONLY = false
        const val DEFAULT_HAPTICS = true
        const val MinRequestTimeoutSeconds = 10
        const val MaxRequestTimeoutSeconds = 180
        const val MaxRequestRetries = 3
        const val MaxRetryBackoffMs = 5000

        /** 本轮四个数值项的键：与偏好字段同名；不进可移植键白名单，也不进网页控制台字段表。 */
        const val KEY_STREAM_EMIT_INTERVAL_MS = "stream_emit_interval_ms"
        const val KEY_SHELL_COMMAND_TIMEOUT_MS = "shell_command_timeout_ms"
        const val KEY_APPROVAL_ALLOW_THRESHOLD = "approval_allow_threshold"
        const val KEY_TTS_SPEECH_RATE = "tts_speech_rate"

        const val DEFAULT_STREAM_EMIT_INTERVAL_MS = 32
        const val DEFAULT_SHELL_COMMAND_TIMEOUT_MS = ShellPolicy.DEFAULT_TIMEOUT_MS
        const val DEFAULT_APPROVAL_ALLOW_THRESHOLD = 0.85f
        const val DEFAULT_TTS_SPEECH_RATE = 1.0f
        const val KEY_TTS_VOLUME = "tts_volume"
        const val DEFAULT_TTS_VOLUME = 0.8f
        const val MIN_TTS_VOLUME = 0.1f
        const val MAX_TTS_VOLUME = 1f
        const val MinStreamEmitIntervalMs = 8
        const val MaxStreamEmitIntervalMs = 200
        const val MinApprovalAllowThreshold = 0.5f
        const val MaxApprovalAllowThreshold = 0.98f
        const val MinTtsSpeechRate = 0.5f
        const val MaxTtsSpeechRate = 2.0f
        internal const val DEFAULT_CALLED_TOOLS = ""
        internal const val DEFAULT_REGISTERED_TOOLS = 0
        const val KEY_DEFAULT_THINKING = "defaultThinking"
        const val KEY_MEMORY_ENABLED = "memoryEnabled"
        const val KEY_AUTO_COMPRESS_CONTEXT = "autoCompressContext"
        const val KEY_COMPACT_MODE = "compactMode"

        const val DEFAULT_AUTO_SPEAK_REPLIES = false
        const val DEFAULT_ASR_LIVE_TRANSCRIPT = true
        const val DEFAULT_PROVIDER_MANAGED_SEARCH = false
        const val DEFAULT_DEFAULT_THINKING = true
        const val DEFAULT_MEMORY_ENABLED = true
        const val DEFAULT_AUTO_COMPRESS_CONTEXT = true
        const val DEFAULT_COMPACT_MODE = false

        const val KEY_HISTORY_COMPRESS_THRESHOLD = "historyCompressThreshold"
        const val KEY_TOOL_APPROVAL_POLICY = "toolApprovalPolicy"
        const val KEY_SANDBOX_LEVEL = "sandboxLevel"
        const val KEY_LONG_PRESS_ACTION = "longPressAction"
        const val KEY_DOUBLE_TAP_ACTION = "doubleTapAction"
        const val KEY_SIDE_KEY_SHORT_ACTION = "sideKeyShortAction"
        const val KEY_SIDE_KEY_LONG_ACTION = "sideKeyLongAction"

        const val DEFAULT_HISTORY_COMPRESS_THRESHOLD = CompressPolicy.DefaultThreshold
        val DEFAULT_TOOL_APPROVAL_POLICY = ToolApprovalPolicy.RISKY
        val DEFAULT_SANDBOX_LEVEL = SandboxLevel.FULL
        val DEFAULT_LONG_PRESS_ACTION = GestureAction.NONE
        val DEFAULT_DOUBLE_TAP_ACTION = GestureAction.NONE
        val DEFAULT_SIDE_KEY_SHORT_ACTION = GestureAction.NONE
        val DEFAULT_SIDE_KEY_LONG_ACTION = GestureAction.NONE

        const val KEY_ASR_ENABLED = "asr_enabled"
        const val KEY_ASR_PROVIDER = "asr_provider"
        const val KEY_ASR_BAIDU_API_KEY = "asr_baidu_api_key"
        const val KEY_ASR_BAIDU_SECRET_KEY = "asr_baidu_secret_key"
        const val KEY_ASR_DEV_PID = "asr_dev_pid"

        const val DEFAULT_ASR_ENABLED = false
        const val DEFAULT_ASR_PROVIDER = "SYSTEM"
        const val DEFAULT_ASR_DEV_PID = 1537
        const val DEFAULT_ASR_KEY = ""

        /** 控制台字段名与网页端一致，改名前先对 console 侧同步。 */
        const val KEY_SELF_DEFINED_TOOLS = "self_defined_tools"
        const val KEY_REMEMBERED_SHELL_COMMANDS = "remembered_shell_commands"

        /** 记住清单上限：手表上不宜无限膨胀，满额后新的"记住"直接不生效。 */
        const val MaxRememberedShellCommands = 20

        const val KEY_ANIMATIONS_ENABLED = "animationsEnabled"
        const val KEY_PROVIDER_PROFILES = "provider_profiles"
        const val KEY_SUB_AGENT_ENABLED = "subAgentEnabled"

        const val DEFAULT_ANIMATIONS_ENABLED = true
        const val DEFAULT_SUB_AGENT_ENABLED = false
    }
}

/** Profile 的稳定 id：手表端只用于列表选中与增删，不做任何顺序语义。 */
internal fun providerProfileId(): String =
    java.util.UUID.randomUUID().toString().replace("-", "").take(12)

/** 套用 Profile 时写入的三项单值字段。 */
data class ProviderEndpoint(
    val baseUrl: String,
    val apiKey: String,
    val modelName: String
)

/** 一套供应商配置：一键套用只是把这三项写回既有单值偏好，读取路径完全不变。 */
data class ProviderProfile(
    val id: String,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val modelName: String
) {
    fun endpoint(): ProviderEndpoint = ProviderEndpoint(
        baseUrl = baseUrl,
        apiKey = apiKey,
        modelName = modelName
    )
}

/**
 * 多供应商 Profile 的纯逻辑账本：不碰 SharedPreferences、不碰 Compose，单测直接钉死
 * 「最多 5 套」「删除掉当前选中项后 activeProfileId 回退」「改名不改选中项」这些硬约束。
 *
 * 每次改动都返回新实例，调用方拿返回值再整块落盘，避免就地改列表造成列表与选中项错位。
 */
data class ProviderProfileBook(
    val profiles: List<ProviderProfile> = emptyList(),
    val activeProfileId: String? = null
) {
    fun active(): ProviderProfile? = profiles.firstOrNull { it.id == activeProfileId }

    /** 新增一套：已满 [MaxProfiles] 套或 id 重复时原样返回。 */
    fun add(profile: ProviderProfile): ProviderProfileBook {
        if (profiles.size >= MaxProfiles) return this
        if (profiles.any { it.id == profile.id }) return this
        return copy(profiles = profiles + profile)
    }

    /** 以当前单值字段为模板新增一套，并立即设为 active。 */
    fun addFromCurrent(name: String, endpoint: ProviderEndpoint, id: String): ProviderProfileBook =
        add(
            ProviderProfile(
                id = id,
                name = name,
                baseUrl = endpoint.baseUrl,
                apiKey = endpoint.apiKey,
                modelName = endpoint.modelName
            )
        ).select(id)

    /** 改名：只动名称，选中项不变；目标不存在或名称为空时原样返回。 */
    fun rename(id: String, name: String): ProviderProfileBook {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return this
        if (profiles.none { it.id == id }) return this
        return copy(
            profiles = profiles.map { if (it.id == id) it.copy(name = trimmed) else it }
        )
    }

    /** 删除：删掉的正是选中项时回退到剩余第一套；删空则选中项置 null。 */
    fun remove(id: String): ProviderProfileBook {
        val remaining = profiles.filterNot { it.id == id }
        if (remaining.size == profiles.size) return this
        val nextActive = if (activeProfileId == id) {
            remaining.firstOrNull()?.id
        } else {
            activeProfileId?.takeIf { active -> remaining.any { it.id == active } }
        }
        return copy(profiles = remaining, activeProfileId = nextActive)
    }

    /** 切换选中项：目标不存在时原样返回。 */
    fun select(id: String): ProviderProfileBook {
        if (profiles.none { it.id == id }) return this
        return copy(activeProfileId = id)
    }

    companion object {
        const val MaxProfiles = 5
    }
}

/**
 * Profile 账本的一行文本编解码。
 *
 * 格式：版本标记 + 选中项 id + 每条记录的五个字段，字段间用 UNIT SEPARATOR、记录间用
 * RECORD SEPARATOR（这两个字符在输入框里敲不出来，用普通分隔符会把 baseUrl 拆坏）。
 * 字段值里的分隔符按 "~~"/"~f"/"~r" 转义，于是任何字符都能原样往返。
 */
object ProviderProfileCodec {
    private const val VersionTag = "v1"
    private const val RecordSeparator = '\u001E'
    private const val FieldSeparator = '\u001F'

    fun encode(book: ProviderProfileBook): String {
        val body = book.profiles.joinToString(RecordSeparator.toString()) { profile ->
            listOf(
                profile.id,
                profile.name,
                profile.baseUrl,
                profile.apiKey,
                profile.modelName
            ).joinToString(FieldSeparator.toString()) { escape(it) }
        }
        return listOf(VersionTag, book.activeProfileId.orEmpty(), body)
            .joinToString(RecordSeparator.toString())
    }

    fun decode(raw: String?): ProviderProfileBook {
        if (raw.isNullOrEmpty()) return ProviderProfileBook()
        val parts = raw.split(RecordSeparator, limit = 3)
        if (parts.size < 3 || parts[0] != VersionTag) return ProviderProfileBook()
        val active = parts[1].ifEmpty { null }
        val profiles = parts[2]
            .split(RecordSeparator)
            .filter { it.isNotEmpty() }
            .mapNotNull { record ->
                val fields = record.split(FieldSeparator).map { unescape(it) }
                if (fields.size != 5 || fields[0].isEmpty()) return@mapNotNull null
                ProviderProfile(
                    id = fields[0],
                    name = fields[1],
                    baseUrl = fields[2],
                    apiKey = fields[3],
                    modelName = fields[4]
                )
            }
        return ProviderProfileBook(
            profiles = profiles,
            activeProfileId = active?.takeIf { id -> profiles.any { it.id == id } }
        )
    }

    private fun escape(value: String): String {
        val out = StringBuilder(value.length)
        for (ch in value) {
            when (ch) {
                '~' -> out.append("~~")
                FieldSeparator -> out.append("~f")
                RecordSeparator -> out.append("~r")
                else -> out.append(ch)
            }
        }
        return out.toString()
    }

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val ch = value[index]
            if (ch == '~' && index + 1 < value.length) {
                when (value[index + 1]) {
                    '~' -> { out.append('~'); index += 2; continue }
                    'f' -> { out.append(FieldSeparator); index += 2; continue }
                    'r' -> { out.append(RecordSeparator); index += 2; continue }
                }
            }
            out.append(ch)
            index++
        }
        return out.toString()
    }
}


