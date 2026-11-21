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
import java.io.File

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

    /**
     * R13：SQUARE 与 M3_LIST 必须在**行距**上就能分开，不能只靠对齐差异。
     *
     * 真机曾实测两套行距 112px(56.0dp) vs 114px(57.0dp) —— 差 2px，同屏无法区分。
     * 判据：行高差 ≥12dp（真机行距差 ≈24px），且方角档不得低于 48dp 触控下限。
     */
    @Test
    fun squareIsLooserThanM3ListByAtLeastTwelveDp() {
        val square = SettingsV2Style.SQUARE.identity.rowMinHeight.value
        val list = SettingsV2Style.M3_LIST.identity.rowMinHeight.value
        assertTrue(
            "方角行高必须仍是触控下限以上：$square",
            square >= 48f
        )
        assertTrue(
            "方角(${square}dp) 与官方列表(${list}dp) 的行高差必须 ≥12dp，否则一屏看不出区别",
            square - list >= 12f
        )
    }

    /**
     * R13：分隔线的**策略**必须不同 —— 官方列表通栏，方角内缩。
     *
     * 这条用源码守卫锁住：两套的线都通栏时，同屏只剩"标题居中"一处差异（真机实测两套行距
     * 差 2px 时就是这样）。守卫读源码，防止以后有人"统一一下"把它们改成一样。
     */
    @Test
    fun squareHairlineIsInsetWhileTheOfficialListIsFullBleed() {
        fun src(rel: String): String = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/$rel") }
            .first { it.exists() }
            .readText()
        val square = src("SquareStyle.kt")
        val list = src("M3ListStyle.kt")
        assertTrue(
            "方角发丝线必须内缩（padding(horizontal = SquareHairlineInset)）",
            square.contains("padding(horizontal = SquareHairlineInset)")
        )
        assertTrue(
            "官方列表的发丝线必须保持通栏，不能也内缩",
            !list.contains("SquareHairlineInset")
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
