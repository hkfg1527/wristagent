package com.example.myapplication.presentation.ui.settings.stylekit

import com.example.myapplication.appearance.SettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 十套风格的动效参数表守卫。
 *
 * 为什么要有：入场/退场/磁滑/边缘缩放这四类参数以前散在渲染代码里，有的写死、有的恒等于 1，
 * 结果是"同一套风格在不同页面表现不一致"，而没有任何测试能发现。
 * 现在参数集中到 [StyleMotions]，这里钉住三件事：
 * 1. 十套风格**每套都给值**（缺一套编译期就过不了，这里再钉一份可读清单）；
 * 2. 参数**互不相同**（不许复制粘贴同一份）；
 * 3. 语义约束：终端档不吸附（命令行连续滚动）、官方档不加额外位移、贴边缩放不许恒等于 1
 *    （恒等于 1 就是 0.0.0.113 审查点名的"空操作"）。
 */
class StyleMotionTest {

    private val all = SettingsStyle.values().toList()

    @Test
    fun everyStyleHasItsOwnMotionParameters() {
        assertEquals("十套风格都必须有动效参数", 10, all.size)
        val motions = all.map { StyleMotions.of(it) }
        assertEquals("十套动效参数不许重复", motions.size, motions.toSet().size)
    }

    @Test
    fun terminalKeepsContinuousScrollWhileOthersSnap() {
        // 终端是命令行语义：连续滚动，不吸附到行
        assertTrue("终端档必须不吸附", !StyleMotions.of(SettingsStyle.TERMINAL).snapToRows)
        for (style in all - SettingsStyle.TERMINAL) {
            assertTrue("$style 必须吸附到最近一行", StyleMotions.of(style).snapToRows)
        }
    }

    @Test
    fun officialStylesAddNoExtraSlide() {
        for (style in listOf(SettingsStyle.M3_LIST, SettingsStyle.M3_TRANSFORM)) {
            val motion = StyleMotions.of(style)
            assertEquals("$style 不加额外入场位移（交给官方节奏）", 0f, motion.enterSlideDp, 0.001f)
            assertEquals("$style 不加额外退场位移", 0f, motion.exitSlideDp, 0.001f)
        }
    }

    @Test
    fun edgeScaleIsActuallySomethingForMostStyles() {
        // 1f = 不缩放。除了明确声明"弧线本身在动、不再缩放"的 ARC 与命令行语义的 TERMINAL，
        // 其余八套都必须真的缩放（0.0.0.113 审查发现当时的 MinScale 恒为 1，等于空操作）。
        val noZoom = all.filter { StyleMotions.of(it).edgeScaleMin >= 1f }.toSet()
        assertEquals(
            "只有弧列与终端允许不缩放",
            setOf(SettingsStyle.ARC, SettingsStyle.TERMINAL),
            noZoom
        )
    }
}
