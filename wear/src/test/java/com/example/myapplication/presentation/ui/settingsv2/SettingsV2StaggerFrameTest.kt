package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 动效的**帧级证据**（真机录屏不可用时的替代方案）。
 *
 * 背景（本轮实测）：单次 `adb exec-out screencap` 往返 **约 506ms（469~555ms）**，
 * 而入场动效总时长只有 200~300ms —— 也就是说真机截图**比动效本身还慢**，
 * 抓不到动画中间帧。录屏在 Wear 上历史验证也不可用（拿到几 KB 空文件）。
 *
 * 所以把"帧级"证据放到 Compose 测试时钟上：手动推进 `mainClock`，
 * 在同一时刻量两行的 y 位置。若逐行错峰成立，**第一行先动、第二行后动**，
 * 于是两行在同一时间窗内的位移量必然不同（相差约 staggerMs 的量）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w200dp-h360dp")
class SettingsV2StaggerFrameTest {

    @get:Rule
    val compose = createComposeRule()

    private fun yOf(text: String): Float =
        compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top

    @Test
    fun theFirstRowMovesBeforeTheSecondOne() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_LIST) }
        }
        // 第一帧：动效起点（此时两行都还在下方位移 enterSlide 处）
        compose.mainClock.advanceTimeByFrame()
        val firstStart = yOf("模型")
        val secondStart = yOf("温度")

        // 推进 120ms：M3_LIST 的 stagger 是 26ms（逐行），第一行已进入动画、第二行刚起步
        compose.mainClock.advanceTimeBy(120L)
        val firstAfter = yOf("模型")
        val secondAfter = yOf("温度")

        val firstMoved = firstStart - firstAfter
        val secondMoved = secondStart - secondAfter

        assertTrue(
            "第一行必须比第二行先动（错峰）：first=$firstMoved second=$secondMoved",
            firstMoved > secondMoved
        )
        assertTrue("第一行在 120ms 内必须产生可见位移：$firstMoved", firstMoved > 0f)
    }

    @Test
    fun bothRowsEventuallyReachTheSameRestingDelta() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_LIST) }
        }
        compose.mainClock.advanceTimeByFrame()
        val startDelta = yOf("温度") - yOf("模型")

        // 推进到所有入场动画结束（M3_LIST：stagger 26ms×行数 + 200ms + 余量）
        compose.mainClock.advanceTimeBy(2_000L)
        val endDelta = yOf("温度") - yOf("模型")

        assertTrue(
            "动画结束后两行应回到同一套布局间距（差值应收敛）：start=$startDelta end=$endDelta",
            kotlin.math.abs(endDelta - startDelta) < 1f
        )
    }
}
