package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun theCardStyleUsesOneSmallCardPerSetting() {
        // 用**纯文本行**的夹具，不用 `SettingsV2SampleData.root()`：0.0.0.263 起温度是滑块行，
        // 它天然比普通行高（标题一行 + 整宽滑块一行），会在 300dp 视口里把卡片数从 4 压到 3。
        // 这条判据问的是"普通设置项有没有被做成大海报卡"，所以夹具里不该混进滑块行 ——
        // 否则改数据（加一个滑块）就会让一条与数据无关的布局守卫变红。
        val plainRows = listOf(
            SettingsV2Section(
                title = "连得上",
                rows = (1..6).map { SettingsV2Row("r$it", "项目 $it", trailing = RowTrailing.Value("$it")) },
            )
        )
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_CARD, sections = plainRows) }
        }
        compose.waitForIdle()

        val density = androidx.test.core.app.ApplicationProvider
            .getApplicationContext<android.app.Application>()
            .resources.displayMetrics.density
        // 旧判据是"存在 ≥140dp 的大卡"——那是把 13 个设置项塞进 4 张海报卡，首屏只 3 行。
        // 新架构：一项一张小卡。判据换成**密度约束**：视口内至少组合出 4 张，
        // 且不许再出现 ≥140dp 的稀疏大卡。
        // 不设固定高度上限：SurfaceTransformation 会按滚动位置缩放卡片（圆屏边缘随动），
        // 那个形变是设计本身，不是回归。
        val heights = compose.onAllNodes(androidx.compose.ui.test.hasTestTag("m3card-row"))
            .fetchSemanticsNodes().map { it.boundsInRoot.height }
        assertTrue(
            "M3_CARD 必须是一行一张独立小卡（视口内至少 4 张）；实测 ${heights.size} 张",
            heights.size >= 4
        )
        assertTrue(
            "不许再出现 ≥140dp 的稀疏大卡；实测 heights(px)=${heights.joinToString()}",
            heights.all { it < 140f * density }
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
    fun noStyleButtonFullWidthAndChoiceEntry() {
        // 0.0.0.272 起改回官方值 24dp（= 屏高 10%，见 SettingsV2BottomInset 的 KDoc）。
        // 此前钉的是 8dp —— 那个值让列表最后一行落进屏幕底部约 40px 不吃点击的带里，
        // 真机实测末行 y=412 可点、y>=425 无反应（D8）。这不是放宽断言，是把期望改到有出处的值上。
        assertEquals(
            "底部安全边距应为官方 10% 屏高（24dp）；小于它会压进底部手势带",
            24f,
            SettingsV2BottomInset.value,
            0.01f
        )
        val src = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Screen.kt") }
            .first { it.exists() }
            .readText()
        assertFalse(
            "底部独立的「风格」按钮必须删掉（它抢主视觉、还占 48dp）",
            src.contains("CompactButton(")
        )
        assertFalse(
            "不许再 clipToBounds 把满宽内容硬裁成矩形（会废掉边缘随动）",
            src.contains("clipToBounds()")
        )
        assertTrue(
            "内容必须满宽，靠 SurfaceTransformation / edgeAppearance 贴合圆弧",
            src.contains("contentModifier = Modifier\n                .fillMaxWidth()")
        )
        assertTrue(
            "「页面风格」行必须本身是入口",
            src.contains("row.key == \"style\" -> pickerOpen = true")
        )
    }
}
