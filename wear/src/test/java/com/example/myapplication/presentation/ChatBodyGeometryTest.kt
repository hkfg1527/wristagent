package com.example.myapplication.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 简洁模式的几何契约：只压两件事 —— 正文行高 20→18sp、正文块间距 5→1dp（减 4dp）。
 *
 * 只做定点减法，不做比例缩放：按比例压行高会把中文行间墨距压到 3dp 以下、整段读成一堵灰墙；
 * 字号、颜色与卡片宽度在两种模式下必须完全一致，否则"简洁"就不是排版问题而是走样问题。
 */
class ChatBodyGeometryTest {

    @Test
    fun `常规模式沿用行距令牌_块间距 5dp`() {
        val body = ChatBodyGeometry.of(compactMode = false)
        assertEquals(20f, body.lineHeightSp, 0.001f)
        assertEquals(5f, body.blockSpacingDp, 0.001f)
    }

    @Test
    fun `简洁模式行高 18sp_块间距减 4dp`() {
        val body = ChatBodyGeometry.of(compactMode = true)
        assertEquals(18f, body.lineHeightSp, 0.001f)
        assertEquals(1f, body.blockSpacingDp, 0.001f)
    }

    @Test
    fun `两种模式的差值恒为定点减法_不随输入比例缩放`() {
        val regular = ChatBodyGeometry.of(compactMode = false)
        val compact = ChatBodyGeometry.of(compactMode = true)
        assertEquals(2f, regular.lineHeightSp - compact.lineHeightSp, 0.001f)
        assertEquals(
            ChatBodyGeometry.CompactBlockSpacingReductionDp,
            regular.blockSpacingDp - compact.blockSpacingDp,
            0.001f
        )
        // 常规行高就是设置页的中文行距令牌，不是另立的第二个数值
        assertEquals(20f, ChatBodyGeometry.RegularLineHeightSp, 0.001f)
    }

    @Test
    fun `紧凑行高与块间距都保持为正_正文不会贴死或叠在一起`() {
        val compact = ChatBodyGeometry.of(compactMode = true)
        assertTrue("行高必须为正，否则字形会互相压", compact.lineHeightSp > 0f)
        assertTrue("块间距必须为正，否则两段正文叠成一团", compact.blockSpacingDp > 0f)
    }

    @Test
    fun `紧凑模式每个分量都更小_单调收紧`() {
        val regular = ChatBodyGeometry.of(compactMode = false)
        val compact = ChatBodyGeometry.of(compactMode = true)
        assertTrue(compact.lineHeightSp < regular.lineHeightSp)
        assertTrue(compact.blockSpacingDp < regular.blockSpacingDp)
    }

    @Test
    fun `纯函数_同输入结果稳定且两个分支不相等`() {
        assertEquals(ChatBodyGeometry.of(true), ChatBodyGeometry.of(true))
        assertEquals(ChatBodyGeometry.of(false), ChatBodyGeometry.of(false))
        assertNotEquals(ChatBodyGeometry.of(true), ChatBodyGeometry.of(false))
    }
}
