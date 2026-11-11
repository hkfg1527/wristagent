package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 线的数量契约：整页只有一种线，每个子项组一根 —— "线数 == 子项组数"。
 *
 * 判据直接用渲染路径里的 [SettingsRowComposition.isSubItemGroupStart]，
 * 实现改坏这里就会红，而不是测试里另写一份 if 自说自话。
 */
class SettingsRowCompositionTest {

    @Test
    fun groupStartsAreExactlyTheFirstSubItemOfEachRun() {
        assertTrue(SettingsRowComposition.isSubItemGroupStart(previousIsSubItem = null, isSubItem = true))
        assertTrue(SettingsRowComposition.isSubItemGroupStart(previousIsSubItem = false, isSubItem = true))
        // 组内第二行起不再画线
        assertFalse(SettingsRowComposition.isSubItemGroupStart(previousIsSubItem = true, isSubItem = true))
        // 普通行永远不是组起点
        assertFalse(SettingsRowComposition.isSubItemGroupStart(previousIsSubItem = null, isSubItem = false))
        assertFalse(SettingsRowComposition.isSubItemGroupStart(previousIsSubItem = true, isSubItem = false))
    }

    @Test
    fun dividerCountEqualsGroupCountForEveryShapeAPageCanHave() {
        assertEquals(0, SettingsRowComposition.subItemGroupCount(emptyList()))
        assertEquals(0, SettingsRowComposition.subItemGroupCount(listOf(false, false, false)))
        assertEquals(1, SettingsRowComposition.subItemGroupCount(listOf(true)))
        // 一个组：组内三行只画一根
        assertEquals(1, SettingsRowComposition.subItemGroupCount(listOf(false, true, true, true, false)))
        // 两个被普通行隔开的组：两根
        assertEquals(2, SettingsRowComposition.subItemGroupCount(listOf(false, true, true, false, true)))
        assertEquals(2, SettingsRowComposition.subItemGroupCount(listOf(true, false, true)))
        assertEquals(3, SettingsRowComposition.subItemGroupCount(listOf(true, true, false, true, false, true, true)))
    }

    @Test
    fun theRenderingLoopDrawsOneDividerPerGroup() {
        val shapes = listOf(
            listOf(false, true, true, false, true),
            listOf(true),
            listOf(false, false),
            listOf(true, false, true, true)
        )
        shapes.forEach { shape ->
            var drawn = 0
            shape.forEachIndexed { index, isSubItem ->
                if (SettingsRowComposition.isSubItemGroupStart(shape.getOrNull(index - 1), isSubItem)) drawn++
            }
            assertEquals("线数必须等于子项组数：$shape", SettingsRowComposition.subItemGroupCount(shape), drawn)
        }
    }
}
