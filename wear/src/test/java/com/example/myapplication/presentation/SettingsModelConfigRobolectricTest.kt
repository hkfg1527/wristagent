package com.example.myapplication.presentation

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.ModelConfigContent
import com.example.myapplication.presentation.ui.settings.SettingsValueMath
import com.example.myapplication.presentation.ui.settings.applySettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 模型与端点页的三条写入路径：Base URL 输入框、API Key 输入框、以及"密钥不许出现在屏上"。
 *
 * 这一页是整个 App 的入口参数，写错的后果不是"看着不顺眼"而是"一条请求都发不出去"。
 * 弹层那两条还多验一件事：**取消必须一个字都不写** —— 草稿只在确认时外泄。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsModelConfigRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    @Before
    fun forceThinTrackStyle() {
        applySettingsStyle(context, SettingsStyle.BALLOON)
    }

    private fun show(p: AgentPreferences) {
        compose.setContent {
            MaterialTheme {
                ModelConfigContent(
                    preferences = p,
                    currentModel = p.modelName,
                    currentBaseUrl = p.baseUrl,
                    onBack = {},
                    onSelect = { _, _ -> }
                )
            }
        }
    }

    private fun confirm() {
        compose.onAllNodesWithText(context.getString(R.string.settings_dialog_confirm))
            .onFirst()
            .performClick()
        compose.waitForIdle()
    }

    private fun cancel() {
        compose.onAllNodesWithText(context.getString(R.string.settings_dialog_cancel))
            .onFirst()
            .performClick()
        compose.waitForIdle()
    }

    @Test
    fun baseUrlDialogWritesThePreference() {
        val p = prefs()
        p.baseUrl = "https://old.example/v1"
        show(p)

        SettingsUi.clickRow(compose, context.getString(R.string.settings_text_base_url))
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("https://new.example/v1")
        compose.waitForIdle()
        confirm()

        assertEquals("确认后 Base URL 必须落盘", "https://new.example/v1", p.baseUrl)
    }

    @Test
    fun baseUrlDialogCancelWritesNothing() {
        val p = prefs()
        p.baseUrl = "https://keep.example/v1"
        show(p)

        SettingsUi.clickRow(compose, context.getString(R.string.settings_text_base_url))
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("https://discard.example/v1")
        compose.waitForIdle()
        cancel()

        assertEquals("取消后一个字符都不许写", "https://keep.example/v1", p.baseUrl)
    }

    @Test
    fun apiKeyDialogWritesThePreference() {
        val p = prefs()
        p.apiKey = ""
        show(p)

        SettingsUi.clickRow(compose, context.getString(R.string.settings_text_api_key))
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("sk-test-123")
        compose.waitForIdle()
        confirm()

        assertEquals("确认后 API Key 必须落盘", "sk-test-123", p.apiKey)
    }

    @Test
    fun apiKeyRowNeverRendersTheRawSecret() {
        val p = prefs()
        p.apiKey = "sk-very-secret-123"
        show(p)

        SettingsUi.scrollTo(compose, context.getString(R.string.settings_text_api_key))
        assertTrue(
            "屏上不许出现原始密钥",
            compose.onAllNodesWithText("sk-very-secret-123", substring = true).fetchSemanticsNodes().isEmpty()
        )
        val masked = SettingsValueMath.maskSecret("sk-very-secret-123")
        assertTrue(
            "密钥行必须显示掩码：" + masked,
            compose.onAllNodesWithText(masked, substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }
}
