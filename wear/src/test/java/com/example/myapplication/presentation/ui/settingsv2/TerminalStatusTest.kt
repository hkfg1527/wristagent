package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.presentation.ui.settingsv2.style.zoneOfAnchor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * R23：底部状态栏的"第几分区"必须跟着滚动走。
 *
 * 每个分区在列表里占 `1（分区头）+ rows.size` 项，所以锚点项到分区的映射是纯算术 —— 这里用
 * 边界值把它钉死（首页、边界、越界到回显区）。
 */
class TerminalStatusTest {

    private fun section(title: String, rows: Int) = SettingsV2Section(
        title = title,
        rows = (1..rows).map { SettingsV2Row(key = "$title-$it", title = "行$it", trailing = RowTrailing.Value("v")) },
    )

    // 三个分区，各占 1+2=3 项：分区1 = 0..2，分区2 = 3..5，分区3 = 6..8
    private val sections = listOf(section("一", 2), section("二", 2), section("三", 2))

    @Test
    fun firstItemBelongsToFirstZone() {
        assertEquals(1, zoneOfAnchor(sections, 0))
    }

    @Test
    fun zoneBoundariesAreExact() {
        assertEquals("分区1 的最后一项", 1, zoneOfAnchor(sections, 2))
        assertEquals("分区2 的第一项", 2, zoneOfAnchor(sections, 3))
        assertEquals("分区2 的最后一项", 2, zoneOfAnchor(sections, 5))
        assertEquals("分区3 的第一项", 3, zoneOfAnchor(sections, 6))
        assertEquals("分区3 的最后一项", 3, zoneOfAnchor(sections, 8))
    }

    @Test
    fun anchorInsideEchoAreaCountsAsLastZone() {
        assertEquals("回显追加在最后，锚点到那儿算最后一个分区", 3, zoneOfAnchor(sections, 9))
        assertEquals(3, zoneOfAnchor(sections, 999))
    }

    @Test
    fun emptySectionsDoNotCrash() {
        assertEquals(1, zoneOfAnchor(emptyList(), 0))
    }
}
