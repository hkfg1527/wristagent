package com.example.myapplication.presentation.ui.settings

import androidx.annotation.StringRes
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences

/** 新增设置项的落位能力域：一个域对应网页端的一页，保证"每个能力域都可达"。 */
enum class WatchSettingsDomain(val landingPage: String) {
    VOICE("voice"),
    TOOLS("tools"),
    MODEL("model"),
    MEMORY("dialog"),
    CONTEXT("generate"),
    INTERFACE("interface"),
    STATUS("device")
}

/** 手表端可承载的设置形态：开关 / 三档枚举 / 滑杆 / 只读状态，没有长文本与列表编辑。 */
enum class WatchSettingForm { SWITCH, TRI_STATE, SLIDER, READ_ONLY }

/**
 * 一条手表端设置项。
 *
 * [preferenceKey] 只有可写项才有值：只读项直接从系统状态或令牌读出，不给可写键，
 * 这样"开关能拨但没有任何效果"的假开关在结构上就不可能出现。
 */
data class WatchSettingItem(
    val id: String,
    val domain: WatchSettingsDomain,
    val form: WatchSettingForm,
    @StringRes val labelRes: Int,
    @StringRes val secondaryRes: Int = 0,
    val preferenceKey: String? = null
) {
    val landingPage: String get() = domain.landingPage
}

/**
 * 手表端 10 条"网页新增项"的唯一清单（默认值一律落在 [AgentPreferences] 的 DEFAULT_* 常量上）。
 *
 * 本轮审计删掉 4 条：voice.wakeOnScreen / voice.wakeOnLockScreen（主源码零读取，
 * 唤醒路径已整体删除）与 interface.usePureBlack / interface.themeMode（Bg 是编译期常量，
 * 主题枚举只被取来渲染文案）。它们都是"画在屏上但拨不动任何东西"的项，见 docs/SETTINGS-AUDIT.md。
 */
object WatchSettingsCatalog {

    val items: List<WatchSettingItem> = listOf(
        // 语音 3：总开关、出向播报、边说边显示（两条自动唤醒项已按审计删除）
        WatchSettingItem(
            id = "voice.asrEnabled",
            domain = WatchSettingsDomain.VOICE,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_voice_asr_enabled,
            secondaryRes = R.string.settings_voice_asr_enabled_secondary,
            preferenceKey = AgentPreferences.KEY_ASR_ENABLED
        ),
        WatchSettingItem(
            id = "voice.autoSpeakReplies",
            domain = WatchSettingsDomain.VOICE,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_voice_auto_speak,
            secondaryRes = R.string.settings_voice_auto_speak_secondary,
            preferenceKey = AgentPreferences.KEY_AUTO_SPEAK_REPLIES
        ),
        WatchSettingItem(
            id = "voice.liveTranscript",
            domain = WatchSettingsDomain.VOICE,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_voice_live_transcript,
            secondaryRes = R.string.settings_voice_live_transcript_secondary,
            preferenceKey = AgentPreferences.KEY_ASR_LIVE_TRANSCRIPT
        ),
        // 网页工具 1：Provider 托管搜索
        WatchSettingItem(
            id = "tools.providerManagedSearch",
            domain = WatchSettingsDomain.TOOLS,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_tools_provider_search,
            secondaryRes = R.string.settings_tools_provider_search_secondary,
            preferenceKey = AgentPreferences.KEY_PROVIDER_MANAGED_SEARCH
        ),

        // 模型接口 1：默认启用思考
        WatchSettingItem(
            id = "model.defaultThinking",
            domain = WatchSettingsDomain.MODEL,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_model_default_thinking,
            secondaryRes = R.string.settings_model_default_thinking_secondary,
            preferenceKey = AgentPreferences.KEY_DEFAULT_THINKING
        ),

        // 记忆 1：长期记忆总开关
        WatchSettingItem(
            id = "dialog.memoryEnabled",
            domain = WatchSettingsDomain.MEMORY,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_memory_enabled,
            secondaryRes = R.string.settings_memory_enabled_secondary,
            preferenceKey = AgentPreferences.KEY_MEMORY_ENABLED
        ),

        // 上下文 2：自动压缩（开关）+ 用量（只读）
        WatchSettingItem(
            id = "generate.autoCompressContext",
            domain = WatchSettingsDomain.CONTEXT,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_context_compress,
            secondaryRes = R.string.settings_context_compress_secondary,
            preferenceKey = AgentPreferences.KEY_AUTO_COMPRESS_CONTEXT
        ),
        WatchSettingItem(
            id = "generate.contextUsage",
            domain = WatchSettingsDomain.CONTEXT,
            form = WatchSettingForm.READ_ONLY,
            labelRes = R.string.settings_context_usage
        ),

        // 界面 1：简洁模式（纯黑与主题模式已按审计删除）
        WatchSettingItem(
            id = "interface.compactMode",
            domain = WatchSettingsDomain.INTERFACE,
            form = WatchSettingForm.SWITCH,
            labelRes = R.string.settings_appearance_compact,
            secondaryRes = R.string.settings_appearance_compact_secondary,
            preferenceKey = AgentPreferences.KEY_COMPACT_MODE
        ),

        // 其它 1：网络状态（只读，已有网络体检行承载）
        WatchSettingItem(
            id = "device.networkStatus",
            domain = WatchSettingsDomain.STATUS,
            form = WatchSettingForm.READ_ONLY,
            labelRes = R.string.settings_netdiag_title
        )
    )
}
