package com.example.myapplication.presentation

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.ShellPolicy
import com.example.myapplication.presentation.ui.settings.LuxTerminalContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Lux 终端的输入链：自由输入 → 弹层 → 确认 → 闸门 → 回显。
 *
 * 这一页在 JVM 上只能测到"闸门之前"，因为真跑命令要 Android 的 `sh`；
 * 但闸门之前恰好是最该钉的一段：命令过长必须在**进终端之前**被拒，
 * 而不是先执行再报错 —— 那样截断的尾巴会被当成命令的一部分跑掉。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsLuxTerminalRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun overlongCommandIsRejectedBeforeItReachesTheShell() {
        val p = AgentPreferences(context)
        compose.setContent { MaterialTheme { LuxTerminalContent(preferences = p, onBack = {}) } }

        // 打开自由输入弹层
        SettingsUi.clickRow(compose, context.getString(R.string.settings_lux_free_input))

        // 超长命令：闸门上限是 ShellPolicy.MAX_COMMAND_LENGTH，这里多给 1 个字符
        val tooLong = "x".repeat(ShellPolicy.MAX_COMMAND_LENGTH + 1)
        compose.onNode(hasSetTextAction()).performTextInput(tooLong)
        compose.waitForIdle()

        // 确认（草稿只在确认时外泄）
        compose.onAllNodesWithText(context.getString(R.string.settings_dialog_confirm))
            .onFirst()
            .performClick()
        compose.waitForIdle()

        // 转录在页尾，是懒加载列表：先滚过去再断言，否则"没组合出来"会被误判成"没回显"
        SettingsUi.scrollTo(compose, "被拒")
        val entries = compose.onAllNodesWithText("被拒", substring = true).fetchSemanticsNodes()
        assertTrue("超长命令必须在终端里留下一条「被拒」回显，实际回显条数：" + entries.size, entries.isNotEmpty())
    }
}
