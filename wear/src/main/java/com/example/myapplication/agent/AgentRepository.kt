package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment

/**
 * Agent 能力入口契约：UI 层只依赖这一层，不直接持有调度引擎。
 * 每个方法都与 [AgentDispatcher] 的现有签名一一对应，不做任何加工，
 * 因此风险闸门、审批挂起、工具执行与流式回调顺序全部留在引擎内部。
 */
interface AgentGateway {
    /** 审批挂起函数：返回 true 为允许，false 为拒绝。与 AgentDispatcher.onRequireConfirmation 同义。 */
    var onRequireConfirmation: (suspend (toolName: String, actionText: String, risk: RiskAssessment) -> Boolean)?

    /** 发送用户指令并驱动多轮交错流回调；终态通过 [onStateUpdate] 的 isFinished 告知。 */
    suspend fun send(
        prompt: String,
        existingBlocks: List<ConversationBlock>,
        onStateUpdate: (AgentExecutionState) -> Unit
    )

    /** 取消当前执行（引擎内的 isCancelled 闸门）。 */
    fun stop()

    /** 重置当前会话的上下文（重新装载 system 记忆头）。 */
    fun clearSession()

    /** 载入某个会话的历史消息。 */
    fun loadSession(history: List<ChatMessage>)

    /** 当前引擎上下文快照（持久化到会话记录时使用）。 */
    fun snapshotHistory(): List<ChatMessage>

    /** 偏好写入的唯一入口：lambda 直接在真实 AgentPreferences 上生效。 */
    fun updatePreferences(update: (AgentPreferences) -> Unit)

    /** 当前已注册的工具定义。 */
    fun registeredTools(): List<ToolDefinition>

    /** 清空长记忆。 */
    fun clearLongTermMemory()

    /** 长记忆条数。 */
    fun memoryFactCount(): Int
}

/**
 * [AgentGateway] 的唯一实现：把调用原样转交给 [AgentDispatcher]。
 * 这里刻意不含任何业务分支——一旦此处出现 if/else，就说明业务语义被改动了。
 */
class AgentRepository(
    private val dispatcher: AgentDispatcher,
    private val preferences: AgentPreferences
) : AgentGateway {

    override var onRequireConfirmation: (suspend (toolName: String, actionText: String, risk: RiskAssessment) -> Boolean)?
        get() = dispatcher.onRequireConfirmation
        set(value) {
            dispatcher.onRequireConfirmation = value
        }

    override suspend fun send(
        prompt: String,
        existingBlocks: List<ConversationBlock>,
        onStateUpdate: (AgentExecutionState) -> Unit
    ) = dispatcher.executeUserPrompt(
        prompt = prompt,
        existingBlocks = existingBlocks,
        onStateUpdate = onStateUpdate
    )

    override fun stop() = dispatcher.cancelCurrentExecution()

    override fun clearSession() = dispatcher.resetConversation()

    override fun loadSession(history: List<ChatMessage>) = dispatcher.loadConversationHistory(history)

    override fun snapshotHistory(): List<ChatMessage> = dispatcher.getConversationHistory()

    override fun updatePreferences(update: (AgentPreferences) -> Unit) = update(preferences)

    override fun registeredTools(): List<ToolDefinition> = dispatcher.getRegisteredTools()

    override fun clearLongTermMemory() = dispatcher.clearLongTermMemory()

    override fun memoryFactCount(): Int = dispatcher.getMemoryFactCount()
}
