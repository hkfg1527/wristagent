package com.example.myapplication.presentation

import android.app.Application
import com.example.myapplication.R
import com.example.myapplication.agent.AgentExecutionState
import com.example.myapplication.agent.AgentGateway
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.ChatMessage
import com.example.myapplication.agent.ToolDefinition
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.MainInputType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 只 mock 外部协作者（AgentGateway）：会话、草稿、审批、底部模式等状态迁移都跑真实 ViewModel。
 */
private class FakeAgentGateway : AgentGateway {

    override var onRequireConfirmation: (suspend (toolName: String, actionText: String, risk: RiskAssessment) -> Boolean)? = null

    override var onReasoningEffortIgnored: (() -> Unit)? = null

    val sentPrompts = mutableListOf<String>()
    val sentBlocks = mutableListOf<List<ConversationBlock>>()
    val loadedHistories = mutableListOf<List<ChatMessage>>()
    var storedHistory: List<ChatMessage> = emptyList()
    var stopCount = 0
    var clearSessionCount = 0
    var preferenceWrites = 0

    private var lastUpdate: ((AgentExecutionState) -> Unit)? = null

    override suspend fun send(
        prompt: String,
        existingBlocks: List<ConversationBlock>,
        onStateUpdate: (AgentExecutionState) -> Unit
    ) {
        sentPrompts += prompt
        sentBlocks += existingBlocks
        lastUpdate = onStateUpdate
        // 首帧：用户气泡 + 思考中
        onStateUpdate(
            AgentExecutionState(
                prompt = prompt,
                conversationBlocks = existingBlocks + ConversationBlock.Text(
                    id = "user-" + prompt,
                    content = prompt,
                    isUser = true
                ),
                isThinking = true
            )
        )
    }

    /** 模拟引擎的后续流式回调。 */
    fun emit(state: AgentExecutionState) {
        lastUpdate?.invoke(state)
    }

    /** 模拟引擎在工具执行前的审批挂起。 */
    suspend fun requestApproval(toolName: String, risk: RiskAssessment): Boolean =
        onRequireConfirmation?.invoke(toolName, "执行 [" + toolName + "]", risk) ?: true

    override fun stop() {
        stopCount += 1
    }

    override fun clearSession() {
        clearSessionCount += 1
    }

    override fun loadSession(history: List<ChatMessage>) {
        loadedHistories += history
    }

    override fun snapshotHistory(): List<ChatMessage> = storedHistory

    override fun updatePreferences(update: (AgentPreferences) -> Unit) {
        preferenceWrites += 1
    }

    override fun registeredTools(): List<ToolDefinition> = emptyList()

    override fun clearLongTermMemory() = Unit

    override fun memoryFactCount(): Int = 0
}

class ChatViewModelTest {

    private val matrixDensityRisk = RiskAssessment(
        isHardBlocked = false,
        isHighRisk = true,
        riskSummary = "会修改表盘点阵密度",
        requireCooldownSeconds = 3,
        whatItDoes = "把点阵间距从 16dp 调到 22dp",
        worstCase = "表盘可能短暂空白，需要重启",
        aiRiskLevel = RiskLevel.HIGH
    )

    private fun TestScope.viewModel(
        gateway: FakeAgentGateway,
        clock: () -> Long = { 0L }
    ): ChatViewModel = ChatViewModel(
        application = Application(),
        gateway = gateway,
        initialMainInputType = MainInputType.VOICE,
        // 显式测试调度器：与 runTest 同一个 scheduler，advanceUntilIdle/advanceTimeBy 能推进 ViewModel 内的协程
        scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
        uptimeMs = clock
    )

    private fun finishedState(
        prompt: String,
        blocks: List<ConversationBlock>,
        answer: String
    ): AgentExecutionState = AgentExecutionState(
        prompt = prompt,
        conversationBlocks = blocks + ConversationBlock.Text(
            id = "answer-" + prompt,
            content = answer,
            isStreaming = false
        ),
        isThinking = false,
        isExecutingTool = false,
        isFinished = true
    )

    // ============ 1. 发送 -> 执行 -> 结束 ============

    @Test
    fun `发送后进入执行态并落地首帧`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        assertEquals(false, vm.uiState.value.isExecuting)

        vm.triggerAgent("查一下电量")
        advanceUntilIdle()

