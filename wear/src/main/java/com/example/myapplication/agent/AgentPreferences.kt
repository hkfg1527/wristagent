package com.example.myapplication.agent

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
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

    var rightButtonAction: RightButtonAction
        get() {
            val stored = prefs.getString("right_button_action", RightButtonAction.MODEL_SWITCH.name)
            return RightButtonAction.values().firstOrNull { it.name == stored } ?: RightButtonAction.MODEL_SWITCH
        }
        set(value) = prefs.edit().putString("right_button_action", value.name).apply()

    var apiKey: String
        get() {
            val saved = prefs.getString("api_key", "") ?: ""
            if (saved.isNotBlank()) return saved
            return com.example.myapplication.BuildConfig.DEEPSEEK_API_KEY
        }
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

    /** 推理努力：LOW / MEDIUM / HIGH，映射到请求体 reasoning_effort；默认中档 */
    var reasoningEffort: String
        get() = prefs.getString("reasoning_effort", ReasoningEffort.DEFAULT_STORAGE)
            ?: ReasoningEffort.DEFAULT_STORAGE
        set(value) = prefs.edit().putString("reasoning_effort", value).apply()

    var ultraSaving: Boolean
        get() = prefs.getBoolean("ultra_saving", false)
        set(value) = prefs.edit().putBoolean("ultra_saving", value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("has_completed_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_completed_onboarding", value).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value).apply()

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

    var mainInputType: String
        get() = prefs.getString("main_input_type", "VOICE") ?: "VOICE"
        set(value) = prefs.edit().putString("main_input_type", value).apply()

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

    /** 清空记住清单：设置页与"全部忘记"共用。 */
    fun forgetRememberedShellCommands() {
        rememberedShellCommands = ""
    }

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

    /** 图片识别内测开关：只放开入口，识别服务尚未接入 */
    var visionBetaEnabled: Boolean
        get() = prefs.getBoolean(KEY_VISION_BETA_ENABLED, DEFAULT_VISION_BETA_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_VISION_BETA_ENABLED, value).apply()

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

    /**
     * 亮屏唤起自动语音输入。
     *
     * 字段保留只为兼容网页控制台写入与历史数据回显，**运行时没有任何读取点**：
     * 亮屏即开麦会让音频链路在每次抬腕时唤醒，是手表续航的不可接受形态，该唤起路径已整体删除。
     * 设置页把这一项画成只读「已移除」，不再提供可写开关。
     */
    var voiceWakeOnScreen: Boolean
        get() = prefs.getBoolean(KEY_VOICE_WAKE_ON_SCREEN, DEFAULT_VOICE_WAKE_ON_SCREEN)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_WAKE_ON_SCREEN, value).apply()

    /** 锁屏唤起自动语音输入：与 [voiceWakeOnScreen] 同理由，路径已删除，仅回显。 */
    var voiceWakeOnLockScreen: Boolean
        get() = prefs.getBoolean(KEY_VOICE_WAKE_ON_LOCK, DEFAULT_VOICE_WAKE_ON_LOCK)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_WAKE_ON_LOCK, value).apply()

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
     * 深色模式下使用纯黑背景。
     *
     * 当前令牌集的底色恒为 0xFF000000（见 WearTokens.Palette.Bg），因此这一项在表端是只读回显：
     * 想改令牌值必须走设计侧审批，设置面不提供可写开关，避免出现"开关能拨但屏上无变化"。
     */
    var usePureBlack: Boolean
        get() = prefs.getBoolean(KEY_USE_PURE_BLACK, DEFAULT_USE_PURE_BLACK)
        set(value) = prefs.edit().putBoolean(KEY_USE_PURE_BLACK, value).apply()

    /** 主题模式三档：表端只有深色令牌集，落在表端的是只读回显，真正的切换在网页端。 */
    var themeMode: ThemeMode
        get() = ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, DEFAULT_THEME_MODE.storageKey))
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value.storageKey).apply()

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

        const val PreferencesName = "agent_prefs"

        const val KEY_AUTO_SPEAK_REPLIES = "autoSpeakReplies"
        const val KEY_ASR_LIVE_TRANSCRIPT = "asrLiveTranscript"
        const val KEY_VOICE_WAKE_ON_SCREEN = "voiceWakeOnScreen"
        const val KEY_VOICE_WAKE_ON_LOCK = "voiceWakeOnLockScreen"
        const val KEY_PROVIDER_MANAGED_SEARCH = "providerManagedSearchEnabled"
        const val KEY_DEFAULT_THINKING = "defaultThinking"
        const val KEY_MEMORY_ENABLED = "memoryEnabled"
        const val KEY_AUTO_COMPRESS_CONTEXT = "autoCompressContext"
        const val KEY_USE_PURE_BLACK = "usePureBlack"
        const val KEY_THEME_MODE = "themeMode"
        const val KEY_COMPACT_MODE = "compactMode"

        const val DEFAULT_AUTO_SPEAK_REPLIES = false
        const val DEFAULT_ASR_LIVE_TRANSCRIPT = true
        const val DEFAULT_VOICE_WAKE_ON_SCREEN = false
        const val DEFAULT_VOICE_WAKE_ON_LOCK = false
        const val DEFAULT_PROVIDER_MANAGED_SEARCH = false
        const val DEFAULT_DEFAULT_THINKING = true
        const val DEFAULT_MEMORY_ENABLED = true
        const val DEFAULT_AUTO_COMPRESS_CONTEXT = true
        const val DEFAULT_USE_PURE_BLACK = true
        const val DEFAULT_COMPACT_MODE = false
        val DEFAULT_THEME_MODE = ThemeMode.DARK

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
        const val KEY_VISION_BETA_ENABLED = "vision_beta_enabled"
        const val KEY_PROVIDER_PROFILES = "provider_profiles"
        const val KEY_SUB_AGENT_ENABLED = "subAgentEnabled"

        const val DEFAULT_ANIMATIONS_ENABLED = true
        const val DEFAULT_VISION_BETA_ENABLED = false
        const val DEFAULT_SUB_AGENT_ENABLED = false
    }
}

/** 主题模式三档：跟随系统 / 深色 / 浅色。存储值即枚举名，未知值一律回退深色。 */
enum class ThemeMode(val storageKey: String) {
    SYSTEM("SYSTEM"),
    DARK("DARK"),
    LIGHT("LIGHT");

    companion object {
        /** 未知 / 空 / 大小写不符一律回退 [DARK]：宁可停在确定可用的深色，也不猜。 */
        fun fromStorage(raw: String?): ThemeMode =
            values().firstOrNull { it.storageKey == raw } ?: DARK
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


