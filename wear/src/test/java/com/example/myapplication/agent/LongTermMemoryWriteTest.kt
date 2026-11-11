package com.example.myapplication.agent

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.example.myapplication.agent.contract.AgentMemory
import com.example.myapplication.agent.contract.MemoryFactBook
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 长期记忆写入方（remember_fact）的验证。
 *
 * ⚠ 断言全部落在**不经过 org.json** 的层上：wear/build.gradle.kts 打开了
 * `unitTests.isReturnDefaultValues = true`，测试依赖只有 junit 与 coroutines-test，
 * 于是 org.json（来自 android.jar）在 JVM 单测里是 AGP 的 mockable 桩：JSONObject.put 返回 null、
 * optString 返回 null、JSONObject(String) 造出空对象。任何"读回 JSON 回执"的断言要么 NPE，
 * 要么退化成空断言 —— 这也是 SelfDefinedToolCodec / SessionCodec 手写编解码的原因。
 *
 * 因此分两层验证：
 * 1. 行为层（真代码直跑）：MemoryFactBook 的归一化/去重/上限、AgentMemory 的落盘与读回、
 *    ShellPolicy 的三档审批判定、注册表的工具清单可见性、SessionSystemPrompt 的系统头重建；
 * 2. 接线层（源码守卫）：executeTool 的分派分支、bindMemory 注入、每轮请求前的
 *    syncSystemMessageWithMemory()、审批页带上记忆原文 —— 这一跳必然经过 ToolCall.parseArgs()
 *    → org.json，JVM 单测里不可执行，只能用源码守卫钉住"没被绕过"。
 */
class LongTermMemoryWriteTest {