        assertEquals(listOf("查一下电量"), gateway.sentPrompts)
        val state = vm.uiState.value
        assertEquals(true, state.isExecuting)
        assertEquals(true, state.isThinkingActive)
        assertEquals(false, state.isTaskCompleted)
        assertEquals("查一下电量", (state.blocks.last() as ConversationBlock.Text).content)
        // 标题在首条消息后由"当前对话"改为消息前 12 字
        assertEquals("查一下电量", state.activeSession.title)
    }

    @Test
    fun `流式终态落地后回到闲置底栏`() = runTest {
        val gateway = FakeAgentGateway()
        var now = 0L
        val vm = viewModel(gateway) { now }
        vm.triggerAgent("今天几号")
        advanceUntilIdle()
        // 执行中：形态派生为"停止"胶囊（底栏只读派生值，不再等 300ms 写库）
        assertEquals(BottomBlockMode.THINKING_STOP, vm.uiState.value.deriveBottomBlockMode())

        now = 500L
        val blocks = vm.uiState.value.blocks
        gateway.emit(finishedState("今天几号", blocks, "2026 年 3 月 1 日"))
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(false, state.isExecuting)
        assertEquals(false, state.isThinkingActive)
        assertEquals(true, state.isTaskCompleted)
        assertEquals(BottomBlockMode.IDLE, state.deriveBottomBlockMode())
        assertEquals("2026 年 3 月 1 日", (state.blocks.last() as ConversationBlock.Text).content)
        // 终态必须同步进会话记录，切走再切回才不丢
        assertEquals(state.blocks, state.activeSession.conversationBlocks)
    }

    @Test
    fun `执行中重复发送被忽略`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        vm.triggerAgent("第一条")
        advanceUntilIdle()

        vm.triggerAgent("第二条")
        advanceUntilIdle()

        assertEquals(listOf("第一条"), gateway.sentPrompts)
        assertEquals(true, vm.uiState.value.isExecuting)
    }

    // ============ 2. 审批 ============

    @Test
    fun `审批放行在塌缩动画结束后交回 true`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        var answer: Boolean? = null

        val approval = launch { answer = gateway.requestApproval("adjust_matrix_density", matrixDensityRisk) }
        advanceUntilIdle()

        val pendingState = vm.uiState.value
        assertEquals("adjust_matrix_density", pendingState.pendingApproval?.toolName)
        assertEquals(RiskLevel.HIGH, pendingState.pendingApproval?.risk?.aiRiskLevel)
        assertEquals(BottomBlockMode.ACTION_ALLOW, pendingState.bottomBlockMode)
        assertTrue(pendingState.isApprovalFocus)
        assertNull(answer)

        vm.approvePending()
        // 先塌缩 220ms，结论此刻还没交回
        assertEquals(true, vm.uiState.value.isApprovalCollapsing)
        assertNull(answer)

        advanceUntilIdle()

        assertEquals("审批结论必须交回放行", true, answer)
        assertEquals(false, vm.uiState.value.isApprovalCollapsing)
        assertEquals(BottomBlockMode.IDLE, vm.uiState.value.bottomBlockMode)
        approval.join()
    }

    @Test
    fun `放行后引擎立刻发起下一次审批时，第二次审批不被清空`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        var first: Boolean? = null
        var second: Boolean? = null

        // Unconfined：结论一交回就地继续跑，把"引擎在别的线程上并发继续"的时序固定成可复现的确定顺序
        val approval = launch(Dispatchers.Unconfined) {
            first = gateway.requestApproval("adjust_matrix_density", matrixDensityRisk)
            second = gateway.requestApproval("set_system_power_mode", matrixDensityRisk)
        }
        advanceUntilIdle()
        assertEquals("adjust_matrix_density", vm.uiState.value.pendingApproval?.toolName)

        vm.approvePending()
        advanceUntilIdle()

        assertEquals(true, first)
        assertEquals(
            "第二次审批必须留在屏幕上：清状态晚于交回结论会把新审批连同续体一起抹掉，引擎永远等不到结论",
            "set_system_power_mode",
            vm.uiState.value.pendingApproval?.toolName
        )
        assertEquals(BottomBlockMode.ACTION_ALLOW, vm.uiState.value.bottomBlockMode)

        vm.rejectPending()
        advanceUntilIdle()
        assertEquals(false, second)
        approval.join()
    }

    @Test
    fun `审批拒绝立刻交回 false 且不播塌缩`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        var answer: Boolean? = null

        val approval = launch { answer = gateway.requestApproval("set_system_power_mode", matrixDensityRisk) }
        advanceUntilIdle()
        assertEquals(BottomBlockMode.ACTION_ALLOW, vm.uiState.value.bottomBlockMode)

        vm.rejectPending()
        advanceUntilIdle()

        assertEquals("审批结论必须交回拒绝", false, answer)
        assertEquals(false, vm.uiState.value.isApprovalCollapsing)
        assertEquals(BottomBlockMode.IDLE, vm.uiState.value.bottomBlockMode)
        approval.join()
    }

    @Test
    fun `停止会取消引擎执行并拒绝挂起审批`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        var answer: Boolean? = null

        vm.triggerAgent("放一首歌")
        advanceUntilIdle()
        val approval = launch { answer = gateway.requestApproval("vibrate_device", matrixDensityRisk) }
        advanceUntilIdle()

        vm.stopAgent()
        advanceUntilIdle()

        assertEquals(1, gateway.stopCount)
        assertEquals("停止必须把挂起审批按拒绝收口", false, answer)
        assertEquals(false, vm.uiState.value.isExecuting)
        assertEquals(false, vm.uiState.value.isThinkingActive)
        assertEquals(BottomBlockMode.IDLE, vm.uiState.value.bottomBlockMode)
        approval.join()
    }

    // ============ 3. 会话 ============

    @Test
    fun `新建会话清空引擎上下文并切到新时间线`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        vm.triggerAgent("第一条")
        advanceUntilIdle()
        gateway.emit(finishedState("第一条", vm.uiState.value.blocks, "好的"))
        advanceUntilIdle()

        val sessionCountBefore = vm.uiState.value.sessions.size
        vm.createNewSession()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(sessionCountBefore + 1, state.sessions.size)
        assertEquals(1, gateway.clearSessionCount)
        assertEquals(state.sessions.first().id, state.activeSessionId)
        assertEquals("新对话就绪", state.agentState.prompt)
        assertEquals(DEFAULT_SESSION_PROMPT, (state.blocks.single() as ConversationBlock.Text).content)
        assertEquals(false, state.isSessionDrawerOpen)
    }

    @Test
    fun `删除当前会话后切到剩余会话`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        vm.createNewSession()
        val newSessionId = vm.uiState.value.activeSessionId
        val previousSessionId = vm.uiState.value.sessions.last().id

        vm.deleteSession(newSessionId)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(listOf(previousSessionId), state.sessions.map { it.id })
        assertEquals(previousSessionId, state.activeSessionId)
        // 切换必然把历史交回引擎，否则续聊就断在旧上下文里
        assertEquals(1, gateway.loadedHistories.size)
    }

    @Test
    fun `切换会话载入历史并替换时间线`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        val previous = vm.uiState.value.activeSession
        vm.createNewSession()
        assertEquals(1, gateway.clearSessionCount)

        vm.openSessionDrawer()
        assertEquals(true, vm.uiState.value.isSessionDrawerOpen)

        vm.switchToSession(previous.id)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(previous.id, state.activeSessionId)
        assertEquals(previous.conversationBlocks, state.blocks)
        assertEquals(false, state.isSessionDrawerOpen)
        assertEquals(false, state.agentState.isThinking)
        assertEquals(true, state.agentState.isFinished)
        assertEquals(1, gateway.loadedHistories.size)
    }

    // ============ 4. 草稿、语音与提示 ============

    @Test
    fun `提交草稿会触发发送并清空草稿`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)

        vm.openTextInput()
        assertEquals(true, vm.uiState.value.draft.isOpen)
        vm.changeDraft("今晚吃什么")
        assertEquals("今晚吃什么", vm.uiState.value.draft.text)

        vm.submitDraft()
        advanceUntilIdle()

        assertEquals(listOf("今晚吃什么"), gateway.sentPrompts)
        assertEquals(false, vm.uiState.value.draft.isOpen)
        assertEquals("", vm.uiState.value.draft.text)
    }

    @Test
    fun `空白草稿提交不发送且保留弹窗`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        vm.openTextInput()
        vm.changeDraft("   ")

        vm.submitDraft()
        advanceUntilIdle()

        assertEquals(emptyList<String>(), gateway.sentPrompts)
        assertEquals(true, vm.uiState.value.draft.isOpen)
    }

    @Test
    fun `瞬时提示到期自动消失`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)

        vm.showNotice(R.string.wearchat_voice_error, isError = true, args = listOf("未识别到有效文字"))
        val shown = vm.uiState.value.notice
        assertEquals(R.string.wearchat_voice_error, shown?.textRes)
        assertEquals(listOf<Any>("未识别到有效文字"), shown?.args)
        assertEquals(true, shown?.isError)

        advanceTimeBy(2599)
        assertEquals(R.string.wearchat_voice_error, vm.uiState.value.notice?.textRes)

        advanceTimeBy(2)
        assertNull(vm.uiState.value.notice)
    }

    @Test
    fun `语音错误转为错误提示并退出录音态`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)

        vm.onVoiceListeningChanged(true)
        assertEquals(true, vm.uiState.value.isRecordingVoice)
        assertEquals(BottomBlockMode.RECORDING, vm.uiState.value.deriveBottomBlockMode())

        vm.onVoiceError("  启动语音录入失败: missing service  ")

        val state = vm.uiState.value
        assertEquals(false, state.isRecordingVoice)
        assertEquals(BottomBlockMode.IDLE, state.deriveBottomBlockMode())
        assertEquals(R.string.wearchat_voice_error, state.notice?.textRes)
        assertEquals(
            listOf<Any>("启动语音录入失败: missing service"),
            state.notice?.args
        )
        assertEquals(true, state.notice?.isError)
    }

    // ============ 4.1 提示通道的资源 id 与参数 ============

    @Test
    fun `瞬时提示携带资源 id 与占位参数`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)

        vm.showNotice(R.string.wearchat_voice_error, isError = true, args = listOf("启动语音录入失败", 2))

        val notice = vm.uiState.value.notice
        assertEquals(R.string.wearchat_voice_error, notice?.textRes)
        assertEquals(listOf<Any>("启动语音录入失败", 2), notice?.args)
        assertEquals(true, notice?.isError)

        // 不带参数的提示必须把上一条的参数清干净，占位符不能串到下一条
        vm.showNotice(R.string.wearchat_notice_input_voice, isError = false)
        val replaced = vm.uiState.value.notice
        assertEquals(R.string.wearchat_notice_input_voice, replaced?.textRes)
        assertEquals(emptyList<Any>(), replaced?.args)
        assertEquals(false, replaced?.isError)
    }

    @Test
    fun `切换输入方式播报对应资源 id`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)

        vm.changeMainInputType(MainInputType.KEYBOARD)
        assertEquals(MainInputType.KEYBOARD, vm.uiState.value.mainInputType)
        assertEquals(R.string.wearchat_notice_input_keyboard, vm.uiState.value.notice?.textRes)
        assertEquals(false, vm.uiState.value.notice?.isError)

        vm.changeMainInputType(MainInputType.VOICE)
        assertEquals(MainInputType.VOICE, vm.uiState.value.mainInputType)
        assertEquals(R.string.wearchat_notice_input_voice, vm.uiState.value.notice?.textRes)
        assertEquals(false, vm.uiState.value.notice?.isError)
        assertEquals(emptyList<Any>(), vm.uiState.value.notice?.args)
    }

    @Test
    fun `空白语音错误不产生提示条`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)

        vm.onVoiceListeningChanged(true)
        vm.onVoiceError("   ")

        assertNull(vm.uiState.value.notice)
        assertEquals(false, vm.uiState.value.isRecordingVoice)
        assertEquals(BottomBlockMode.IDLE, vm.uiState.value.deriveBottomBlockMode())

        vm.onVoiceError(null)
        assertNull(vm.uiState.value.notice)
    }

    // ============ 5. 外部 intent prompt ============

    @Test
    fun `外部 prompt 只消费一次且执行中不消费`() = runTest {
        val gateway = FakeAgentGateway()
        val vm = viewModel(gateway)
        val prompt = ExternalPrompt(seq = 1L, text = " 查一下电量 ")

        assertEquals(true, vm.consumeExternalPrompt(prompt))
        advanceUntilIdle()
        assertEquals(listOf("查一下电量"), gateway.sentPrompts)

        // 同 seq 不重放
        assertEquals(false, vm.consumeExternalPrompt(prompt))
        // 执行中不抢跑
        assertEquals(false, vm.consumeExternalPrompt(ExternalPrompt(seq = 2L, text = "再来一条")))
        assertEquals(listOf("查一下电量"), gateway.sentPrompts)
    }
}
