package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isSelected
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
                // 用「安全策略」当选项行的样本：温度已改成滑块（数值项不再开子菜单），
                // 拿它当选项样本会与真实数据形状不一致。
                SettingsV2Row(
                    key = "security",
                    title = "安全策略",
                    trailing = RowTrailing.Value("标准"),
                    choice = securityChoice("STANDARD"),
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

        // 点「安全策略」→ 进入子列表：用的是**同一套行容器**（m3card-row）
        compose.onNodeWithText("安全策略", substring = true).performClick()
        compose.waitForIdle()
        assertTrue(
            "子列表必须复用主页的行容器（m3card-row），而不是另做一套",
            compose.onAllNodesWithTag("m3card-row").fetchSemanticsNodes().isNotEmpty()
        )
        // 返回行必须在（子列表靠它回主页，不依赖系统返回键）
        compose.onNodeWithText("‹ 返回", substring = true).assertIsDisplayed()

        // 选一个靠近顶部的选项：M3_CARD 只组合可见项，远处选项在视口外查不到节点
        compose.onNodeWithText("自动执行", substring = true).performClick()
        compose.waitForIdle()
        assertEquals("security" to "EXPERT", picked)
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

        compose.onNodeWithText("安全策略", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithText("‹ 返回", substring = true).performClick()
        compose.waitForIdle()

        // 回到主页：温度行在、且不再显示选项值
        compose.onNodeWithText("自动播报回答", substring = true).assertIsDisplayed()
        assertTrue(
            "返回后不该还停在子列表（选项「极度宽松」不应存在）",
            compose.onAllNodesWithText("自动执行").fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * 读屏要能念出"已选择"。
     *
     * 视觉上当前项只画了一个 `✓` 字符 —— 读屏不会把 "✓" 念成"已选中"。
     * 所以选中状态必须走 `SemanticsProperties.Selected`。
     */
    @Test
    fun theSelectedOptionIsAnnouncedAsSelected() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_CARD, sections = sections) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("安全策略", substring = true).performClick()
        compose.waitForIdle()

        compose.onNode(
            androidx.compose.ui.test.hasText("标准") and androidx.compose.ui.test.isSelected(),
        ).assertExists()
    }

    @Test
    fun theChoiceSubListStartsWithABackRowWithoutAForwardChevron() {
        // 真机 0.0.0.260：返回行右边挂着 `›`，与左边的 `‹ 返回` 自相矛盾。
        // 子列表结构是逻辑（不是样式），所以直接断言数据，不靠点界面猜。
        val row = SettingsV2Row(
            key = "security",
            title = "安全策略",
            trailing = RowTrailing.Value("标准"),
            choice = securityChoice("STANDARD"),
        )
        val sub = choiceSubSections(row)

        assertEquals("一行选项只摊成一个分区", 1, sub.size)
        assertEquals("子列表标题必须沿用父行标题", "安全策略", sub[0].title)

        val back = sub[0].rows.first()
        assertEquals("首行必须是返回行", CHOICE_PREFIX + CHOICE_BACK, back.key)
        assertEquals("‹ 返回", back.title)
        assertEquals("返回行不得挂右向箭头（‹ 与 › 同时出现是自相矛盾）", RowTrailing.None, back.trailing)
        assertTrue("返回行必须可点（isActionable），否则点了没反应", back.isActionable())

        assertEquals(
            "选项行数 = 选项数 + 1 个返回行",
            securityChoice("STANDARD").options.size + 1,
            sub[0].rows.size,
        )
        val checked = sub[0].rows.filter { it.trailing == RowTrailing.Value("✓") }
        assertEquals("只有当前值带勾", 1, checked.size)
        assertEquals("勾必须落在当前值「标准」上", "标准", checked.single().title)
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
