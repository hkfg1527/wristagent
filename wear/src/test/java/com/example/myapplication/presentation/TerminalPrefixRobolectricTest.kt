package com.example.myapplication.presentation

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.VoiceEntryContent
import com.example.myapplication.presentation.ui.settings.applySettingsStyle
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 终端档的 `> ` 前缀真的画到了动作行上。
 *
 * 背景：0.0.0.128 把前缀接进 `SettingsSlotRow` 时，**选项行**也被加了前缀，导致按文本找风格名的用例全红；
 * 于是加了 `plainLabel`（选项行不加）。这条用例钉住"动作行确实有前缀"这一半 ——
 * 另一半（选项行不加）由 `SettingsRadioRow` 传 `plainLabel = true` 保证。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class TerminalPrefixRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val appearanceIsolation = SettingsAppearanceResetRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun terminalActionRowsCarryThePromptPrefix() {
        applySettingsStyle(context, SettingsStyle.TERMINAL)
        val prefs = AgentPreferences(context)
        compose.setContent {
            MaterialTheme { VoiceEntryContent(preferences = prefs, onBack = {}, onOpenAsr = {}) }
        }
        compose.waitForIdle()

        // 动作行必须以 `> ` 开头：这里只要求"页面上至少有一个节点文本含前缀"，
        // 前缀的拼接规则本身由 TerminalTextTest 逐条钉住（不重复、非终端不动）。
        val prefixed = compose.onAllNodes(hasText(TerminalPrefix, substring = true))
            .fetchSemanticsNodes()
        assertTrue(
            "终端档的动作行必须以「$TerminalPrefix」开头（实际命中 ${prefixed.size} 个节点）",
            prefixed.isNotEmpty()
        )
    }

    private companion object {
        /** 与 `TerminalText.ROW_PREFIX` 一致；这里写死一份，改常量时这条会红。 */
        const val TerminalPrefix = "> "
    }

    @Test
    fun terminalSwitchesAreTextNotCapsules() {
        applySettingsStyle(context, SettingsStyle.TERMINAL)
        val prefs = AgentPreferences(context)
        compose.setContent {
            MaterialTheme { VoiceEntryContent(preferences = prefs, onBack = {}, onOpenAsr = {}) }
        }
        compose.waitForIdle()

        // 终端里没有胶囊开关：状态用 [ ON ] / [ OFF ] 文本表达（命令行习惯）
        val onNodes = compose.onAllNodes(hasText("[ ON ]", substring = true)).fetchSemanticsNodes()
        val offNodes = compose.onAllNodes(hasText("[ OFF ]", substring = true)).fetchSemanticsNodes()
        assertTrue(
            "终端档开关必须渲染成 [ ON ]/[ OFF ] 文本（ON=${onNodes.size} OFF=${offNodes.size}）",
            onNodes.isNotEmpty() || offNodes.isNotEmpty()
        )
    }
}
