package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.presentation.ui.settings.SettingsValueMath

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

/** 一个可选项：显示用 [label]，落库用 [value]（枚举 storageKey / 数值字符串）。 */
data class ChoiceOption(val value: String, val label: String)

/** 一行"点开后从几个选项里选一个"的规格。 */
data class RowChoice(
    val options: List<ChoiceOption>,
    val selectedValue: String,
) {
    /** 当前值对应的显示文本；值没在列表里时回退到原值本身。 */
    fun labelOfSelected(): String =
        options.firstOrNull { it.value == selectedValue }?.label ?: selectedValue
}

/** 一行设置：标题 + 可选副标题 + 可选行尾（值/开关/箭头），或一组可选项。 */
data class SettingsV2Row(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val trailing: RowTrailing = RowTrailing.None,
    val onClick: (() -> Unit)? = null,
    val choice: RowChoice? = null,
    /** 滑块行右侧显示的当前值文本（如 "16dp"）。 */
    val sliderText: String? = null,
)

/** 行尾形态。渲染侧按自己的风格决定"开关画成什么样"。 */
sealed interface RowTrailing {
    data object None : RowTrailing
    data class Value(val text: String) : RowTrailing
    data class Toggle(val checked: Boolean, val onChange: (Boolean) -> Unit) : RowTrailing
    data object Chevron : RowTrailing

    /**
     * 连续量（点阵密度、语速…）用**滑块**，不是开关。
     *
     * 用户原话："点阵密度你给个开关，不应该是官方那种进度条控件吗"。
     * 开关只有两态，而 matrixSpacingDp 是 0..64 的连续值 —— 用开关等于把可调范围压成 0/1，
     * 是语义错误，不只是不好看。
     */
    data class Slider(
        val value: Float,
        val range: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val onChange: (Float) -> Unit,
    ) : RowTrailing
}

/**
 * 该行是否可点：开关、跳转、选项组都可点，只读值/无行尾不可点。
 *
 * **滑块行不算"可点"**：它靠拖动滑块交互，整行再挂一层 clickable 会把拖动抢走 ——
 * 真机 0.0.0.264 实测：点/左拖/右拖滑块，温度一秒都不动；而 JVM 的 `SetProgress` 是通的，
 * 差别就在"有没有人替它拦住触摸"。语义上也讲不通：点一下这行没有任何意义。
 */
fun SettingsV2Row.isActionable(): Boolean =
    onClick != null || choice != null || trailing is RowTrailing.Toggle ||
        trailing is RowTrailing.Chevron

/** 一行的数据源：把"长什么样"与"值从哪来"分开，root() 与 fromPreferences() 共用同一套骨架。 */
data class RowSpec(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
    val toggle: Boolean? = null,
    val choice: RowChoice? = null,
    val chevron: Boolean = false,
    /** 滑块：非 null 时这一行画成滑块而不是开关/文本。 */
    val slider: Float? = null,
    val sliderRange: ClosedFloatingPointRange<Float> = 0f..1f,
    val sliderSteps: Int = 0,
    val sliderFormat: (Float) -> String = { it.toString() },
) {
    fun toRow(): SettingsV2Row = SettingsV2Row(
        key = key,
        title = title,
        subtitle = subtitle,
        trailing = when {
            slider != null -> RowTrailing.Slider(slider, sliderRange, sliderSteps, {})
            toggle != null -> RowTrailing.Toggle(checked = toggle, onChange = {})
            chevron -> RowTrailing.Chevron
            value != null -> RowTrailing.Value(value)
            else -> RowTrailing.None
        },
        choice = choice,
        sliderText = slider?.let { sliderFormat(it) },
    )
}

/** 一行数据的来源：示例值 / 真实偏好值，但行 key 与顺序完全由同一份骨架决定。 */data class SettingsV2Content(
    val connection: List<RowSpec>,
    val action: List<RowSpec>,
    val hear: List<RowSpec>,
    val answer: List<RowSpec>,
) {
    fun toSections(): List<SettingsV2Section> = listOf(
        SettingsV2Section("连得上", connection.map { it.toRow() }),
        SettingsV2Section("让它能做事", action.map { it.toRow() }),
        SettingsV2Section("让它听懂我", hear.map { it.toRow() }),
        SettingsV2Section("让它答得好", answer.map { it.toRow() }),
    )
}