    private class MemoryContext(private val store: SharedPreferences) : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = store
    }

    private fun memory(
        store: FakeSharedPreferences = FakeSharedPreferences()
    ): AgentMemory = AgentMemory(MemoryContext(store))

    private fun preferences(memorySwitch: Boolean = true): AgentPreferences =
        AgentPreferences(FakeSharedPreferences()).apply { memoryEnabled = memorySwitch }

    // ==================== 条目规则（纯 Kotlin，真代码直跑） ====================

    @Test
    fun normalizeCollapsesWhitespaceSoPaddingCannotCreateADuplicate() {
        assertEquals("每天 23:00 睡觉", MemoryFactBook.normalize("  每天   23:00\t睡觉  "))

        val first = MemoryFactBook.add("", "每天 23:00 睡觉", 10)
        assertEquals(MemoryFactBook.Status.ADDED, first.status)

        val same = MemoryFactBook.add(first.notes, "  每天  23:00   睡觉 ", 10)
        assertEquals(MemoryFactBook.Status.DUPLICATE, same.status)
        assertEquals("重复写入不许改变条目数", 1, same.count)
        assertEquals("重复写入不许改动清单", first.notes, same.notes)
    }

    @Test
    fun nearDuplicateFactsAreRejectedByComparingLettersAndDigitsOnly() {
        // 只差标点与大小写：近似重复必须并成一条，否则记忆库会被同一条偏好的各种写法挤满
        val first = MemoryFactBook.add("", "DeepSeek 用 high 档", 10)
        val near = MemoryFactBook.add(first.notes, "deepseek 用 HIGH 档。", 10)
        assertEquals(MemoryFactBook.Status.DUPLICATE, near.status)
        assertEquals(1, near.count)

        val different = MemoryFactBook.add(first.notes, "DeepSeek 用 low 档", 10)
        assertEquals("改一个词就是另一条，不许被误判成重复", MemoryFactBook.Status.ADDED, different.status)
        assertEquals(2, different.count)
    }

    @Test
    fun blankAndOverlongFactsAreRejectedWithoutTouchingTheList() {
        val seeded = MemoryFactBook.add("", "常驻城市杭州", 10)
        assertEquals(MemoryFactBook.Status.INVALID, MemoryFactBook.add(seeded.notes, "   ", 10).status)
        val tooLong = "长".repeat(MemoryFactBook.MAX_FACT_LENGTH + 1)
        val overlong = MemoryFactBook.add(seeded.notes, tooLong, 10)
        assertEquals(MemoryFactBook.Status.INVALID, overlong.status)
        assertEquals("被拒的写入不许改动清单", seeded.notes, overlong.notes)
    }

    @Test
    fun theCapHoldsAndTheLastRejectedWriteLeavesTheListUnchanged() {
        var notes = ""
        repeat(AgentPreferences.MAX_MEMORY_FACTS) { index ->
            val result = MemoryFactBook.add(notes, "第 " + index + " 条偏好", AgentPreferences.MAX_MEMORY_FACTS)
            assertEquals(
                "前 50 条必须都能写进去（第 " + index + " 条失败）",
                MemoryFactBook.Status.ADDED,
                result.status
            )
            notes = result.notes
        }
        assertEquals(AgentPreferences.MAX_MEMORY_FACTS, MemoryFactBook.facts(notes).size)

        val refused = MemoryFactBook.add(notes, "第 51 条偏好", AgentPreferences.MAX_MEMORY_FACTS)
        assertEquals(MemoryFactBook.Status.FULL, refused.status)
        assertEquals("超限后条目数不许增长", AgentPreferences.MAX_MEMORY_FACTS, refused.count)
        assertEquals("超限后清单一个字都不许变", notes, refused.notes)
    }

    @Test
    fun factsAreOneEntryPerLineAndASeparatorInsideAFactStaysInThatFact() {
        assertEquals(listOf("先看日志", "再看电池"), MemoryFactBook.facts("先看日志\n再看电池"))
        assertEquals("空行不算条目", listOf("先看日志", "再看电池"), MemoryFactBook.facts("\n先看日志\n\n再看电池\n"))
        // 分号是条目内部的标点：写一条就是一跳，不许被读成两条（否则条数与注入内容自相矛盾）
        assertEquals(listOf("先看日志；再看电池"), MemoryFactBook.facts("先看日志；再看电池"))
    }

    // ==================== 落盘 / 读回（真 AgentMemory + 内存版存储） ====================

    @Test
    fun rememberFactPersistsOnlyNewFactsAndSurvivesAColdStart() {
        val store = FakeSharedPreferences()
        val memory = memory(store)

        val added = memory.rememberFact("  常驻城市杭州  ", AgentPreferences.MAX_MEMORY_FACTS)
        assertEquals(MemoryFactBook.Status.ADDED, added.status)
        assertEquals(1, added.count)
        assertEquals("归一化后落盘", "常驻城市杭州", memory.longTermNotes)
        assertTrue("记忆头必须带上这条", memory.buildSystemMemoryHeader().contains("常驻城市杭州"))

        // 重复写入：状态是 duplicate，盘上内容一个字都不变
        val duplicate = memory.rememberFact("常驻城市杭州", AgentPreferences.MAX_MEMORY_FACTS)
        assertEquals(MemoryFactBook.Status.DUPLICATE, duplicate.status)
        assertEquals("常驻城市杭州", memory.longTermNotes)

        // 冷启动后重新读盘：读到的是落盘真值，而不是内存里的临时值
        assertEquals(listOf("常驻城市杭州"), memory(store).facts())
    }

    @Test
    fun rememberFactRefusesPastTheCapAndTheStoreDoesNotGrow() {
        val store = FakeSharedPreferences()
        val memory = memory(store)
        repeat(AgentPreferences.MAX_MEMORY_FACTS) { index ->
            memory.rememberFact("第 " + index + " 条偏好", AgentPreferences.MAX_MEMORY_FACTS)
        }
        val full = memory.longTermNotes
        assertEquals(AgentPreferences.MAX_MEMORY_FACTS, memory.facts().size)

        val refused = memory.rememberFact("第 51 条偏好", AgentPreferences.MAX_MEMORY_FACTS)
        assertEquals(MemoryFactBook.Status.FULL, refused.status)
        assertEquals("超限后盘上内容一个字都不许变", full, memory.longTermNotes)
        assertEquals(AgentPreferences.MAX_MEMORY_FACTS, memory.facts().size)
    }

    @Test
    fun clearingTheStoreRemovesEveryFact() {
        val memory = memory()
        memory.rememberFact("常驻城市杭州", AgentPreferences.MAX_MEMORY_FACTS)
        memory.rememberFact("讨厌客套话", AgentPreferences.MAX_MEMORY_FACTS)
        assertEquals(2, memory.facts().size)

        // AgentDispatcher.clearLongTermMemory 的落点
        memory.longTermNotes = ""
        assertEquals(emptyList<String>(), memory.facts())
        assertFalse(memory.buildSystemMemoryHeader().contains("持久用户偏好"))
    }

    // ==================== 权限分级（纯函数，真代码直跑） ====================

    @Test
    fun memoryWriteSitsInTheForcedApprovalTierAndNotInTheShellOrHeuristicTier() {
        val selfDefined = setOf("disk_usage")

        // 第一档：特权 shell（run_shell / 自写工具）。remember_fact 不执行任何命令，不落这一档。
        assertTrue(ShellPolicy.isPrivilegedTool(ShellPolicy.TOOL_RUN_SHELL, selfDefined))
        assertFalse(ShellPolicy.isPrivilegedTool(ShellPolicy.TOOL_REMEMBER_FACT, selfDefined))

        // 第二档：强制审批（define_tool / remember_fact）。它持久化数据，写进去的内容之后每一轮
        // 都进系统提示词，所以和"扩大能力面"同档：任何安全模式都要问人。
        assertTrue(ShellPolicy.isMemoryTool(ShellPolicy.TOOL_REMEMBER_FACT))
        assertFalse("普通工具不是记忆工具", ShellPolicy.isMemoryTool(ShellPolicy.TOOL_RUN_SHELL))
        for (mode in SecurityMode.values()) {
            assertTrue(
                mode.name + " 模式下也必须强制审批",
                ShellPolicy.requiresApprovalAlways(ShellPolicy.TOOL_REMEMBER_FACT, selfDefined)
            )
        }

        // 第三档：通用分级闸门。低风险元数据类工具照旧自动放行 —— 证明前两档不是"把所有工具都拉成必批"。
        assertFalse(ShellPolicy.requiresApprovalAlways("get_device_status", selfDefined))
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ApprovalGate.decide(
                RiskAssessment(aiRiskLevel = RiskLevel.LOW),
                SecurityMode.STANDARD,
                ToolApprovalPolicy.RISKY
            )
        )
        // 通用闸门在"从不确认"策略下直接放行；remember_fact 因此必须留在强制档，
        // 否则一条伪造的长期偏好可以在用户以为"从不确认"时被静默写进上下文。
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ApprovalGate.decide(
                RiskAssessment(aiRiskLevel = RiskLevel.LOW),
                SecurityMode.STANDARD,
                ToolApprovalPolicy.NEVER
            )
        )
    }

    @Test
    fun theRegistryPutsRememberFactInTheForcedTierAndNotInTheShellTier() {
        val registry = AgentToolRegistry(ContextWrapper(null), preferences())
        assertTrue("注册表必须把它认成强制审批档", registry.requiresApprovalAlways(ShellPolicy.TOOL_REMEMBER_FACT))
        assertFalse("但它不是特权 shell 工具", registry.isPrivilegedTool(ShellPolicy.TOOL_REMEMBER_FACT))
        assertNull(
            "它不是 shell 命令，因此不进沙箱分档、也没有'记住此命令'这条旁路",
            registry.shellCommandOf(ToolCall(id = "m", name = ShellPolicy.TOOL_REMEMBER_FACT, arguments = "{}"))
        )
    }

    @Test
    fun memorySwitchOffRemovesTheToolFromTheCatalog() {
        assertFalse(
            "总开关关掉时工具清单里不该有它",
            AgentToolRegistry(ContextWrapper(null), preferences(memorySwitch = false))
                .getToolDefinitions().any { it.name == ShellPolicy.TOOL_REMEMBER_FACT }
        )
        assertTrue(
            "总开关打开时它必须在清单里",
            AgentToolRegistry(ContextWrapper(null), preferences())
                .getToolDefinitions().any { it.name == ShellPolicy.TOOL_REMEMBER_FACT }
        )
    }

    // ==================== 生效时机：同一会话的下一轮（纯数据，不涉 JSON） ====================

    /**
     * 生产环境的同一套装配：修订键取 memoryEnabled + 长期笔记，装配走 AgentSystemPrompt.compose。
     * 这里用的就是 AgentDispatcher 里那个 [SessionSystemPrompt]（同一个类、同一条修订键规则）。
     */
    private class Session(private val memory: AgentMemory, private val preferences: AgentPreferences) {
        private val sections = listOf("身份段", "能力段", "纪律段", "边界段")
        private val telemetry = "[宿主物理环境与实时感知 Telemetry]"
        val history = mutableListOf<ChatMessage>()
        val prompt = SessionSystemPrompt(
            revision = { memoryPromptRevision(preferences.memoryEnabled, memory.longTermNotes) },
            assemble = {
                AgentSystemPrompt.compose(
                    sections = sections,
                    memoryHeader = MemoryInjection.header(preferences.memoryEnabled, memory.buildSystemMemoryHeader()),
                    telemetryBlock = telemetry,
                    customPrompt = ""
                )
            }
        )

        fun systemContent(): String = history.first { it.role == AgentRole.SYSTEM }.content!!
    }

    @Test
    fun aWrittenFactReachesTheNextRoundsSystemPromptWithoutRestartingTheSession() {
        val memory = memory()
        val session = Session(memory, preferences())

        // 第 1 轮：系统头装好，此时什么都没有记
        assertTrue("首轮必须装配", session.prompt.sync(session.history))
        assertFalse(session.systemContent().contains("持久用户偏好"))

        // 第 1 轮之后：模型调用 remember_fact（这里走它的落点，同一个仓储、同一条规则）
        assertEquals(
            MemoryFactBook.Status.ADDED,
            memory.rememberFact("主人讨厌客套话", AgentPreferences.MAX_MEMORY_FACTS).status
        )

        // 第 2 轮请求前：记忆集合变了 → 系统头重装，本轮请求里就带着它
        assertTrue("记忆变了必须重装系统头", session.prompt.sync(session.history))
        assertEquals("会话里仍然只有一条 system 头", 1, session.history.count { it.role == AgentRole.SYSTEM })
        assertTrue(
            "同一会话的下一轮请求必须带上刚写进去的记忆，收到：" + session.systemContent(),
            session.systemContent().contains("主人讨厌客套话")
        )
        assertTrue("记忆头那一行也要在", session.systemContent().contains("持久用户偏好"))
    }

    @Test
    fun unchangedMemoryIsNotReassembledEveryRound() {
        val memory = memory()
        val session = Session(memory, preferences())
        assertTrue(session.prompt.sync(session.history))
        assertEquals(
            MemoryFactBook.Status.ADDED,
            memory.rememberFact("飞行模式睡前开", AgentPreferences.MAX_MEMORY_FACTS).status
        )
        assertTrue("写一条才换来一次重装", session.prompt.sync(session.history))
        repeat(3) { round ->
            assertFalse("第 " + (round + 2) + " 轮记忆没变，不许重装", session.prompt.sync(session.history))
        }
        assertEquals(1, session.history.count { it.role == AgentRole.SYSTEM })
    }

    @Test
    fun clearingTheMemoryRemovesThatSectionFromThePrompt() {
        val memory = memory()
        val session = Session(memory, preferences())
        session.prompt.sync(session.history)
        memory.rememberFact("常驻城市杭州", AgentPreferences.MAX_MEMORY_FACTS)
        assertTrue(session.prompt.sync(session.history))
        assertTrue(session.systemContent().contains("常驻城市杭州"))

        memory.longTermNotes = ""
        assertTrue("清空也是一次变化，必须重装", session.prompt.sync(session.history))
        assertFalse("清空后那条记忆不许再出现", session.systemContent().contains("常驻城市杭州"))
        assertFalse("整段持久偏好都没有了", session.systemContent().contains("持久用户偏好"))
        assertTrue("记忆头其余部分不受牵连", session.systemContent().contains("【宿主平台与长记忆核心约束】"))
    }

    @Test
    fun turningTheMemorySwitchOffDropsTheHeaderWithoutTouchingTheStore() {
        val memory = memory()
        memory.rememberFact("常驻城市杭州", AgentPreferences.MAX_MEMORY_FACTS)
        val prefs = preferences()
        val session = Session(memory, prefs)
        session.prompt.sync(session.history)
        assertTrue(session.systemContent().contains("常驻城市杭州"))

        prefs.memoryEnabled = false
        assertTrue("开关变化也要重装", session.prompt.sync(session.history))
        assertFalse("关掉后记忆头整段不注入", session.systemContent().contains("【宿主平台与长记忆核心约束】"))
        assertFalse(session.systemContent().contains("常驻城市杭州"))
        assertEquals("关掉只影响注入，不动盘上数据", listOf("常驻城市杭州"), memory.facts())
    }

    // ==================== 接线守卫（必经 org.json 的那一跳） ====================

    @Test
    fun theRegistryWiresRememberFactFromCatalogToStore() {
        val registry = registrySource()
        assertTrue(
            "executeTool 必须有 remember_fact 的分派分支",
            registry.contains("ShellPolicy.TOOL_REMEMBER_FACT -> executeRememberFact(args)")
        )
        assertTrue(
            "工具清单必须注册它（且受 memoryEnabled 裁剪）",
            registry.contains("all.add(memoryToolDefinition())")
        )
        assertTrue(
            "落盘必须走 AgentMemory.rememberFact 并使用上限常量",
            registry.contains("store.rememberFact(") && registry.contains("AgentPreferences.MAX_MEMORY_FACTS")
        )
        val rememberBody = registry.substringAfter("private fun executeRememberFact")
            .substringBefore("\n    private suspend fun executeRunShell")
        assertTrue(
            "总开关关掉时执行侧还要再拒一次（清单残留不许绕过）",
            rememberBody.contains("errorJson(\"memory_disabled\"")
        )
        assertTrue(
            "四种结局都要有回执：success / duplicate / memory_full / invalid_fact",
            rememberBody.contains("put(\"status\", \"success\")") &&
                rememberBody.contains("put(\"status\", \"duplicate\")") &&
                rememberBody.contains("\"memory_full\"") &&
                rememberBody.contains("\"invalid_fact\"")
        )
    }

    @Test
    fun theDispatcherBindsTheStoreRebuildsThePromptAndShowsTheFactToTheHuman() {
        val source = dispatcherSource()
        assertTrue(
            "仓储必须注入给工具层：同一个实例，写的就是系统头读到的那份",
            source.contains("toolRegistry.bindMemory(memory)")
        )
        val roundBody = source
            .substringAfter("private suspend fun streamChatCompletions")
            .substringBefore("\n    private ")
        assertTrue(
            "每轮请求前必须同步一次记忆（否则新记忆要重开会话才生效）",
            roundBody.contains("syncSystemMessageWithMemory()")
        )
        val syncBody = source
            .substringAfter("private fun syncSystemMessageWithMemory")
            .substringBefore("fun clearLongTermMemory")
        assertTrue("重装结果必须换进会话历史的 system 头", syncBody.contains("sessionPrompt.sync(conversationHistory)"))
        assertTrue("强制重装只留给新会话时机", source.contains("sessionPrompt.sync(conversationHistory, force = true)"))
        assertTrue(
            "审批页必须显示要记住的原文，而不是只给工具名",
            source.contains("toolRegistry.memoryFactOf(call)") && source.contains("记住长期偏好：「")
        )
        assertTrue(
            "强制审批档的风险说明必须按记忆工具单独写（否则审批页说的是'特权工具'）",
            source.contains("val isMemoryTool = ShellPolicy.isMemoryTool(toolName)")
        )
        assertTrue(
            "清理记忆后必须重建系统头",
            source.substringAfter("fun clearLongTermMemory")
                .substringBefore("fun getMemoryFactCount")
                .contains("initSystemMessage()")
        )
        assertTrue(
            "条数统计必须与写入方共用同一条解析规则",
            source.contains("fun getMemoryFactCount(): Int = memory.facts().size")
        )
    }

    /** 接线守卫读的是真实源文件（CWD=模块目录，从仓库根跑也兼容，见 readProjectFile）。 */
    private fun registrySource(): String =
        readProjectFile("src/main/java/com/example/myapplication/agent/AgentToolRegistry.kt")

    private fun dispatcherSource(): String =
        readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
}
