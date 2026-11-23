package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「选项子页直接复用外层列表 UI」的界面级验证。
 *
 * 用户要求："里列表直接套外面列表的 UI 就行了"。
 * 所以子页不是独立组件，而是把选项摊成同样的 section/row 交给同一个渲染器：
 * 它必须①出现在同一套列表里（同一个行 tag）、②有可点的返回行、③选中后回调并返回。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h400dp")
class SettingsV2OptionInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private val sections = listOf(
        SettingsV2Section(
            title = "连得上",
            rows = listOf(
                SettingsV2Row(
                    key = "temperature",
                    title = "温度",
                    trailing = RowTrailing.Value("0.7"),
                    choice = temperatureChoice("0.7"),
                ),
                SettingsV2Row(
                    key = "tts",
                    title = "自动播报回答",
                    trailing = RowTrailing.Toggle(checked = false, onChange = {}),
                ),
            ),
        )
    )

    @Test
    fun choosingAnOptionUsesTheSameListAndCallsBack() {
        var picked: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(
                    style = SettingsV2Style.M3_CARD,
                    sections = sections,
                    onChoiceSelected = { key, value -> picked = key to value },
                )
            }
        }
        compose.waitForIdle()

        // 点「温度」→ 进入子列表：用的是**同一套行容器**（m3card-row）
        compose.onNodeWithText("温度", substring = true).performClick()
        compose.waitForIdle()
        assertTrue(
            "子列表必须复用主页的行容器（m3card-row），而不是另做一套",
            compose.onAllNodesWithTag("m3card-row").fetchSemanticsNodes().isNotEmpty()
        )
        // 返回行必须在（子列表靠它回主页，不依赖系统返回键）
        compose.onNodeWithText("‹ 返回", substring = true).assertIsDisplayed()

        // 选一个靠近顶部的选项：M3_CARD 只组合可见项，远处选项在视口外查不到节点
        compose.onNodeWithText("0.3", substring = true).performClick()
        compose.waitForIdle()
        assertEquals("temperature" to "0.3", picked)
        assertTrue(
            "选完后必须回到主页列表",
            compose.onAllNodesWithText("自动播报回答").fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun backRowReturnsToTheMainListWithoutLeavingThePage() {
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(style = SettingsV2Style.M3_CARD, sections = sections)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("温度", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("‹ 返回", substring = true).performClick()
        compose.waitForIdle()

        // 回到主页：温度行在、且不再显示选项值
        compose.onNodeWithText("自动播报回答", substring = true).assertIsDisplayed()
        assertTrue(
            "返回后不该还停在子列表（1.6 不应作为选项行存在）",
            compose.onAllNodesWithText("取消").fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun toggleRowReportsItselfToHost() {
        val clicked = mutableListOf<String>()
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(
                    style = SettingsV2Style.M3_CARD,
                    sections = sections,
                    onRowClick = { row -> clicked.add(row.key) },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("自动播报回答", substring = true).performClick()
        compose.waitForIdle()
        assertEquals(listOf("tts"), clicked)
    }
}

private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithText(
    text: String,
): androidx.compose.ui.test.SemanticsNodeInteractionCollection =
    onAllNodes(androidx.compose.ui.test.hasText(text, substring = true))
