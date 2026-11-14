package com.example.myapplication.agent

import com.example.myapplication.agent.contract.MemoryFactBook
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 第 18–20 个真实设置：记忆单条长度上限、控制台锁定窗口、子代理层数。
 *
 * 三条都属于"限制类"旋钮 —— 它们的价值全在边界上，所以测试逐个钉住夹取与真实接线。
 */
class LimiterSettingsTest {

    @Test
    fun theMemoryLengthLimitIsClampedToFortyThroughTwoForty() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals(MemoryFactBook.MAX_FACT_LENGTH, p.memoryMaxFactLength)
        p.memoryMaxFactLength = 1
        assertEquals(MemoryFactBook.MIN_FACT_LENGTH, p.memoryMaxFactLength)
        p.memoryMaxFactLength = 9_999
        assertEquals(MemoryFactBook.MAX_FACT_LENGTH_LIMIT, p.memoryMaxFactLength)
    }

    @Test
    fun theMemoryWriterHonoursTheConfiguredLimit() {
        val long = "x".repeat(100)
        // 默认 120：100 字能进
        assertEquals(MemoryFactBook.Status.ADDED, MemoryFactBook.add("", long, 10).status)
        // 调到 60：同一段就被拒
        assertEquals(MemoryFactBook.Status.INVALID, MemoryFactBook.add("", long, 10, 60).status)
        // 越界值在写入判定里也会被夹（99 夹到 40）
        assertEquals(MemoryFactBook.Status.INVALID, MemoryFactBook.add("", long, 10, 99).status)
    }

    @Test
    fun theRegistryPassesTheMemoryLimitFromPreferences() {
        val registry = readProjectFile("src/main/java/com/example/myapplication/agent/AgentToolRegistry.kt")
        assertTrue(
            "remember_fact 必须按偏好写入",
            registry.contains("preferences?.memoryMaxFactLength ?: MemoryFactBook.MAX_FACT_LENGTH")
        )
    }

    @Test
    fun theConsoleLockoutWindowIsClampedToOneThroughSixtyMinutes() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals(AgentPreferences.DEFAULT_CONSOLE_LOCKOUT_MINUTES, p.consoleLockoutMinutes)
        p.consoleLockoutMinutes = 0
        assertEquals(AgentPreferences.MIN_CONSOLE_LOCKOUT_MINUTES, p.consoleLockoutMinutes)
        p.consoleLockoutMinutes = 999
        assertEquals(AgentPreferences.MAX_CONSOLE_LOCKOUT_MINUTES, p.consoleLockoutMinutes)
    }

    @Test
    fun theSubAgentDepthCanBeTurnedOff() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals(SubAgentPolicy.MAX_DEPTH, p.subagentMaxDepth)
        p.subagentMaxDepth = 0
        assertEquals(0, p.subagentMaxDepth)
        p.subagentMaxDepth = 9
        assertEquals(SubAgentPolicy.MAX_DEPTH, p.subagentMaxDepth)
    }

    @Test
    fun depthZeroMeansNoSubAgentAtAll() {
        // 主循环在 depth 0：允许开一层时 true，关闭时 false
        assertTrue(SubAgentPolicy.depthAllowed(0, 1))
        assertFalse(SubAgentPolicy.depthAllowed(0, 0))
        // 层内（depth 1）永远不许再开
        assertFalse(SubAgentPolicy.depthAllowed(1, 1))
    }

    @Test
    fun theRunnerAndDispatcherShareTheDepthPreference() {
        val runner = readProjectFile("src/main/java/com/example/myapplication/agent/SubAgentRunner.kt")
        assertTrue("runner 必须用自己的层数判据", runner.contains("depthAllowed(parentDepth, subagentMaxDepth)"))
        val dispatcher = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
        assertTrue("主循环必须把偏好交给 runner", dispatcher.contains("subagentMaxDepth = preferences.subagentMaxDepth"))
    }
}
