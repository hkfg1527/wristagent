package com.example.myapplication.agent

import android.content.ContextWrapper
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.readProjectFile
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I2-4 子代理模式的端到端验证。
 *
 * 环境约束（本仓库既定，别绕）：wear/src/test 里的 org.json 是 android.jar 的 mockable 桩
 * （build.gradle.kts 的 unitTests.isReturnDefaultValues = true），方法一律返回默认值 ——
 * JSONObject.toString() 返回 null。因此 **任何** registry.executeTool(...) 都会在生产代码的
 * .toString() 上撞空值断言，本文件一律不碰它。取证分三层：
 *
 * 1. 清单可见性：registry.getToolDefinitions().map { it.name }（不经过 org.json）；
 * 2. 子代理真实行为：直接构造真实 [SubAgentRunner]，断言独立上下文 / 工具集裁剪 / 结论回填 / 深度拒绝；
 * 3. 注册表与调度器里那几行"接线"（路由、执行前再核策略、回填主链路）：单测环境不可达，
 *    改由读源码的结构守护钉住，并在此说明理由 —— 这不是放水，是换一种可执行的证据。
 */
class SubAgentDispatchEndToEndTest {

    private fun preferencesFor(mode: SecurityMode, subAgent: Boolean): AgentPreferences =
        AgentPreferences(FakeSharedPreferences()).apply {
            securityMode = mode
            subAgentEnabled = subAgent
        }

    /** 工具清单的可观测量：名字表。清单构造只 new JSONObject，不调用它的方法。 */
    private fun catalog(mode: SecurityMode, enabled: Boolean): List<String> =
        AgentToolRegistry(ContextWrapper(null), preferencesFor(mode, enabled))
            .getToolDefinitions()
            .map { it.name }

    private fun toolDefinition(name: String) =
        ToolDefinition(name = name, description = "广告给模型的清单项", parametersSchema = JSONObject())

    @Test
    fun catalogOnlyOffersRunSubagentWhenTheSwitchIsEffective() {
        for (mode in listOf(SecurityMode.NOVICE, SecurityMode.STANDARD, SecurityMode.EXPERT)) {
            for (enabled in listOf(false, true)) {
                val names = catalog(mode, enabled)
                assertTrue(
                    "清单本身不能是空的，否则这条断言等于没测：" + mode,
                    "get_device_status" in names
                )
                assertEquals(
                    "清单结论错了：$mode 开关=$enabled",
                    SubAgentPolicy.isEffective(mode, enabled),
                    SubAgentPolicy.TOOL_NAME in names
                )
            }
        }
    }

    @Test
    fun expertSwitchAddsExactlyTheSubAgentToolAndNothingElse() {
        val off = catalog(SecurityMode.EXPERT, enabled = false).toSet()
        val on = catalog(SecurityMode.EXPERT, enabled = true).toSet()

        assertEquals("打开开关只能多出 run_subagent", setOf(SubAgentPolicy.TOOL_NAME), on - off)
        assertEquals("打开开关不许挤掉任何既有工具", emptySet<String>(), off - on)
    }

    @Test
    fun runnerKeepsItsOwnContextAndReportsTheConclusionBack() = runTest {
        val histories = mutableListOf<List<ChatMessage>>()
        val toolSets = mutableListOf<List<String>>()
        val executed = mutableListOf<String>()
        // 广告给子代理的清单里故意带上 run_subagent：裁剪必须真的发生在执行前
        val advertised = listOf(
            toolDefinition("get_device_status"),
            toolDefinition(SubAgentPolicy.TOOL_NAME),
            toolDefinition("web_search")
        )
        val runner = SubAgentRunner(
            requestTurn = { history, tools ->
                histories += history.toList()
                toolSets += tools.map { it.name }
                if (histories.size == 1) {
                    SubAgentTurn(
                        content = "",
                        toolCalls = listOf(ToolCall(id = "sub-1", name = "get_device_status", arguments = "no-args"))
                    )
                } else {
                    SubAgentTurn(content = "  设备已运行 3 天，一切正常。  ")
                }
            },
            executeTool = { call ->
                executed += call.name
                ToolResult(toolCallId = call.id, toolName = call.name, output = "battery_level_percent=88")
            },
            availableTools = { advertised },
            clock = { 1_000L }
        )

        val report = runner.run(
            parentDepth = 0,
            systemPrompt = "你是子代理，只回结论",
            task = "看这台设备跑了多久",
            focus = "只要结论和数字"
        )

        // 结论回填：主循环拿到的是 SubAgentReport（status / summary / toolCalls），失败才走 error
        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals("summary 取子代理最终正文并去掉首尾空白", "设备已运行 3 天，一切正常。", report.summary)
        assertEquals("子代理确实执行了一次工具", 1, report.toolCalls)

        // 独立上下文：第一份 history 只有 system + task，主对话一条都不进来
        val first = histories.first()
        assertEquals("子代理上下文只有 system + task 两条", 2, first.size)
        assertEquals(AgentRole.SYSTEM, first[0].role)
        assertEquals("你是子代理，只回结论", first[0].content)
        assertEquals(AgentRole.USER, first[1].role)
        assertTrue(first[1].content!!.contains("看这台设备跑了多久"))
        assertTrue("focus 必须进子代理上下文", first[1].content!!.contains("只要结论和数字"))

        // 工具集裁剪：清单里没有 run_subagent，其余原样保留
        assertEquals(listOf("get_device_status", "web_search"), toolSets.first())
        assertEquals(listOf("get_device_status"), executed)

        // 工具回执回到子代理自己的下一轮请求（而不是主对话）
        val receipt = histories.last().first { it.role == AgentRole.TOOL }
        assertEquals("get_device_status", receipt.name)
        assertEquals("battery_level_percent=88", receipt.content)
    }

