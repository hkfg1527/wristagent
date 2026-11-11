package com.example.myapplication.agent

import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 本轮新增三档的运行时闸门：每一条都钉"改了设置 -> 行为真的变"。
 *
 * 全部是纯逻辑：不依赖 Android、不依赖 Compose，也不打桩被测算逻辑。
 */
class AgentSettingPolicyTest {

    // ---------------------------------------------------------------- 压缩阈值

    @Test
    fun compressBudgetFollowsTheTurnCount() {
        assertEquals(60, CompressPolicy.messageBudget(30))
        assertEquals(8, CompressPolicy.messageBudget(4))
        assertEquals("轮数为 0 时按 1 轮算，不给出 0 条预算", 2, CompressPolicy.messageBudget(0))
    }

    @Test
    fun thresholdMovesTheTriggerPointAcrossTheSameBudget() {
        // 同一份 30 轮预算（60 条）：0.8 要攒到 49 条才压，0.3 攒到 19 条就压
        assertEquals(48, CompressPolicy.triggerAt(30, 0.8f))
        assertEquals(18, CompressPolicy.triggerAt(30, 0.3f))

        assertFalse("刚好等于触发点不算满", CompressPolicy.shouldCompress(48, 30, 0.8f))
        assertTrue("越过触发点就要压", CompressPolicy.shouldCompress(49, 30, 0.8f))
        assertFalse("低阈值下 18 条还没到", CompressPolicy.shouldCompress(18, 30, 0.3f))
        assertTrue("低阈值下 19 条就该压", CompressPolicy.shouldCompress(19, 30, 0.3f))
    }

    @Test
    fun outOfRangeThresholdsAreClampedInsteadOfDisablingTheFeature() {
        assertEquals(CompressPolicy.MaxThreshold, CompressPolicy.clampThreshold(9f))
        assertEquals(CompressPolicy.MinThreshold, CompressPolicy.clampThreshold(-1f))
        assertEquals(CompressPolicy.DefaultThreshold, CompressPolicy.clampThreshold(Float.NaN))
        assertEquals("上界夹紧后仍能触发", CompressPolicy.triggerAt(30, 9f), CompressPolicy.triggerAt(30, 0.95f))
    }

    @Test
    fun compactionKeepsAReserveSoItDoesNotRunEveryTurn() {
        val budget = CompressPolicy.messageBudget(30)
        val keep = CompressPolicy.keepMessages(30)
        assertTrue("压缩后必须留出回旋空间", keep < budget)
        assertEquals(36, keep)
        assertEquals("极小预算也要留下两轮", 2, CompressPolicy.keepMessages(1))
    }

    @Test
    fun trimToMessagesKeepsSystemHeadAndDropsOrphanToolReplies() {
        val history = mutableListOf(ChatMessage(role = AgentRole.SYSTEM, content = "SYS"))
        repeat(20) { index ->
            history.add(ChatMessage(role = AgentRole.USER, content = "u" + index))
            history.add(ChatMessage(role = AgentRole.ASSISTANT, content = "a" + index))
        }
        history.add(ChatMessage(role = AgentRole.TOOL, content = "orphan", toolCallId = "t0", name = "x"))

        val trimmed = ContextCompactor.trimToMessages(history, keepMessages = 6)
        assertEquals("system 头必须保留", AgentRole.SYSTEM, trimmed.first().role)
        assertEquals("system 头 + 6 条（剔除孤回执后由更早的消息补足预算）", 7, trimmed.size)
        assertTrue("修剪边界上的孤立 tool 回执要丢掉", trimmed.none { it.role == AgentRole.TOOL })
        assertEquals("补进来的必须是切口之前最近的那条，不是任意一条", "a19", trimmed.last().content)
    }

