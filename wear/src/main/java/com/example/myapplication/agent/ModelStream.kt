package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 模型流式增量：正文、思考与工具调用共用同一条结构化出口。
 * 调度引擎只消费这里的事件，不再把 SSE 解析与时间线拼装混在同一个循环里。
 */
sealed class StreamDelta {
    /** 正文增量。 */
    data class Content(val text: String) : StreamDelta()

    /** 思考（reasoning_content / reasoning）增量，与正文分开累积。 */
    data class Reasoning(val text: String) : StreamDelta()

    /** 工具调用分片：name 通常一次到齐，arguments 按字符串分片到达。 */
    data class ToolCall(
        val index: Int,
        val id: String?,
        val nameDelta: String,
        val argumentsDelta: String
    ) : StreamDelta()

    /** 供应商的结束标记（data: [DONE]）。 */
    object Done : StreamDelta()
}

/** 单条 delta 里与工具调用相关的原始字段。 */
data class ToolCallFields(
    val index: Int = 0,
    val id: String? = null,
    val name: String? = null,
    val arguments: String? = null
)

/**
 * 单条 delta 的原始字段（已从 JSON 取出）。
 * 解析规则刻意只依赖这份结构：JSON 适配层留在 [ModelStreamParser]，业务分流逻辑可被单测。
 */
data class DeltaFields(
    val reasoningContent: String? = null,
    val reasoning: String? = null,
    val content: String? = null,
    val toolCalls: List<ToolCallFields> = emptyList()
)

/** delta 字段 -> 事件序列：空片段不产生事件，思考与正文互不吞并。 */
object StreamEventMapper {
    fun eventsOf(fields: DeltaFields): List<StreamDelta> {
        val out = mutableListOf<StreamDelta>()
        // 思考：reasoning_content 是 DeepSeek 的正式字段，reasoning 是 OpenAI 兼容网关的别名
        val reasoning = fields.reasoningContent?.takeIf { it.isNotEmpty() }
            ?: fields.reasoning?.takeIf { it.isNotEmpty() }
        if (reasoning != null) out.add(StreamDelta.Reasoning(reasoning))
        fields.content?.takeIf { it.isNotEmpty() }?.let { out.add(StreamDelta.Content(it)) }
        fields.toolCalls.forEach { call ->
            val id = call.id?.takeIf { it.isNotEmpty() }
            val name = call.name.orEmpty()
            val arguments = call.arguments.orEmpty()
            // 只有 index 没有内容的分片不产生事件：时间线上不该出现空胶囊
            if (id == null && name.isEmpty() && arguments.isEmpty()) return@forEach
            out.add(StreamDelta.ToolCall(call.index, id, name, arguments))
        }
        return out
    }
}

/**
 * 增量累加器：思考与正文各存一份，工具调用按 index 分片拼接成完整 [ToolCall]。
 * 时间线（胶囊）由调度引擎按事件类型拼装，这里只保证数据不串线。
 */
class StreamAccumulator {
    private val reasoningBuilder = StringBuilder()
    private val contentBuilder = StringBuilder()
    private val toolCallParts = mutableMapOf<Int, ToolCallPart>()

    /** 已累积的思考文本。 */
    val reasoning: String get() = reasoningBuilder.toString()

    /** 已累积的正文文本。 */
    val content: String get() = contentBuilder.toString()

    /** 应用一批事件（不做 JSON 解析，便于单测）。 */
    fun applyAll(deltas: List<StreamDelta>) {
        deltas.forEach { delta ->
            when (delta) {
                is StreamDelta.Content -> contentBuilder.append(delta.text)
                is StreamDelta.Reasoning -> reasoningBuilder.append(delta.text)
                is StreamDelta.ToolCall -> {
                    val part = toolCallParts.getOrPut(delta.index) { ToolCallPart() }
                    if (!delta.id.isNullOrBlank()) part.id = delta.id
                    part.name.append(delta.nameDelta)
                    part.arguments.append(delta.argumentsDelta)
                }
                StreamDelta.Done -> Unit
            }
        }
    }

    /** 解析一行 SSE 并累积，返回该行产生的事件。 */
    fun applyLine(rawLine: String): List<StreamDelta> {
        val deltas = ModelStreamParser.parseLine(rawLine)
        applyAll(deltas)
        return deltas
    }

    /** 拼接完成的工具调用；没有名字的分片视为噪声丢弃，顺序按供应商给的 index。 */
    fun toolCalls(): List<ToolCall> =
        toolCallParts.toSortedMap().values.mapNotNull { part ->
            val name = part.name.toString()
            if (name.isBlank()) {
                null
            } else {
                ToolCall(
                    id = part.id.ifBlank { "call_" + UUID.randomUUID().toString().take(8) },
                    name = name,
                    arguments = part.arguments.toString()
                )
            }
        }

    fun reset() {
        reasoningBuilder.setLength(0)
        contentBuilder.setLength(0)
        toolCallParts.clear()
    }

    private class ToolCallPart {
        var id: String = ""
        val name = StringBuilder()
        val arguments = StringBuilder()
    }
}

/**
 * 思考耗时：以**请求发出**为起点、本轮收尾为终点的墙钟跨度。
 * 起点缺失或时钟回拨一律算 0，绝不出现负数；换算成"秒"由界面层负责。
 */
object ThoughtDuration {
    fun between(startedAtMs: Long, finishedAtMs: Long): Long =
        if (startedAtMs <= 0L || finishedAtMs <= startedAtMs) 0L else finishedAtMs - startedAtMs
}

