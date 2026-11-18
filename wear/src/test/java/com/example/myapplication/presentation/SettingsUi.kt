package com.example.myapplication.presentation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import org.junit.Assert.assertEquals

/**
 * 设置页 Robolectric 测试共用的两个动作：滚到某一行、点这一行。
 *
 * 两个都是踩坑踩出来的：
 * 1. 页面里有 **两个** 可滚动节点（ScreenScaffold 外壳 + 列表本身），必须挑带 `CollectionInfo` 的那个，
 *    否则 `performScrollToNode` 直接报「找到 2 个」。
 * 2. 同一句文案可能既出现在分区标题又出现在行标签（「语音转写开关」），也可能出现在别的行的副标题里
 *    （「官方转场」的副标题写着「同官方列表…」）。所以默认精确匹配，再按"可点击"收敛到一个节点。
 */
internal object SettingsUi {

    fun scrollTo(rule: ComposeContentTestRule, text: String) {
        rule.onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
            .onFirst()
            .performScrollToNode(hasText(text, substring = true))
        rule.waitForIdle()
    }

    fun clickRow(rule: ComposeContentTestRule, text: String, exact: Boolean = true) {
        scrollTo(rule, text)
        rule.onAllNodesWithText(text, substring = !exact).filterToOne(hasClickAction()).performClick()
        rule.waitForIdle()
    }

    /**
     * 用语义动作把某条滑杆拨到目标值（等价于读屏双击后上下滑）。
     *
     * 顺带钉死"这一行是不是独立的可调节节点"：卡组外层是可点的 Card（会 mergeDescendants），
     * 滑杆行若不自设语义边界，就会被整张卡吸成一个节点、区间取邻居的 —— 那样这里对不上。
     * 不断言"整行完全露出"：`performScrollToNode` 只保证进视口，露多少是取样位置的事。
     */
    fun setSlider(
        rule: ComposeContentTestRule,
        label: String,
        target: Float,
        expectedRange: ClosedFloatingPointRange<Float>
    ) {
        scrollTo(rule, label)
        val node = rule.onAllNodesWithContentDescription(label)
            .filterToOne(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
        assertEquals(
            "「$label」的可调节区间必须是它自己的",
            expectedRange,
            node.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range
        )
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(target) }
        rule.waitForIdle()
    }
}
