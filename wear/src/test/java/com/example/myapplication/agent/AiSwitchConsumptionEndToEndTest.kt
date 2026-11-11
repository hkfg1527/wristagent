package com.example.myapplication.agent

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.myapplication.agent.contract.AgentMemory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * I2-5 两个设置项的消费点验证：打开开关后到底哪一行行为变了。
 *
 * - providerManagedSearchEnabled：改的是**工具清单**（AgentToolRegistry:169-231），注册表级验证；
 * - memoryEnabled：改的是**最终系统提示词**（AgentDispatcher.initSystemMessage 里的
 *   [MemoryInjection] 闸门 → [AgentSystemPrompt.compose]），用真实 [AgentMemory] 存储验证。
 *
 * 环境约束（本仓库既定）：wear/src/test 的 org.json 是 android.jar 的 mockable 桩
 * （unitTests.isReturnDefaultValues = true），JSONObject 的方法一律返回默认值、toString() 返回 null。
 * 因此本文件只断言纯 Kotlin 可观测量（工具名、工具文案、提示词字符串），不读任何 schema 内容。
 */
class AiSwitchConsumptionEndToEndTest {

    private data class SearchCase(val local: Boolean, val provider: Boolean, val expected: List<String>)

    /** 长记忆仓储只依赖 Context.getSharedPreferences，单测里换成内存版存储即可跑真代码。 */
    private class MemoryContext(private val store: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = store
    }

    private fun registryWithSearch(local: Boolean, provider: Boolean): AgentToolRegistry {
        val preferences = AgentPreferences(FakeSharedPreferences()).apply {
            toolWebSearchEnabled = local
            providerManagedSearchEnabled = provider
        }
        return AgentToolRegistry(ContextWrapper(null), preferences)
    }

    @Test
    fun providerManagedSearchSwitchIsTheOnlySourceOfItsTool() {
        val cases = listOf(
            SearchCase(local = true, provider = false, expected = listOf(SearchToolGate.TOOL_LOCAL_SEARCH)),
            SearchCase(local = false, provider = true, expected = listOf(SearchToolGate.TOOL_PROVIDER_SEARCH)),
            SearchCase(
                local = true,
                provider = true,
                expected = listOf(SearchToolGate.TOOL_LOCAL_SEARCH, SearchToolGate.TOOL_PROVIDER_SEARCH)
            ),
            SearchCase(local = false, provider = false, expected = emptyList())
        )
        for (case in cases) {
            val names = registryWithSearch(case.local, case.provider).getToolDefinitions().map { it.name }
            val searches = names.filter {
                it == SearchToolGate.TOOL_LOCAL_SEARCH || it == SearchToolGate.TOOL_PROVIDER_SEARCH
            }
            assertEquals(
                "检索工具清单错了：本地开关=" + case.local + " 托管开关=" + case.provider,
                case.expected,
                searches
            )
        }
    }

    @Test
    fun providerManagedToolDeclaresThatItStillFallsBackToLocalSearch() {
        val definitions = registryWithSearch(local = false, provider = true).getToolDefinitions()
        val providerTool = definitions.first { it.name == SearchToolGate.TOOL_PROVIDER_SEARCH }
        // AgentToolRegistry:470-474：当前没有 Provider 托管端点，executeProviderWebSearch 走本地检索，
        // 回执里 search_route=local_fallback。工具文案必须把这个事实说清楚，不许让模型以为结果来自 Provider 侧。
        // 只断言纯 Kotlin 的文案：parametersSchema 是 org.json 对象，单测环境里是桩，
        // 读它的内容只会拿到 null（踩过一次，记在这里）。
        assertTrue("文案要写明兜底，收到：" + providerTool.description, providerTool.description.contains("兜底"))
        assertTrue("文案要写明回执标注 search_route", providerTool.description.contains("search_route"))
        assertTrue(
            "托管检索必须自报来源，不许让模型以为是别的链路：" + providerTool.description,
            providerTool.description.contains("Provider")
        )
    }

    @Test
    fun memorySwitchGatesTheRealStoreIntoTheAssembledSystemPrompt() {
        val store = FakeSharedPreferences()
        val memory = AgentMemory(MemoryContext(store))
        // 这就是"缺失的写入方"本该调用的落点：仓储与读回路径本身是通的
        memory.longTermNotes = "主人偏好中文回复；常驻城市杭州"

        val header = memory.buildSystemMemoryHeader()
        val sections = listOf("身份段", "能力段", "纪律段", "边界段")
        val telemetry = "[宿主物理环境与实时感知 Telemetry]"

        val on = AgentSystemPrompt.compose(sections, MemoryInjection.header(true, header), telemetry, "")
        val off = AgentSystemPrompt.compose(sections, MemoryInjection.header(false, header), telemetry, "")

        assertTrue("打开后整段记忆头必须进提示词", on.startsWith("【宿主平台与长记忆核心约束】"))
        assertTrue("打开后长记忆笔记必须进上下文", on.contains("主人偏好中文回复；常驻城市杭州"))
        assertTrue("打开后快捷能力仍在记忆头里", on.contains("手表常驻快捷能力"))

        assertFalse("关闭后整段记忆头不许出现", off.contains("【宿主平台与长记忆核心约束】"))
        assertFalse("关闭后笔记不许漏进上下文", off.contains("主人偏好中文回复；常驻城市杭州"))

        // 关掉记忆只许去掉记忆头：遥测与四段正文一条都不能受牵连
        assertTrue(off.contains(telemetry))
        assertTrue(off.contains("身份段"))
        // compose 装配时会 trim 记忆头，因此差集比对也用 trim 后的同一份
        assertEquals("关掉开关后的提示词 = 打开后的提示词去掉记忆头", on.removePrefix(header.trim()).trim(), off.trim())
    }

    @Test
    fun longTermNotesSurviveTheStoreSoTheGateReadsRealData() {
        val store = FakeSharedPreferences()
        AgentMemory(MemoryContext(store)).longTermNotes = "常驻城市杭州"
        // 新建实例 = 冷启动后重新读盘：闸门读到的永远是落盘内容，不是内存里的临时值
        val reopened = AgentMemory(MemoryContext(store))
        assertEquals("常驻城市杭州", reopened.longTermNotes)
        assertTrue(reopened.buildSystemMemoryHeader().contains("常驻城市杭州"))
    }
}
