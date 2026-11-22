package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.presentation.ui.settingsv2.style.TerminalBannerPrefix
import com.example.myapplication.presentation.ui.settingsv2.style.absoluteItemIndex
import com.example.myapplication.presentation.ui.settingsv2.style.focusItemIndex
import com.example.myapplication.presentation.ui.settingsv2.style.zoneOfAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    // 三个分区，各占 2+2=4 项（规则线 + 分区头 + 2 行）；**index 0 是 banner**：
    // 分区1 = 1..4，分区2 = 5..8，分区3 = 9..12
    private val sections = listOf(section("一", 2), section("二", 2), section("三", 2))

    @Test
    fun firstItemBelongsToFirstZone() {
        assertEquals(1, zoneOfAnchor(sections, 0))
    }

    @Test
    fun zoneBoundariesAreExact() {
        assertEquals("分区1 的最后一项", 1, zoneOfAnchor(sections, 4))
        assertEquals("分区2 的第一项", 2, zoneOfAnchor(sections, 5))
        assertEquals("分区2 的最后一项", 2, zoneOfAnchor(sections, 8))
        assertEquals("分区3 的第一项", 3, zoneOfAnchor(sections, 9))
        assertEquals("分区3 的最后一项", 3, zoneOfAnchor(sections, 12))
    }

    @Test
    fun anchorInsideEchoAreaCountsAsLastZone() {
        assertEquals("回显追加在最后，锚点到那儿算最后一个分区", 3, zoneOfAnchor(sections, 13))
        assertEquals(3, zoneOfAnchor(sections, 999))
    }

    @Test
    fun emptySectionsDoNotCrash() {
        assertEquals(1, zoneOfAnchor(emptyList(), 0))
    }

    /**
     * R27：焦点行（反白）靠"绝对项序号"定位，所以这个映射必须精确 ——
     * 每分区先占 2 项（规则线 + 分区头），再排各行。
     */
    @Test
    fun absoluteItemIndexCountsRulesAndHeaders() {
        val map = absoluteItemIndex(sections)
        assertEquals("分区1 第一行（banner 占 0）", 3, map["一-1"])
        assertEquals("分区1 第二行", 4, map["一-2"])
        assertEquals("分区2 第一行（banner+4 项+规则+头）", 7, map["二-1"])
        assertEquals("分区2 第二行", 8, map["二-2"])
        assertEquals("分区3 第一行", 11, map["三-1"])
    }

    @Test
    fun focusIsTheFirstActionableRowAfterTheAnchor() {
        val mixed = listOf(
            SettingsV2Section(
                title = "一",
                rows = listOf(
                    SettingsV2Row("a1", "只读1", trailing = RowTrailing.Value("v")),
                    SettingsV2Row("a2", "只读2", trailing = RowTrailing.Value("v")),
                    SettingsV2Row("a3", "可点1", trailing = RowTrailing.Chevron),
                ),
            ),
            SettingsV2Section(
                title = "二",
                rows = listOf(SettingsV2Row("b1", "可点2", trailing = RowTrailing.Toggle(false) {})),
            ),
        )
        // 布局：banner0 | 规则1 头2 只读3 只读4 可点5 | 规则6 头7 可点8
        assertEquals("锚点在顶部时，焦点是第一条可操作行", 5, focusItemIndex(mixed, 0))
        assertEquals("锚点越过它之后，焦点移到下一条", 8, focusItemIndex(mixed, 6))
        assertEquals("锚点在最后一条之后 → 没有焦点", null, focusItemIndex(mixed, 9))
    }

    /**
     * R47：banner 必须**短到不换行**。
     *
     * 第一版 `wristagent · TERMINAL · v` + 版本号在真机上被裁成 `wristagent · TERMINAL▨`，
     * 版本号完全看不见 —— 而 dump 里文本是完整的，所以只有肉眼/像素能发现。
     * 12sp 等宽下每字符约 14.4px，内容列约 394px -> 上限约 27 字符，这里卡 26。
     */
    @Test
    fun bannerFitsOnOneLine() {
        val banner = TerminalBannerPrefix + "0.0.0.229"
        assertTrue(
            "banner 必须 <=26 字符（否则真机换行/截断），实际 ${banner.length}：$banner",
            banner.length <= 26
        )
    }
}
