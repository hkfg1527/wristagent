package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.ArcCardTag
import com.example.myapplication.presentation.ui.settingsv2.style.ArcBottomTail
import com.example.myapplication.presentation.ui.settingsv2.style.ArcSafeInset
import com.example.myapplication.presentation.ui.settingsv2.style.ArcTopInset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R3：`ARC` 华为式贴边卡的判据。
 *
 * 这是上一轮（旧实现）**被卡死的那一条**，所以判据写得最硬：
 * ①卡片必须**比 176dp 更宽**（贴边的前提，旧实现永远等于 176dp）；
 * ②卡片**左边缘必须贴到屏幕左边**（left ≤ 2px）；
 * ③安全边距必须是华为 8.1.1 的 26vp/20vp，而不是随手取的值。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class ArcStyleTest {

    @get:Rule
    val compose = createComposeRule()

    private val density: Float = ApplicationProvider
        .getApplicationContext<android.app.Application>()
        .resources.displayMetrics.density

    @Test
    fun arcCardsAreEdgeToEdgeAndWiderThanTheStandard176dp() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.ARC) }
        }
        compose.waitForIdle()

        val cards = compose.onAllNodesWithTag(ArcCardTag).fetchSemanticsNodes()
        assertTrue("一张贴边卡都没渲染出来，判据无效", cards.isNotEmpty())

        val standardWidth = 176f * density
        val widest = cards.maxOf { it.boundsInRoot.width }
        assertTrue(
            "贴边卡必须比 176dp(${standardWidth}px) 更宽，实际最宽 ${widest}px",
            widest > standardWidth
        )

        val flush = cards.filter { it.boundsInRoot.left <= 2f }
        assertTrue(
            "至少一张卡必须贴住屏幕左边（left<=2px），实际 left=" +
                cards.map { it.boundsInRoot.left }.distinct().sorted(),
            flush.isNotEmpty()
        )
    }

    @Test
    fun arcUsesHuaweiSafeMargins() {
        assertTrue("华为 8.1.1 左右安全边距应为 26vp，实际 ${ArcSafeInset.value}", ArcSafeInset.value == 26f)
        assertTrue("页顶留白应为 20vp，实际 ${ArcTopInset.value}", ArcTopInset.value == 20f)
        assertTrue(
            "ARC 必须走贴边宽度策略",
            SettingsV2Style.ARC.identity.width == RowWidthPolicy.EdgeToEdge
        )
    }

    @Test
    fun arcKeepsHuaweiBottomTailOf40vp() {
        // 华为 8.1.1：长列表滚到底部留 40vp。真机实测末行文本底部距屏幕底 69dp
        //（无尾留白时约 29dp），这里钉住常量本身
        assertEquals(40f, ArcBottomTail.value, 0.001f)
    }

    @Test
    fun arcRendersItsContent() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.ARC) }
        }
        compose.waitForIdle()
        compose.onNodeWithText("连得上").assertIsDisplayed()
        compose.onNodeWithText("供应商").assertIsDisplayed()
    }
}
