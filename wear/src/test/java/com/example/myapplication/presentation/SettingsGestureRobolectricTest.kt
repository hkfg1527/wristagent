package com.example.myapplication.presentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.presentation.ui.settings.GestureBindingsContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 手势与侧键绑定：四段单选，每段四个动作。
 *
 * 难点不在点击，在**定位**：同一句「终止当前任务」在页面上出现四次，
 * 而列表是懒加载的（离屏项根本没被组合）。所以定位规则是"两段标题之间的那一条带"——
 * 用两段标题的 `boundsInRoot.top` 夹出一条区间，区间内的节点才可能是本段的。
 * 两段标题没同时出现时先小步上滑再试，而不是猜索引。
 *
 * 断言同时钉住"别的三段一个都没动"：否则点错段也会绿。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsGestureRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private val sectionHeaders = listOf(
        R.string.settings_gesture_long_press,
        R.string.settings_gesture_double_tap,
        R.string.settings_gesture_side_short,
        R.string.settings_gesture_side_long
    )

    private fun headerText(index: Int): String = context.getString(sectionHeaders[index])

    private fun actionLabelRes(action: GestureAction): Int = when (action) {
        GestureAction.NONE -> R.string.settings_gesture_none
        GestureAction.STOP_TURN -> R.string.settings_gesture_stop_turn
        GestureAction.SWITCH_MODEL -> R.string.settings_gesture_switch_model
        GestureAction.FORGET_REMEMBERED -> R.string.settings_gesture_forget_remembered
    }

    private fun list() = compose
        .onAllNodes(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.CollectionInfo))
        .onFirst()

    /** 标题节点的顶边（px）；没被组合出来时返回 null。 */
    private fun topOf(text: String): Float? =
        compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes()
            .minByOrNull { it.boundsInRoot.top }
            ?.boundsInRoot?.top

    private fun nudgeUp() {
        list().performTouchInput {
            swipe(
                start = Offset(centerX, height * 0.72f),
                end = Offset(centerX, height * 0.72f - 110.dp.toPx())
            )
        }
        compose.waitForIdle()
    }

    /**
     * 在第 [sectionIndex] 段里点 [action]。
     *
     * 该段的区间 = (本段标题顶边, 下段标题顶边)；最后一段没有下段标题，用页尾说明行当右边界。
     */
    private fun bindInSection(sectionIndex: Int, action: GestureAction) {
        val header = headerText(sectionIndex)
        // 最后一段没有下段标题当右边界：用无穷远即可 —— 它后面只有一句页尾说明，
        // 不会再有别的段的行混进来。
        val lower = if (sectionIndex + 1 < sectionHeaders.size) headerText(sectionIndex + 1) else null
        val actionText = context.getString(actionLabelRes(action))

        list().performScrollToNode(hasText(header, substring = true))
        compose.waitForIdle()

        var clicked = false
        for (attempt in 0 until 8) {
            val top = topOf(header)
            val bottom = lower?.let { topOf(it) } ?: Float.MAX_VALUE
            if (top != null && bottom > top) {
                val inBand = SemanticsMatcher("第 ${sectionIndex + 1} 段区间") { node ->
                    val y = node.boundsInRoot.top
                    y > top && y < bottom
                }
                val hits = compose
                    .onAllNodes(hasText(actionText, substring = false) and hasClickAction() and inBand)
                    .fetchSemanticsNodes()
                if (hits.isNotEmpty()) {
                    compose
                        .onAllNodes(hasText(actionText, substring = false) and hasClickAction() and inBand)
                        .onFirst()
                        .performClick()
                    compose.waitForIdle()
                    clicked = true
                    break
                }
            }
            nudgeUp()
        }
        if (!clicked) {
            throw AssertionError("在「$header」段里找不到「$actionText」这一行")
        }
    }

    @Test
    fun eachGestureSectionBindsOnlyItsOwnKey() {
        val p = AgentPreferences(context)
        p.longPressAction = GestureAction.NONE
        p.doubleTapAction = GestureAction.NONE
        p.sideKeyShortAction = GestureAction.NONE
        p.sideKeyLongAction = GestureAction.NONE

        compose.setContent { MaterialTheme { GestureBindingsContent(preferences = p, onBack = {}) } }

        val plan = listOf(
            0 to GestureAction.SWITCH_MODEL,
            1 to GestureAction.STOP_TURN,
            2 to GestureAction.FORGET_REMEMBERED,
            3 to GestureAction.SWITCH_MODEL
        )
        val expected = mutableListOf(
            GestureAction.NONE,
            GestureAction.NONE,
            GestureAction.NONE,
            GestureAction.NONE
        )

        for ((index, action) in plan) {
            bindInSection(index, action)
            expected[index] = action
            assertEquals(
                "第 ${index + 1} 段点「" + context.getString(actionLabelRes(action)) + "」后四条绑定",
                expected.toList(),
                listOf(p.longPressAction, p.doubleTapAction, p.sideKeyShortAction, p.sideKeyLongAction)
            )
        }
    }
}
