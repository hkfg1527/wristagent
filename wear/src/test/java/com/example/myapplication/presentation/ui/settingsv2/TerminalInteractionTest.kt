package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalRowTag
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
