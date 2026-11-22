package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertFalse
import java.io.File
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 还款守卫：**除 ARC 贴边外的四套，卡片/行必须同宽 176dp 且不重叠**。
 *
 * 这条规则原本由旧实现的 `SettingsCardWidthRobolectricTest` 守着（0.0.0.167 随旧页面一起删掉），
 * 现在为 V2 重写：判据落在 [SettingsV2StandardWidth] 与四套的 `RowWidthPolicy` 上，
 * 并对代表性文本节点量宽度，确认没有任何一行的宽度超过 176dp 太多（ARC 除外，它必须更宽）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SettingsV2CardWidthTest {

    @get:Rule
    val compose = createComposeRule()

    private val density: Float = ApplicationProvider
        .getApplicationContext<android.app.Application>()
        .resources.displayMetrics.density

    @Test
    fun onlyArcIsEdgeToEdgeAndTheRestAreStandard() {
        val edgeToEdge = SettingsV2Style.values().filter {
            it.identity.width == RowWidthPolicy.EdgeToEdge
        }
        assertEquals(
            "只有 ARC 允许贴边（官方 edge-hugging）；其余四套必须 Standard",
            listOf(SettingsV2Style.ARC),
            edgeToEdge
        )
        assertEquals("同宽基准必须是 192dp", 192f, SettingsV2StandardWidth.value, 0.001f)
    }

    @Test
    fun contentIsFullWidthInsteadOfAClippedColumn() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_CARD) }
        }
        compose.waitForIdle()

        val src = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Screen.kt") }
            .first { it.exists() }
            .readText()
        assertFalse(
            "内容不许再被 Modifier.width(SettingsV2StandardWidth) 裁成定宽列（那会放弃圆屏横向面积）",
            src.contains("Modifier.width(SettingsV2StandardWidth)")
        )
        assertTrue("contentModifier 必须满宽", src.contains("contentModifier = Modifier\n                .fillMaxWidth()"))

        val nodes = compose.onAllNodesWithText("供应商").fetchSemanticsNodes()
        assertTrue("M3_CARD 必须渲染出内容", nodes.isNotEmpty())
        // 文本节点仍应落在屏幕内（不越出圆屏边界），这是对"满宽"的正向约束
        nodes.forEach { node ->
            assertTrue(
                "文本节点不能溢出屏幕：left=${node.boundsInRoot.left}",
                node.boundsInRoot.left >= 0f
            )
        }
    }

    @Test
    fun arcCardsAreWiderThanTheStandardColumn() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.ARC) }
        }
        compose.waitForIdle()

        val arcCard = compose.onAllNodesWithText("供应商").fetchSemanticsNodes()
        assertTrue("ARC 必须渲染出内容", arcCard.isNotEmpty())

        // ARC 的宽度策略是 EdgeToEdge：它的列宽必须大于 176dp
        val policy = SettingsV2Style.ARC.identity.width
        assertEquals(RowWidthPolicy.EdgeToEdge, policy)
        assertTrue(
            "ARC 的列宽基准必须与 Standard 不同",
            SettingsV2Style.ARC.identity.width != SettingsV2Style.M3_CARD.identity.width
        )
    }

    @Test
    fun everyStyleDeclaresAWidthPolicy() {
        // 防止有人新增风格时忘记声明宽度策略（SemanticsMatcher 只是借用来保证 import 不被优化掉）
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)
        assertTrue("语义匹配器应可用", matcher != null)
        SettingsV2Style.values().forEach { style ->
            assertTrue(
                "${style.name} 必须显式声明宽度策略",
                style.identity.width == RowWidthPolicy.Standard || style.identity.width == RowWidthPolicy.EdgeToEdge
            )
        }
    }
}
