package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2SliderTagPrefix
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「拖动滑块真的会改设置」—— 走**真实 Host**（不是抄一份写入逻辑），所以 Host 里的分支一旦被删就会红。
 *
 * 为什么必须有这条：0.0.0.263 把温度从选项子菜单改成滑块，界面能画出来不等于能写回去。
 * 真机上用 `adb input swipe` 拖不动（未验明原因），所以在 JVM 上用语义动作 `SetProgress`
 * 把「滑块 → onSliderChange → preferences」这条链路钉死；真机只负责"画出来了没有"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h400dp")
class SettingsV2SliderWriteTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun settingTheTemperatureSliderWritesTheQuantizedPreference() {
        val p = AgentPreferences(FakeSharedPreferences())
        p.temperature = 0.7f

        compose.setContent {
            SettingsV2Host(preferences = p, onBack = {})
        }
        compose.waitForIdle()

        compose.onNodeWithTag(SettingsV2SliderTagPrefix + "temperature", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1.0f) }
        compose.waitForIdle()

        assertEquals("滑块必须把值写回 preferences.temperature", 1.0f, p.temperature, 1e-6f)
    }

    @Test
    fun theWrittenTemperatureIsQuantizedToOneDecimal() {
        val p = AgentPreferences(FakeSharedPreferences())
        p.temperature = 0.7f

        compose.setContent {
            SettingsV2Host(preferences = p, onBack = {})
        }
        compose.waitForIdle()

        // 连续滑块能给出任意小数，落库前必须量化到 0.1（否则 0.7000001 会进偏好）
        compose.onNodeWithTag(SettingsV2SliderTagPrefix + "temperature", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.7499f) }
        compose.waitForIdle()

        assertEquals("0.7499 必须量化成 0.7", 0.7f, p.temperature, 1e-6f)
    }
}
