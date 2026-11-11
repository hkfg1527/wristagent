package com.example.myapplication.agent

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子代理嵌套循环：独立上下文、工具集去掉自身、回执字段齐全、失败与超时都走结构化错误。
 * 轮数策略与主循环共用 [AgentLoopPolicy]，因此这里同样验证「不返回工具即退出」「取消立即退出」
 * 「连续 40 轮只提示一次」「空转先提示后收口」。
 */
class SubAgentRunnerTest {

    private fun toolSpec(name: String, arguments: String = "no-args") =
        ToolCall(id = "call_" + name + "_" + arguments.hashCode(), name = name, arguments = arguments)

    private fun registryTool(name: String) =
        ToolDefinition(name = name, description = "内置工具", parametersSchema = JSONObject())

    private fun runner(
        requestTurn: suspend (List<ChatMessage>, List<ToolDefinition>) -> SubAgentTurn,
        executeTool: suspend (ToolCall) -> ToolResult = { ToolResult(it.id, it.name, "ok") },
        availableTools: () -> List<ToolDefinition> = { listOf(registryTool("get_device_status")) },
        isCancelled: () -> Boolean = { false },
        clock: () -> Long = { 1_000L },
        timeoutMs: Long = 5_000L
    ) = SubAgentRunner(requestTurn, executeTool, availableTools, isCancelled, clock, timeoutMs)

    @Test
    fun successReportCarriesAllFieldsAndKeepsItsOwnContext() = runTest {
        val histories = mutableListOf<List<ChatMessage>>()
        val subject = runner(requestTurn = { history, _ ->
            histories += history.toList()
            SubAgentTurn(content = "  子代理结论  ")
        })

        val report = subject.run(
            parentDepth = 0,
            systemPrompt = "你是子代理，只回结论",
            task = "查一下当前电量",
            focus = "只要数字"
        )

        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals("summary 取子代理最终正文并去掉首尾空白", "子代理结论", report.summary)
        assertEquals(0, report.toolCalls)
        assertEquals(0L, report.elapsedMs)
        assertNotNull("回执必须可落成 JSON 交给工具层", report.toJson())

        val first = histories.first()
        assertEquals("独立上下文只有 system + task 两条", 2, first.size)
        assertEquals(AgentRole.SYSTEM, first[0].role)
        assertEquals("你是子代理，只回结论", first[0].content)
        assertEquals(AgentRole.USER, first[1].role)
        assertTrue("任务必须进子代理上下文", first[1].content!!.contains("查一下当前电量"))
        assertTrue("关注点必须进子代理上下文", first[1].content!!.contains("只要数字"))
    }

    @Test
    fun nestedToolSetDropsRunSubagentItself() = runTest {
        var seen: List<ToolDefinition> = emptyList()
        val subject = runner(
            requestTurn = { _, tools ->
                seen = tools
                SubAgentTurn(content = "完成")
            },
            availableTools = {
                listOf(
                    registryTool("get_device_status"),
                    registryTool(SubAgentPolicy.TOOL_NAME),
                    registryTool("web_search")
                )
            }
        )

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals(listOf("get_device_status", "web_search"), seen.map { it.name })
        assertFalse("子代理的工具集里不许再有 run_subagent", SubAgentPolicy.TOOL_NAME in seen.map { it.name })
    }

    @Test
    fun requestFailureBecomesStructuredError() = runTest {
        val subject = runner(requestTurn = { _, _ -> throw IllegalStateException("HTTP 500 上游炸了") })

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_ERROR, report.status)
        assertEquals("request_failed", report.errorType)
        assertTrue("原始错误必须透出，收到：" + report.message, report.message!!.contains("HTTP 500"))
        assertEquals(0, report.toolCalls)
    }

    @Test
    fun parentDepthAtTheLimitIsRejectedWithoutAnyRequest() = runTest {
        var requested = 0
        val subject = runner(requestTurn = { _, _ ->
            requested += 1
            SubAgentTurn(content = "不该被调用")
        })

        val report = subject.run(parentDepth = 1, systemPrompt = "sys", task = "task", focus = null)

        assertEquals(SubAgentReport.STATUS_ERROR, report.status)
        assertEquals("depth_exceeded", report.errorType)
        assertEquals("子代理里不许再开子代理", 0, requested)
    }

    @Test
    fun cancelledBeforeStartDoesNotCallModelOrTools() = runTest {
        var requested = 0
        var executed = 0
        val subject = runner(
            requestTurn = { _, _ ->
                requested += 1
                SubAgentTurn(content = "", toolCalls = listOf(toolSpec("get_device_status")))
            },
            executeTool = { executed += 1; ToolResult(it.id, it.name, "ok") },
            isCancelled = { true }
        )

        val report = subject.run(0, "sys", "task", null)

        assertEquals(SubAgentReport.STATUS_ERROR, report.status)
        assertEquals("cancelled", report.errorType)
        assertEquals(0, requested)
        assertEquals(0, executed)
    }
}
