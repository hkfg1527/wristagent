package com.example.myapplication.agent

import com.example.myapplication.agent.contract.SecurityMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * 子代理模式策略（纯逻辑，不碰 Android 与网络）。
 *
 * 开关只是"申请"，[isEffective] 才决定是否真的生效：只有专家模式 + 开关打开同时成立才生效。
 * 其余模式里开关可以留着（不丢用户设置），但工具清单里没有这一条，也没有任何执行路径。
 */
object SubAgentPolicy {
    /** 子代理里不许再开子代理：主循环在 depth 0，子代理在 depth 1。 */
    const val MAX_DEPTH = 1

    const val TOOL_NAME = "run_subagent"

    fun isEffective(mode: SecurityMode, enabled: Boolean): Boolean =
        mode == SecurityMode.EXPERT && enabled

    /**
     * 允许在 [depth] 处开子代理吗。[maxDepth] 来自设置项 subagent_max_depth：0 = 关闭，1 = 一层。
     */
    fun depthAllowed(depth: Int, maxDepth: Int = MAX_DEPTH): Boolean =
        depth >= 0 && depth < maxDepth.coerceIn(0, MAX_DEPTH)

    /** 工具清单只有一份真源：不生效时返回空表，调用方 addAll 之后清单里就没有这一行（不是灰显）。 */
    fun toolDefinitions(mode: SecurityMode, enabled: Boolean): List<ToolDefinition> =
        if (isEffective(mode, enabled)) listOf(toolDefinition()) else emptyList()

    fun toolDefinition(): ToolDefinition = ToolDefinition(
        name = TOOL_NAME,
        description = "把一段独立的子任务派给子代理：子代理用独立对话上下文跑自己的工具循环，" +
            "只把结论带回来，主对话不会被它的中间过程污染。子代理里不能再开子代理，失败会以 error 回执返回",
        parametersSchema = JSONObject().apply {
            put("type", "object")
            val props = JSONObject()
            props.put(
                "task",
                JSONObject().apply {
                    put("type", "string")
                    put(
                        "description",
                        "交给子代理的完整任务描述：它看不到主对话，需要的信息必须在里写清"
                    )
                }
            )
            props.put(
                "focus",
                JSONObject().apply {
                    put("type", "string")
                    put("description", "可选，只关心/只回答的角度，例如“只要结论与数字，不要过程”")
                }
            )
            put("properties", props)
            put("required", JSONArray(listOf("task")))
        }
    )
}
