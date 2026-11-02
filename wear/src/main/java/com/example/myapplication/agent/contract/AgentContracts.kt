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

    /**
     * 深度思考小胶囊：平时折叠为紧凑药丸，点开在胶囊内部流式滚动显示思考文本。
     * [isStreaming] 为真时标题报"思考中"，为假时标题报"思考完成 · Ns"（总耗时取 [durationMs]）。
     * [startedAtMs] 是该粒胶囊第一次拿到思考增量的墙钟时刻，用于进行中每秒刷新已用时间。
     */
    data class Thought(
        override val id: String,
        val reasoning: String,
        val durationMs: Long = 0,
        val isStreaming: Boolean = false,
        val startedAtMs: Long = 0L
    ) : ConversationBlock()

    /** 工具调用小胶囊：穿插在两段话之间，展示调用状态与清洗后的结果 */
    data class ToolCall(
        override val id: String,
        val toolName: String,
        val argsJson: String,
        val resultSummary: String? = null,
        val isExecuting: Boolean = false
    ) : ConversationBlock()
}

/**
 * 三级安全审查模式 (对标 Operit 权限确认策略)
 */
enum class SecurityMode {
    NOVICE,     // 严格确认 (默认)：全部命令审批 + 危险操作锁定 3 秒 + 详细说明
    STANDARD,   // 标准模式：智能预审，只读放行，高风险操作需滑动确认
    EXPERT      // 自动执行：除硬黑名单外全部直接执行
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
 * AI 判定的风险等级（审批页危险条与文案共用）
 */
enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

/**
 * 安全审查评估结果
 */
data class RiskAssessment(
    val isHardBlocked: Boolean = false,       // 是否命中硬黑名单
    val isHighRisk: Boolean = false,          // 是否属于中高风险操作
    val riskSummary: String = "",             // 大白话风险描述/最严重后果说明
    val requireCooldownSeconds: Int = 0,      // 审批滑块强制冷却倒计时 (秒)
    val whatItDoes: String = "",              // 这条命令会做什么（AI 生成，<=40 汉字）
    val worstCase: String = "",               // 最坏情况（AI 生成，<=40 汉字）
    val aiRiskLevel: RiskLevel = RiskLevel.MEDIUM
)
