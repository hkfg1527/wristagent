package com.example.myapplication.agent.contract

import org.json.JSONArray
import org.json.JSONObject

/**
 * 交错流内容块：文本与小胶囊在时间线上自然交错排列
 */
sealed class ConversationBlock {
    abstract val id: String

    /** 纯文本对话段落 */
    data class Text(
        override val id: String,
        val content: String,
        val isStreaming: Boolean = false,
        val isUser: Boolean = false
    ) : ConversationBlock()

    /** 深度思考小胶囊：平时折叠为紧凑药丸，可点击展开查看详情 */
    data class Thought(
        override val id: String,
        val reasoning: String,
        val durationMs: Long = 0,
        val isExpanded: Boolean = false
    ) : ConversationBlock()

    /** 工具调用小胶囊：穿插在两段话之间，展示调用状态与清洗后的结果 */
    data class ToolCall(
        override val id: String,
        val toolName: String,
        val argsJson: String,
        val resultSummary: String? = null,
        val isExecuting: Boolean = false,
        val isExpanded: Boolean = false
    ) : ConversationBlock()
}

/**
 * 三级安全审查模式
 */
enum class SecurityMode {
    NOVICE,     // 小白模式 (默认)：全部命令审批 + 大白话说明 + 危险操作强制锁滑块 3 秒
    STANDARD,   // 普通人模式：后台快速模型预审，无风险放行，有风险审批并显示最严重后果说明
    EXPERT      // 大佬模式：除致命黑名单外全部自动放行
}

/**
 * 右侧副键自定义配置
 */
enum class RightButtonAction {
    MODEL_SWITCH,   // 快捷切换 deepseek-chat / deepseek-reasoner (默认)
    CLEAR_MEMORY,   // 一键复位清空会话记忆
    OPEN_SETTINGS   // 打开系统设置
}

/**
 * 安全审查评估结果
 */
data class RiskAssessment(
    val isHardBlocked: Boolean = false,       // 是否命中硬黑名单
    val isHighRisk: Boolean = false,          // 是否属于中高风险操作
    val riskSummary: String = "",             // 大白话风险描述/最严重后果说明
    val requireCooldownSeconds: Int = 0       // 审批滑块强制冷却倒计时 (秒)
)
