package com.example.myapplication.presentation.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 折叠态胶囊必须"扁"：高度 28~32dp、左右内边距 10~12dp、全圆角、12sp 细标签。
 * 这些数字以前散落在 Composable 的修改器链里（heightIn(min = 44dp) 直接把胶囊立成了一块卡片），
 * 现在钉成常量，一旦有人把它改回"卡片尺寸"，这里先红。
 */
class ThoughtPillMetricsTest {

    @Test
    fun collapsedHeightStaysInTheFlatRange() {
        assertTrue(
            "折叠态高度 ${ThoughtPillMetrics.CollapsedHeightDp}dp 不在 28~32dp",
            ThoughtPillMetrics.CollapsedHeightDp in 28..32
        )
    }

    @Test
    fun horizontalPaddingStaysInTheLabelRange() {
        assertTrue(
            "左右内边距 ${ThoughtPillMetrics.HorizontalPaddingDp}dp 不在 10~12dp",
            ThoughtPillMetrics.HorizontalPaddingDp in 10..12
        )
    }

    @Test
    fun cornerRadiusIsAlwaysHalfTheHeight() {
        assertEquals(
            ThoughtPillMetrics.CollapsedHeightDp / 2f,
            ThoughtPillMetrics.cornerRadiusDp(),
            0.001f
        )
    }

    @Test
    fun cornerRadiusFollowsHeightChanges() {
        // 三角验证：换一个高度，圆角必须跟着变成一半，而不是钉死在某个令牌上
        assertEquals(16f, ThoughtPillMetrics.cornerRadiusDp(32), 0.001f)
        assertEquals(10f, ThoughtPillMetrics.cornerRadiusDp(20), 0.001f)
    }

    @Test
    fun labelFontIsTwelveSp() {
        assertEquals(12, ThoughtPillMetrics.LabelFontSp)
    }
}
