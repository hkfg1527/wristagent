package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.SettingsAppearanceResetRule
import com.example.myapplication.presentation.ui.settings.WearAppearance
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R6 的前置判据：**换风格后界面必须真的换掉**。
 *
 * 真机上出现过"POST 成功、/api/state 变了、屏幕上纹丝不动"（0.0.0.153 连测三次只得到两个哈希，
 * 而那两个哈希对应的是屏幕亮/灭）。这条用例在 JVM 里复现同一场景：
 * ①先以 M3_CARD 渲染；②在 snapshot 里把风格改成 TERMINAL（模拟控制台写入）；③断言 UI 变成终端形态。
 *
 * 它同时钉住了"`key(style)` 重建"这件修复是否真的起作用 —— 如果哪天有人把 `key` 去掉，
 * 这条会红，而不是等到真机上再看一次"没反应"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SettingsV2StyleSwitchTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val appearanceIsolation = SettingsAppearanceResetRule()

    @Test
    fun switchingStyleWhileOpenActuallyChangesWhatIsRendered() {
        // ① 先 M3_CARD（偏好的默认值就是 BALLOON → 映射到 M3_CARD）
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.settingsStyle = SettingsStyle.BALLOON
        compose.setContent {
            MaterialTheme { SettingsV2Host(preferences = prefs, onBack = {}) }
        }
        compose.waitForIdle()
        assertTrue(
            "M3_CARD 应渲染分区标题",
            compose.onAllNodesWithText("连得上", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "M3_CARD 不该出现终端前缀",
            compose.onAllNodesWithText("> 供应商", substring = true).fetchSemanticsNodes().isEmpty()
        )

        // ② 改偏好 + 广播（模拟控制台 POST：写偏好后 bump 一次）
        prefs.settingsStyle = SettingsStyle.TERMINAL
        com.example.myapplication.agent.SettingsChangeBus.bump()
        compose.waitForIdle()

        // ③ 断言真的变成终端形态
        assertTrue(
            "切到 TERMINAL 后必须出现 '> ' 前缀的行",
            compose.onAllNodesWithText("> ", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        assertTrue(
            "切到 TERMINAL 后必须是终端（'# ' 分组头）",
            compose.onAllNodesWithText("# ", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }
}
