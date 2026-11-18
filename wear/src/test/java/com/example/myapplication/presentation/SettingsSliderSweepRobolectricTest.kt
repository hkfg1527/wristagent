package com.example.myapplication.presentation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.AppearanceContent
import com.example.myapplication.presentation.ui.settings.MemoryManagerContent
import com.example.myapplication.presentation.ui.settings.VoiceEntryContent
import com.example.myapplication.presentation.ui.settings.applySettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 剩下所有滑杆的扫荡：记忆三根、语音两根，外加官方 `Slider` 分支（M3_LIST 档）。
 *
 * 为什么每根都要单独测：吸附规则是**各行自己写的**（整数、0.05、20、0.1、0.1），
 * 共用的是轨道不是业务。一根写错，用户在屏上只会觉得"滑了但数字怪"，不做断言看不出来。
 *
 * 官方档（M3_LIST / M3_TRANSFORM）走的是另一套控件：轨道不可点、不可拖，
 * 只有两端 ±48dp 的按钮可点（见 `SettingsOfficialControls` 文件头 3.2），所以那一档用"点加号"验。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsSliderSweepRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    private fun density(): Float = context.resources.displayMetrics.density

    @Before
    fun forceThinTrackStyle() {
        // WearAppearance 是全局单例：上一个测试类（乃至本类那个官方档用例）会把 style 留在
        // M3_LIST/M3_TRANSFORM 上，那两档的滑杆是官方 Slider、没有 contentDescription ——
        // 不重置就会报"找不到节点"，而真正原因是渲染走了另一档。所以每个用例都从「气泡档」起步。
        applySettingsStyle(context, SettingsStyle.BALLOON)
    }

    /**
     * 滚到这一行并拨到目标值。
     *
     * 不断言"整行完全露出"：`performScrollToNode` 只保证进视口，常常只露一半，
     * 而此时节点已经组合好、语义动作照常派发 —— 值有没有落盘才是判据，
     * 露多少是取样位置的事（"看得见"另有像素测试管）。
     */
    private fun setSlider(label: String, target: Float, expectedRange: ClosedFloatingPointRange<Float>) {
        SettingsUi.scrollTo(compose, label)
        val node = compose.onAllNodesWithContentDescription(label)
            .filterToOne(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        assertEquals(
            "「$label」的可调节区间必须是它自己的",
            expectedRange,
            node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range
        )
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
        compose.waitForIdle()
    }

    @Test
    fun contextTurnsSliderSnapsToWholeTurn() {
        val p = prefs()
        p.maxContextTurns = 12
        compose.setContent { MaterialTheme { MemoryManagerContent(preferences = p, onBack = {}) } }
        setSlider(context.getString(R.string.settings_context_turns), 30.4f, 4f..60f)
        assertEquals("拨到 30.4 必须吸附成 30 轮", 30, p.maxContextTurns)
    }

    @Test
    fun compressThresholdSliderSnapsToFivePercent() {
        val p = prefs()
        p.historyCompressThreshold = 0.5f
        compose.setContent { MaterialTheme { MemoryManagerContent(preferences = p, onBack = {}) } }
        setSlider(context.getString(R.string.settings_compress_threshold), 0.62f, 0.3f..0.95f)
        assertEquals("拨到 0.62 必须吸附成 0.60", 0.6f, p.historyCompressThreshold, 0.0001f)
    }

    @Test
    fun memoryFactLengthSliderSnapsToTwenty() {
        val p = prefs()
        p.memoryMaxFactLength = 120
        compose.setContent { MaterialTheme { MemoryManagerContent(preferences = p, onBack = {}) } }
        setSlider(context.getString(R.string.settings_memory_max_fact_length), 137f, 40f..240f)
        assertEquals("拨到 137 必须吸附成 140", 140, p.memoryMaxFactLength)
    }

    @Test
    fun speechRateSliderSnapsToTenth() {
        val p = prefs()
        p.ttsSpeechRate = 1f
        compose.setContent {
            MaterialTheme { VoiceEntryContent(preferences = p, onBack = {}, onOpenAsr = {}) }
        }
        setSlider(context.getString(R.string.settings_tts_speech_rate), 1.37f, 0.5f..2f)
        assertEquals("拨到 1.37 必须吸附成 1.4", 1.4f, p.ttsSpeechRate, 0.0001f)
    }

    @Test
    fun ttsVolumeSliderSnapsToTenth() {
        val p = prefs()
        p.ttsVolume = 0.8f
        compose.setContent {
            MaterialTheme { VoiceEntryContent(preferences = p, onBack = {}, onOpenAsr = {}) }
        }
        setSlider(context.getString(R.string.settings_tts_volume), 0.44f, 0.1f..1f)
        assertEquals("拨到 0.44 必须吸附成 0.4", 0.4f, p.ttsVolume, 0.0001f)
    }

    @Test
    fun officialSliderBranchStepsOneSegmentPerPlusTap() {
        applySettingsStyle(context, SettingsStyle.M3_LIST)
        val p = prefs()
        p.matrixSpacingDp = 4f
        compose.setContent { MaterialTheme { AppearanceContent(preferences = p, onBack = {}) } }

        val label = context.getString(R.string.settings_appearance_matrix_spacing)
        SettingsUi.scrollTo(compose, label)
        // 官方档没有自绘轨道：标签是一枚普通 Text，没有 contentDescription，改用文字定位。
        val labelTop = compose.onAllNodesWithText(label, substring = false).fetchSemanticsNodes()
            .minByOrNull { it.boundsInRoot.top }
            ?.boundsInRoot?.top
            ?: throw AssertionError("官方档里找不到「$label」这一行的标签")

        // 这一行的控件 = 左减 / 右加两个 48dp 按钮；取标签下方 120dp 内最靠右的那个，连点三次。
        repeat(3) {
            val clickables = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            val band = labelTop..(labelTop + 120f * density())
            val plus = clickables
                .filter { it.boundsInRoot.top in band }
                .maxByOrNull { it.boundsInRoot.left }
                ?: throw AssertionError("「$label」这一行里点不到加号")
            compose.onAllNodes(hasClickAction())[clickables.indexOf(plus)].performClick()
            compose.waitForIdle()
        }

        assertEquals("官方档每点一次加号走一段（4dp + 3 段 = 7dp）", 7f, p.matrixSpacingDp, 0.0001f)
    }
}
