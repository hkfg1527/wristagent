package com.example.myapplication.agent

import com.example.myapplication.agent.remote.ConsoleJson

/** 自写工具的声明档位；实际执行时以渲染后的命令重新分档，声明值只用于展示与审批文案。 */
enum class SelfToolDanger(val storage: String) {
    SAFE("safe"),
    RISKY("risky"),
    DESTRUCTIVE("destructive");

    companion object {
        fun fromStorage(raw: String?): SelfToolDanger =
            values().firstOrNull { it.storage.equals(raw?.trim(), ignoreCase = true) } ?: RISKY
    }
}

/**
 * Agent 给自己定义的一条工具：命令模板 + $1..$9 位置参数。
 *
 * 模板渲染后的命令仍然穿过 [ShellPolicy] 重新分档：声明 SAFE 但渲染出 "rm -rf /data"
 * 的工具同样按破坏性命令处理，声明档位不能用来绕过审批。
 */
data class SelfDefinedTool(
    val name: String,
    val description: String,
    val shellTemplate: String,
    val danger: SelfToolDanger = SelfToolDanger.RISKY
) {
    /** 把位置参数按序填进 $1..$9；缺参填空串，未出现的占位符原样保留。 */
    fun bind(args: List<String>): String {
        if (shellTemplate.isEmpty()) return ""
        val out = StringBuilder(shellTemplate.length)
        var index = 0
        while (index < shellTemplate.length) {
            val ch = shellTemplate[index]
            val next = shellTemplate.getOrNull(index + 1)
            if (ch == '\u0024' && next != null && next.isDigit()) {
                var cursor = index + 1
                var position = 0
                while (cursor < shellTemplate.length && shellTemplate[cursor].isDigit()) {
                    position = position * 10 + (shellTemplate[cursor] - '0')
                    cursor++
                }
                out.append(args.getOrNull(position - 1).orEmpty())
                index = cursor
                continue
            }
            out.append(ch)
            index++
        }
        return out.toString()
    }
}

object SelfDefinedToolBook {
    const val MaxTools = 8
    const val MinNameLength = 3
    const val MaxNameLength = 32
    const val MaxDescriptionLength = 200
    const val MaxTemplateLength = 400

    /**
     * 内置工具名：自写工具不得与之重名，否则等于偷偷替换内置能力。
     * 新增内置工具时同步这里。
     */
    val BuiltInToolNames: Set<String> = setOf(
        "get_device_status",
        "vibrate_device",
        "adjust_matrix_density",
        "change_matrix_mode",
        "set_system_power_mode",
        "query_time_weather",
        "web_search",
        ShellPolicy.TOOL_RUN_SHELL,
        ShellPolicy.TOOL_DEFINE_TOOL,
        ShellPolicy.TOOL_LIST_TOOLS,
        ShellPolicy.TOOL_REMOVE_TOOL
    )

    private val namePattern = Regex("^[a-z][a-z0-9_]{2,31}$")

    /** null 表示合法；非 null 是要回给模型的拒绝原因。 */
    fun validate(
        name: String,
        description: String,
        template: String,
        reserved: Set<String> = BuiltInToolNames
    ): String? {
        val trimmedName = name.trim()
        if (!namePattern.matches(trimmedName)) {
            return "工具名不合法：必须 " + MinNameLength + "-" + MaxNameLength + " 位、小写字母开头、只含 a-z0-9_"
        }
        if (trimmedName in reserved) return "工具名与内置工具冲突：" + trimmedName
        val trimmedDescription = description.trim()
        if (trimmedDescription.isEmpty()) return "工具描述不可为空"
        if (trimmedDescription.length > MaxDescriptionLength) {
            return "工具描述过长（上限 " + MaxDescriptionLength + " 字符）"
        }
        if (template.trim().isEmpty()) return "命令模板不可为空"
        if (template.length > MaxTemplateLength) return "命令模板过长（上限 " + MaxTemplateLength + " 字符）"
        return null
    }

    /** 同名覆盖即"改定义"，不占新名额；满额时新增一律原样返回。 */
    fun upsert(tools: List<SelfDefinedTool>, tool: SelfDefinedTool): List<SelfDefinedTool> {
        val existing = tools.indexOfFirst { it.name == tool.name }
        if (existing >= 0) {
            val next = tools.toMutableList()
            next[existing] = tool
            return next
        }
        if (tools.size >= MaxTools) return tools
        return tools + tool
    }

    fun remove(tools: List<SelfDefinedTool>, name: String): List<SelfDefinedTool> =
        tools.filterNot { it.name == name.trim() }
}

/**
 * 自写工具的 JSON 持久化（单一字段落盘到 AgentPreferences）。
 *
 * 不用 org.json：它在本模块的 JVM 单测里被 android.jar 的桩替换，方法一律返回默认值，
 * 那样往返测试就成了空断言。这里复用控制台那套已验证的极简编解码。
 */
object SelfDefinedToolCodec {
    private const val Version = 1
    private const val KeyVersion = "version"
    private const val KeyTools = "tools"

    fun encode(tools: List<SelfDefinedTool>): String {
        if (tools.isEmpty()) return ""
        val payload = mapOf(
            KeyVersion to Version,
            KeyTools to tools.map { tool ->
                mapOf(
                    "name" to tool.name,
                    "description" to tool.description,
                    "template" to tool.shellTemplate,
                    "danger" to tool.danger.storage
                )
            }
        )
        return ConsoleJson.write(payload)
    }

    fun decode(raw: String?): List<SelfDefinedTool> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = ConsoleJson.parseObject(raw) ?: return emptyList()
        val entries = root[KeyTools] as? List<*> ?: return emptyList()
        return entries.mapNotNull { entry ->
            val map = entry as? Map<*, *> ?: return@mapNotNull null
            val name = (map["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null
            SelfDefinedTool(
                name = name,
                description = (map["description"] as? String).orEmpty(),
                shellTemplate = (map["template"] as? String).orEmpty(),
                danger = SelfToolDanger.fromStorage(map["danger"] as? String)
            )
        }.distinctBy { it.name }.take(SelfDefinedToolBook.MaxTools)
    }
}