/**
 * V2 的内容。**四个分区名是硬判据**（`release-check` 的"四分区按序"按这四个名字扫），不能改。
 *
 * 结构只有一份：[SettingsV2Content] 的四个列表。root() 与 fromPreferences() 只是往同一副骨架里
 * 填不同的值 —— 这就从根上消灭了"两处手写行结构、改一处漏一处"的事故（本会话踩过三次）。
 */
object SettingsV2SampleData {

    val zoneTitles: List<String> = listOf("连得上", "让它能做事", "让它听懂我", "让它答得好")

    /** 示例内容（分区列表），供预览/测试/默认参数用。 */
    fun root(): List<SettingsV2Section> = exampleContent().toSections()

    /** 示例内容：所有开关按默认值，所有值用占位文案。 */
    private fun exampleContent(): SettingsV2Content = SettingsV2Content(
        connection = listOf(
            RowSpec("provider", "供应商", value = "deepseek", choice = providerChoiceSample()),
            RowSpec("model", "模型", value = "deepseek-v4", choice = modelChoice("deepseek-chat,deepseek-reasoner", "deepseek-v4")),
            // 数值项在示例里也是滑块（不是选项表），与实值路径保持同一形状
            RowSpec(
                "temperature", "温度",
                subtitle = "越高越发散",
                slider = 0.7f,
                sliderRange = SettingsValueMath.TemperatureMin..SettingsValueMath.TemperatureMax,
                sliderSteps = 0,
                sliderFormat = { formatTemperature(it) },
            ),
            RowSpec("reply_style", "回复风格", value = "均衡", choice = replyChoice("BALANCED")),
        ),
        action = listOf(
            RowSpec("tools", "工具与 MCP", subtitle = "7/7 启用"),
            RowSpec("tool_device", "设备状态", toggle = true),
            RowSpec("tool_vibrate", "振动", toggle = true),
            RowSpec("tool_matrix", "点阵密度", slider = 16f, sliderRange = 0f..32f, sliderSteps = 7, sliderFormat = { "${it.toInt()}dp" }),
            RowSpec("tool_power", "省电模式", toggle = true),
            RowSpec("tool_weather", "天气", toggle = true),
            RowSpec("tool_web", "联网搜索", toggle = true),
            RowSpec("mcp", "MCP", toggle = true),
            RowSpec("security", "安全策略", value = "标准", choice = securityChoice("STANDARD")),
            RowSpec("root", "特权执行", value = "全权限", choice = sandboxChoice("FULL")),
        ),
        hear = listOf(
            RowSpec("voice", "语音与唤醒", subtitle = "SYSTEM · 未启用"),
            RowSpec("asr_enabled", "语音识别", toggle = false),
            RowSpec("asr_lang", "识别语言", value = "普通话", choice = asrLanguageChoice("1537")),
            RowSpec("asr_live", "实时转写", toggle = true),
            RowSpec("tts", "自动播报回答", toggle = false),
            RowSpec("wake", "唤醒词", value = "关闭"),
        ),
        answer = listOf(
            RowSpec("style", "页面风格", subtitle = "BALLOON", chevron = true),
            RowSpec("compact", "紧凑模式", toggle = false),
            RowSpec("console", "网页控制台", value = "8080"),
        ),
    )

    /**
     * 用**真实偏好**生成分区列表：与 [root] 共用同一副骨架，只把值换成偏好里的真值。
     * 每次重组时读一遍偏好（控制台改完会 bump SettingsChangeBus），不缓存快照。
     */
    fun fromPreferences(p: com.example.myapplication.agent.AgentPreferences): List<SettingsV2Section> =
        preferencesContent(p).toSections()

