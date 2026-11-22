package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalRowTag
import com.example.myapplication.presentation.ui.settingsv2.style.appendCappedEcho
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import java.io.File
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

    /**
     * R32：**滑动不许误触发点击**（否则滑一下就执行一条命令）。
     *
     * 真机复核过（滑动后无回显、单击后有回显），这里用 Compose 的手势注入把它钉住：
     * 行是 clickable 的，同时列表还能滚动 —— 两者的竞争关系必须一直是"位移超过 slop 就取消点击"。
     */
    @Test
    fun swipingOnARowDoesNotExecuteIt() {
        val sections = listOf(
            SettingsV2Section(
                title = "测试区",
                rows = listOf(SettingsV2Row(key = "open", title = "跳转项", trailing = RowTrailing.Chevron)) +
                    (1..10).map { i -> SettingsV2Row(key = "k$i", title = "行$i", trailing = RowTrailing.Value("v$i")) },
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.TERMINAL, sections = sections) }
        }
        compose.waitForIdle()

        val row = compose.onAllNodes(hasTestTag(TerminalRowTag) and hasText("/open", substring = true))[0]
        row.performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertTrue(
            "滑动之后不许出现回显（否则滑一下就执行命令）",
            compose.onAllNodesWithText("# open", substring = true).fetchSemanticsNodes().isEmpty()
        )

        // 对照：单击同一样必须执行
        compose.onAllNodes(hasTestTag(TerminalRowTag) and hasText("/open", substring = true))[0].performClick()
        compose.waitForIdle()
        assertTrue(
            "单击必须执行（否则说明手势判据把点击也吞了）",
            compose.onAllNodesWithText("# open", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    /**
     * R41：回显项的 key 必须是**自增 id**，不能用文本。
     *
     * 真机 FATAL（0.0.0.224）：`Key "echo-# open 工具与 MCP" was already used` ——
     * 执行同一条命令两次会产生两条相同文本，用文本当 LazyColumn 的 key 直接崩溃重启。
     * 这条守卫把"key 只能用 id"钉死；下面那条纯函数测试保证"重复文本是合法输入"，
     * 两条合起来才能说明为什么必须用 id。
     */
    @Test
    fun echoItemsAreKeyedByIdNotByText() {
        val src = File("wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt")
            .let { if (it.exists()) it else File("../wear/src/main/java/com/example/myapplication/presentation/ui/settingsv2/style/TerminalStyle.kt") }
            .readText()
        assertTrue("回显 key 必须用自增 id", src.contains("item(key = \"echo-\" + echo.id)"))
        assertFalse("不许再用文本当 key（会重复）", src.contains("item(key = \"echo-\" + line)"))
        assertTrue("回显项必须带 id 字段", src.contains("internal data class EchoLine(val id: Long, val text: String)"))
    }

    /**
     * 重复文本是**合法输入**（同一条命令可以执行多次），所以容量规则不能靠去重来"解决"重复 key。
     */
    @Test
    fun duplicateEchoTextIsLegalInput() {
        val echoes = mutableListOf("# open 工具与 MCP", "# open 工具与 MCP")
        appendCappedEcho(echoes, "# open 工具与 MCP")
        assertEquals(
            "三条完全相同的回显必须都保留（去重会掩盖问题，真正的解是 id）",
            listOf("# open 工具与 MCP", "# open 工具与 MCP", "# open 工具与 MCP"),
            echoes.toList()
        )
    }

    @Test
    fun tappingAToggleFlipsItAndAppendsAnEchoLine() {        // 自包含数据：只放一条 OFF 开关 + 一条 /open，不依赖示例数据里恰好有什么
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
