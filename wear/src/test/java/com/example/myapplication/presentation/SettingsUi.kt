package com.example.myapplication.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.filterToOne
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode

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
}
