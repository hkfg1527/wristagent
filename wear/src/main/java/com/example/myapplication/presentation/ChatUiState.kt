package com.example.myapplication.presentation

import androidx.annotation.StringRes
import com.example.myapplication.agent.AgentExecutionState
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.MainInputType

/** 默认会话首条欢迎语：新建会话与首个会话共用同一条文案。 */
const val DEFAULT_SESSION_PROMPT = "主人，今天有什么需要解决的技术或生活问题？"

/** 瞬时提示可见时长。 */
const val NOTICE_VISIBLE_MS = 2600L

/** 思考态拉长成"停止"胶囊前的延迟：更快会让刚发出的消息看着像立刻要停。 */
const val ThinkingStretchDelayMs = 300L

/** 放行塌缩动画时长：动画走完才把结论交回 Agent。 */
const val ApprovalCollapseDurationMs = 220

/** 审批挂起项：文案浮层与底栏滑轨共用同一份风险评估。 */
data class PendingApproval(
    val toolName: String,
    val risk: RiskAssessment
)

/**
 * 瞬时提示条内容：语音错误与主键模式反馈共用一条通道。
 * 只携带资源 id 与占位参数，文案解析交给 UI 层。
 */
data class ChatNotice(
    @StringRes val textRes: Int,
    val args: List<Any> = emptyList(),
    val isError: Boolean
)

/**
 * 聊天主界面的只读快照：Composable 只读这里，所有可变状态都在 [ChatViewModel] 内推进。
 * 刻意保留 [agentState] 整体而非逐字段摊平——交错流的审批光相、错误文案与执行标志同源同一份状态。
 */
data class ChatUiState(
    val agentState: AgentExecutionState,
    val sessions: List<ChatSession>,
    val activeSessionId: String,
    val isExecuting: Boolean,
    val isThinkingActive: Boolean,
    val isStreamingBurst: Boolean,
    val isTaskCompleted: Boolean,
    val bottomBlockMode: BottomBlockMode,
    val mainInputType: MainInputType,
    val pendingApproval: PendingApproval?,
    val approvalProgress: Float,
    val isApprovalCollapsing: Boolean,
    val isSessionDrawerOpen: Boolean,
    val isRecordingVoice: Boolean,
    val draft: TextInputState,
    val notice: ChatNotice?
) {
    /** 时间线交错块：列表分组与镜头跟随的唯一数据源。 */
    val blocks: List<ConversationBlock> get() = agentState.conversationBlocks

    /** 审批沉浸态：居中文字浮层接管，列表退到 35%。 */
    val isApprovalFocus: Boolean get() = bottomBlockMode == BottomBlockMode.ACTION_ALLOW

    /**
     * 底栏强制展开态：执行中、审批（含放行塌缩）、录音、输入弹窗、抽屉。
     * 这些状态下按钮必须完整展开且不可收起，否则收起的小白条会盖住当前唯一的可交互入口。
     */
    val isBottomBarForceExpanded: Boolean
        get() = bottomBlockMode != BottomBlockMode.IDLE ||
            isExecuting ||
            pendingApproval != null ||
            isRecordingVoice ||
            draft.isOpen ||
            isSessionDrawerOpen

    val activeSession: ChatSession get() = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()

    companion object {
        /** 启动态：单会话、就绪、空闲底栏。 */
        fun initial(mainInputType: MainInputType): ChatUiState {
            val firstSession = ChatSession(
                id = "session_default",
                title = "当前对话",
                conversationBlocks = listOf(
                    ConversationBlock.Text(
                        id = "init_0",
                        content = DEFAULT_SESSION_PROMPT,
                        isUser = false
                    )
                )
            )
            return ChatUiState(
                agentState = AgentExecutionState(
                    prompt = "就绪",
                    conversationBlocks = firstSession.conversationBlocks,
                    isFinished = true
                ),
                sessions = listOf(firstSession),
                activeSessionId = firstSession.id,
                isExecuting = false,
                isThinkingActive = false,
                isStreamingBurst = false,
                isTaskCompleted = false,
                bottomBlockMode = BottomBlockMode.IDLE,
                mainInputType = mainInputType,
                pendingApproval = null,
                approvalProgress = 0f,
                isApprovalCollapsing = false,
                isSessionDrawerOpen = false,
                isRecordingVoice = false,
                draft = TextInputState(),
                notice = null
            )
        }
    }
}

/** 持久化的主键输入方式 -> 枚举：只认 "KEYBOARD"，其余一律语音优先。 */
fun mainInputTypeFromStored(stored: String?): MainInputType =
    if (stored == "KEYBOARD") MainInputType.KEYBOARD else MainInputType.VOICE
