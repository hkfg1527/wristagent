package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 官方滑杆的无障碍：**标签与区间必须在同一个可调节节点上**。
 *
 * 背景（0.0.0.119 实测）：官方 `Slider` 自带 `SetProgress`，但行标签没并进去 ——
 * 读屏只念"百分之几"，不念"这是哪一项"。当时把 BALLOON 切到官方滑杆，11 条用例同时全红。
 * 更要紧的是：`M3_LIST` / `M3_TRANSFORM` 本来就是官方滑杆，而现有页面用例只在默认风格下渲染，
 * 所以这两套的无障碍缺口一直没被发现。
 *
 * 这条用例直接钉封装本身（与风格无关），修好了它，任何风格切官方滑杆都不会再丢标签。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class OfficialSliderAccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Harness(value: Float = 0.4f, onValueChange: (Float) -> Unit = {}) {
        MaterialTheme {
            Box(Modifier.fillMaxWidth()) {
                OfficialSliderRow(
                    label = "播报音量",
                    value = value,
                    valueRange = 0f..1f,
                    steps = 9,
                    onValueChange = onValueChange
                )
            }
        }
    }

    @Test
    fun theOfficialSliderCarriesBothItsLabelAndItsRange() {
        compose.setContent { Harness() }
        val node = compose.onNodeWithContentDescription("播报音量")
        node.assertExists()
        node.assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        node.assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
    }

    @Test
    fun theSliderStillAcceptsProgrammaticProgress() {
        var seen = -1f
        compose.setContent { Harness(onValueChange = { seen = it }) }
        compose.onNodeWithContentDescription("播报音量")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.8f) }
        compose.waitForIdle()
        assertTrue("SetProgress 必须真的把值传出去，实际 $seen", seen > 0.5f)
    }
}
