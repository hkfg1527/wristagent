package com.example.myapplication.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文本输入弹窗纯状态机单元测试 (TDD 严格红绿循环)
 * 验证：
 * 1. 打开弹窗绝不清空既有文本
 * 2. 取消关闭后文本保留，重开仍可继续编辑
 * 3. 发送非空文本 -> 关闭 + 清空 + 产出一次性待发指令
 * 4. 发送空白文本 -> 不关闭、不产出指令 (避免空指令打断对话流)
 */
class TextInputDialogStateMachineTest {

    private val closedEmpty = TextInputState()

    @Test
    fun testClosedByDefault() {
        assertFalse(closedEmpty.isOpen)
        assertEquals("", closedEmpty.text)
        assertNull(closedEmpty.lastSent)
    }

    @Test
    fun testOpen_keepsExistingText() {
        val typed = closedEmpty
            .reduce(TextInputEvent.Open)
            .reduce(TextInputEvent.Change("查一下电量"))
            .reduce(TextInputEvent.Cancel)

        assertFalse(typed.isOpen)
        assertEquals("查一下电量", typed.text)

        val reopened = typed.reduce(TextInputEvent.Open)
        assertTrue(reopened.isOpen)
        assertEquals("查一下电量", reopened.text)
    }

    @Test
    fun testCancel_doesNotClearUnsentText() {
        val cancelled = closedEmpty
            .reduce(TextInputEvent.Open)
            .reduce(TextInputEvent.Change("未发送文本"))
            .reduce(TextInputEvent.Cancel)

        assertFalse(cancelled.isOpen)
        assertEquals("未发送文本", cancelled.text)
        assertNull(cancelled.lastSent)
    }

    @Test
    fun testSubmit_withText_closesClearsAndEmitsPrompt() {
        val submitted = closedEmpty
            .reduce(TextInputEvent.Open)
            .reduce(TextInputEvent.Change("今晚吃什么"))
            .reduce(TextInputEvent.Submit)

        assertFalse(submitted.isOpen)
        assertEquals("", submitted.text)
        assertEquals("今晚吃什么", submitted.lastSent)
    }

    @Test
    fun testSubmit_blankText_keepsDialogOpenAndEmitsNothing() {
        val blank = closedEmpty
            .reduce(TextInputEvent.Open)
            .reduce(TextInputEvent.Change("   "))
            .reduce(TextInputEvent.Submit)

        assertTrue(blank.isOpen)
        assertEquals("   ", blank.text)
        assertNull(blank.lastSent)
    }

    @Test
    fun testSubmit_thenCancel_leavesNothingPendingAndKeepsFreshText() {
        val afterFlow = closedEmpty
            .reduce(TextInputEvent.Open)
            .reduce(TextInputEvent.Change("第一条"))
            .reduce(TextInputEvent.Submit)
            .reduce(TextInputEvent.Open)
            .reduce(TextInputEvent.Change("第二条"))

        assertTrue(afterFlow.isOpen)
        assertEquals("第二条", afterFlow.text)
        assertEquals("第一条", afterFlow.lastSent)

        val cancelled = afterFlow.reduce(TextInputEvent.Cancel)
        assertFalse(cancelled.isOpen)
        assertEquals("第二条", cancelled.text)
    }

    @Test
    fun testOpen_whenAlreadyOpen_isIdempotent() {
        val open = closedEmpty.reduce(TextInputEvent.Open).reduce(TextInputEvent.Open)
        assertTrue(open.isOpen)
        assertEquals("", open.text)
    }
}
