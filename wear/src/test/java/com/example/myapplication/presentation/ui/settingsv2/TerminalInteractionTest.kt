package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalRowTag
import com.example.myapplication.presentation.ui.settingsv2.style.appendCappedEcho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R18：终端行是**可执行对象** —— 点一下必须真的生效，而不是只有外观。
 *
 * 上一轮用 `adb shell input tap` 在真机上点开关行没翻（dump 里开关文本甚至消失），
 * 无法判断是"代码没接上"还是"tap 被列表惯性吞掉"。这里用 Robolectric 的确定性点击把两者分开：
 * 若本测试绿 ⇒ 代码是通的，真机那次是注入手势的问题；若红 ⇒ 真 bug。
 *
 * 视口给到 1400dp 高：回显行追加在列表末尾，矮视口里根本不会被组合，断言会假红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h1400dp")
class TerminalInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * R22：执行一行之后必须**跟随到底**，否则用户点了命令看不到输出（真终端就是这么做的）。
     *
     * 视口故意压到 200dp：回显在列表末尾，不跟随就根本不会被组合 —— 这条断言因此是真的行为判据，
     * 不是"源码里有没有那个词"。
     */
    @Test
    @Config(sdk = [35], qualifiers = "w220dp-h200dp")
    fun terminalFollowsTheOutputAfterExecutingARow() {
        val sections = listOf(
            SettingsV2Section(
                title = "测试区",
                // 可点的 /open 放**第一行**（200dp 视口只组合前几行，放最后会点不到），
                // 后面堆满行，让回显落在视口之外 —— 不跟随就看不到，断言才有意义
                rows = listOf(SettingsV2Row(key = "open", title = "跳转项", trailing = RowTrailing.Chevron)) +
                    (1..12).map { i -> SettingsV2Row(key = "k$i", title = "行$i", trailing = RowTrailing.Value("v$i")) },
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL, sections = sections) }
        }
        compose.waitForIdle()

        val openRow = compose.onAllNodes(
            hasTestTag(TerminalRowTag) and hasText("/open", substring = true)
        )[0]
        openRow.performClick()
        compose.waitForIdle()

        assertTrue(
            "执行后必须自动跟随到底，让回显可见",
            compose.onAllNodesWithText("# open", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    /**
     * R30：回显最多留 3 行（旧行被顶掉）。
     *
     * 测的是纯函数 [appendCappedEcho]：UI 层的"连点四次再数节点"会被"是否被组合/是否在视口内"干扰，
     * 判不了这条规则本身。真机侧已复核：连点 4 次后 dump 里回显 2 行（≤3）。
     */
    @Test
    fun echoesAreCappedAtThreeLines() {
        val echoes = mutableListOf<String>()
        appendCappedEcho(echoes, "# a -> ON")
        appendCappedEcho(echoes, "# b -> OFF")
        assertEquals(2, echoes.size)
        appendCappedEcho(echoes, "# c -> ON")
        appendCappedEcho(echoes, "# d -> OFF")
        assertEquals("上限 3 行", 3, echoes.size)
        assertEquals("最旧的被顶掉", listOf("# b -> OFF", "# c -> ON", "# d -> OFF"), echoes.toList())
        appendCappedEcho(echoes, "# e -> ON", cap = 1)
        assertEquals("cap 可注入", listOf("# e -> ON"), echoes.toList())
    }

    @Test
    fun tappingAToggleFlipsItAndAppendsAnEchoLine() {
        // 自包含数据：只放一条 OFF 开关 + 一条 /open，不依赖示例数据里恰好有什么
        val sections = listOf(
            SettingsV2Section(
                title = "测试区",
                rows = listOf(
                    SettingsV2Row(
                        key = "t1",
                        title = "开关项",
                        trailing = RowTrailing.Toggle(checked = false) {},
                    ),
                    SettingsV2Row(key = "t2", title = "跳转项", trailing = RowTrailing.Chevron),
                ),
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL, sections = sections) }
        }
        compose.waitForIdle()

        // 注意语义树合并：clickable 的 Column 会把子节点文本并进自己，
        // 所以用"行 tag + 行文本"直接匹配合并后的节点，不要用 hasAnyDescendant（合并树里没有子节点）
        val offRow = compose.onAllNodes(
            hasTestTag(TerminalRowTag) and hasText("[ OFF ]", substring = true)
        )[0]
        offRow.performClick()
        compose.waitForIdle()

        assertTrue(
            "点一下必须翻成 [ ON  ]（否则行只是外观，不是可执行对象）",
            compose.onAllNodesWithText("[ ON  ]", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "翻转必须在列表末尾留下回显 `-> ON`",
            compose.onAllNodesWithText("-> ON", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }
}
