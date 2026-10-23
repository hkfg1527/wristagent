package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RiskAssessment
import org.json.JSONArray
import org.json.JSONObject

/**
 * 审批态的视觉光相变枚举
 */
enum class ApprovalVisualState {
    NONE,           // 常态
    PENDING_CASCADE,// 审批中：顶部重力红瀑下倾
    APPROVED_GREEN, // 同意放行：瞬时转翡翠绿
    REJECTED_EBB    // 拒绝退潮：红瀑向上收敛
}

/**
 * Agent 消息角色
 */
object AgentRole {
    const val SYSTEM = "system"
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
}

/**
 * 结构化工具调用描述
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String
) {
    fun parseArgs(): JSONObject {
        return try {
            JSONObject(arguments)
        } catch (_: Exception) {
            JSONObject()
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("type", "function")
        val fnObj = JSONObject()
        fnObj.put("name", name)
        fnObj.put("arguments", arguments)
        obj.put("function", fnObj)
        return obj
    }

    companion object {
        fun fromJson(json: JSONObject): ToolCall {
            val id = json.optString("id", System.currentTimeMillis().toString())
            val fn = json.optJSONObject("function") ?: JSONObject()
            val name = fn.optString("name", "")
            val args = fn.optString("arguments", "{}")
            return ToolCall(id, name, args)
        }
    }
}

/**
 * 工具定义 (Function Calling Schema)
 */
data class ToolDefinition(
    val name: String,
    val description: String,
    val parametersSchema: JSONObject
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("type", "function")
        val fn = JSONObject()
        fn.put("name", name)
        fn.put("description", description)
        fn.put("parameters", parametersSchema)
        root.put("function", fn)
        return root
    }
}

/**
 * 工具执行结果
 */
data class ToolResult(
    val toolCallId: String,
    val toolName: String,
    val output: String,
    val isError: Boolean = false
)

/**
 * 对话消息体
 */
data class ChatMessage(
    val role: String,
    val content: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null,
    val name: String? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("role", role)
        if (content != null) obj.put("content", content)
        if (!toolCalls.isNullOrEmpty()) {
            val arr = JSONArray()
            toolCalls.forEach { arr.put(it.toJson()) }
            obj.put("tool_calls", arr)
        }
        if (toolCallId != null) obj.put("tool_call_id", toolCallId)
        if (name != null) obj.put("name", name)
        return obj
    }
}

/**
 * 完整的 Agent 步进执行状态记录
 */
data class AgentExecutionState(
    val prompt: String,
    val thoughtChain: String = "",
    val activeToolCalls: List<ToolCall> = emptyList(),
    val completedToolResults: List<ToolResult> = emptyList(),
    val finalResponse: String = "",
    val conversationBlocks: List<ConversationBlock> = emptyList(), // 时间线交错流
    val approvalVisualState: ApprovalVisualState = ApprovalVisualState.NONE, // 视觉光相变
    val pendingRiskAssessment: RiskAssessment? = null,              // 当前待审批的风险评估
    val isThinking: Boolean = false,
    val isExecutingTool: Boolean = false,
    val isFinished: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 独立的聊天会话记录 (支持穿戴原生多会话切换、连贯追加、新建与关闭)
 */
data class ChatSession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "新对话",
    val createdAt: Long = System.currentTimeMillis(),
    val conversationBlocks: List<ConversationBlock> = listOf(
        ConversationBlock.Text(
            id = java.util.UUID.randomUUID().toString(),
            content = "主人，今天有什么需要解决的技术或生活问题？",
            isUser = false
        )
    ),
    val history: List<ChatMessage> = emptyList()
)
