package com.example.myapplication.presentation.ui

import androidx.annotation.StringRes
import com.example.myapplication.R
import com.example.myapplication.agent.contract.ConversationBlock

/**
 * 视觉呈现的对话单元：
 * 1. 用户输入始终以右对齐气泡展示
 * 2. 属于 Assistant 的所有内容（思考胶囊、工具调用胶囊、正文段落）统一汇聚进同一个方形框内部
 */
sealed class RenderableConversationItem {
    /** 用户发起的问题/指令 (右对齐紧凑聊天气泡) */
    data class UserPrompt(val block: ConversationBlock.Text) : RenderableConversationItem()

    /** Assistant 的完整回复回合 (纯正方形模糊框，内嵌思考、工具调用与正文回复) */
    data class AssistantTurn(
        val key: String,
        val blocks: List<ConversationBlock>
    ) : RenderableConversationItem()
}

/**
 * 工具视觉元数据 (色彩系统 + 展示名资源)。
 * 图标位不使用 emoji 或任何外链字形，只由 [tagColorHex] 驱动一个 6dp 纯色圆点。
 * 展示名走资源 id 以便中英切换；[displayNameRes] 为 null 时回落到工具名原文。
 */
data class ToolVisualMeta(
    val toolName: String,
    @StringRes val displayNameRes: Int?,
    val tagColorHex: Long        // 主题色 ARGB Long
) {
    companion object {
        fun resolve(toolName: String): ToolVisualMeta {
            return when (toolName) {
                "web_search" -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = R.string.tools_name_web_search,
                    tagColorHex = 0xFF06B6D4       // 霓虹青蓝
                )
                "query_time_weather" -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = R.string.tools_name_time_weather,
                    tagColorHex = 0xFFF59E0B       // 琥珀金
                )
                "get_device_status" -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = R.string.tools_name_device_status,
                    tagColorHex = 0xFF10B981       // 翡翠绿
                )
                "vibrate_device" -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = R.string.tools_name_vibrate,
                    tagColorHex = 0xFF8B5CF6       // 紫罗兰
                )
                "set_system_power_mode" -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = R.string.tools_name_power_mode,
                    tagColorHex = 0xFFF97316       // 珊瑚橙
                )
                "adjust_matrix_density" -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = R.string.tools_name_matrix,
                    tagColorHex = 0xFF38BDF8       // 极客天蓝
                )
                else -> ToolVisualMeta(
                    toolName = toolName,
                    displayNameRes = null,
                    tagColorHex = 0xFF94A3B8       // 工业冷灰
                )
            }
        }
    }
}

/**
 * 将平铺的交错 blocks 分组为可视化的呈现回合
 */
object ConversationGrouping {
    fun groupBlocks(
        blocks: List<ConversationBlock>,
        activeUserPrompts: Set<String> = emptySet()
    ): List<RenderableConversationItem> {
        if (blocks.isEmpty()) return emptyList()

        val result = mutableListOf<RenderableConversationItem>()
        var currentAssistantBlocks = mutableListOf<ConversationBlock>()

        fun flushAssistant() {
            if (currentAssistantBlocks.isNotEmpty()) {
                val key = currentAssistantBlocks.first().id
                result.add(RenderableConversationItem.AssistantTurn(key, currentAssistantBlocks.toList()))
                currentAssistantBlocks = mutableListOf()
            }
        }

        for (block in blocks) {
            val isUser = block is ConversationBlock.Text && (block.isUser || activeUserPrompts.contains(block.id))
            if (isUser) {
                flushAssistant()
                result.add(RenderableConversationItem.UserPrompt(block as ConversationBlock.Text))
            } else {
                currentAssistantBlocks.add(block)
            }
        }
        flushAssistant()

        return result
    }
}
