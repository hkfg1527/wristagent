package com.example.myapplication.presentation

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.AccentPreset
import com.example.myapplication.presentation.ui.settings.AppearanceContent
import com.example.myapplication.presentation.ui.settings.AppearanceStore
import com.example.myapplication.presentation.ui.settings.FontScaleTier
import com.example.myapplication.presentation.ui.settings.HapticAmplitude
import com.example.myapplication.presentation.ui.settings.WearAppearance
import com.example.myapplication.presentation.ui.settings.applySettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 外观页剩下的四组控制：触觉强度、字号档、强调色档、三条数值滑杆。
 *
 * 为什么单列一页来钉：这些控件**写的是不同地方**——
 * 触觉/滑杆写 [AgentPreferences]，字号/强调色写 [WearAppearance] + `AppearanceStore`。
 * 只要有一处"点了只改屏上状态、没落盘"，重启后又变回去，而截图看不出来。
 *
 * 滑杆那三条尤其值钱：轨道是自绘的（`SettingsThinSlider`），
 * 但它补了 ProgressBarRangeInfo + SetProgress 语义，于是读屏能拨、测试也能拨 ——
 * 这里用**语义动作**驱动，而不是"点某个像素"，所以不依赖屏幕尺寸。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsAppearanceDeepRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    @Before
    fun forceStyleWithThinSlider() {
        // 上一批测试会把 WearAppearance.style 留成上一轮最后点的那套（全局单例，跨类泄漏）。
        // 这里显式落到「气泡档」：它的滑杆槽位是 THIN_TRACK，正是本文件要测的自绘轨道。
        applySettingsStyle(context, SettingsStyle.BALLOON)
        WearAppearance.accentOverride = null
        WearAppearance.fontScale = 1f
    }

    private fun show(preferences: AgentPreferences) {
        compose.setContent { MaterialTheme { AppearanceContent(preferences = preferences, onBack = {}) } }
    }

    private fun clickRow(text: String) = SettingsUi.clickRow(compose, text)

    /**
     * 用语义动作把滑杆拨到目标值：等价于读屏双击后上下滑。
     *
     * 顺带钉死"这一行是不是独立的可调节节点"：卡组外层是可点的 Card（会 mergeDescendants），
     * 滑杆行若不自设语义边界，就会被整张卡吸成一个节点、区间取邻居的 —— 那样这里对不上。
     */
    private fun setSlider(label: String, target: Float, expectedRange: ClosedFloatingPointRange<Float>) {
        SettingsUi.setSlider(compose, label, target, expectedRange)
    }

    @Test
    fun threeHapticLevelsEachWriteThePreference() {
        val p = prefs()
        p.hapticAmplitude = HapticAmplitude.STANDARD
        show(p)
        for (level in HapticAmplitude.entries) {
            clickRow(context.getString(level.labelRes))
            assertEquals(
                "点「" + context.getString(level.labelRes) + "」后偏好必须是这一档",
                level,
                p.hapticAmplitude
            )
            // 只写存储不够：直接读 preferences 的 selected 不会触发重组，圆点会停在原处。
            // 这里断言屏上那一行真的变成了"已选中"（单选行走 selectable，带 Selected 语义）。
            compose.onAllNodesWithText(context.getString(level.labelRes))
                .filterToOne(hasClickAction())
                .assertIsSelected()
        }
    }

    @Test
    fun fontScaleTiersWriteBothMirrorAndStore() {
        val p = prefs()
        show(p)
        for (tier in FontScaleTier.entries) {
            // 用百分比副标题当选择器：字号档的标签本身就是「标准 / 大 / 特大」，
            // 单字「大」在整页里到处都是（大卡片…），按标签点会滚到别的分区去。
            clickRow(tier.percent)
            assertEquals("字号档 " + tier.percent, tier.scale, WearAppearance.fontScale, 0.001f)
            assertEquals(
                "字号档必须同步落盘，否则重启即回退",
                tier.scale,
                AppearanceStore.of(context).fontScale(),
                0.001f
            )
        }
    }

    @Test
    fun accentPresetsWriteRuntimeOverrideAndStore() {
        val p = prefs()
        show(p)
        for (preset in AccentPreset.entries) {
            clickRow(context.getString(preset.labelRes))
            assertEquals(
                "强调色 " + preset.name + " 必须成为运行时覆盖色",
                preset.argb.toInt(),
                (WearAppearance.accentOverride ?: error("覆盖色为空")).toArgb()
            )
            assertEquals(
                "强调色必须同步落盘",
                preset.argb.toInt(),
                (AppearanceStore.of(context).accent() ?: error("落盘为空")).toArgb()
            )
        }
    }

    @Test
    fun matrixSpacingSliderSnapsToWholeDp() {
        val p = prefs()
        p.matrixSpacingDp = 4f
        show(p)
        setSlider(context.getString(R.string.settings_appearance_matrix_spacing), 9.4f, 4f..16f)
        assertEquals("拨到 9.4 必须吸附成 9dp", 9f, p.matrixSpacingDp, 0.001f)
    }

    @Test
    fun bottomBarAutoHideSliderSnapsToHalfSecond() {
        val p = prefs()
        p.bottomBarAutoHideMs = AgentPreferences.DEFAULT_BOTTOM_BAR_AUTO_HIDE_MS
        show(p)
        setSlider(context.getString(R.string.settings_bottom_bar_auto_hide), 3300f, 1000f..8000f)
        assertEquals("拨到 3.3s 必须吸附成 3.5s", 3500L, p.bottomBarAutoHideMs)
    }

    @Test
    fun rotaryGainSliderSnapsToTenth() {
        val p = prefs()
        p.rotaryScrollGain = 1f
        show(p)
        setSlider(context.getString(R.string.settings_rotary_gain), 2.27f, 0.5f..3f)
        assertEquals("拨到 2.27 必须吸附成 2.3", 2.3f, p.rotaryScrollGain, 0.001f)
    }
}