    private fun preferencesContent(p: com.example.myapplication.agent.AgentPreferences): SettingsV2Content {
        val toolFlags = listOf(
            p.toolDeviceStatusEnabled, p.toolVibrateEnabled, p.toolMatrixDensityEnabled,
            p.toolPowerModeEnabled, p.toolWeatherEnabled, p.toolWebSearchEnabled, p.isMcpEnabled,
        )
        return SettingsV2Content(
            connection = listOf(
                RowSpec(
                    "provider", "供应商",
                    value = p.providerProfiles().active()?.name ?: "当前配置",
                    choice = providerChoice(p.providerProfiles(), p.providerProfiles().activeProfileId),
                ),
                RowSpec(
                    "model", "模型",
                    value = p.modelName.ifBlank { "未设置" },
                    choice = modelChoice(p.cachedRemoteModels, p.modelName),
                ),
                // 温度是**连续量**，直接用官方滑块，不再开一层子菜单。
                // 用户原话："很多数值的拿那玩意（滑块）不就行了。为什么要再整个子菜单？"
                // 区间与量化复用既有 `SettingsValueMath`：0..1.5、步进 0.1，与旧设置页逐位一致；
                // 顺带消灭旧选项表里 1.6 / 2.0 两个**超出应用上限**的档位（控制台阈值就是 0..1.5）。
                RowSpec(
                    "temperature", "温度",
                    subtitle = "越高越发散",
                    slider = p.temperature.coerceIn(
                        SettingsValueMath.TemperatureMin,
                        SettingsValueMath.TemperatureMax,
                    ),
                    sliderRange = SettingsValueMath.TemperatureMin..SettingsValueMath.TemperatureMax,
                    // steps = 0 → 连续滑块（"进度条那种"）。Wear 的官方 Slider 只要 steps > 0 就画成
                    // −/+ 分段步进器，标题会被挤没；0.1 的量化在落库前做（quantizeTemperature），
                    // 所以连续拖动也不会把 0.7000001 写进偏好。
                    sliderSteps = 0,
                    sliderFormat = { formatTemperature(it) },
                ),
                RowSpec("reply_style", "回复风格", value = replyChoice(p.replyStyle.ifBlank { "BALANCED" }).labelOfSelected(), choice = replyChoice(p.replyStyle.ifBlank { "BALANCED" })),
            ),
            action = listOf(
                RowSpec("tools", "工具与 MCP", subtitle = "${toolFlags.count { it }}/${toolFlags.size} 启用"),
                RowSpec("tool_device", "设备状态", toggle = p.toolDeviceStatusEnabled),
                RowSpec("tool_vibrate", "振动", toggle = p.toolVibrateEnabled),
                // 点阵密度是**连续量**（matrixSpacingDp，0..64），必须用滑块而不是开关。
                // 开关只有两态，会把整个可调范围压成 0/1 —— 这是语义错误，不只是不好看。
                RowSpec(
                    "tool_matrix", "点阵密度",
                    slider = p.matrixSpacingDp,
                    sliderRange = 0f..32f,
                    sliderSteps = 7,   // 0/4/8/…/32，每档 4dp
                    sliderFormat = { "${it.toInt()}dp" },
                ),
                RowSpec("tool_power", "省电模式", toggle = p.toolPowerModeEnabled),
                RowSpec("tool_weather", "天气", toggle = p.toolWeatherEnabled),
                RowSpec("tool_web", "联网搜索", toggle = p.toolWebSearchEnabled),
                RowSpec("mcp", "MCP", toggle = p.isMcpEnabled),
                RowSpec("security", "安全策略", value = securityChoice(p.securityMode.name).labelOfSelected(), choice = securityChoice(p.securityMode.name)),
                RowSpec("root", "特权执行", value = sandboxChoice(p.sandboxLevel.storageKey).labelOfSelected(), choice = sandboxChoice(p.sandboxLevel.storageKey)),
            ),
            hear = listOf(
                RowSpec("voice", "语音与唤醒", subtitle = "${p.asrProvider} · ${if (p.asrEnabled) "已启用" else "未启用"}"),
                RowSpec("asr_enabled", "语音识别", toggle = p.asrEnabled),
                RowSpec("asr_lang", "识别语言", value = asrLanguageChoice(p.asrDevPid.toString()).labelOfSelected(), choice = asrLanguageChoice(p.asrDevPid.toString())),
                RowSpec("asr_live", "实时转写", toggle = p.asrLiveTranscript),
                RowSpec("tts", "自动播报回答", toggle = p.autoSpeakReplies),
                // 唤醒词目前没有对应偏好字段，明确标注为示例值，不冒充已接好
                RowSpec("wake", "唤醒词", value = "关闭"),
            ),
            answer = listOf(
                RowSpec("style", "页面风格", subtitle = p.settingsStyle.name, chevron = true),
                RowSpec("compact", "紧凑模式", toggle = p.compactMode),
                RowSpec("console", "网页控制台", value = "8080"),
            ),
        )
    }

