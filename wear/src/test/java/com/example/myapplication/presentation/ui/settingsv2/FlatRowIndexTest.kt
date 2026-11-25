package com.example.myapplication.presentation.ui.settingsv2

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「从选项子页返回时滚回原行」的索引换算。
 *
 * 四套列表都是「分区标题 + 行」扁平化排列，标题也占一项，所以不能只数行。
 * 真机缺陷（0.0.0.265 实测）：在「安全策略」里选中「自动执行」后，列表被弹回第一屏「连得上」。
 */
class FlatRowIndexTest {

    private fun row(key: String) = SettingsV2Row(key, "标题-$key")

    private val sections = listOf(
        SettingsV2Section("连得上", listOf(row("a"), row("b"))),
        SettingsV2Section("让它能做事", listOf(row("c"), row("d"))),
    )

    @Test
    fun theFirstRowComesRightAfterItsSectionHeader() {
        assertEquals("第 1 项是分区标题，第一行在 1", 1, flatRowIndex(sections, "a"))
        assertEquals(2, flatRowIndex(sections, "b"))
    }

    @Test
    fun headersCountTowardsTheIndex() {
        // [h1, a, b, h2, c, d] → c 在 4
        assertEquals("第二个分区标题占一项，c 应在 4", 4, flatRowIndex(sections, "c"))
        assertEquals(5, flatRowIndex(sections, "d"))
    }

    @Test
    fun unknownKeyReturnsMinusOne() {
        assertEquals("找不到时返回 -1（调用方据此不滚）", -1, flatRowIndex(sections, "not-a-key"))
    }
}
