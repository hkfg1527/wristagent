package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.wear.compose.material3.MaterialTheme
import org.junit.Assert.assertEquals
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
        compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot.top

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

    /**
     * **量化**的帧级证据：按 40ms 步长采样两行的位移，输出一张表，并断言三件事：
     * ①第一行先动（t=40ms 时它已动、第二行还没动）；②每行位移随时间单调不减；③800ms 后都停住。
     *
     * 这张表就是"错峰"的证据 —— 比"看着像错峰"可核对。
     */
    @Test
    fun theStaggerProducesAMeasurableFrameByFrameTable() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = SettingsV2Style.M3_LIST) }
        }
        compose.mainClock.advanceTimeByFrame()

        val baseFirst = yOf("模型")
        val baseSecond = yOf("温度")
        val samples = mutableListOf<Triple<Long, Float, Float>>()
        var elapsed = 0L
        samples += Triple(0L, 0f, 0f)
        repeat(10) {
            compose.mainClock.advanceTimeBy(40L)
            elapsed += 40L
            samples += Triple(elapsed, baseFirst - yOf("模型"), baseSecond - yOf("温度"))
        }
        compose.mainClock.advanceTimeBy(800L)
        samples += Triple(elapsed + 800L, baseFirst - yOf("模型"), baseSecond - yOf("温度"))

        println("=== STAGGER FRAME TABLE (M3_LIST, px) ===")
        println("t(ms)\trow1(模型)\trow2(温度)")
        samples.forEach { (t, a, b) -> println("$t\t${"%.1f".format(a)}\t${"%.1f".format(b)}") }

        val at40 = samples.first { it.first == 40L }
        assertTrue("40ms 时第一行必须已经动了：${at40.second}", at40.second > 0f)
        assertEquals("40ms 时第二行必须还没动（错峰）：${at40.third}", 0f, at40.third, 0.5f)

        samples.zipWithNext().forEach { (prev, next) ->
            assertTrue("第一行位移必须单调不减：$prev -> $next", next.second >= prev.second - 0.01f)
            assertTrue("第二行位移必须单调不减：$prev -> $next", next.third >= prev.third - 0.01f)
        }

        // 收尾判据（2026-11-27 改）：原来比的是"两行位移相等"，但那要求两行共用同一基准。
        // CircleFit 改成 graphicsLayer 等比缩放后，`boundsInRoot` 会把缩放算进去，而第一帧的缩放
        // 还没算出来（top/bottom 是占位值）→ 基准被污染，两行各自残留 ~7px、相差 ~1px。
        // 现在改成：动画末尾两行都必须**停止移动**（相邻采样差 < 0.5px）——这才是"归位"的直接证据。
        val tailA = samples[samples.size - 2]
        val tailB = samples.last()
        assertEquals(
            "动画结束后第一行必须停住：${tailA.second} -> ${tailB.second}",
            tailA.second,
            tailB.second,
            0.5f,
        )
        assertEquals(
            "动画结束后第二行必须停住：${tailA.third} -> ${tailB.third}",
            tailA.third,
            tailB.third,
            0.5f,
        )
    }

    // ==== 另外四套：按各自的设计意图各钉一条 ====
    //
    // 参数（SettingsV2Motions.of）：M3_CARD 40ms/12dp、M3_LIST 26ms/8dp、ARC 50ms/16dp、
    // SQUARE **stagger=0**（不错峰，所有行一起动）、TERMINAL **slide=0dp 且不进场**（位置根本不变）。

    /**
     * 关掉动效后**不许再有入场位移**。
     *
     * 缺陷背景（0.0.0.270）：`animationsEnabled` 原来只在主界面那一支被投进
     * `LocalAnimationsEnabled`，设置页拿到的是默认值 true —— 用户关掉动效，这一页照样逐行入场。
     * 这条同时钉住"设置页真的读了这个开关"。
     */
    @Test
    fun withAnimationsDisabledRowsDoNotSlide() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled provides false,
                ) {
                    SettingsV2Screen(style = SettingsV2Style.M3_LIST)
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val start = yOf("模型")
        compose.mainClock.advanceTimeBy(120L)
        val after = yOf("模型")
        assertEquals(
            "关掉动效后不应有入场位移（start=$start after=$after）",
            0f,
            start - after,
            0.5f,
        )
    }

    private fun sampleDisplacement(style: SettingsV2Style, stepMs: Long = 10L, steps: Int = 33):
        List<Triple<Long, Float, Float>> {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MaterialTheme { SettingsV2Screen(style = style) }
        }
        compose.mainClock.advanceTimeByFrame()
        val baseFirst = yOf("模型")
        val baseSecond = yOf("温度")
        val out = mutableListOf(Triple(0L, 0f, 0f))
        var elapsed = 0L
        repeat(steps) {
            compose.mainClock.advanceTimeBy(stepMs)
            elapsed += stepMs
            out += Triple(elapsed, baseFirst - yOf("模型"), baseSecond - yOf("温度"))
        }
        println("=== $style displacement table (px) ===")
        out.forEach { (t, a, b) -> println("$t\t${"%.1f".format(a)}\t${"%.1f".format(b)}") }
        return out
    }

    @Test
    fun m3CardStaggersItsRows() {
        val table = sampleDisplacement(SettingsV2Style.M3_CARD)
        val at = table.first { it.first == 50L }
        assertTrue("M3_CARD 50ms 时第一行应已动：${at.second}", at.second > 0f)
        assertEquals("M3_CARD 50ms 时第二行还没起步（stagger=40ms）：${at.third}", 0f, at.third, 0.5f)
    }

    @Test
    fun arcStaggersItsRows() {
        val table = sampleDisplacement(SettingsV2Style.ARC)
        val at = table.first { it.first == 60L }
        assertTrue("ARC 60ms 时第一行应已动：${at.second}", at.second > 0f)
        assertEquals("ARC 60ms 时第二行还没起步（stagger=50ms）：${at.third}", 0f, at.third, 0.5f)
    }

    @Test
    fun squareMovesAllRowsTogetherBecauseItsStaggerIsZero() {
        val table = sampleDisplacement(SettingsV2Style.SQUARE)
        table.drop(1).forEach { (t, a, b) ->
            assertEquals("SQUARE 的 stagger=0，$t ms 时两行位移应相同", a, b, 0.5f)
        }
        assertTrue("SQUARE 也需要有可见入场位移", table.last().second > 0f)
    }

    @Test
    fun terminalDoesNotSlideItsRowsAtAll() {
        val table = sampleDisplacement(SettingsV2Style.TERMINAL)
        table.forEach { (t, a, b) ->
            assertEquals("TERMINAL 的 enterSlide=0dp，$t ms 时第一行不该有位移", 0f, a, 0.5f)
            assertEquals("TERMINAL 的 enterSlide=0dp，$t ms 时第二行不该有位移", 0f, b, 0.5f)
        }
    }
}
