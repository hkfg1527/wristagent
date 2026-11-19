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

    @Test
    fun theValueBarHasEightCellsAndRoundsToTheNearestCell() {
        // 0% / 50% / 100% 三个锚点：8 格时分别是 0 / 4 / 8 个实心
        assertEquals("[□□□□□□□□] 0%", TerminalText.valueBar(0f, 0f, 1f))
        assertEquals("[■■■■□□□□] 50%", TerminalText.valueBar(0.5f, 0f, 1f))
        assertEquals("[■■■■■■■■] 100%", TerminalText.valueBar(1f, 0f, 1f))
        // 非 0 起点：2..6 里的 4 就是 50%
        assertEquals("[■■■■□□□□] 50%", TerminalText.valueBar(4f, 2f, 6f))
    }

    @Test
    fun theValueBarClampsOutOfRangeValuesAndSurvivesABadRange() {
        assertEquals("[□□□□□□□□] 0%", TerminalText.valueBar(-5f, 0f, 1f))
        assertEquals("[■■■■■■■■] 100%", TerminalText.valueBar(99f, 0f, 1f))
        // range 配错（下界 >= 上界）不能抛异常，按 0% 处理
        assertEquals("[□□□□□□□□] 0%", TerminalText.valueBar(3f, 5f, 5f))
    }
}
