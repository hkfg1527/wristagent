package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.appearance.SettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「V2 接真实偏好」的判据。
 *
 * 这一轮只接了四处实值（模型 / 温度 / 回复风格 / 页面风格），所以判据也只钉这四处：
 * ①改偏好 → 生成的 sections 里对应行文本变化；
 * ②温度按一位小数显示（0.75 -> 0.8，不出现 0.7500001）；
 * ③空模型名兜底成「未设置」，不显示空白行尾；
 * ④把接实值的 sections 交给真实的界面，文本能渲染出来（不是只测数据层）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SettingsV2PreferencesWiringTest {

    @get:Rule
    val compose = createComposeRule()

    private fun prefs(): AgentPreferences = AgentPreferences(FakeSharedPreferences())

    private fun valueOf(sections: List<SettingsV2Section>, key: String): String =
        sections.flatMap { it.rows }
            .first { it.key == key }
            .trailing.let { it as RowTrailing.Value }
            .text


    private fun subtitleOf(sections: List<SettingsV2Section>, key: String): String =
        sections.flatMap { it.rows }.first { it.key == key }.subtitle ?: ""

    private fun toggleOf(sections: List<SettingsV2Section>, key: String): Boolean =
        sections.flatMap { it.rows }
            .first { it.key == key }
            .trailing.let { it as RowTrailing.Toggle }
            .checked

    /**
     * R53：工具副标题是**派生值**（n/7 启用），必须随六个工具开关 + MCP 变化。
     *
     * 用三组数据（全关 / 混合 / 全开）验证，避免"恰好等于 7/7"这种巧合通过。
     */
    @Test
    fun toolsSummaryCountsEnabledFlags() {
        val p = prefs()
        p.toolDeviceStatusEnabled = false
        p.toolVibrateEnabled = false
        p.toolMatrixDensityEnabled = false
        p.toolPowerModeEnabled = false
        p.toolWeatherEnabled = false
        p.toolWebSearchEnabled = false
        p.isMcpEnabled = false
        assertEquals("全关", "0/7 启用", subtitleOf(SettingsV2SampleData.fromPreferences(p), "tools"))

        p.toolWeatherEnabled = true
        p.isMcpEnabled = true
        p.toolVibrateEnabled = true
        assertEquals("混合", "3/7 启用", subtitleOf(SettingsV2SampleData.fromPreferences(p), "tools"))

        listOf(true).forEach { _ ->
            p.toolDeviceStatusEnabled = true
            p.toolMatrixDensityEnabled = true
            p.toolPowerModeEnabled = true
            p.toolWebSearchEnabled = true
        }
        assertEquals("全开", "7/7 启用", subtitleOf(SettingsV2SampleData.fromPreferences(p), "tools"))
    }

    /** 紧凑模式也接实值（两态）。 */
    @Test
    fun compactModeFollowsThePreference() {
        val p = prefs()
        p.compactMode = true
        assertTrue(toggleOf(SettingsV2SampleData.fromPreferences(p), "compact"))
        p.compactMode = false
        assertFalse(toggleOf(SettingsV2SampleData.fromPreferences(p), "compact"))
    }
    /**
     * R52：安全策略 / 自动播报 / 特权执行三行接实值。
     *
     * 接法故意不做文案美化：控制台 `/api/state` 显示什么枚举名，设置页就显示什么 ——
     * 这样"两处数字/枚举对不上"这类问题不会藏在翻译层里。
     */
    @Test
    fun securityTtsAndSandboxFollowThePreferences() {
        val p = prefs()
        p.autoSpeakReplies = true
        val on = SettingsV2SampleData.fromPreferences(p)
        assertEquals("安全策略接 securityMode", p.securityMode.name, valueOf(on, "security"))
        assertEquals("特权执行接 sandboxLevel", p.sandboxLevel.name, valueOf(on, "root"))
        assertTrue("自动播报接 autoSpeakReplies（true）", toggleOf(on, "tts"))

        p.autoSpeakReplies = false
        val off = SettingsV2SampleData.fromPreferences(p)
        assertFalse("自动播报接 autoSpeakReplies（false）", toggleOf(off, "tts"))
    }

    @Test
    fun wiredRowsFollowThePreferenceValues() {
        val p = prefs()
        p.modelName = "qwen3-max"
        p.temperature = 0.75f
        p.replyStyle = "concise"
        p.settingsStyle = SettingsStyle.ARC

        val sections = SettingsV2SampleData.fromPreferences(p)
        assertEquals("qwen3-max", valueOf(sections, "model"))
        assertEquals("0.8", valueOf(sections, "temperature"))
        assertEquals("concise", valueOf(sections, "reply_style"))
        assertEquals("ARC", valueOf(sections, "style"))
    }

    @Test
    fun blankModelFallsBackAndTemperatureKeepsOneDecimal() {
        val p = prefs()
        p.modelName = ""
        p.temperature = 1.0f

        val sections = SettingsV2SampleData.fromPreferences(p)
        assertEquals("未设置", valueOf(sections, "model"))
        assertEquals("1.0", valueOf(sections, "temperature"))
        assertEquals("1.0", SettingsV2SampleData.formatTemperature(1.0f))
        assertEquals("0.0", SettingsV2SampleData.formatTemperature(0.04f))
    }

    @Test
    fun wiredSectionsActuallyRenderInTheScreen() {
        val p = prefs()
        p.modelName = "glm-4.6"
        p.temperature = 0.3f
        val sections = SettingsV2SampleData.fromPreferences(p)

        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_CARD, sections = sections) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("glm-4.6").assertIsDisplayed()
        compose.onNodeWithText("0.3").assertIsDisplayed()
    }
}
