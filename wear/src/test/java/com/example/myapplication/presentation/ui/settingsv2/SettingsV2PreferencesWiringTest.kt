package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.appearance.SettingsStyle
import org.junit.Assert.assertEquals
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
