package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑杆说明行的守卫。
 *
 * 第 14 轮我把"请求超时/自动重试/重试退避"三个滑杆从根页搬进子页，**说明文字没跟着搬** ——
 * 文案还躺在 strings 里、界面已经不带它们了，而且构建与单测全都绿。
 * 这类丢失只有在真机上逐行看才会发现，所以在这里把每一处钉住。
 *
 * 新增滑杆时把键加进 [mustExplain]；判据很简单：**这个数字改变了什么，用户看不出来**。
 */
class SettingsRowExplanationGuardTest {

    private val mustExplain = listOf(
        "settings_temperature",
        "settings_max_tokens",
        "settings_top_p",
        "settings_stream_emit_interval",
        "settings_idle_spin_stop",
        "settings_context_turns",
        "settings_compress_threshold",
        "settings_net_diag_timeout",
        "settings_console_max_failures",
        "settings_subagent_max_depth",
        "settings_mcp_max_pages",
        "settings_tool_result_chars",
        "settings_approval_threshold",
        "settings_tts_volume",
        "settings_rotary_gain",
        "settings_appearance_matrix_spacing",
    )

    private val pageFiles = listOf(
        "src/main/java/com/example/myapplication/presentation/ui/settings/HyperParamsSection.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/MemoryAndSecuritySections.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/NetworkRequestSection.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/RemoteConsoleSection.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/ToolsMcpSection.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/WatchSettingsSections.kt",
    )

    @Test
    fun everyDocumentedSliderStillCarriesItsExplanation() {
        val sources = pageFiles.associateWith { readProjectFile(it) }
        val missing = mutableListOf<String>()
        for (key in mustExplain) {
            val owner = sources.entries.firstOrNull { it.value.contains("R.string." + key + ")") }
                ?: continue
            if (!owner.value.contains("R.string." + key + "_secondary)")) {
                missing += key + "（在 " + owner.key.substringAfterLast('/') + "）"
            }
        }
        assertTrue(
            "以下滑杆丢了说明行：搬家时把 secondary 一起带走，或从 mustExplain 里删掉并说明理由。" +
                "缺失：" + missing,
            missing.isEmpty()
        )
    }
}
