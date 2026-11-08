package com.example.myapplication.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 用户气泡宽度：必须窄于内容盒，否则"用户靠右/助手靠左"在视觉上不存在。 */
class UserBubbleWidthTest {

    @Test
    fun bubbleIsNarrowerThanTheContentBox() {
        val content = ChatVisualMetrics.BubbleMaxWidthDp
        val bubble = ChatVisualMetrics.userBubbleWidthDp()
        assertTrue("气泡 " + bubble + " 必须窄于内容盒 " + content, bubble < content)
        assertTrue("差得太多就变成一条窄柱", bubble > content * 0.6f)
    }

    @Test
    fun bubbleScalesWithTheContentBox() {
        assertEquals(143.5f, ChatVisualMetrics.userBubbleWidthDp(184f), 0.1f)
        assertEquals(156.0f, ChatVisualMetrics.userBubbleWidthDp(200f), 0.1f)
        assertEquals(78.0f, ChatVisualMetrics.userBubbleWidthDp(100f), 0.1f)
    }
}
