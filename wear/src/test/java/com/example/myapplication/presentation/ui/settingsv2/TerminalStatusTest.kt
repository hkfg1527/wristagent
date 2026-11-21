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

    // 三个分区，各占 2+2=4 项（规则线 + 分区头 + 2 行）：分区1 = 0..3，分区2 = 4..7，分区3 = 8..11
    private val sections = listOf(section("一", 2), section("二", 2), section("三", 2))

    @Test
    fun firstItemBelongsToFirstZone() {
        assertEquals(1, zoneOfAnchor(sections, 0))
    }

    @Test
    fun zoneBoundariesAreExact() {
        assertEquals("分区1 的最后一项", 1, zoneOfAnchor(sections, 3))
        assertEquals("分区2 的第一项", 2, zoneOfAnchor(sections, 4))
        assertEquals("分区2 的最后一项", 2, zoneOfAnchor(sections, 7))
        assertEquals("分区3 的第一项", 3, zoneOfAnchor(sections, 8))
        assertEquals("分区3 的最后一项", 3, zoneOfAnchor(sections, 11))
    }

    @Test
    fun anchorInsideEchoAreaCountsAsLastZone() {
        assertEquals("回显追加在最后，锚点到那儿算最后一个分区", 3, zoneOfAnchor(sections, 12))
        assertEquals(3, zoneOfAnchor(sections, 999))
    }

    @Test
    fun emptySectionsDoNotCrash() {
        assertEquals(1, zoneOfAnchor(emptyList(), 0))
    }
}
