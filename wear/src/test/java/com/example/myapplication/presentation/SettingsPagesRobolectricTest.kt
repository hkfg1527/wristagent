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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.presentation.ui.settings.HyperParamsContent
import com.example.myapplication.presentation.ui.settings.NetworkRequestContent
import com.example.myapplication.presentation.ui.settings.VoiceAsrContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 设置项「点了到底有没有落到偏好里」——在 JVM 上用 Robolectric 渲染真实页面断，不再依赖真机手点。
 *
 * 背景：HANDOFF §0.5 的「设置项功能实测」一直只有 6 个键可信，原因是探针靠坐标点击、不可靠。
 * 这里换一条路：直接渲染页面 composable，点语义节点，读 [AgentPreferences] 真值。
 * 覆盖用户点名的三页：模型参数 / 网络与请求 / 语音与唤醒（语音转写）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsPagesRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    private fun scrollTo(text: String) {
        // 页面里有两个可滚动节点（ScreenScaffold 的外壳 + 列表本身）；带 CollectionInfo 的才是列表。
        compose.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            .onFirst()
            .performScrollToNode(hasText(text, substring = true))
        compose.waitForIdle()
    }

    private fun clickRow(text: String) {
        scrollTo(text)
        // 同一句文案可能既是分区标题又是开关行标签（「语音转写开关」就是），只点可点的那一个。
        compose.onAllNodesWithText(text, substring = true)
            .filterToOne(hasClickAction())
            .performClick()
        compose.waitForIdle()
    }

    @Test
    fun networkPageSwitchesWriteTheirPreference() {
        val p = prefs()
        p.wifiOnlyRequests = false
        p.hapticsEnabled = true
        compose.setContent {
            MaterialTheme {
                NetworkRequestContent(
                    preferences = p,
                    privilegeManager = PrivilegeManager(context),
                    baseUrl = { "https://api.deepseek.com" },
                    onBack = {},
                )
            }
        }
        clickRow("仅 WiFi 请求")
        assertTrue("点「仅 WiFi 请求」必须落到 prefs.wifiOnlyRequests", p.wifiOnlyRequests)

        clickRow("触觉反馈")
        assertFalse("点「触觉反馈」必须落到 prefs.hapticsEnabled", p.hapticsEnabled)
    }

    @Test
    fun hyperParamsSwitchAndPresetWriteTheirPreference() {
        val p = prefs()
        p.streamMode = true
        p.temperature = 0.7f
        compose.setContent {
            MaterialTheme {
                HyperParamsContent(preferences = p, onBack = {})
            }
        }
        clickRow("开启 SSE 流式")
        assertFalse("点「开启 SSE 流式」必须落到 prefs.streamMode", p.streamMode)

        clickRow("极速 0.2")
        assertEquals(0.2f, p.temperature, 0.001f)
    }

    @Test
    fun voicePageSwitchWritesAsrEnabled() {
        val p = prefs()
        p.asrEnabled = true
        compose.setContent {
            MaterialTheme {
                VoiceAsrContent(preferences = p, onBack = {})
            }
        }
        clickRow("语音转写开关")
        assertFalse("点「语音转写开关」必须落到 prefs.asrEnabled", p.asrEnabled)
    }
}
