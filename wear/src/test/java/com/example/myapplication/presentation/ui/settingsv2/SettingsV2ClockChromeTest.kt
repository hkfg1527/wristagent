package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * 顶部钟带（曲面时钟）的判据。
 *
 * 为什么需要它：V2 是整页重写，重写时把旧设置页的 `TimeText` 这一层**整层漏掉**了，
 * 真机首屏顶部因此是一条空黑带。官方 Wear 每一页都有这条钟带，缺了它一眼就不是官方的表。
 *
 * **判据边界（重要）**：这里只钉"钟带存在 + 位置正确"，**不钉时间文本**。
 * 实测 Robolectric 下 `TimeText` 产出的文本节点是空的（语义树里只有一个 5×18px 的空 `Text` 节点，
 * 无 `Text` 属性），因为平台时间格式化器在影子环境里给不出字符串。
 * 若强行断言"文本含冒号"，这条用例会永远是红的 —— 那属于环境限制，不是实现缺陷。
 * 时间真的显示出来，由真机截图（`docs/device/v2five/M3_CARD-clock.png`）负责。
 *
 * 一套风格一个用例：`ComposeTestRule` 每个用例只能 `setContent` 一次，
 * 四套写在一个用例里会直接抛 "has already set content"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h300dp")
class SettingsV2ClockChromeTest {

    @get:Rule
    val compose = createComposeRule()

    /** 一行足够：钟带是页面层叠的，与行数无关。 */
    private val sections = listOf(
        SettingsV2Section(
            title = "连得上",
            rows = listOf(SettingsV2Row("model", "模型", trailing = RowTrailing.Value("x"))),
        )
    )

    private fun show(style: SettingsV2Style) {
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(style = style, sections = sections)
            }
        }
        compose.waitForIdle()
    }

    /** 钟带必须贴顶（24dp 安全区内）且水平居中 —— 这两条就是官方 `TimeText` 的形态。 */
    private fun assertClockBand(style: SettingsV2Style) {
        show(style)
        val clock = compose.onNodeWithTag(SettingsV2ClockTag, useUnmergedTree = true)
        clock.assertIsDisplayed()

        val box = clock.getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()

        assertTrue("$style 的钟带上沿必须贴顶（实测 ${box.top}）", box.top <= 8.dp)
        val clockCenter = (box.left + box.right) / 2
        val rootCenter = (root.left + root.right) / 2
        assertTrue(
            "$style 的钟带必须水平居中（钟心 $clockCenter vs 屏心 $rootCenter）",
            abs((clockCenter - rootCenter).value) <= 1f
        )
    }

    @Test
    fun m3CardHasTheClockBand() = assertClockBand(SettingsV2Style.M3_CARD)

    @Test
    fun m3ListHasTheClockBand() = assertClockBand(SettingsV2Style.M3_LIST)

    @Test
    fun arcHasTheClockBand() = assertClockBand(SettingsV2Style.ARC)

    @Test
    fun squareHasTheClockBand() = assertClockBand(SettingsV2Style.SQUARE)

    @Test
    fun theTerminalStyleDeliberatelyHasNoClock() {
        show(SettingsV2Style.TERMINAL)
        assertEquals(
            "TERMINAL 顶部是 banner，不得再叠时钟（这条是有意的排除）",
            0,
            compose.onAllNodesWithTag(SettingsV2ClockTag, useUnmergedTree = true).fetchSemanticsNodes().size
        )
    }
}
