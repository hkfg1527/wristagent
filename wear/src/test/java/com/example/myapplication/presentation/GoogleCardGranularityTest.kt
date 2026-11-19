package com.example.myapplication.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.VoiceEntryContent
import com.example.myapplication.presentation.ui.settings.SettingsContentWidth
import com.example.myapplication.presentation.ui.settings.SettingsSquareCardDp
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
 * 大卡片档的**两种粒度**：通栏长卡（176dp）与小方块卡（88dp）必须同时存在。
 *
 * 为什么要这条：0.0.0.126 加了 `squareCard`，但连续两轮没能在真机上截到开关行（adb 摸索不可靠），
 * 于是"到底有没有画出 88dp 方块卡"一直悬着。这类几何问题本来就不该靠截图回答 ——
 * 这条用例直接渲染真实的「外观与动效」页，量每个可点节点的宽度。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class GoogleCardGranularityTest {

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val appearanceIsolation = SettingsAppearanceResetRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun theCardStyleRendersBothLongCardsAndSquareCards() {
        applySettingsStyle(context, SettingsStyle.GOOGLE_CARD)
        val prefs = AgentPreferences(context)
        compose.setContent { MaterialTheme { VoiceEntryContent(preferences = prefs, onBack = {}, onOpenAsr = {}) } }
        compose.waitForIdle()

        val density = context.resources.displayMetrics.density
        val longPx = SettingsContentWidth.value * density
        val squarePx = SettingsSquareCardDp.value * density

        // 页面是懒列表：首屏看不到开关行，必须一路往下扫，边扫边收集宽度
        val scrollable = compose
            .onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            .onFirst()
        val seen = LinkedHashSet<Float>()
        var squareNodes = 0
        repeat(24) {
            val nodes = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
            nodes.forEach { node ->
                val w = node.boundsInRoot.width
                if (w > 1f) seen.add(w)
                if (abs(w - squarePx) <= 1f) {
                    squareNodes++
                    assertEquals(
                        "方块卡必须是正方形：宽=" + w + " 高=" + node.boundsInRoot.height,
                        w, node.boundsInRoot.height, 1f
                    )
                }
            }
            if (squareNodes > 0) return@repeat
            runCatching { scrollable.performTouchInput { swipeUp() } }
            compose.waitForIdle()
        }

        assertTrue("一个可点节点都没扫到，判据无效", seen.isNotEmpty())
        assertTrue(
            "必须有通栏长卡（${longPx}px），实际宽度集合=" + seen.sorted(),
            seen.any { abs(it - longPx) <= 1f }
        )
        assertTrue(
            "整页都找不到 88dp 小方块卡（${squarePx}px），实际宽度集合=" + seen.sorted(),
            seen.any { abs(it - squarePx) <= 1f }
        )
        assertTrue("基准常量必须是 176dp", abs(SettingsContentWidth.value - 176f) < 0.01f)
        assertTrue("方块卡常量必须是 88dp", abs(SettingsSquareCardDp.value - 88f) < 0.01f)
    }
}
