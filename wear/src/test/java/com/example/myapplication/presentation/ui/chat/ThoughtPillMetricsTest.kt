package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ui.theme.Wear
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
    fun cornerRadiusComesFromThePillToken() {
        // 全圆例外写进令牌：胶囊圆角不再是"页面里各算一份高度一半"的 15dp
        assertEquals(Wear.Radius.Pill.value, ThoughtPillMetrics.cornerRadiusDp(), 0.001f)
    }

    @Test
    fun pillTokenStaysFullyRoundedEvenIfThePillGrows() {
        // Pill 远大于任何胶囊高度：高度翻倍也依然是两端半圆，不会变成圆角矩形
        assertTrue(Wear.Radius.Pill.value >= ThoughtPillMetrics.CollapsedHeightDp * 2f)
    }

    @Test
    fun labelFontIsTwelveSp() {
        assertEquals(12, ThoughtPillMetrics.LabelFontSp)
    }
}