    /**
     * 三角验证的另一边：回执的发起方**在**窗口内时必须原样保留。
     *
     * 少了这条，把"丢掉孤回执"写成"丢掉所有回执"也能让上面那条测试变绿 ——
     * 而那会把合法的工具结果一起删掉。
     */
    @Test
    fun completeToolReceiptSurvivesTheCut() {
        val history = listOf(
            ChatMessage(role = AgentRole.SYSTEM, content = "SYS"),
            ChatMessage(role = AgentRole.USER, content = "u1"),
            ChatMessage(
                role = AgentRole.ASSISTANT,
                content = "a1",
                toolCalls = listOf(ToolCall(id = "c1", name = "get_device_status", arguments = "{}"))
            ),
            ChatMessage(role = AgentRole.TOOL, content = "t1", toolCallId = "c1", name = "get_device_status"),
            ChatMessage(role = AgentRole.USER, content = "u2"),
            ChatMessage(role = AgentRole.ASSISTANT, content = "a2")
        )

        val trimmed = ContextCompactor.trimToMessages(history, keepMessages = 4)
        assertEquals("system 头 + 4 条", 5, trimmed.size)
        assertTrue(
            "发起方还在窗口里时，回执是完整的合法结果，必须保留",
            trimmed.any { it.role == AgentRole.TOOL && it.toolCallId == "c1" }
        )
        assertTrue("发起方同样必须保留", trimmed.any { it.role == AgentRole.ASSISTANT && it.toolCalls?.any { call -> call.id == "c1" } == true })
    }

    // ---------------------------------------------------------------- 沙箱等级

    @Test
    fun sandboxLevelDecidesWhatCanRun() {
        assertEquals(SandboxDecision.ALLOW, SandboxGate.decide(SandboxLevel.FULL, ShellDanger.SAFE))
        assertEquals(SandboxDecision.ALLOW, SandboxGate.decide(SandboxLevel.FULL, ShellDanger.RISKY))
        assertEquals(SandboxDecision.ALLOW, SandboxGate.decide(SandboxLevel.FULL, ShellDanger.DESTRUCTIVE))

        assertEquals(SandboxDecision.ALLOW, SandboxGate.decide(SandboxLevel.WORKSPACE, ShellDanger.SAFE))
        assertEquals(SandboxDecision.ALLOW, SandboxGate.decide(SandboxLevel.WORKSPACE, ShellDanger.RISKY))
        assertEquals(
            "工作区等级必须挡下破坏性命令",
            SandboxDecision.BLOCK,
            SandboxGate.decide(SandboxLevel.WORKSPACE, ShellDanger.DESTRUCTIVE)
        )

        assertEquals(SandboxDecision.ALLOW, SandboxGate.decide(SandboxLevel.READ_ONLY, ShellDanger.SAFE))
        assertEquals(
            SandboxDecision.BLOCK,
            SandboxGate.decide(SandboxLevel.READ_ONLY, ShellDanger.RISKY)
        )
        assertEquals(
            SandboxDecision.BLOCK,
            SandboxGate.decide(SandboxLevel.READ_ONLY, ShellDanger.DESTRUCTIVE)
        )
    }

    @Test
    fun sandboxBlockMessageIsOnlyProducedWhenBlocked() {
        assertNull(SandboxGate.blockMessage(SandboxLevel.FULL, ShellDanger.DESTRUCTIVE))
        val message = SandboxGate.blockMessage(SandboxLevel.WORKSPACE, ShellDanger.DESTRUCTIVE)
        assertTrue("挡下时必须给出可读原因", message != null && message.isNotBlank())
        assertTrue("原因里要点明当前等级", message!!.contains(SandboxLevel.WORKSPACE.storageKey))
        assertTrue("原因里要点明命令档位", message.contains(ShellDanger.DESTRUCTIVE.name))
    }

