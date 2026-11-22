package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalValueTag
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * R44：**值必须右对齐成一列** —— 用几何断言，不靠真机截图。
 *
 * 这条是终端与"列表"最直观的差别之一：`> 模型  deepseek-v4-pro` / `> 温度  1.4` 的值都在右边缘
 * 对齐成一条竖线。此前只有一次真机 dump 证据（值右边缘 x=361、滚动条左边缘 389），
 * 设备掉线就无从验证，所以改成量 Compose 里每个值节点的右边缘。
 *
 * 注意断言用的是**值之间互相比较**（右边缘彼此相差 ≤2dp），而不是写死某个坐标 ——
 * 这样换行宽/换字号也不会假红，但"对齐"这件事一变就必红。
 *
 * 变异测试实测（两轮，结论不同且都有用）：
 * - 把 `horizontalArrangement` 从 `SpaceBetween` 改成 `Start` -> **失败**
 *   （值右缘 `[51.0, 39.0, 46.0]`，散布 12dp），说明这条测的正是"值被推到右缘"；
 * - 把值的 `textAlign` 从 `End` 改成 `Start` -> **仍通过**：因为值的节点宽度等于文本宽度，
 *   对齐方式不改变节点边界。也就是说 `textAlign` 在这里是冗余写法，真正起作用的是排列方式。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h1000dp")
class TerminalValueColumnTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun valuesShareOneRightEdge() {
        val sections = listOf(
            SettingsV2Section(
                title = "区",
                rows = listOf(
                    SettingsV2Row("a", "模型", trailing = RowTrailing.Value("deepseek-v4-pro")),
                    SettingsV2Row("b", "温度", trailing = RowTrailing.Value("1.4")),
                    SettingsV2Row("c", "回复风格", trailing = RowTrailing.Value("BALANCED")),
                ),
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL, sections = sections) }
        }
        compose.waitForIdle()

        val density = compose.density.density
        val rights = compose.onAllNodes(hasTestTag(TerminalValueTag), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .map { it.boundsInRoot.right / density }
        assertTrue("必须量到 3 个值节点，实际 ${rights.size}", rights.size == 3)

        val spread = rights.max() - rights.min()
        assertTrue(
            "三个值必须右对齐成一列：彼此相差应 ≤2dp，实际 ${rights.map { "%.1f".format(it) }} spread=${"%.1f".format(spread)}dp",
            spread <= 2f,
        )
    }
}