    @Test
    fun runnerRefusesNestedDispatchWithoutReachingTheExecutor() = runTest {
        val histories = mutableListOf<List<ChatMessage>>()
        val executed = mutableListOf<String>()
        val runner = SubAgentRunner(
            requestTurn = { history, _ ->
                histories += history.toList()
                if (history.none { it.role == AgentRole.TOOL }) {
                    // 模型自造清单外的工具名：清单只是告知，执行侧必须自己兜住深度契约
                    SubAgentTurn(
                        content = "",
                        toolCalls = listOf(ToolCall(id = "nested-1", name = SubAgentPolicy.TOOL_NAME, arguments = "{}"))
                    )
                } else {
                    SubAgentTurn(content = "我自己查完了")
                }
            },
            executeTool = { call ->
                executed += call.name
                ToolResult(toolCallId = call.id, toolName = call.name, output = "不该被执行")
            },
            availableTools = { listOf(toolDefinition(SubAgentPolicy.TOOL_NAME)) }
        )

        val report = runner.run(0, "sys", "看看设备状态", null)

        assertEquals(SubAgentReport.STATUS_SUCCESS, report.status)
        assertEquals("我自己查完了", report.summary)
        assertTrue("嵌套派发一次都不许进执行层：" + executed, executed.isEmpty())
        assertEquals("被拒的调用不计入已执行工具数", 0, report.toolCalls)

        val receipt = histories.last().first { it.role == AgentRole.TOOL }
        assertEquals("回执必须挂回原调用", SubAgentPolicy.TOOL_NAME, receipt.name)
        assertTrue(
            "回执要说明拒绝理由（org.json 退化时降级成纯文本，同样带错误码）：" + receipt.content,
            receipt.content!!.contains(SubAgentReport.ERROR_DEPTH)
        )
    }

    /**
     * 结构守护：单测环境里 registry.executeTool 不可用（org.json 桩），所以"派发路径真的连着"
     * 这件事由源码证据钉住。任何一行被有意改动时，这条测试会失败并指出该重新确认的契约。
     */
    @Test
    fun dispatchWiringStaysConnectedFromCatalogToMainLoop() {
        val registry = readProjectFile("src/main/java/com/example/myapplication/agent/AgentToolRegistry.kt")
        assertTrue(
            "清单必须由 SubAgentPolicy 单一真源裁剪",
            registry.contains("SubAgentPolicy.toolDefinitions(")
        )
        assertTrue(
            "执行侧必须按保留名路由到 executeRunSubAgent",
            registry.contains("SubAgentPolicy.TOOL_NAME -> executeRunSubAgent(args)")
        )
        val executeBody = registry
            .substringAfter("private suspend fun executeRunSubAgent")
            .substringBefore("\n    private suspend fun executeSelfDefinedTool")
        assertTrue(
            "执行前必须再核一次策略，会话中途降级不能被旧清单绕过",
            executeBody.contains("SubAgentPolicy.isEffective(mode, preferences?.subAgentEnabled ?: false)")
        )
        assertTrue("策略失效要回结构化错误而不是硬跑", executeBody.contains("subagent_disabled"))
        assertTrue("执行层未接线时也要回结构化错误", executeBody.contains("subagent_unavailable"))

        val dispatcher = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
        assertTrue(
            "调度器必须把注册表清单交给子代理（否则子代理没有工具可用）",
            dispatcher.contains("availableTools = { toolRegistry.getToolDefinitions() }")
        )
        assertTrue(
            "调度器必须把 run_subagent 的执行体接到真实 SubAgentRunner 上",
            dispatcher.contains("toolRegistry.bindSubAgentRunner {")
        )
        assertTrue(
            "回执必须作为 TOOL 消息回填主链路：content = result.output",
            dispatcher.contains("content = result.output,")
        )
    }
}