    @Test
    fun sandboxLevelSurvivesAnUnknownStoredValue() {
        assertEquals(SandboxLevel.FULL, SandboxLevel.fromStorage("JAIL"))
        assertEquals(SandboxLevel.READ_ONLY, SandboxLevel.fromStorage("READ_ONLY"))
        assertEquals(ToolApprovalPolicy.RISKY, ToolApprovalPolicy.fromStorage("SOMETIMES"))
        assertEquals(GestureAction.NONE, GestureAction.fromStorage(null))
    }

    // ---------------------------------------------------------------- 审批策略

    private fun lowRisk() = RiskAssessment(aiRiskLevel = RiskLevel.LOW, riskSummary = "read only")

    private fun highRisk() = RiskAssessment(
        isHighRisk = true,
        aiRiskLevel = RiskLevel.HIGH,
        riskSummary = "touches system state"
    )

    private fun fatal() = RiskAssessment(
        isHardBlocked = true,
        isHighRisk = true,
        aiRiskLevel = RiskLevel.CRITICAL,
        riskSummary = "fatal"
    )

    @Test
    fun approvalPolicyAlwaysAsksEvenForReadOnlyTools() {
        assertEquals(
            ApprovalGate.Decision.REQUIRE_APPROVAL,
            ApprovalGate.decide(lowRisk(), SecurityMode.STANDARD, ToolApprovalPolicy.ALWAYS)
        )
    }

    @Test
    fun approvalPolicyNeverAutoAllowsRiskyTools() {
        assertEquals(
            ApprovalGate.Decision.AUTO_ALLOW,
            ApprovalGate.decide(highRisk(), SecurityMode.NOVICE, ToolApprovalPolicy.NEVER)
        )
    }

    @Test
    fun approvalPolicyCanNotBypassTheFatalBlacklist() {
        assertEquals(
            ApprovalGate.Decision.HARD_BLOCK,
            ApprovalGate.decide(fatal(), SecurityMode.EXPERT, ToolApprovalPolicy.NEVER)
        )
    }

    @Test
    fun riskyPolicyKeepsTheLegacyLadder() {
        // 默认策略必须与历史行为逐项一致，否则升级会静默改变审批体验
        listOf(SecurityMode.NOVICE, SecurityMode.STANDARD, SecurityMode.EXPERT).forEach { mode ->
            listOf(lowRisk(), highRisk(), fatal()).forEach { risk ->
                assertEquals(
                    mode.name + " 下默认策略与历史判定必须一致",
                    ApprovalGate.decide(risk, mode),
                    ApprovalGate.decide(risk, mode, ToolApprovalPolicy.RISKY)
                )
            }
        }
    }

    // ---------------------------------------------------------------- 手势绑定

    @Test
    fun eachGestureEventResolvesToItsOwnBinding() {
        val bindings = GestureBindings(
            longPress = GestureAction.STOP_TURN,
            doubleTap = GestureAction.SWITCH_MODEL,
            sideKeyShort = GestureAction.NONE,
            sideKeyLong = GestureAction.FORGET_REMEMBERED
        )
        assertEquals(GestureAction.STOP_TURN, GestureBindingGate.actionFor(bindings, GestureEvent.LONG_PRESS))
        assertEquals(GestureAction.SWITCH_MODEL, GestureBindingGate.actionFor(bindings, GestureEvent.DOUBLE_TAP))
        assertEquals(GestureAction.NONE, GestureBindingGate.actionFor(bindings, GestureEvent.SIDE_KEY_SHORT))
        assertEquals(
            GestureAction.FORGET_REMEMBERED,
            GestureBindingGate.actionFor(bindings, GestureEvent.SIDE_KEY_LONG)
        )
        assertTrue(GestureBindingGate.anyStopsCurrentTurn(bindings))
        assertFalse("默认表一条都不绑，不该报有终止动作", GestureBindingGate.anyStopsCurrentTurn(GestureBindings()))
    }

