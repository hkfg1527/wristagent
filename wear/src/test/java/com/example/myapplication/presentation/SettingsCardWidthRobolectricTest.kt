package com.example.myapplication.presentation

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.AppearanceContent
import com.example.myapplication.presentation.ui.settings.SettingsContentWidth
import com.example.myapplication.presentation.ui.settings.applySettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

/**
 * UI 硬约束「所有卡同宽、不横向撑满」的 JVM 版判据（六套风格各跑一遍）。
 *
 * 为什么用"可点节点的宽度"当判据：设置页的交互行要么是卡组里的行、要么是官方控件，
 * 它们都由 [SettingsContentWidth] 定宽 —— 一旦有人给某行加了 `fillMaxWidth()`、或把卡宽改掉，
 * 这里立刻能看出来。真机像素采样只能看**某一屏**，这里六套风格全跑且不需要设备。
 *
 * 判据同时钉「至少有一个节点真的等于 176dp」：否则整页一个可点节点都没有时，用例会假绿。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsCardWidthRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val appearanceIsolation = SettingsAppearanceResetRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun everyStyleKeepsInteractiveRowsAtTheSharedCardWidth() {
        val density = context.resources.displayMetrics.density
        val expectedWidthPx = SettingsContentWidth.value * density

        applySettingsStyle(context, SettingsStyle.BALLOON)
        val prefs = AgentPreferences(context)
        compose.setContent { MaterialTheme { AppearanceContent(preferences = prefs, onBack = {}) } }
        compose.waitForIdle()

        for (style in SettingsStyle.values()) {
            // 风格是渲染期读的单例状态：改完让它重组一次再量
            applySettingsStyle(context, style)
            compose.waitForIdle()

            val widths = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                .map { it.boundsInRoot.width }
                .filter { it > 0f }

            assertTrue("$style：一个可点节点都没扫到，判据无效", widths.isNotEmpty())
            val widest = widths.max()
            assertTrue(
                "$style：有行比卡片基准宽度还宽（最宽 " + widest + "px，基准 " + expectedWidthPx + "px）",
                widest <= expectedWidthPx + 1f
            )
            assertTrue(
                "$style：没有任何节点等于卡片基准宽度 " + expectedWidthPx + "px，实际=" +
                    widths.distinct().sorted(),
                widths.any { abs(it - expectedWidthPx) <= 1f }
            )
        }
        assertEquals("卡片基准宽度必须仍是 176dp", 176f, SettingsContentWidth.value, 0.01f)
    }
}
