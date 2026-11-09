package com.example.myapplication.presentation

import androidx.annotation.StringRes
import com.example.myapplication.agent.AgentExecutionState
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.MainInputType
import com.example.myapplication.presentation.ui.chat.CapsuleExpansion
import com.example.myapplication.presentation.ui.settings.SettingsSpacingMath

/** 默认会话首条欢迎语：新建会话与首个会话共用同一条文案。 */
const val DEFAULT_SESSION_PROMPT = "主人，今天有什么需要解决的技术或生活问题？"

/** 瞬时提示可见时长。 */
const val NOTICE_VISIBLE_MS = 2600L

/** 放行塌缩动画时长：动画走完才把结论交回 Agent。 */
const val ApprovalCollapseDurationMs = 220

/** 审批挂起项：文案浮层与底栏滑轨共用同一份风险评估。 */
data class PendingApproval(
    val toolName: String,
    val risk: RiskAssessment,
    /** 待执行命令原文：run_shell 与自写工具才有值，浮层用它替掉"改动"那一块。 */
    val command: String = "",
    /** 是否允许"记住此命令"：破坏性命令恒为 false，记住也永不生效。 */
    val rememberable: Boolean = false
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
    val notice: ChatNotice?,
    /**
     * 已展开的胶囊键（见 CapsuleKeys）：把展开态提升到 ViewModel，
     * 列表滚动回收、重组、切到设置页再返回都不会把用户点开的胶囊折回去。
     */
    val expandedCapsules: Set<String> = emptySet(),
    /**
     * 简洁模式：只收紧正文行高与块间距（几何映射见 [ChatBodyGeometry.of]）。值来自设置项 compactMode，
     * 本层只保证这个事实只有一个来源，换算交给纯函数、渲染交给列表读取。
     */
    val compactMode: Boolean = false
) {
    /** 时间线交错块：列表分组与镜头跟随的唯一数据源。 */
    val blocks: List<ConversationBlock> get() = agentState.conversationBlocks

    /** 某粒胶囊是否处于展开态。 */
    fun isCapsuleExpanded(key: String): Boolean = CapsuleExpansion.isExpanded(expandedCapsules, key)

    /**
     * 审批沉浸态：居中文字浮层接管，列表退到 35%。
     * [bottomBlockMode] 现在只作为审批闩锁被写（ACTION_ALLOW / 放行拒绝后清回 IDLE），
     * 因此它既是沉浸态也是底栏强制展开的唯一输入。
     */
    val isApprovalFocus: Boolean get() = bottomBlockMode == BottomBlockMode.ACTION_ALLOW

    /**
     * 底栏强制展开态：执行中、审批（含放行塌缩）、录音、输入弹窗、抽屉。
     * 这些状态下按钮必须完整展开且不可收起，否则收起的小白条会盖住当前唯一的可交互入口。
     */
    val isBottomBarForceExpanded: Boolean
        get() = isApprovalFocus ||
            isExecuting ||
            pendingApproval != null ||
            isRecordingVoice ||
            draft.isOpen ||
            isSessionDrawerOpen

    val activeSession: ChatSession get() = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.first()

    companion object {
        /** 启动态：单会话、就绪、空闲底栏；[compactMode] 取设置里的简洁模式。 */
        fun initial(mainInputType: MainInputType, compactMode: Boolean = false): ChatUiState {
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
                notice = null,
                compactMode = compactMode
            )
        }
    }
}

/** 正文排版几何：只含紧凑模式会动的两个量，其余（字号、颜色、卡片宽度）一律不动。 */
data class BodyTextGeometry(
    val lineHeightSp: Float,
    val blockSpacingDp: Float
)

/**
 * [ChatUiState.compactMode] 的几何映射（纯函数，唯一来源）：常规 22sp 行高 + 5dp 块间距，
 * 紧凑模式行高 22→20sp、块间距 5→1dp（减 4dp）。
 *
 * 只做两组定点减法，不按比例缩放：按比例压行高会把中文行间墨距压到 3dp 以下、整段读成一堵灰墙
 * （行距令牌 [SettingsSpacingMath.BodyLineHeightSp] 的来历就是这个），而字号本身保持不变。
 */
object ChatBodyGeometry {
    /** 常规行高：直接引用设置页的中文行距令牌（20sp），不另立第二个数值。 */
    const val RegularLineHeightSp = SettingsSpacingMath.BodyLineHeightSp

    /** 紧凑行高：只压 2sp，从常规行高派生 —— 行距令牌改一次这里自动跟，不会再漂。 */
    const val CompactLineHeightSp = SettingsSpacingMath.BodyLineHeightSp - 2f

    /** 常规正文块间距（dp）：助手回合内多段正文之间。 */
    const val RegularBlockSpacingDp = 5f

    /** 紧凑态块间距缩减量（dp）。 */
    const val CompactBlockSpacingReductionDp = 4f

    /** 紧凑块间距（dp）：5 - 4 = 1dp，恒为正，两段正文不会叠在一起。 */
    const val CompactBlockSpacingDp = RegularBlockSpacingDp - CompactBlockSpacingReductionDp

    fun of(compactMode: Boolean): BodyTextGeometry = if (compactMode) {
        BodyTextGeometry(lineHeightSp = CompactLineHeightSp, blockSpacingDp = CompactBlockSpacingDp)
    } else {
        BodyTextGeometry(lineHeightSp = RegularLineHeightSp, blockSpacingDp = RegularBlockSpacingDp)
    }
}

/**
 * 底栏形态的唯一派生来源（纯函数）：审批 > 录音 > 执行中 > 空闲。
 *
 * 四个写者（延迟拉长成停止胶囊、引擎吐字、语音开关、审批续体）以前各自 copy 同一个字段，
 * 后写者能覆盖先写者 —— 真机上表现为"点过按钮之后状态混乱"：录音被思考态抢走、
 * 正文一开始流式就没了停止键、审批放行后按钮回不来。
 * 形态只由快照派生，写入顺序不再影响结果；非审批形态的写入因此全部删除，
 * 字段只剩审批闩锁这一个语义（[isApprovalFocus]），不再有第二个真相。
 */
fun ChatUiState.deriveBottomBlockMode(): BottomBlockMode = when {
    isApprovalFocus -> BottomBlockMode.ACTION_ALLOW
    isRecordingVoice -> BottomBlockMode.RECORDING
    isExecuting -> BottomBlockMode.THINKING_STOP
    else -> BottomBlockMode.IDLE
}

/** 持久化的主键输入方式 -> 枚举：只认 "KEYBOARD"，其余一律语音优先。 */
fun mainInputTypeFromStored(stored: String?): MainInputType =
    if (stored == "KEYBOARD") MainInputType.KEYBOARD else MainInputType.VOICE
