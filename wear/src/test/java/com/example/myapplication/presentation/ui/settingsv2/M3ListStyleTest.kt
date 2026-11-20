package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.presentation.ui.settingsv2.style.M3ListRowMinHeight
import com.example.myapplication.presentation.ui.settingsv2.style.M3ListSurfaceAlpha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R2：`M3_LIST` 官方列表形态的判据。
 *
 * 分两层：
 * ①**渲染层**：首组标题与第一行真的画出来了（懒列表只组合可见项，所以只断言首屏）；
 * ②**规格层**：行最小高 ≥48dp、表面完全透明（无卡面）、有分隔线 —— 这三条是"列表"与"大卡片"的分界，
 *   直接读 [SettingsV2Style.M3_LIST] 的身份参数，比截图更稳（真机截图留到 R6）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class M3ListStyleTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun theListStyleRendersSectionTitleAndFirstRow() {
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_LIST) }
        }
        compose.waitForIdle()

        compose.onNodeWithText("模型与服务").assertIsDisplayed()
        compose.onNodeWithText("供应商").assertIsDisplayed()
    }

    @Test
    fun theListStyleHasNoCardSurfaceAndIsTallEnoughToTap() {
        assertEquals(
            "列表行表面必须完全透明（否则就不是列表而是卡片）",
            0f,
            M3ListSurfaceAlpha,
            0.0001f
        )
        assertTrue(
            "列表行最小高必须 ≥48dp，实际 ${M3ListRowMinHeight.value}dp",
            M3ListRowMinHeight.value >= 48f
        )
        assertTrue(
            "列表必须有行间分隔线",
            SettingsV2Style.M3_LIST.identity.dividers
        )
        assertEquals(
            "列表页底色应为纯黑",
            androidx.compose.ui.graphics.Color(0xFF000000),
            SettingsV2Style.M3_LIST.identity.pageBackground
        )
    }
}