    @Test
    fun stopTurnIsDeliveredExactlyOnce() {
        GestureRequestQueue.clear()
        val prefs = AgentPreferences(FakeSharedPreferences())
        GestureRuntime(prefs).perform(GestureAction.STOP_TURN)
        assertTrue("入队后第一次消费必须拿到终止", GestureRequestQueue.consumeStopTurn())
        assertFalse("队列只生效一次：下一个任务不能被上一次长按打断", GestureRequestQueue.consumeStopTurn())
    }

    @Test
    fun switchModelCyclesThroughTheCachedCatalog() {
        assertEquals("b", GestureRuntime.nextModel("a,b,c", "a"))
        assertEquals("c", GestureRuntime.nextModel("a,b,c", "b"))
        assertEquals("a", GestureRuntime.nextModel("a,b,c", "c"))
        assertEquals("当前模型不在清单里时回到第一个", "a", GestureRuntime.nextModel("a,b,c", "z"))
        assertNull("只有一个模型时没有下一个", GestureRuntime.nextModel("a", "a"))
        assertNull("清单为空时没有下一个", GestureRuntime.nextModel("", "a"))
    }

    @Test
    fun switchModelWritesTheNewModelBackToPreferences() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.cachedRemoteModels = "deepseek-chat,deepseek-reasoner"
        prefs.modelName = "deepseek-chat"
        val outcome = GestureRuntime(prefs).perform(GestureAction.SWITCH_MODEL)
        assertEquals(GestureAction.SWITCH_MODEL, outcome.action)
        assertEquals("deepseek-reasoner", outcome.modelName)
        assertEquals("下一次请求必须用新模型", "deepseek-reasoner", prefs.modelName)
    }

    @Test
    fun forgetRememberedActionIsTheOnlyThingThatClearsTheAllowList() {
        val store = FakeSharedPreferences()
        val prefs = AgentPreferences(store)
        assertTrue(prefs.rememberShellCommand("pm list packages"))
        assertEquals(1, prefs.rememberedShellCommandCount)

        val outcome = GestureRuntime(prefs).perform(GestureAction.FORGET_REMEMBERED)
        assertEquals(GestureAction.FORGET_REMEMBERED, outcome.action)
        assertEquals("动作名与副作用必须一一对应", 0, prefs.rememberedShellCommandCount)
    }

    @Test
    fun noneActionTouchesNothing() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.cachedRemoteModels = "a,b"
        prefs.modelName = "a"
        assertTrue(prefs.rememberShellCommand("pm list packages"))
        GestureRequestQueue.clear()

        val outcome = GestureRuntime(prefs).perform(GestureAction.NONE)
        assertFalse("未绑定不得有任何副作用", outcome.effective)
        assertEquals("a", prefs.modelName)
        assertEquals(1, prefs.rememberedShellCommandCount)
        assertFalse(GestureRequestQueue.consumeStopTurn())
    }

    // ---------------------------------------------------------------- 记住清单上限

    @Test
    fun rememberedCapacityIsVisibleAndRefusesFurtherWrites() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        assertFalse(prefs.rememberedShellCommandsFull)
        repeat(AgentPreferences.MaxRememberedShellCommands) { index ->
            assertTrue("第 " + (index + 1) + " 条必须记住", prefs.rememberShellCommand("echo step" + index))
        }
        assertEquals(AgentPreferences.MaxRememberedShellCommands, prefs.rememberedShellCommandCount)
        assertTrue("满额后必须能被 UI 看出来", prefs.rememberedShellCommandsFull)
        assertFalse("满额后新的记住必须被拒绝", prefs.rememberShellCommand("echo overflow"))
        assertEquals(
            "拒绝写入时清单不许增长",
            AgentPreferences.MaxRememberedShellCommands,
            prefs.rememberedShellCommandCount
        )
        prefs.forgetRememberedShellCommands()
        assertFalse(prefs.rememberedShellCommandsFull)
        assertEquals(0, prefs.rememberedShellCommandCount)
    }
}
