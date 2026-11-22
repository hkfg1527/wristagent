package com.example.myapplication.presentation.ui.settingsv2

/**
 * 设置页 V2 的内容模型：**纯数据，不含任何渲染**。
 *
 * 旧实现把"内容"和"长什么样"搅在同一个 2000 行文件里，改一个间距要翻十个分支。
 * 这里切开：内容只有分区与行；每套风格自己决定怎么画。
 */
data class SettingsV2Section(
    val title: String,
    val rows: List<SettingsV2Row>,
)

/** 一行设置：标题 + 可选副标题 + 可选行尾（值/开关/箭头）。 */
data class SettingsV2Row(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val trailing: RowTrailing = RowTrailing.None,
    val onClick: (() -> Unit)? = null,
)

/** 行尾形态。渲染侧按自己的风格决定"开关画成什么样"。 */
sealed interface RowTrailing {
    data object None : RowTrailing
    data class Value(val text: String) : RowTrailing
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : RowTrailing
    data object Chevron : RowTrailing
}

/**
 * V2 的内容（当前是示例数据，等 R6 之后接到真实偏好）。
 *
 * **分区名必须与应用真实的设置分区一致**：`release-check` 的"四分区按序"判据就是按这四个名字扫的，
 * 旧实现曾因为改过名字让这条判据静默失灵一次。
 */
object SettingsV2SampleData {

    private const val ZONE_CONNECT = "连得上"
    private const val ZONE_ACT = "让它能做事"
    private const val ZONE_HEAR = "让它听懂我"
    private const val ZONE_ANSWER = "让它答得好"

    fun root(): List<SettingsV2Section> = listOf(
        SettingsV2Section(
            title = ZONE_CONNECT,
            rows = listOf(
                SettingsV2Row(key = "provider", title = "供应商", trailing = RowTrailing.Value("配置 1")),
                SettingsV2Row(key = "model", title = "模型", trailing = RowTrailing.Value("deepseek-v4")),
                SettingsV2Row(key = "temperature", title = "温度", subtitle = "越高越发散", trailing = RowTrailing.Value("1.4")),
            )
        ),
        SettingsV2Section(
            title = ZONE_ACT,
            rows = listOf(
                SettingsV2Row(key = "tools", title = "工具与 MCP", subtitle = "全部启用", trailing = RowTrailing.Chevron),
                SettingsV2Row(key = "security", title = "安全策略", trailing = RowTrailing.Value("标准提示")),
                SettingsV2Row(key = "root", title = "特权执行", trailing = RowTrailing.Value("ROOT")),
            )
        ),
        SettingsV2Section(
            title = ZONE_HEAR,
            rows = listOf(
                SettingsV2Row(key = "voice", title = "语音与唤醒", subtitle = "出向能力独立", trailing = RowTrailing.Chevron),
                SettingsV2Row(key = "tts", title = "自动播报回答", trailing = RowTrailing.Toggle(checked = false, onChange = {})),
                SettingsV2Row(key = "wake", title = "唤醒词", trailing = RowTrailing.Value("关闭")),
            )
        ),
        SettingsV2Section(
            title = ZONE_ANSWER,
            rows = listOf(
                SettingsV2Row(key = "style", title = "页面风格", trailing = RowTrailing.Chevron),
                SettingsV2Row(key = "compact", title = "紧凑模式", trailing = RowTrailing.Toggle(checked = false, onChange = {})),
                SettingsV2Row(key = "console", title = "网页控制台", trailing = RowTrailing.Value("8080")),
            )
        ),
    )

    /** 供判据读取：四分区标题（顺序即页面顺序）。 */
    val zoneTitles: List<String> = listOf(ZONE_CONNECT, ZONE_ACT, ZONE_HEAR, ZONE_ANSWER)

