package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V2 第一套（`M3_CARD` 大卡片）的判据。
 *
 * 钉三件事：
 * ①五套风格的枚举都在，且**每套的身份参数互不相同**（防止又出现"十套里四套是空壳"）；
 * ②大卡片真的画出内容（组标题与行标题可见）；
 * ③大卡片是"大"的：至少一张卡的高度 ≥ 140dp —— 这是 Google Health 那种一屏一两张的形态特征。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SettingsV2ScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun thereAreExactlyFiveStylesAndEachHasItsOwnIdentity() {
        val styles = SettingsV2Style.values().toList()
        assertTrue("V2 只保留五套风格，实际 ${styles.size} 套", styles.size == 5)

        val identities = styles.map { it.identity }
        assertTrue(
            "每套风格的身份参数必须互不相同（否则就是换名字的空壳）",
            identities.toSet().size == identities.size
        )
        assertTrue(
            "必须存在贴边策略的风格（ARC），否则贴边卡没法实现",
            styles.any { it.identity.width == RowWidthPolicy.EdgeToEdge }
        )
    }

    @Test
    fun theCardStyleRendersSectionAndRowTitles() {
        compose.setContent {
            MaterialTheme {
                SettingsV2Screen(style = SettingsV2Style.M3_CARD)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("模型与服务").assertIsDisplayed()
        compose.onNodeWithText("供应商").assertIsDisplayed()
        // 第二组标题可能不在首屏，但至少应存在于语义树中
        assertTrue(
            "至少应能找到两组以上分区标题",
            compose.onAllNodesWithText("外观", substring = true).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("让它能做事", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    @Test
    fun theCardStyleUsesTallCards() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_CARD) }
        }
        compose.waitForIdle()

        val density = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.app.Application>()
            .resources.displayMetrics.density
        val minHeightPx = 140f * density
        val tall = compose.onAllNodes(androidx.compose.ui.test.hasText("模型与服务", substring = true))
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.height >= minHeightPx }
        assertTrue(
            "大卡片档必须存在高度 ≥140dp 的卡片（Google Health 那种一屏一两张）",
            tall.isNotEmpty()
        )
    }
}