/**
 * 思考胶囊时间线：SSE 读取循环与"胶囊怎么长"解耦的纯状态机。
 *
 * 每个 reasoning 增量改写同一粒胶囊并返回 true —— 上层据此**每来一块就提交一次状态**，
 * 这就是"思考流式上屏"的粒度保证；起点固定为**请求发出**的时刻（不是第一个增量到达的时刻，
 * 更不是响应到达的时刻），于是收尾耗时是这一轮真实的墙钟跨度，不会退化成 0s。
 */
class ThoughtTimeline(
    private val requestStartedAtMs: Long,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    private var blockId: String? = null
    private var startedAtMs = 0L

    /** 已经建起来的胶囊 id；还没收到任何思考增量时为 null。 */
    val id: String? get() = blockId

    /** 已收到的思考增量块数，用于日志核对"到底流了几块"。 */
    var incrementCount: Int = 0
        private set

    /** 距思考起点已经过去多久（进行中给日志与标题用）。 */
    fun elapsedMs(): Long = ThoughtDuration.between(startedAtMs, clock())

    /** 收尾耗时：请求发出到本轮结束的墙钟跨度。 */
    fun durationMs(finishedAtMs: Long): Long = ThoughtDuration.between(startedAtMs, finishedAtMs)

    /**
     * 一个思考增量：第一次建块，之后原地改写。返回 true 表示这次需要向 UI 提交状态。
     * 空增量不建块也不提交 —— 时间线上不该出现一粒什么都没想的胶囊。
     */
    fun onReasoning(blocks: MutableList<ConversationBlock>, reasoning: String): Boolean {
        if (reasoning.isEmpty()) return false
        incrementCount++
        if (startedAtMs <= 0L) startedAtMs = if (requestStartedAtMs > 0L) requestStartedAtMs else clock()
        val id = blockId
        if (id == null) {
            val newId = idFactory()
            blockId = newId
            blocks.add(
                ConversationBlock.Thought(
                    id = newId,
                    reasoning = reasoning,
                    isStreaming = true,
                    startedAtMs = startedAtMs
                )
            )
            return true
        }
        val idx = blocks.indexOfFirst { it.id == id }
        if (idx == -1) return false
        blocks[idx] = ConversationBlock.Thought(
            id = id,
            reasoning = reasoning,
            isStreaming = true,
            startedAtMs = startedAtMs
        )
        return true
    }

    /** 收尾：胶囊从"思考中"翻成"思考完成 · Ns"。返回 true 表示需要提交一次状态。 */
    fun finish(blocks: MutableList<ConversationBlock>, reasoning: String, finishedAtMs: Long): Boolean {
        val id = blockId ?: return false
        val idx = blocks.indexOfFirst { it.id == id }
        if (idx == -1) return false
        blocks[idx] = ConversationBlock.Thought(
            id = id,
            reasoning = reasoning,
            durationMs = durationMs(finishedAtMs),
            isStreaming = false,
            startedAtMs = startedAtMs
        )
        return true
    }
}

/**
 * SSE 行 -> 事件：只认 data: 行，[DONE] 结束，注释/心跳/坏 JSON 一律丢弃。
 * 这是唯一接触 JSON 的薄适配层，字段分流规则仍交给 [StreamEventMapper]。
 */
object ModelStreamParser {

    /** 一行 SSE -> 事件：注释、心跳、非 data 行与坏 JSON 一律返回空列表。 */
    fun parseLine(rawLine: String): List<StreamDelta> {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith(":")) return emptyList()
        if (!line.startsWith(DATA_PREFIX)) return emptyList()
        val payload = line.removePrefix(DATA_PREFIX).trim()
        if (payload.isEmpty()) return emptyList()
        if (payload == DONE_MARKER) return listOf(StreamDelta.Done)
        val chunk = try {
            JSONObject(payload)
        } catch (_: Exception) {
            return emptyList()
        }
        val choice = chunk.optJSONArray("choices")?.optJSONObject(0) ?: return emptyList()
        // 流式走 delta，非流式回放走 message：两份字段同名，共用同一套分流规则
        val body = choice.optJSONObject("delta") ?: choice.optJSONObject("message") ?: return emptyList()
        return StreamEventMapper.eventsOf(fieldsOf(body))
    }

    /** 非流式响应里的 message 对象 -> 事件。 */
    fun messageEvents(message: JSONObject): List<StreamDelta> = StreamEventMapper.eventsOf(fieldsOf(message))

    private fun fieldsOf(source: JSONObject): DeltaFields = DeltaFields(
        reasoningContent = piece(source, "reasoning_content").ifEmpty { null },
        reasoning = piece(source, "reasoning").ifEmpty { null },
        content = piece(source, "content").ifEmpty { null },
        toolCalls = toolCallFields(source.optJSONArray("tool_calls"))
    )

    private fun piece(source: JSONObject, key: String): String {
        if (!source.has(key) || source.isNull(key)) return ""
        return source.optString(key, "")
    }

    private fun toolCallFields(array: JSONArray?): List<ToolCallFields> {
        if (array == null || array.length() == 0) return emptyList()
        val out = mutableListOf<ToolCallFields>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val fn = item.optJSONObject("function")
            out.add(
                ToolCallFields(
                    index = item.optInt("index", i),
                    id = piece(item, "id").ifEmpty { null },
                    name = fn?.let { piece(it, "name") },
                    arguments = fn?.let { piece(it, "arguments") }
                )
            )
        }
        return out
    }

    private const val DATA_PREFIX = "data:"
    private const val DONE_MARKER = "[DONE]"
}