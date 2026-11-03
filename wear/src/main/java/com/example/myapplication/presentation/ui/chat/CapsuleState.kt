package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.agent.ThoughtDuration
import com.example.myapplication.agent.contract.ConversationBlock

/**
 * 胶囊展开态的持久化键：与 block id 一对一，重组、滚动回收、切屏返回都能找回同一粒胶囊。
 * 键里带类型前缀，思考胶囊与工具胶囊即使 id 撞车也不会互相展开。
 */
object CapsuleKeys {
    private const val THOUGHT_PREFIX = "thought:"
    private const val TOOL_PREFIX = "tool:"
    private const val TEXT_PREFIX = "text:"

    fun of(block: ConversationBlock): String = when (block) {
        is ConversationBlock.Thought -> THOUGHT_PREFIX + block.id
        is ConversationBlock.ToolCall -> TOOL_PREFIX + block.id
        is ConversationBlock.Text -> TEXT_PREFIX + block.id
    }
}

/** 展开态集合的纯逻辑：状态提升到 ViewModel 后，这里只做不可变集合变换。 */
object CapsuleExpansion {
    fun toggle(expanded: Set<String>, key: String): Set<String> =
        if (expanded.contains(key)) expanded - key else expanded + key

    fun isExpanded(expanded: Set<String>, key: String): Boolean = expanded.contains(key)
}

/**
 * 思考耗时换算：引擎记下的是墙钟毫秒（起点 = 请求发出时刻），界面只负责换算成"秒"。
 * 跨度计算复用引擎侧的 [ThoughtDuration]，两层不会算出不同的数。
 */
object ThoughtTiming {
    /** 显示用秒数：向下取整，因此 999ms 仍然报 0s，与"进行中"的读法一致。 */
    fun seconds(elapsedMs: Long): Int = if (elapsedMs <= 0L) 0 else (elapsedMs / 1000L).toInt()
}

/** 思考胶囊标题：进行中报已用秒数，结束后报总耗时；点阵省略号按帧循环。 */
object ThoughtCapsuleState {
    fun isStreaming(thought: ConversationBlock.Thought): Boolean = thought.isStreaming

    fun elapsedSeconds(thought: ConversationBlock.Thought, nowMs: Long): Int {
        // 进行中按墙钟累加（起点 = 请求发出时刻），收尾直接读引擎记下的真实跨度
        val elapsedMs = if (thought.isStreaming) {
            ThoughtDuration.between(thought.startedAtMs, nowMs)
        } else {
            thought.durationMs
        }
        return ThoughtTiming.seconds(elapsedMs)
    }

    /** 闪烁省略号的点数：1..3 循环。 */
    fun thinkingDots(frame: Int): String = ".".repeat(frame.mod(3) + 1)
}

/**
 * 流式节流用的"屏幕尾部文本"：正文带 isStreaming 时取正文，思考带 isStreaming 时取思考。
 * 两类增量共用同一个 32ms 通道，突发判定衡量的始终是屏幕上最后一段正在长的文字。
 */
object StreamingTail {
    fun of(blocks: List<ConversationBlock>): String {
        blocks.asReversed().forEach { block ->
            when (block) {
                is ConversationBlock.Text -> if (block.isStreaming) return block.content
                is ConversationBlock.Thought -> if (block.isStreaming) return block.reasoning
                is ConversationBlock.ToolCall -> Unit
            }
        }
        return ""
    }
}