package com.example.myapplication.presentation.ui.settings.stylekit

import org.junit.Assert.assertEquals
import org.junit.Test

/** 终端行前缀：加一次、不重复、非终端档原样。 */
class TerminalTextTest {

    @Test
    fun terminalRowsGetExactlyOnePrefix() {
        assertEquals("> 播报音量", TerminalText.decorate("播报音量", isTerminal = true))
        assertEquals(">> 播报音量", TerminalText.decorate("播报音量", isTerminal = true, active = true))
    }

    @Test
    fun anAlreadyPrefixedTitleIsNotPrefixedTwice() {
        // 子页与根页共用同一套行渲染，一处多拼就会变成 "> > 标题"
        assertEquals("> 标题", TerminalText.decorate("> 标题", isTerminal = true))
        assertEquals(">> 标题", TerminalText.decorate(">> 标题", isTerminal = true, active = true))
        assertEquals("> 标题", TerminalText.decorate("> 标题", isTerminal = true, active = true))
    }

    @Test
    fun otherStylesKeepTheirTextUntouched() {
        assertEquals("播报音量", TerminalText.decorate("播报音量", isTerminal = false))
        assertEquals("播报音量", TerminalText.decorate("播报音量", isTerminal = false, active = true))
        assertEquals("> 已带前缀", TerminalText.decorate("> 已带前缀", isTerminal = false))
    }
}
