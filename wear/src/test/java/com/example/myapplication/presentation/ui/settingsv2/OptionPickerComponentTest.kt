package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 单独测 OptionPicker 组件：点选项必须回调 value。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h400dp")
class OptionPickerComponentTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun clickingAnOptionReportsItsValue() {
        var picked: String? = null
        compose.setContent {
            MaterialTheme {
                OptionPicker(
                    title = "温度",
                    choice = temperatureChoice("0.7"),
                    colors = SettingsV2Style.M3_CARD.identity,
                    onPick = { picked = it },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()

        val node = compose.onNodeWithTag(OptionPickerItemTagPrefix + "1.6", useUnmergedTree = true)
        // 直接派发语义动作，绕开"圆屏视口 + 滚动容器"导致的坐标命中不确定
        node.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertEquals("1.6", picked)
    }
}
