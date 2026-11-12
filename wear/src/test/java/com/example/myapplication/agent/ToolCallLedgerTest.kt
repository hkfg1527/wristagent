package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 工具调用账本：同名只记一次，换实例仍读得到。 */
class ToolCallLedgerTest {

    @Test
    fun firstCallAddsTheName() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        assertTrue(prefs.calledToolNames.isEmpty())

        prefs.recordToolCall("get_device_status")
        assertEquals(setOf("get_device_status"), prefs.calledToolNames)
    }

    @Test
    fun repeatedCallsDoNotInflateTheLedger() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        repeat(5) { prefs.recordToolCall("web_search") }
        prefs.recordToolCall("web_search")
        assertEquals(1, prefs.calledToolNames.size)
    }

    @Test
    fun blankNamesAreIgnored() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.recordToolCall("")
        prefs.recordToolCall("   ")
        assertTrue(prefs.calledToolNames.isEmpty())
    }

    @Test
    fun ledgerSurvivesANewRepositoryOverTheSameStore() {
        val store = FakeSharedPreferences()
        AgentPreferences(store).recordToolCall("run_shell")
        assertEquals(setOf("run_shell"), AgentPreferences(store).calledToolNames)
    }

    @Test
    fun registeredCountRoundTrips() {
        val store = FakeSharedPreferences()
        AgentPreferences(store).registeredToolCount = 12
        assertEquals(12, AgentPreferences(store).registeredToolCount)
        assertEquals(0, AgentPreferences(FakeSharedPreferences()).registeredToolCount)
    }

    @Test
    fun ratioNeverExceedsOneWhenTheLedgerOutlivesTheRegistry() {
        // 关掉开关后注册数会变小，已调用集合却还在：界面必须夹到 1.0，不能画出越界的环。
        val prefs = AgentPreferences(FakeSharedPreferences())
        listOf("a", "b", "c").forEach { prefs.recordToolCall(it) }
        prefs.registeredToolCount = 2
        val ratio = prefs.calledToolNames.size.toFloat() / prefs.registeredToolCount.coerceAtLeast(1)
        assertTrue(ratio > 1f)
        assertEquals(1f, ratio.coerceIn(0f, 1f), 0.0001f)
    }
}
