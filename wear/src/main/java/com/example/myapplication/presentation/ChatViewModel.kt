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
import com.example.myapplication.agent.ReplySpeech
import com.example.myapplication.agent.ReplySpeaker
import com.example.myapplication.agent.VoiceDraftGate
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.session.SessionStore
import java.io.File
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.presentation.ui.BottomBarMode
import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.StreamingThrottleState
import com.example.myapplication.presentation.ui.chat.CapsuleExpansion
import com.example.myapplication.presentation.ui.chat.StreamingTail
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
    /** 会话落盘出口：生产取 filesDir，单测注入临时目录或注定失败的路径。 */
    private val sessionStore: SessionStore? =
        application.filesDir?.let { SessionStore(File(it, "sessions.txt")) },
    /** 测试注入用：默认走 viewModelScope。 */
    scope: CoroutineScope? = null,
    /** 流式节流的时间基准：默认 uptimeMillis，单测注入假时钟。 */
    private val uptimeMs: () -> Long = { SystemClock.uptimeMillis() },
    private val noticeVisibleMs: Long = NOTICE_VISIBLE_MS,
    private val approvalCollapseMs: Long = ApprovalCollapseDurationMs.toLong()
) : AndroidViewModel(application) {

    private val workScope: CoroutineScope = scope ?: viewModelScope

    private val throttle = StreamingThrottleState()
    private val _uiState = MutableStateFlow(
        ChatUiState.initial(gateway.displayPreferences().compactMode)
    )

    /** 自动播报的 Android 出口：只在真有内容要播时报建，播报失败只记日志。 */
    private var replySpeaker: ReplySpeaker? = null

    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** 审批挂起项的续体：放行/拒绝/停止/返回键都通过它交回结论。 */
    private var pendingContinuation: CancellableContinuation<Boolean>? = null
    private var noticeJob: Job? = null
    private var consumedExternalSeq: Long? = null

    // ==================== 跨屏存活的镜头/底栏状态 ====================
    // ChatScreen 在切到设置页时会离开组合，remember 全部丢失。若把这些状态留在 Compose 里，
    // 返回主界面时镜头会重新"对齐回合开头"、底栏会重置展开、3 秒静默计时会重开 —— 用户看到的就是抽搐。
    // 因此「已接管的回合」与「底栏形态」都提升到这里，随 ViewModel 存活。

    /** 已经接管过镜头的回合 key（每个回合只对齐一次）。 */
    private val cameraHandledTurns = mutableSetOf<String>()

    /** 已经驱动过"3 秒静默自动收起"的回合 key（每个回合只计时一次）。 */
    private val autoCollapseHandledTurns = mutableSetOf<String>()

    private val _bottomBarMode = MutableStateFlow(BottomBarMode.Expanded)
    val bottomBarMode: StateFlow<BottomBarMode> = _bottomBarMode.asStateFlow()

    /** 返回 true 表示这是该回合第一次请求接管，调用方可以开始镜头动画。 */
    fun claimCameraTurn(turnKey: String): Boolean = cameraHandledTurns.add(turnKey)

    /** 返回 true 表示这是该回合第一次请求计时，调用方可以开始 3 秒静默计时。 */
    fun claimAutoCollapseTurn(turnKey: String): Boolean = autoCollapseHandledTurns.add(turnKey)

    fun setBottomBarMode(mode: BottomBarMode) {
        _bottomBarMode.value = mode
    }

    /**
     * 列表的滚动位置。同样必须跨屏存活：ChatScreen 切走即销毁，
     * ScalingLazyListState 跟着一起没，返回时列表会跳回顶部 —— 用户看到的是"镜头抽搐/内容错位"。
     */
    // Int.MAX_VALUE 会被 ScalingLazyListState 夹到最后一项：冷启动直接落在最底部，
    // 不再"先渲染顶部再滚到底"（那是首次 0 -> 列表末尾的定位过程）。
    var savedCenterItemIndex: Int = Int.MAX_VALUE
        private set
    var savedCenterItemScrollOffset: Int = 0
        private set

    fun saveListPosition(centerItemIndex: Int, centerItemScrollOffset: Int) {
        savedCenterItemIndex = centerItemIndex
        savedCenterItemScrollOffset = centerItemScrollOffset
    }

    /**
     * 设置里改「简洁模式」要立刻生效。
     *
     * 以前 compactMode 只在 init 时读一次，之后全仓没有任何回写路径 ——
     * 打开开关后正文行高不变，必须杀进程重启。
     */
    fun syncCompactMode(enabled: Boolean) {
        if (_uiState.value.compactMode != enabled) {
            _uiState.update { it.copy(compactMode = enabled) }
        }
    }

    init {
        gateway.onRequireConfirmation = { toolName, _, risk -> awaitApprovalDecision(toolName, risk) }
        // 供应商拒收 reasoning_effort：引擎已去掉字段重试，这里只负责把"已忽略"讲清楚
        gateway.onReasoningEffortIgnored = {
            showNotice(R.string.wearchat_notice_reasoning_effort_ignored, isError = false)
        }

        restoreSessions()
        persistSessions()
    }

    /** 启动时恢复上次会话；历史同时灌回引擎，于是模型接着上文说，而不是从零开始。 */
    private fun restoreSessions() {
        val snapshot = sessionStore?.load() ?: return
        val active = snapshot.sessions.firstOrNull { it.id == snapshot.activeSessionId }
            ?: snapshot.sessions.firstOrNull()
            ?: return
        _uiState.update { state ->
            state.copy(
                sessions = snapshot.sessions,
                activeSessionId = active.id,
                agentState = state.agentState.copy(conversationBlocks = active.conversationBlocks)
            )
        }
        gateway.loadSession(active.history)
    }

    /** 会话一变就落盘；流式回答期间变更密集，用 collectLatest + delay 合并成一次写。 */
    private fun persistSessions() {
        val store = sessionStore ?: return
        workScope.launch {
            uiState
                .map { it.sessions to it.activeSessionId }
                .distinctUntilChanged()
                .collectLatest { (sessions, activeId) ->
                    delay(SESSION_SAVE_DEBOUNCE_MS)
                    saveSessions(store, sessions, activeId)
                }
        }
    }

    /**
     * 写盘结果必须变成可观测状态：把 [SessionStore.save] 的返回值丢掉就等于回到空 catch。
     * 先隔 [SESSION_SAVE_RETRY_MS] 重试一次（瞬时失败不该立刻惊动用户），
     * 重试仍失败才置 [ChatUiState.sessionSaveFailed]；下一次写成功立刻清掉它。
     * 手表上不弹窗、不 Toast，这个标记由聊天页渲染成一行说明。
     */
    private suspend fun saveSessions(store: SessionStore, sessions: List<ChatSession>, activeId: String) {
        if (store.save(sessions, activeId).isSuccess) {
            markSessionSaveFailed(false)
            return
        }
        delay(SESSION_SAVE_RETRY_MS)
        markSessionSaveFailed(store.save(sessions, activeId).isFailure)
    }

    /** 只在值真的翻转时发新快照：写盘是高频路径，别为每一份相同的状态再推一次。 */
    private fun markSessionSaveFailed(failed: Boolean) {
        _uiState.update { state ->
            if (state.sessionSaveFailed == failed) state else state.copy(sessionSaveFailed = failed)
        }
    }

    override fun onCleared() {
        gateway.onRequireConfirmation = null
        gateway.onReasoningEffortIgnored = null
        replySpeaker?.release()
        replySpeaker = null
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

        // 合帧窗口来自设置项 stream_emit_interval_ms：每轮开场重读一次，
        // 于是设置页调完不必杀进程，下一轮就按新窗口合帧
        throttle.updateInterval(gateway.displayPreferences().streamEmitIntervalMs.toLong())
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

    /** 停止：取消引擎执行 + 拒绝挂起审批 + 放开审批闩锁。 */
    fun stopAgent() {
        // 决策锁：塌缩动画期间"放行"已经生效，停止键不允许再把它改写成拒绝
        if (_uiState.value.isApprovalCollapsing) return
        gateway.stop()
        pendingContinuation?.resume(false)
        pendingContinuation = null
        _uiState.update {
            it.copy(
                // 挂起项必须跟停止一起清：底栏强制展开、浮层文案与滑块风险都读它，
                // 留着等于"按了停止，审批还在等一个永远不会来的结论"。
                pendingApproval = null,
                // 闩锁是字段唯一还需要显式放开的写入，形态本身一律派生
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
            newText = StreamingTail.of(newState.conversationBlocks),
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
            speakReplyIfEnabled(newState.conversationBlocks)
            _uiState.update {
                it.copy(
                    isExecuting = false,
                    isThinkingActive = false,
                    // 触发 30 度表镜掠影反光
                    isTaskCompleted = true
                )
            }
        } else if (!newState.isThinking && !newState.isExecutingTool) {
            // 思考/工具阶段收尾：仍挂着审批说明引擎还在等结论，这一格不能抹
            _uiState.update {
                if (it.isApprovalFocus) it else it.copy(pendingApproval = null)
            }
        }
    }

    /**
     * 自动播报：只有设置里打开"自动播报回答"且这一轮确有一段非空助手正文时才朗读。
     * 没有 TTS 引擎、初始化失败都只记日志（见 [ReplySpeaker]），绝不影响对话链路。
     */
    private fun speakReplyIfEnabled(blocks: List<ConversationBlock>) {
        val display = gateway.displayPreferences()
        val text = ReplySpeech.spokenText(display.autoSpeakReplies, blocks) ?: return
        val speaker = replySpeaker ?: ReplySpeaker(getApplication<Application>()).also { replySpeaker = it }
        speaker.speak(text, display.ttsSpeechRate, display.ttsVolume)
    }

    // ======================== 胶囊展开态 ========================

    /**
     * 思考/工具胶囊的展开态：唯一真相在这里，Composable 只回调这个键。
     * 键与 block id 一对一，因此增量的重写不会把展开态冲掉。
     */
    fun toggleCapsule(key: String) {
        _uiState.update { it.copy(expandedCapsules = CapsuleExpansion.toggle(it.expandedCapsules, key)) }
    }

    // ======================== 审批 ========================

    /** 审批挂起：状态先落地（浮层立刻出现），续体最后挂上，顺序与引擎回调一致。 */
    private suspend fun awaitApprovalDecision(toolName: String, risk: RiskAssessment): Boolean {
        _uiState.update {
            it.copy(
                pendingApproval = PendingApproval(
                    toolName = toolName,
                    risk = risk,
                    command = risk.commandText
                ),
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
            // 先摘掉本轮的状态与续体，最后才交回结论：resume 会就地继续引擎，
            // 引擎可能立刻挂上第二次审批，晚一步清状态会把新审批连同它的续体一起抹掉。
            val answered = pendingContinuation
            pendingContinuation = null
            _uiState.update {
                it.copy(
                    isApprovalCollapsing = false,
                    bottomBlockMode = BottomBlockMode.IDLE,
                    pendingApproval = null
                )
            }
            answered?.resume(true)
        }
    }

    /**
     * 记住后放行：先把归一化命令写进 allowlist，再走正常放行。
     * 破坏性命令写不进去（[AgentPreferences.rememberShellCommand] 直接拒绝），
     * 因此这条路径不可能把危险命令变成"以后不再问"。
     */
    fun approvePendingAndRemember() {
        val pending = _uiState.value.pendingApproval
        if (pendingContinuation == null || _uiState.value.isApprovalCollapsing) return
        // 键就取 [ApprovalRememberPolicy] 的那一个：写进 allowlist 的字符串与 AgentDispatcher
        // 下次命中判定读的字符串同源，中间不再有第二套归一化。
        val key = ApprovalRememberPolicy.keyOf(pending?.risk)
        if (pending == null || key == null) {
            approvePending()
            return
        }
        gateway.updatePreferences { it.rememberShellCommand(key) }
        approvePending()
    }

    /** 拒绝 / 返回键退出审批：立刻交回 false，不走塌缩动画。 */
    fun rejectPending() {
        // 决策锁：放行后的 220ms 塌缩窗口内按返回/点拒绝，会把已生效的"放行"改写成"拒绝"
        // （危险命令被真正拒绝，与用户刚才的操作相反）。锁用已有的塌缩状态，不新增字段。
        if (_uiState.value.isApprovalCollapsing) return
        // 与放行同一条顺序：先摘状态与续体再交回结论，否则引擎就地发起的下一次审批会被这里抹掉。
        val answered = pendingContinuation
        pendingContinuation = null
        _uiState.update { it.copy(bottomBlockMode = BottomBlockMode.IDLE, pendingApproval = null) }
        answered?.resume(false)
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

    // ======================== 草稿与文字输入 ========================

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

    // ======================== 语音 ========================

    fun onVoiceListeningChanged(listening: Boolean) {
        _uiState.update { it.copy(isRecordingVoice = listening) }
    }

    /**
     * 语音识别的中间结果：只有设置里打开"边说边显示"才写进草稿；
     * 关闭时草稿一动不动，最终结果仍走 [onVoiceResult] 提交。
     */
    fun onVoicePartial(partial: String) {
        val live = gateway.displayPreferences().asrLiveTranscript
        _uiState.update { current ->
            val merged = VoiceDraftGate.merge(current.draft.text, partial, live)
            if (merged == current.draft.text) {
                current
            } else {
                current.copy(draft = current.draft.reduce(TextInputEvent.Change(merged)))
            }
        }
    }

    fun onVoiceResult(recognizedText: String) {
        _uiState.update {
            it.copy(isRecordingVoice = false)
        }
        triggerAgent(recognizedText)
    }

    fun onVoiceError(rawMessage: String?) {
        _uiState.update {
            it.copy(isRecordingVoice = false)
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
        /** 流式回答期间会话高频变化，合并成一次写盘，避免每条增量都碰磁盘。 */
        private const val SESSION_SAVE_DEBOUNCE_MS = 800L

        /** 写盘失败后的重试间隔：一次瞬时失败不必惊动用户，但也不该拖到下一次变更才重试。 */
        private const val SESSION_SAVE_RETRY_MS = 1200L

        /**
         * 生产装配：把 Android 侧协作者（引擎 / 偏好）一次性接进 ViewModel。
         * 上下文取 Application —— 引擎内部只用注册广播、剪贴板与系统服务，与 Activity 上下文等价且不泄漏。
         */
        fun factory(
            application: Application,
            gateway: AgentGateway
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ChatViewModel(
                    application = application,
                    gateway = gateway
                ) as T
        }
    }
}
