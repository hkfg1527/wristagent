package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.agent.privilege.ShellResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lux 终端的纯逻辑：转录渲染与条数收口。
 *
 * 这些规则决定"用户看到的到底是命令输出，还是一段长得像输出的东西"，所以逐条钉住：
 * 成功但无输出、失败只有 stderr、输出超预算被截断，三种情况都不能让人误读。
 */
class LuxTerminalPolicyTest {

    @Test
    fun asuccessfulEntryShowsTheCommandAndItsOutput() {
        val text = LuxTerminalPolicy.renderEntry("echo lux", ShellResult(0, "lux\n", ""), 8_000)
        assertTrue(text.startsWith("❯ echo lux"))
        assertTrue(text.contains("lux"))
    }

    @Test
    fun stderrIsUsedWhenStdoutIsEmpty() {
        val text = LuxTerminalPolicy.renderEntry("ls /nope", ShellResult(1, "", "No such file"), 8_000)
        assertTrue("必须显示 stderr", text.contains("No such file"))
    }

    @Test
    fun silentSuccessAndSilentFailureReadDifferently() {
        assertTrue(
            "成功但没输出要说明白",
            LuxTerminalPolicy.renderEntry("true", ShellResult(0, "", ""), 8_000).contains("(无输出)")
        )
        assertTrue(
            "失败但没输出至少要报退出码",
            LuxTerminalPolicy.renderEntry("false", ShellResult(7, "", ""), 8_000).contains("exit 7")
        )
    }

    @Test
    fun overlongOutputIsCutAndTheDropIsReported() {
        val big = "y".repeat(1_000)
        val text = LuxTerminalPolicy.renderEntry("cat big", ShellResult(0, big, ""), 400)
        assertTrue("必须变短", text.length < 1_000)
        assertTrue("必须保留前 400 字", text.contains("y".repeat(400)))
        assertTrue("必须报告截断", text.contains("truncated"))
    }

    @Test
    fun blankInputNeverReachesTheShell() {
        assertEquals(null, LuxTerminalPolicy.parseCommand(""))
        assertEquals(null, LuxTerminalPolicy.parseCommand("   "))
        assertEquals(null, LuxTerminalPolicy.parseCommand("\n\t "))
    }

    @Test
    fun commandsAreTrimmedButNotRewritten() {
        assertEquals("ls -la", LuxTerminalPolicy.parseCommand("  ls -la  "))
        // 引号与管道原样保留：替用户改写命令比不执行更糟
        assertEquals("echo 'a b' | wc -l", LuxTerminalPolicy.parseCommand("echo 'a b' | wc -l"))
    }

    @Test
    fun theTranscriptKeepsOnlyTheMostRecentEntries() {
        val entries = (1..20).map { "entry-" + it }
        val trimmed = LuxTerminalPolicy.trimEntries(entries)
        assertEquals(LuxTerminalPolicy.MAX_ENTRIES, trimmed.size)
        assertEquals("entry-20", trimmed.last())
        assertEquals("entry-" + (20 - LuxTerminalPolicy.MAX_ENTRIES + 1), trimmed.first())
    }
}
