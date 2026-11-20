package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.V2Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2 三件动效（入场错峰 / 边缘淡出 / 边缘缩放）的**纯函数判据**。
 *
 * 为什么不靠截图：手表上的动效帧根本抓不到（旧实现试过录屏，拿到 9KB 空文件）。
 * 这三个量全是纯函数，钉住性质比看图更可靠；渲染是否真的用上，由各风格的
 * Robolectric 用例（等时钟推进后断言透明度/缩放变化）负责。
 */
class SettingsV2MotionTest {

    private val styles = SettingsV2Style.values().toList()

    @Test
    fun everyStyleHasItsOwnMotionParameters() {
        val motions = styles.map { SettingsV2Motions.of(it) }
        assertEquals(
            "五套动效参数必须两两不同（否则就是一套动效套五个名字）",
            motions.size,
            motions.toSet().size
        )
    }

    @Test
    fun enterDelayIsExactlyIndexTimesStagger() {
        styles.forEach { style ->
            val motion = SettingsV2Motions.of(style)
            listOf(0, 1, 4, 9).forEach { index ->
                assertEquals(
                    "$style 第 $index 行的入场延迟应为 index×stagger",
                    index.toLong() * motion.staggerMs,
                    SettingsV2Motions.enterDelayMs(index, motion)
                )
            }
            // 负 index 要夹到 0，不能返回负延迟
            assertEquals(0L, SettingsV2Motions.enterDelayMs(-3, motion))
        }
    }

    @Test
    fun edgeScaleAndAlphaDecreaseMonotonicallyAndRespectTheFloor() {
        styles.forEach { style ->
            val motion: V2Motion = SettingsV2Motions.of(style)
            var prevScale = SettingsV2Motions.edgeScale(0f, motion)
            var prevAlpha = SettingsV2Motions.edgeAlpha(0f, motion)
            assertEquals("正中必须完全不缩", 1f, prevScale, 0.0001f)
            assertEquals("正中必须完全不淡", 1f, prevAlpha, 0.0001f)

            listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { d ->
                val s = SettingsV2Motions.edgeScale(d, motion)
                val a = SettingsV2Motions.edgeAlpha(d, motion)
                assertTrue("$style 缩放必须随距离单调不增：$prevScale -> $s", s <= prevScale + 0.0001f)
                assertTrue("$style 透明度必须随距离单调不增：$prevAlpha -> $a", a <= prevAlpha + 0.0001f)
                assertTrue("$style 缩放不得低于下限 ${motion.edgeScaleMin}", s >= motion.edgeScaleMin - 0.0001f)
                assertTrue("$style 透明度不得低于下限 ${motion.edgeAlphaMin}", a >= motion.edgeAlphaMin - 0.0001f)
                prevScale = s
                prevAlpha = a
            }
            // 越界输入要夹住：距离 2f 等价于 1f
            assertEquals(SettingsV2Motions.edgeScale(1f, motion), SettingsV2Motions.edgeScale(2f, motion), 0.0001f)
        }
    }

    @Test
    fun terminalDoesNotScaleAtTheEdgeAndSquareBarelyStaggers() {
        val terminal = SettingsV2Motions.of(SettingsV2Style.TERMINAL)
        assertEquals("终端不缩（命令行不该缩放）", 1f, terminal.edgeScaleMin, 0.0001f)
        val square = SettingsV2Motions.of(SettingsV2Style.SQUARE)
        assertEquals("方角不做错峰（它就该是最克制的一套）", 0L, square.staggerMs)
    }
}
