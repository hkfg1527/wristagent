package com.example.myapplication.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settings.SettingsSwitchRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 开关行的「点了到底有没有反应」——以前只能在真机上手点看截图，现在 JVM 上就能断。
 *
 * 这个控件是设置页 15 行里出现最多的一类（仅 WiFi 请求 / 触觉反馈 / 语音转写开关 / 六个工具开关…），
 * 所以先钉它：文案在不在、点一下回调有没有拿到取反的值、无障碍状态描述跟不跟。
 * 语言固定 zh-rCN：随包发布的就是这套文案，用英文断言等于没验。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsSwitchRowRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun rendersLabelAndSubtitle() {
        compose.setContent {
            MaterialTheme {
                SettingsSwitchRow(
                    label = "仅 WiFi 请求",
                    checked = false,
                    onCheckedChange = {},
                    secondary = "不为一次请求动用蜂窝数据",
                )
            }
        }
        compose.onNodeWithText("仅 WiFi 请求").assertIsDisplayed()
        compose.onNodeWithText("不为一次请求动用蜂窝数据").assertIsDisplayed()
    }

    @Test
    fun clickingRowAsksForTheOppositeValue() {
        val seen = ArrayList<Boolean>()
        compose.setContent {
            MaterialTheme {
                SettingsSwitchRow(label = "触觉反馈", checked = true, onCheckedChange = { seen += it })
            }
        }
        compose.onNodeWithText("触觉反馈").performClick()
        compose.waitForIdle()
        assertEquals(listOf(false), seen)
    }

    @Test
    fun stateDescriptionFollowsChecked() {
        compose.setContent {
            MaterialTheme {
                SettingsSwitchRow(label = "语音转写开关", checked = true, onCheckedChange = {})
            }
        }
        val node = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription))
            .fetchSemanticsNode()
        assertEquals("已开启", node.config[SemanticsProperties.StateDescription])
    }
}
