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
}
