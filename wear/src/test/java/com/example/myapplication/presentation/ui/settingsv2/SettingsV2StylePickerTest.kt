package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「手表上能换风格」的判据（V2 之前只能靠网页控制台，屏幕上没有入口）。
 *
 * 覆盖三件事：
 * ①底部「风格」按钮存在且可点开选择器；
 * ②选择器里五套都在（每套一个条目）；
 * ③点某一套 → 回调收到对应风格、覆盖层关闭。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SettingsV2StylePickerTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theStyleButtonOpensAPickerWithAllFiveStyles() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_CARD) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("风格").assertIsDisplayed()
        assertTrue(
            "打开前不应有选择器",
            compose.onAllNodesWithTag(StylePickerTag).fetchSemanticsNodes().isEmpty()
        )

        compose.onNodeWithText("风格").performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(StylePickerTag).assertIsDisplayed()
        // 覆盖层可滚动：200dp 视口下先滚到目标再断言
        SettingsV2Style.values().forEach { style ->
            compose.onNodeWithTag(StylePickerItemTagPrefix + style.name).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun pickingAStyleReportsItAndClosesThePicker() {
        var picked: SettingsV2Style? = null
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(
                    style = SettingsV2Style.M3_CARD,
                    onStyleSelected = { picked = it },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("风格").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(StylePickerItemTagPrefix + SettingsV2Style.TERMINAL.name).performScrollTo().performClick()
        compose.waitForIdle()

        assertEquals("选中终端应回调 TERMINAL", SettingsV2Style.TERMINAL, picked)
        assertTrue(
            "选完后覆盖层必须关闭",
            compose.onAllNodesWithTag(StylePickerTag).fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun everyV2StyleMapsBackToALegacyValueForPersistence() {
        // 偏好里存的是旧枚举，所以必须有反向映射，且五套互不相同
        val legacy = SettingsV2Style.values().map { it.toLegacy() }
        assertEquals("五套必须映射到五个不同的旧值", 5, legacy.toSet().size)
        assertEquals(
            "M3_CARD 落库时沿用 BALLOON（历史值，保证老用户不丢风格）",
            com.example.myapplication.appearance.SettingsStyle.BALLOON,
            SettingsV2Style.M3_CARD.toLegacy()
        )
    }
}
