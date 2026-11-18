package com.example.myapplication.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.WearAppearance
import com.example.myapplication.presentation.ui.settings.AppearanceContent
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 用户最在意的两页：外观（六套风格能不能真的切过去）与工具（六个开关是不是真写进偏好）。
 *
 * 风格那一组尤其值得钉死：一整套渲染分流的入口就是 `WearAppearance.style` + `SettingsStylePolicy`，
 * 只要有一处"点了没落盘"，用户看到的就是"选了不变"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsAppearanceAndToolsRobolectricTest {

    /** 外观单例隔离：本类会把风格逐套点一遍，收尾必须复位，否则脏到后面的类。 */
    @get:Rule
    val appearanceIsolation = SettingsAppearanceResetRule()

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    private fun scrollTo(text: String) {
        compose.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            .onFirst()
            .performScrollToNode(hasText(text, substring = true))
        compose.waitForIdle()
    }

    private fun clickRow(text: String) {
        scrollTo(text)
        // 精确匹配：风格说明里会互相提到（「官方转场」的副标题就写着「同官方列表…」），
        // 用 substring 会同时命中两行。
        compose.onAllNodesWithText(text, substring = false).filterToOne(hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun everyStyleRadioSwitchesTheActiveStyle() {
        val p = prefs()
        compose.setContent { MaterialTheme { AppearanceContent(preferences = p, onBack = {}) } }
        for (style in SettingsStyle.values()) {
            val label = context.getString(style.labelRes)
            clickRow(label)
            assertEquals("点「$label」后偏好里必须是这套风格", style, p.settingsStyle)
            assertEquals("点「$label」后运行时镜像必须同步", style, WearAppearance.style)
        }
    }

    @Test
    fun compactModeSwitchWritesPreference() {
        val p = prefs()
        p.compactMode = false
        compose.setContent { MaterialTheme { AppearanceContent(preferences = p, onBack = {}) } }
        clickRow(context.getString(R.string.settings_appearance_compact))
        assertEquals("点「紧凑」之后偏好必须翻转成 true", true, p.compactMode)
    }

    @Test
    fun sixToolSwitchesEachWriteTheirOwnKey() {
        val p = prefs()
        val keys = listOf(
            R.string.settings_tool_device_status to { p.toolDeviceStatusEnabled },
            R.string.settings_tool_vibrate to { p.toolVibrateEnabled },
            R.string.settings_tool_matrix_density to { p.toolMatrixDensityEnabled },
            R.string.settings_tool_power_mode to { p.toolPowerModeEnabled },
            R.string.settings_tool_time_weather to { p.toolWeatherEnabled },
            R.string.settings_tool_web_search to { p.toolWebSearchEnabled },
        )
        compose.setContent {
            MaterialTheme {
                ToolsMcpContent(
                    preferences = p,
                    isMcpEnabled = p.isMcpEnabled,
                    mcpEndpoint = p.mcpEndpoint,
                    onBack = {},
                    onToggleMcp = { _, _ -> },
                )
            }
        }
        for ((res, read) in keys) {
            val before = read()
            clickRow(context.getString(res))
            assertEquals("点「" + context.getString(res) + "」必须翻转它自己的偏好", !before, read())
        }
    }
}
