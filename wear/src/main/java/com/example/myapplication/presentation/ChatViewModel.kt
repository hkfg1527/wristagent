package com.example.myapplication.presentation

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.annotation.StringRes
import com.example.myapplication.R
import com.example.myapplication.agent.AgentExecutionState
import com.example.myapplication.agent.AgentGateway
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.MainInputType
import com.example.myapplication.presentation.ui.StreamingThrottleState
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.UUID
import kotlin.coroutines.resume

/**
 * 聊天主界面的状态所有者：会话、草稿、审批挂起项、底部块模式与瞬时提示全部收敛在这里，
 * Composable 只读 [uiState] 并回调本类的方法。
 *
 * 外部协作方只有 [AgentGateway] 一层：AgentDispatcher 的业务语义（风险闸门、审批挂起、
 * 工具执行、流式回调顺序）原样留在引擎内，本类只负责把回调映射成 UI 状态。
 */
class ChatViewModel(
    application: Application,
    private val gateway: AgentGateway,
    initialMainInputType: MainInputType = MainInputType.VOICE,
    /** 测试注入用：默认走 viewModelScope。 */
    scope: CoroutineScope? = null,
    /** 流式节流的时间基准：默认 uptimeMillis，单测注入假时钟。 */
    private val uptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    private val noticeVisibleMs: Long = NOTICE_VISIBLE_MS,
    private val approvalCollapseMs: Long = ApprovalCollapseDurationMs.toLong()
) : AndroidViewModel(application) {

    private val workScope: CoroutineScope = scope ?: viewModelScope

    private val throttle = StreamingThrottleState()
    private val _uiState = MutableStateFlow(ChatUiState.initial(initialMainInputType))

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 审批挂起项的续体：放行/拒绝/停止/返回键都通过它交回结论。 */
    private var pendingContinuation: CancellableContinuation<Boolean>? = null
    private var delayedStretchJob: Job? = null
    private var noticeJob: Job? = null
    private var consumedExternalSeq: Long? = null

    init {
        gateway.onRequireConfirmation = { toolName, _, risk -> awaitApprovalDecision(toolName, risk) }
    }

    override fun onCleared() {
        gateway.onRequireConfirmation = null
        delayedStretchJob?.cancel()
        noticeJob?.cancel()
        pendingContinuation = null
    }

    // ======================== 发送与执行 ========================

    /**
     * 触发一轮 Agent 执行。返回 false 表示正在执行中（本次忽略），
     * 调用方据此决定是否播底栏激荡波纹——被忽略的发送不该有动效。
     */
    fun triggerAgent(prompt: String): Boolean {
        if (_uiState.value.isExecuting) return false
        delayedStretchJob?.cancel()

        val existingBlocks = _uiState.value.activeSession.conversationBlocks
        val sessionTitle = _uiState.value.activeSession.title
        val updatedTitle = if (sessionTitle == "新对话" || sessionTitle == "当前对话") {
            prompt.take(12)
        } else {
            sessionTitle
        }

        _uiState.update {
            it.copy(isExecuting = true, isThinkingActive = true, isTaskCompleted = false)
        }

        delayedStretchJob = workScope.launch {
            delay(ThinkingStretchDelayMs)
            _uiState.update {
                if (it.isExecuting && it.bottomBlockMode != BottomBlockMode.ACTION_ALLOW) {
                    it.copy(bottomBlockMode = BottomBlockMode.THINKING_STOP)
                } else {
                    it
                }
            }
        }

        // 新一轮从空基线起算：首帧立即可见，突发判定不看上一轮的残留文本
        throttle.reset()
        _uiState.update { it.copy(isStreamingBurst = false) }

        workScope.launch {
            gateway.send(prompt, existingBlocks) { newState ->
                onAgentStateUpdate(newState, updatedTitle)
            }
        }
        return true
    }

    /** 停止：取消引擎执行 + 拒绝挂起审批 + 底栏回到闲置。 */
    fun stopAgent() {
        gateway.stop()
        pendingContinuation?.resume(false)
        pendingContinuation = null
        _uiState.update {
            it.copy(
                bottomBlockMode = BottomBlockMode.IDLE,
                isExecuting = false,
                isThinkingActive = false
            )
        }
    }

    /**
     * 流式回调的唯一落点：逐 token 提交等于每来一个字就重排整列正文，只按节拍提交；
     * 终态（一轮结束 / 报错）强制落地，绝不能被节流窗口挡掉。
     */
    private fun onAgentStateUpdate(newState: AgentExecutionState, updatedTitle: String) {
        val emitNow = throttle.accept(
            newText = streamingTailText(newState.conversationBlocks),
            nowMs = uptimeMs(),
            force = newState.isFinished
        )

        _uiState.update { current ->
            current.copy(
                isThinkingActive = current.isExecuting && !newState.isFinished &&
                    (newState.isThinking || newState.isExecutingTool)
            )
        }

        if (emitNow) {
            val history = gateway.snapshotHistory()
            _uiState.update { current ->
                current.copy(
                    isStreamingBurst = throttle.isBurst,
                    agentState = newState,
                    sessions = current.sessions.map { session ->
                        if (session.id == current.activeSessionId) {
                            session.copy(
                                title = updatedTitle,
                                conversationBlocks = newState.conversationBlocks,
                                history = history
                            )
                        } else {
                            session
                        }
                    }
                )
            }
        }

        if (newState.isFinished) {
            delayedStretchJob?.cancel()
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    isThinkingActive = false,
                    // 触发 30 度表镜掠影反光
                    isTaskCompleted = true,
                    bottomBlockMode = BottomBlockMode.IDLE
                )
            }
        } else if (!newState.isThinking && !newState.isExecutingTool) {
            _uiState.update {
                if (it.bottomBlockMode != BottomBlockMode.ACTION_ALLOW) {
                    it.copy(bottomBlockMode = BottomBlockMode.IDLE)
                } else {
                    it
                }
            }
        }
    }

    /** 正在流式输出的那一段文本：突发判定的基准是"上一帧屏幕上的文字 → 本帧文字"。 */
    private fun streamingTailText(blocks: List<ConversationBlock>): String {
        val tail = blocks.lastOrNull { it is ConversationBlock.Text && it.isStreaming } as? ConversationBlock.Text
        return tail?.content ?: ""
    }

    // ======================== 审批 ========================

    /** 审批挂起：状态先落地（浮层立刻出现），续体最后挂上，顺序与引擎回调一致。 */
    private suspend fun awaitApprovalDecision(toolName: String, risk: RiskAssessment): Boolean {
        _uiState.update {
            it.copy(
                pendingApproval = PendingApproval(toolName = toolName, risk = risk),
                bottomBlockMode = BottomBlockMode.ACTION_ALLOW,
                // 新一轮审批从完整态开始：上一轮放行留下的塌缩进度必须归零
                isApprovalCollapsing = false
            )
        }
        return suspendCancellableCoroutine { continuation ->
            pendingContinuation = continuation
        }
    }

    /** 放行：先播塌缩动画，动画走完才把结论交回 Agent。 */
    fun approvePending() {
        if (pendingContinuation == null || _uiState.value.isApprovalCollapsing) return
        _uiState.update { it.copy(isApprovalCollapsing = true) }
        workScope.launch {
            delay(approvalCollapseMs)
            _uiState.update { it.copy(isApprovalCollapsing = false) }
            pendingContinuation?.resume(true)
            pendingContinuation = null
            _uiState.update { it.copy(bottomBlockMode = BottomBlockMode.IDLE) }
        }
    }

    /** 拒绝 / 返回键退出审批：立刻交回 false，不走塌缩动画。 */
    fun rejectPending() {
        pendingContinuation?.resume(false)
        pendingContinuation = null
        _uiState.update { it.copy(bottomBlockMode = BottomBlockMode.IDLE) }
    }

    /** 审批滑块进度：0=拒绝，1=放行，由底栏回调驱动点阵着色。 */
    fun onApprovalProgress(progress: Float) {
        _uiState.update { it.copy(approvalProgress = progress) }
    }

    // ======================== 会话 ========================

    fun switchToSession(id: String) {
        val current = _uiState.value
        val target = current.sessions.find { it.id == id } ?: return
        gateway.loadSession(target.history)
        _uiState.update {
            it.copy(
                activeSessionId = id,
                agentState = it.agentState.copy(
                    conversationBlocks = target.conversationBlocks,
                    isFinished = true,
                    isThinking = false
                ),
                isSessionDrawerOpen = false
            )
        }
    }

    fun createNewSession() {
        val newSession = ChatSession(
            title = "新对话",
            conversationBlocks = listOf(
                ConversationBlock.Text(
                    id = UUID.randomUUID().toString(),
                    content = DEFAULT_SESSION_PROMPT,
                    isUser = false
                )
            )
        )
        gateway.clearSession()
        _uiState.update {
            it.copy(
                sessions = listOf(newSession) + it.sessions,
                activeSessionId = newSession.id,
                agentState = AgentExecutionState(
                    prompt = "新对话就绪",
                    conversationBlocks = newSession.conversationBlocks,
                    isFinished = true
                ),
                isSessionDrawerOpen = false
            )
        }
    }

    fun deleteSession(id: String) {
        val remaining = _uiState.value.sessions.filter { it.id != id }
        if (remaining.isEmpty()) {
            createNewSession()
            return
        }
        _uiState.update { it.copy(sessions = remaining) }
        if (_uiState.value.activeSessionId == id) {
            switchToSession(remaining.first().id)
        }
    }

    fun openSessionDrawer() {
        _uiState.update { it.copy(isSessionDrawerOpen = true) }
    }

    fun closeSessionDrawer() {
        _uiState.update { it.copy(isSessionDrawerOpen = false) }
    }

    /** 会话抽屉里的模型切换：唯一副作用是写偏好，不动引擎上下文。 */
    fun selectModel(modelName: String) {
        gateway.updatePreferences { it.modelName = modelName }
    }

    // ======================== 草稿与输入方式 ========================

    fun openTextInput() = reduceDraft(TextInputEvent.Open)

    fun cancelTextInput() = reduceDraft(TextInputEvent.Cancel)

    fun changeDraft(text: String) = reduceDraft(TextInputEvent.Change(text))

    /** 提交草稿：屏幕按钮与输入法 Action 键共用同一条路径。 */
    fun submitDraft() {
        val afterSubmit = _uiState.value.draft.reduce(TextInputEvent.Submit)
        _uiState.update { it.copy(draft = afterSubmit) }
        afterSubmit.lastSent?.let { triggerAgent(it) }
    }

    private fun reduceDraft(event: TextInputEvent) {
        _uiState.update { it.copy(draft = it.draft.reduce(event)) }
    }

    /** 主键输入方式切换：先落状态、再持久化、最后播提示（文案与启动播报同一套）。 */
    fun changeMainInputType(type: MainInputType) {
        _uiState.update { it.copy(mainInputType = type) }
        gateway.updatePreferences { it.mainInputType = type.name }
        showNotice(
            textRes = when (type) {
                MainInputType.KEYBOARD -> R.string.wearchat_notice_input_keyboard
                MainInputType.VOICE -> R.string.wearchat_notice_input_voice
            },
            isError = false
        )
    }

    // ======================== 语音 ========================

    fun onVoiceListeningChanged(listening: Boolean) {
        _uiState.update {
            it.copy(
                isRecordingVoice = listening,
                bottomBlockMode = if (listening) BottomBlockMode.RECORDING else BottomBlockMode.IDLE
            )
        }
    }

    fun onVoiceResult(recognizedText: String) {
        _uiState.update {
            it.copy(isRecordingVoice = false, bottomBlockMode = BottomBlockMode.IDLE)
        }
        triggerAgent(recognizedText)
    }

    fun onVoiceError(rawMessage: String?) {
        _uiState.update {
            it.copy(isRecordingVoice = false, bottomBlockMode = BottomBlockMode.IDLE)
        }
        SpeechErrorNotice.notice(rawMessage)?.let {
            showNotice(textRes = it.textRes, isError = it.isError, args = it.args)
        }
    }

    // ======================== 瞬时提示 ========================

    fun showNotice(@StringRes textRes: Int, isError: Boolean, args: List<Any> = emptyList()) {
        noticeJob?.cancel()
        _uiState.update {
            it.copy(notice = ChatNotice(textRes = textRes, args = args, isError = isError))
        }
        noticeJob = workScope.launch {
            delay(noticeVisibleMs)
            _uiState.update { it.copy(notice = null) }
        }
    }

    // ======================== 外部 intent prompt ========================

    /**
     * 外部 intent prompt：进入主界面且空闲时消费一次。
     * 返回 true 表示已消费，调用方负责回清 externalPrompt。
     */
    fun consumeExternalPrompt(prompt: ExternalPrompt?): Boolean {
        if (!ExternalPromptGate.shouldConsume(
                prompt = prompt,
                ready = !_uiState.value.isExecuting,
                lastConsumedSeq = consumedExternalSeq
            )
        ) {
            return false
        }
        consumedExternalSeq = prompt!!.seq
        triggerAgent(prompt.text.trim())
        return true
    }

    companion object {
        /**
         * 生产装配：把 Android 侧协作者（引擎 / 偏好）一次性接进 ViewModel。
         * 上下文取 Application —— 引擎内部只用注册广播、剪贴板与系统服务，与 Activity 上下文等价且不泄漏。
         */
        fun factory(
            application: Application,
            gateway: AgentGateway,
            initialMainInputType: MainInputType
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    application = application,
                    gateway = gateway,
                    initialMainInputType = initialMainInputType
                ) as T
        }
    }
}