    /**
     * 用**真实偏好**生成内容（R6 之后接的第一批）。
     *
     * 目前接实值的行（模型 / 温度 / 回复风格 / 页面风格 / 安全策略 / 特权执行 / 自动播报 / 紧凑模式 / 工具数），其余行暂时沿用示例文案 ——
     * 这一点必须在代码里显式标注，不能让"看起来像真的"冒充"已经接好"。
     * 接实值的方式是**每次重组时读一遍偏好**，而不是缓存快照：控制台改完偏好会 bump
     * `SettingsChangeBus`，宿主据此重组。
     */
    fun fromPreferences(preferences: com.example.myapplication.agent.AgentPreferences): List<SettingsV2Section> {
        val wiredModel = preferences.modelName.ifBlank { "未设置" }
        val wiredTemperature = formatTemperature(preferences.temperature)
        val wiredReplyStyle = preferences.replyStyle.ifBlank { "默认" }
        // SettingsStyle 是 appearance 包的枚举，没有中文 label，这里用枚举名（与 /api/state 一致）
        val wiredStyle = preferences.settingsStyle.name
        // 新增接实值的三行：安全策略 / 自动播报 / 特权执行。
        // 都直接读偏好枚举名或布尔值，不做文案美化 —— 控制台 /api/state 里显示什么，这里就该显示什么。
        val wiredSecurity = preferences.securityMode.name
        val wiredTts = preferences.autoSpeakReplies
        val wiredSandbox = preferences.sandboxLevel.name
        // 工具与 MCP：派生"n/7 启用"（六个工具开关 + MCP）。这是真实的组合状态，
        // 比"全部启用"这种写死的文案诚实 —— 文案会与开关实际状态脱节。
        val toolFlags = listOf(
            preferences.toolDeviceStatusEnabled,
            preferences.toolVibrateEnabled,
            preferences.toolMatrixDensityEnabled,
            preferences.toolPowerModeEnabled,
            preferences.toolWeatherEnabled,
            preferences.toolWebSearchEnabled,
            preferences.isMcpEnabled,
        )
        val wiredToolsSummary = "${toolFlags.count { it }}/${toolFlags.size} 启用"
        val wiredCompact = preferences.compactMode

        return listOf(
            SettingsV2Section(
                title = ZONE_CONNECT,
                rows = listOf(
                    // 实值
                    SettingsV2Row(key = "model", title = "模型", trailing = RowTrailing.Value(wiredModel)),
                    // 实值
                    SettingsV2Row(key = "temperature", title = "温度", subtitle = "越高越发散", trailing = RowTrailing.Value(wiredTemperature)),
                    // 实值
                    SettingsV2Row(key = "reply_style", title = "回复风格", trailing = RowTrailing.Value(wiredReplyStyle)),
                )
            ),
            SettingsV2Section(
                title = ZONE_ACT,
                rows = listOf(
                    // 实值：n/7 启用（六个工具开关 + MCP）
                    SettingsV2Row(key = "tools", title = "工具与 MCP", subtitle = wiredToolsSummary, trailing = RowTrailing.Chevron),
                    // 实值
                    SettingsV2Row(key = "security", title = "安全策略", trailing = RowTrailing.Value(wiredSecurity)),
                    // 实值
                    SettingsV2Row(key = "root", title = "特权执行", trailing = RowTrailing.Value(wiredSandbox)),
                )
            ),
            SettingsV2Section(
                title = ZONE_HEAR,
                rows = listOf(
                    SettingsV2Row(key = "voice", title = "语音与唤醒", subtitle = "出向能力独立", trailing = RowTrailing.Chevron),
                    SettingsV2Row(key = "tts", title = "自动播报回答", trailing = RowTrailing.Toggle(checked = wiredTts, onChange = {})),
                    SettingsV2Row(key = "wake", title = "唤醒词", trailing = RowTrailing.Value("关闭")),
                )
            ),
            SettingsV2Section(
                title = ZONE_ANSWER,
                rows = listOf(
                    // 实值：风格名用旧枚举的中文 label，与当前生效的风格一致
                    SettingsV2Row(key = "style", title = "页面风格", trailing = RowTrailing.Value(wiredStyle)),
                    SettingsV2Row(key = "compact", title = "紧凑模式", trailing = RowTrailing.Toggle(checked = wiredCompact, onChange = {})),
                    SettingsV2Row(key = "console", title = "网页控制台", trailing = RowTrailing.Value("8080")),
                )
            ),
        )
    }

    /** 温度显示：一位小数，避免 0.7000001 这种浮点噪声上屏。 */
    internal fun formatTemperature(value: Float): String {
        val rounded = Math.round(value * 10f) / 10f
        return rounded.toString()
    }
}
