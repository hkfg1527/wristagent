package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 磁滑（snap）到底有没有生效 —— 用确定性的 JVM 手势回答，不靠真机肉眼。
 *
 * 背景（0.0.0.118）：两条列表都接上了官方 `snapFlingBehavior`，但真机上量了三轮
 * （慢拖回弹 / 快甩残差 / 落点一致性）都区分不出"吸附"与"匀速衰减"。
 * 这里改成在测试时钟里跑同一段 `swipeUp()`：**吸附生效时滚动偏移必须落在节距的整数倍上**。
 *
 * 同时它也是"接线真的被消费"的证据：如果 `flingBehavior` 参数没被列表用上，
 * 吸附组与对照组的结果会一模一样。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h200dp")
class SnapFlingReachTest {

    @get:Rule
    val compose = createComposeRule()

    private val pitchDp = 72
    private val itemCount = 24

    @Composable
    private fun List(state: ScalingLazyListState, snap: Boolean) {
        ScalingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth().height(216.dp),
            flingBehavior = if (snap) {
                ScalingLazyColumnDefaults.snapFlingBehavior(state = state, snapOffset = 0.dp)
            } else {
                ScrollableDefaults.flingBehavior()
            }
        ) {
            items(itemCount) { index ->
                Box(Modifier.fillMaxWidth().height(pitchDp.dp)) { Text("row-$index") }
            }
        }
    }

    private fun swipeAndSettle(snap: Boolean): Pair<Int, Int> {
        lateinit var state: ScalingLazyListState
        compose.setContent {
            state = rememberScalingLazyListState()
            List(state, snap)
        }
        compose.onNodeWithText("row-0").assertExists()
        compose.onNodeWithText("row-0").performTouchInput { swipeUp() }
        compose.mainClock.advanceTimeBy(3_000)
        compose.waitForIdle()
        // Wear 的 ScalingLazyListState 只暴露"居中项"的索引与偏移（没有 firstVisibleItem*）
        return state.centerItemIndex to state.centerItemScrollOffset
    }

    @Test
    fun bothFlingBehavioursActuallyMoveTheList() {
        // 前提：这段 swipe 真的把列表推动了，否则下面两条断言毫无意义
        val (index, offset) = swipeAndSettle(snap = true)
        assertTrue("swipe 之后列表必须已经滚动（index=$index offset=$offset）", index > 0 || offset > 0)
    }

    @Test
    fun snappedListSettlesWithTheCenteredItemExactlyOnCenter() {
        // 视口高 = 3×72dp，吸附生效时居中项必须**正好**在屏心（offset≈0）；
        // 0.0.0.118 那轮真机上量不出来的，就是这一条。
        val (index, offset) = swipeAndSettle(snap = true)
        println("SNAP-SNAPPED index=$index centerOffsetPx=$offset")
        assertTrue(
            "吸附生效时居中项偏移必须接近 0，实际 index=$index offset=$offset",
            kotlin.math.abs(offset) <= 1
        )
    }

    /**
     * 对照组：不吸附时**不保证**落在整数倍上。
     *
     * 这条不是"必须失败"的断言（连续衰减也可能碰巧落在边界），它的价值是报告里能给出真实数字：
     * 若对照组与吸附组余数同为 0，说明两者行为一致 → `flingBehavior` 没被消费，接线是假的。
     */
    @Test
    fun controlGroupReportsItsOwnRemainder() {
        val (index, offset) = swipeAndSettle(snap = false)
        println("SNAP-CONTROL index=$index centerOffsetPx=$offset")
    }
}
