package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.wear.compose.material3.MaterialTheme
import java.io.File
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
 * ①「页面风格」行存在且可点开选择器（独立底部按钮已删除：它抢主视觉还占 48dp）；
 * ②选择器里五套都在（每套一个条目）；
 * ③点某一套 → 回调收到对应风格、覆盖层关闭。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h300dp")
class SettingsV2StylePickerTest {

    @get:Rule
    val compose = createComposeRule()

    /** 视口小，用最小数据把「页面风格」行放在第一屏，避免它没被组合导致查不到。 */
    private val sections = listOf(
        SettingsV2Section(
            title = "连得上",
            rows = listOf(
                SettingsV2Row("model", "模型", trailing = RowTrailing.Value("x")),
                SettingsV2Row("style", "页面风格", trailing = RowTrailing.Chevron),
            ),
        )
    )

    private fun show(picked: ((SettingsV2Style) -> Unit)? = null) {
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(
                    style = SettingsV2Style.M3_CARD,
                    sections = sections,
                    onStyleSelected = picked,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theStyleButtonOpensAPickerWithAllFiveStyles() {
        show()

        compose.onNodeWithText("页面风格", substring = true).assertIsDisplayed()
        assertTrue(
            "打开前不应有选择器",
            compose.onAllNodesWithTag(StylePickerTag).fetchSemanticsNodes().isEmpty()
        )

        compose.onNodeWithText("页面风格", substring = true).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(StylePickerTag).assertIsDisplayed()
        // 覆盖层可滚动：视口小，先滚到目标再断言
        SettingsV2Style.values().forEach { style ->
            compose.onNodeWithTag(StylePickerItemTagPrefix + style.name).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun pickingAStyleReportsItAndClosesThePicker() {
        var picked: SettingsV2Style? = null
        show { picked = it }

        compose.onNodeWithText("页面风格", substring = true).performClick()
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

    @Test
    fun thePickerBackgroundIsFullyOpaque() {
        // 真机踩过：背景用 0xF2（95% 不透明）时，底层页面文字会 ghost 到覆盖层上
        // （TERMINAL 底层时顶部出现 > 温度）。这条守卫钉住"必须全不透明"。
        val src = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2StylePicker.kt") }
            .first { it.exists() }
            .readText()
        assertTrue("覆盖层背景必须是全不透明的 0xFF000000", src.contains("Color(0xFF000000)"))
        assertTrue(
            "不允许再出现半透明背景（0xF2 之类）",
            !Regex("""Color\\(0x(F[0-9A-F]|E[0-9A-F])000000\\)""").containsMatchIn(src)
        )
    }
}