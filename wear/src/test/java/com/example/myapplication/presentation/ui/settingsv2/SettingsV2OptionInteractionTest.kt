package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「设置项点了真的能改」的**界面级**交互验证（不只是源码守卫）。
 *
 * 之前只有 SettingsV2WritableTest 读 Host 源码确认映射存在，没有真正在 Compose 里点开、
 * 选值、收回调。这条补上链路：行点击 → OptionPicker → 选项点击 → onChoiceSelected。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h400dp")
class SettingsV2OptionInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    /** 最小数据：温度（选项）+ 自动播报（开关）放第一屏，避免行没被组合。 */
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
    fun pickingAnOptionCallsBackAndClosesThePicker() {
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

        // 点行 -> 选项列表出现
        compose.onNodeWithText("温度", substring = true).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(OptionPickerTag).assertIsDisplayed()
        assertTrue(
            "打开前不应有回调",
            picked == null
        )

        // 点 1.6 -> 回调收到 temperature/1.6，且覆盖层关闭
        // 用语义动作派发而非坐标点击：Robolectric 的圆屏小视口 + 滚动容器下，
        // performClick 会点到合并父节点造成假阴性（组件级测试已定位到这一点）。
        compose.onNodeWithTag(OptionPickerItemTagPrefix + "1.6", useUnmergedTree = true)
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.waitForIdle()
        assertTrue(
            "回调应收到 temperature/1.6，实际 picked=$picked",
            picked == "temperature" to "1.6"
        )
        assertTrue(
            "选完后覆盖层必须关闭",
            compose.onAllNodesWithTag(OptionPickerTag).fetchSemanticsNodes().isEmpty()
        )
        // 页面内容仍在（没有被连页关掉）
        compose.onNodeWithText("自动播报回答", substring = true).assertIsDisplayed()
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
        assertTrue(
            "开关行不该打开选项列表",
            compose.onAllNodesWithTag(OptionPickerTag).fetchSemanticsNodes().isEmpty()
        )
    }

    /** BackHandler 的"只关覆盖层"由源码守卫钉住（此处不派发真实返回键，避免空洞断言）。 */
    @Test
    fun optionPickerIsWiredToBackHandler() {
        val src = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Screen.kt") }
            .first { it.exists() }
            .readText()
        assertTrue(
            "BackHandler 必须覆盖选项列表（activeChoice != null）",
            src.contains("BackHandler(enabled = pickerOpen || activeChoice != null)")
        )
        assertTrue(
            "返回时选项列表必须被关掉而不是退出页面",
            src.contains("else -> activeChoice = null")
        )
    }
}