    /** 温度显示：一位小数，避免 0.7000001 这种浮点噪声上屏。 */
    internal fun formatTemperature(value: Float): String {
        val rounded = Math.round(value * 10f) / 10f
        return rounded.toString()
    }
}

// ==== 选项组：value 是落库键，label 是给人看的中文 ====

/** 模型选项：从偏好里的缓存清单取，兜底两个内置默认名；当前值一定在列表里（哪怕不在缓存里）。 */
internal fun modelChoice(cached: String, selected: String): RowChoice {
    val ids = cached.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    val defaults = listOf("deepseek-chat", "deepseek-reasoner")
    val options = (ids.ifEmpty { defaults } + listOf(selected))
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .map { ChoiceOption(it, it) }
    return RowChoice(options, selected)
}

/** 供应商（Profile）选项：value 是 profile id，选中后由 Host 调 applyProviderProfile。 */
internal fun providerChoice(book: com.example.myapplication.agent.ProviderProfileBook, selectedId: String?): RowChoice {
    val current = book.profiles.map { ChoiceOption(it.id, it.name) }
    // 没有 Profile 时给一条"当前配置"，避免空列表让选项页什么都没有
    val options = current.ifEmpty { listOf(ChoiceOption("", "当前配置")) }
    return RowChoice(options, selectedId.orEmpty())
}

/** 示例数据的供应商选项（无真实 Profile，只给一条占位）。 */
internal fun providerChoiceSample(): RowChoice =
    RowChoice(listOf(ChoiceOption("", "当前配置")), "")

internal fun replyChoice(selected: String) = RowChoice(
    listOf(ChoiceOption("CONCISE", "简洁"), ChoiceOption("BALANCED", "均衡"), ChoiceOption("DETAILED", "详细")),
    selected,
)

internal fun securityChoice(selected: String) = RowChoice(
    listOf(ChoiceOption("NOVICE", "严格确认"), ChoiceOption("STANDARD", "标准"), ChoiceOption("EXPERT", "自动执行")),
    selected,
)

internal fun sandboxChoice(selected: String) = RowChoice(
    listOf(ChoiceOption("READ_ONLY", "只读"), ChoiceOption("WORKSPACE", "工作区"), ChoiceOption("FULL", "全权限")),
    selected,
)

internal fun asrLanguageChoice(selected: String) = RowChoice(
    listOf(
        ChoiceOption("1537", "普通话"), ChoiceOption("1737", "英语"),
        ChoiceOption("1637", "粤语"), ChoiceOption("1837", "四川话"),
    ),
    selected,
)

/**
 * 从 baseUrl 里取**供应商主标签**：`https://api.deepseek.com` -> `deepseek`。
 *
 * 整段主机名有 17 个字符，会把标题挤成 `供..`（真机截图实测）；供应商要的是牌子，不是主机名。
 */
internal fun providerFromUrl(baseUrl: String): String {
    val host = runCatching { java.net.URI(baseUrl).host }.getOrNull()?.lowercase().orEmpty()
    if (host.isBlank()) return "未配置"
    val labels = host.split('.').filter { it.isNotBlank() }
    val meaningful = labels.firstOrNull { it !in setOf("www", "api", "open", "chat") }
        ?: labels.firstOrNull()
        ?: return "未配置"
    return meaningful
}

/**
 * 四套风格的列表都是「分区标题 + 行」扁平化排列，这里算出某个行 key 的**扁平下标**。
 *
 * 用途：从选项子页返回时把列表滚回原来那一行。没有它，选完一项会被弹回顶部
 * （真机 0.0.0.265 实测：在「安全策略」里选中「自动执行」，列表跳回第一屏「连得上」）。
 *
 * 返回 -1 表示没找到（此时调用方不要滚）。
 */
internal fun flatRowIndex(sections: List<SettingsV2Section>, key: String): Int {
    var index = 0
    sections.forEach { section ->
        index += 1 // 分区标题各占一项
        section.rows.forEach { row ->
            if (row.key == key) return index
            index += 1
        }
    }
    return -1
}
