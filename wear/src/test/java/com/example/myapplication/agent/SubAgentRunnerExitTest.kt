package com.example.myapplication.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 嵌套循环的收口条件：取消、无工具、能量提示、空转、超时。 */
class SubAgentRunnerExitTest {

    private fun toolSpec(name: String, arguments: String = "no-args") =
        ToolCall(id = "call_" + name + "_" + arguments.hashCode(), name = name, arguments = arguments)

    private fun runner(
        requestTurn: suspend (List<ChatMessage>, List<ToolDefinition>) -> SubAgentTurn,
        executeTool: suspend (ToolCall) -> ToolResult = { ToolResult(it.id, it.name, "ok") },
        availableTools: () -> List<ToolDefinition> = { emptyList() },
        isCancelled: () -> Boolean = { false },
        clock: () -> Long = { 1_000L },
        timeoutMs: Long = 5_000L
    ) = SubAgentRunner(requestTurn, executeTool, availableTools, isCancelled, clock, timeoutMs)

    @Test
    fun cancelMidLoopStopsBeforeAnyToolRuns() = runTest {
        var requests = 0
        var executed = 0
        val subject = runner(
            requestTurn = { _, _ ->
                requests += 1
                SubAgentTurn(content = "", toolCalls = listOf(toolSpec("get_device_status")))
            },
            executeTool = { executed += 1; ToolResult(it.id, it.name, "ok") },
            isCancelled = { requests > 0 }
        )

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_ERROR, report.status)
        assertEquals("cancelled", report.errorType)
        assertEquals(1, requests)
        assertEquals("取消后工具不许再执行", 0, executed)
    }

    @Test
    fun noToolCallsEndsTheLoopAfterOneTurn() = runTest {
        var requests = 0
        val subject = runner(requestTurn = { _, _ ->
            requests += 1
            SubAgentTurn(content = "直接给结论")
        })

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals("直接给结论", report.summary)
        assertEquals("模型不返回工具就必须收口", 1, requests)
    }

    @Test
    fun energyHintIsInjectedExactlyOnceOverAFortyFiveTurnRun() = runTest {
        var turn = 0
        val histories = mutableListOf<List<ChatMessage>>()
        val subject = runner(requestTurn = { history, _ ->
            histories += history.toList()
            turn += 1
            if (turn <= 45) {
                SubAgentTurn(content = "", toolCalls = listOf(toolSpec("tool_" + turn)))
            } else {
                SubAgentTurn(content = "收尾结论")
            }
        })

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals("收尾结论", report.summary)
        assertEquals(45, report.toolCalls)
        val hints = histories.last().filter { it.content?.contains("已连续执行") == true }
        assertEquals("能量提示只允许出现一次", 1, hints.size)
        assertTrue("提示必须带上触发的轮数，收到：" + hints.single().content, hints.single().content!!.contains("40"))
    }

    @Test
    fun repeatedIdenticalCallStopsTheNestedLoop() = runTest {
        var requests = 0
        var executed = 0
        val subject = runner(
            requestTurn = { _, _ ->
                requests += 1
                SubAgentTurn(content = "", toolCalls = listOf(toolSpec("get_device_status", "x=1")))
            },
            executeTool = { executed += 1; ToolResult(it.id, it.name, "ok") }
        )

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_ERROR, report.status)
        assertEquals("idle_spin", report.errorType)
        assertEquals("同一份调用第 5 次即收口", 5, requests)
        assertEquals(5, executed)
    }

    @Test
    fun toolExecutionFailureBecomesToolReplyAndLoopContinues() = runTest {
        var executed = 0
        val subject = runner(
            requestTurn = { history, _ ->
                if (history.none { it.role == AgentRole.TOOL }) {
                    SubAgentTurn(content = "", toolCalls = listOf(toolSpec("run_shell", "command=id")))
                } else {
                    SubAgentTurn(content = "工具挂了，我改用结论")
                }
            },
            executeTool = { executed += 1; throw IllegalStateException("特权层不可用") }
        )

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals("工具挂了，我改用结论", report.summary)
        assertEquals(1, executed)
        assertEquals(1, report.toolCalls)
    }

    @Test
    fun timeoutReturnsStructuredError() = runTest {
        val subject = runner(
            requestTurn = { _, _ ->
                delay(30_000)
                SubAgentTurn(content = "太迟了")
            },
            timeoutMs = 100L
        )

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_ERROR, report.status)
        assertEquals("timeout", report.errorType)
    }
}
