package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * V2 第一套（`M3_CARD` 大卡片）的判据。
 *
 * 钉三件事：
 * ①五套风格的枚举都在，且**每套的身份参数互不相同**（防止又出现"十套里四套是空壳"）；
 * ②大卡片真的画出内容（组标题与行标题可见）；
 * ③大卡片是"大"的：至少一张卡的高度 ≥ 140dp —— 这是 Google Health 那种一屏一两张的形态特征。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h300dp")   // 视口要放得下"一张 140dp 大卡 + 上下安全区内缩"
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

        compose.onNodeWithText("连得上").assertIsDisplayed()
        compose.onNodeWithText("供应商").assertIsDisplayed()
        // 懒列表只组合可见项：这里只要求"首组内容完整可见"，不去找屏外的分区
        assertTrue(
            "首组至少要有一行设置项",
            compose.onAllNodesWithText("模型", substring = true).fetchSemanticsNodes().isNotEmpty()
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
        val all = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("m3card-section"))
            .fetchSemanticsNodes().map { it.boundsInRoot.height }
        val tall = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("m3card-section"))
            .fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .filter { it.height >= minHeightPx }
        assertTrue(
            "大卡片档必须存在高度 ≥140dp 的卡片（Google Health 那种一屏一两张）；" +
                "实测 heights(px)=${all.joinToString()} minHeightPx=$minHeightPx density=$density",
            tall.isNotEmpty()
        )
    }

    /**
     * R14：底部为「风格」按钮让出的内边距必须是 48dp，且内容必须被裁剪。
     *
     * 真机实测（0.0.0.195）：按钮可点击节点 `[177,370][289,466]`（96px = 48dp 高，顶边 370）；
     * 列表 `scrollable` 节点底边 386（= 466 - 40dp×2）；而末行右值 `BALANCED` 被画在
     * `[90,411][376,442]` —— **超出列表边界 56px**，从按钮底下穿出来。
     * 原因：`TransformingLazyColumn` 不裁剪，加上 40dp 内边距比按钮矮 16px。
     */
    @Test
    fun bottomInsetMatchesTheStyleButtonAndContentIsClipped() {
        assertEquals(
            "底部内边距必须等于按钮高度 48dp（真机量得按钮 96px 高）",
            48f,
            SettingsV2ContentBottomInset.value,
            0.01f
        )
        val src = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Screen.kt") }
            .first { it.exists() }
            .readText()
        assertTrue(
            "内容必须 clipToBounds()：TransformingLazyColumn 会把行画到边界之外",
            src.contains("clipToBounds()")
        )
        assertTrue(
            "底部内边距必须走 SettingsV2ContentBottomInset 常量，不许再写魔法数字 40.dp",
            !src.contains("padding(bottom = 40.dp)")
        )
    }
}
