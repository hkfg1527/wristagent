package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalLineHeight
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalRowTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * R42：终端行必须落在**字符行网格**上 —— 这次用布局几何断言，而不是真机 dump。
 *
 * 为什么值得单独立一条：行距是这套"像不像终端"的地基（36px/18dp），但此前它**只有真机 dump 证据**，
 * 设备一掉线就无从验证（本会话已经因此在 44px/36px 之间反复过一次）。
 * 这里直接量 Compose 里每个行容器的实际高度与相邻顶边差，要求它们都是 [TerminalLineHeight] 的整数倍。
 *
 * 视口给 1400dp 高是为了让全部行都被组合（矮视口里没组合的行取不到 bounds）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h1400dp")
class TerminalGridTest {

    @get:Rule
    val compose = createComposeRule()

    private fun isGridMultiple(px: Float, linePx: Float): Boolean {
        val ratio = px / linePx
        return abs(ratio - ratio.roundToInt()) <= 0.05f
    }

    @Test
    fun everyRowSitsOnTheCharacterGrid() {
        val sections = listOf(
            SettingsV2Section(
                title = "区",
                rows = listOf(
                    SettingsV2Row("a", "只读行", trailing = RowTrailing.Value("v")),
                    SettingsV2Row("b", "带说明", subtitle = "说明", trailing = RowTrailing.Value("v")),
                    SettingsV2Row("c", "可点行", trailing = RowTrailing.Chevron),
                    SettingsV2Row("d", "开关行", trailing = RowTrailing.Toggle(false) {}),
                ),
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL, sections = sections) }
        }
        compose.waitForIdle()

        // **故意不引用 TerminalLineHeight**：网格和被测值若都来自同一个常量，断言永远绿
        // （变异测试教训：把常量改成 17dp 时，原版断言照样通过 —— 那是空洞断言）。
        // 18dp 是设计值，写死；用 dp 而非 px 以免受 Robolectric 默认密度影响。
        val lineDp = 18f
        val density = compose.density.density
        val nodes = compose.onAllNodes(hasTestTag(TerminalRowTag)).fetchSemanticsNodes()
        assertTrue("必须量到行容器", nodes.size >= 4)

        nodes.forEach { node ->
            val hDp = node.boundsInRoot.height / density
            assertTrue(
                "行容器高度必须是 ${lineDp}dp 的整数倍（实际 ${hDp}dp）",
                isGridMultiple(hDp, lineDp),
            )
        }
        val tops = nodes.map { it.boundsInRoot.top }.sorted()
        tops.zipWithNext().forEach { (a, b) ->
            val gapDp = (b - a) / density
            assertTrue(
                "相邻行的顶边差必须是 ${lineDp}dp 的整数倍（实际 ${gapDp}dp）",
                isGridMultiple(gapDp, lineDp),
            )
        }
    }
}