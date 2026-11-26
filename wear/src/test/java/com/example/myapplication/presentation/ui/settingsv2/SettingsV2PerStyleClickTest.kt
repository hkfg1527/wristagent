package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 五套风格**逐套**验证「点得到」：点一行必须把这一行回报给宿主。
 *
 * 用户要求「每个风格试一试」。此前只有 M3_CARD 的选项子页交互有测试
 * （`SettingsV2OptionInteractionTest`），另外四套能不能点、点了有没有回调，没有逐套钉过。
 * 这里对每套渲染一次、点同一个开关行，断言 `onRowClick` 收到的是这一行。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w233dp-h233dp-xhdpi")
class SettingsV2PerStyleClickTest {

    @get:Rule
    val compose = createComposeRule()

    private fun openWith(style: SettingsV2Style, onRow: (SettingsV2Row) -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(
                    style = style,
                    sections = listOf(
                        SettingsV2Section(
                            "让它能做事",
                            listOf(
                                SettingsV2Row(
                                    key = "tool_vibrate",
                                    title = "振动",
                                    trailing = RowTrailing.Toggle(true, {}),
                                    onClick = {},
                                ),
                                SettingsV2Row(
                                    key = "tool_device",
                                    title = "设备状态",
                                    trailing = RowTrailing.Toggle(false, {}),
                                    onClick = {},
                                ),
                            ),
                        ),
                    ),
                    onRowClick = onRow,
                )
            }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(800L)
    }

    // Compose 的测试规则一个用例只能 setContent 一次，所以五套各写一条（0.0.0.274 踩过 "already set content"）
    private fun clickAndExpect(style: SettingsV2Style) {
        var clicked: String? = null
        openWith(style) { clicked = it.key }
        compose.onNodeWithText("振动", substring = true).performClick()
        compose.mainClock.advanceTimeBy(16L)
        assertEquals("${style.name} 点「振动」应回报 tool_vibrate，实际 $clicked", "tool_vibrate", clicked)
    }

    @Test fun m3Card() = clickAndExpect(SettingsV2Style.M3_CARD)
    @Test fun m3List() = clickAndExpect(SettingsV2Style.M3_LIST)
    @Test fun arc() = clickAndExpect(SettingsV2Style.ARC)
    @Test fun square() = clickAndExpect(SettingsV2Style.SQUARE)
    @Test fun terminal() = clickAndExpect(SettingsV2Style.TERMINAL)
}
