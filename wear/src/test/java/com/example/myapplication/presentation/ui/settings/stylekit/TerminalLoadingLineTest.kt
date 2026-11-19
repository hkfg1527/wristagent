package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 终端加载行：字面形态 + 光标真的在闪。
 *
 * 用 `mainClock.autoAdvance = false` + `advanceTimeBy` 推进测试时钟，
 * 这样"600ms 后光标熄灭"是被断言出来的，而不是靠真机截两帧去数。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class TerminalLoadingLineTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theCursorBlinksBetweenBlockAndBlank() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { TerminalLoadingLine(label = "正在思考", periodMs = 600L) }
        }
        compose.mainClock.advanceTimeByFrame()
        compose.onNode(hasText("█", substring = true)).assertIsDisplayed()

        // 推进 600ms：进入后半周期 → 光标变成空格
        compose.mainClock.advanceTimeBy(620)
        val blank = compose.onAllNodes(hasText("> 正在思考… ", substring = true))
            .fetchSemanticsNodes()
        assertTrue("600ms 后光标必须熄灭（变成空格）", blank.isNotEmpty())

        // 再推进 600ms：回到前半周期 → 又亮起来
        compose.mainClock.advanceTimeBy(620)
        val lit = compose.onAllNodes(hasText("█", substring = true)).fetchSemanticsNodes()
        assertTrue("再过 600ms 光标必须重新亮起", lit.isNotEmpty())
    }
}
