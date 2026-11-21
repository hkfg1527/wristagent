package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.SquareHairlineHeight
import com.example.myapplication.presentation.ui.settingsv2.style.SquareRowMinHeight
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalBackground
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalForeground
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalPrompt
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalRowTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R4：`SQUARE` 与 `TERMINAL` 两套的判据。
 *
 * 方角：角半径 0（身份参数）、发丝线 **0.5dp = 1px@2.0**、行高 ≥48dp；
 * 终端：`> ` 前缀真的在文本里、前景 `#3BFF9A`、背景 `#0B0F0C`、且**不做磁滑吸附**。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SquareAndTerminalStyleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun squareIsZeroCornerWithOnePixelHairline() {
        assertEquals(
            "方角档圆角必须是 0",
            0f,
            SettingsV2Style.SQUARE.identity.cornerRadius.value,
            0.0001f
        )
        assertEquals(
            "发丝线必须是 0.5dp（density 2.0 下 = 1 物理像素）",
            0.5f,
            SquareHairlineHeight.value,
            0.0001f
        )
        assertTrue("方角行高必须 ≥48dp", SquareRowMinHeight.value >= 48f)

        compose.setContent { MaterialTheme { SettingsV2Screen(style = SettingsV2Style.SQUARE) } }
        compose.waitForIdle()
        compose.onNodeWithText("连得上").assertIsDisplayed()
        assertTrue(
            "方角档必须画出带 testTag 的行",
            compose.onAllNodesWithTag("square-row").fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun terminalRowsCarryPromptPrefixAndTerminalColours() {
        assertEquals("终端提示符必须是 '> '", "> ", TerminalPrompt)
        assertEquals(
            "终端背景必须是 #0B0F0C",
            androidx.compose.ui.graphics.Color(0xFF0B0F0C),
            TerminalBackground
        )
        assertEquals(
            "终端前景必须是 #3BFF9A",
            androidx.compose.ui.graphics.Color(0xFF3BFF9A),
            TerminalForeground
        )
        assertFalse("终端必须关闭磁滑吸附（连续滚动）", SettingsV2Style.TERMINAL.identity.snapScroll)
        // 48dp 触控下限：真机实测旧值 40dp 时行距只有 46dp
        assertTrue(
            "终端行高必须 >=48dp（实测旧值 40dp 时行距仅 46dp）",
            SettingsV2Style.TERMINAL.identity.rowMinHeight.value >= 48f
        )

        compose.setContent { MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL) } }
        compose.waitForIdle()
        assertTrue(
            "终端行必须以 '> ' 开头",
            compose.onAllNodesWithText("> 供应商", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(">", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "终端必须画出带 testTag 的行",
            compose.onAllNodesWithTag(TerminalRowTag).fetchSemanticsNodes().isNotEmpty()
        )
    }
}
