package com.example.myapplication.agent.mcp

import org.json.JSONArray
import org.json.JSONObject

/**
 * MCP 内部 JSON 与 org.json 的唯一交界点。
 *
 * ToolDefinition.parametersSchema 是既有工具契约里已经定死的 JSONObject，
 * 所以远端 inputSchema 必须在最后一步搬进 org.json；除此之外 MCP 链路不碰 org.json，
 * 单测才能在 mockable android.jar 上跑出真实结果。
 */
internal object McpJsonBridge {

    fun toParametersSchema(schema: McpJson.Obj?): JSONObject {
        val root = JSONObject()
        runCatching {
            root.put("type", "object")
            schema?.fields.orEmpty().forEach { entry ->
                if (entry.key == "type") return@forEach
                root.put(entry.key, toOrgJson(entry.value))
            }
        }
        return root
    }

    private fun toOrgJson(value: McpJson): Any = when (value) {
        is McpJson.Obj -> JSONObject().apply {
            value.fields.forEach { entry -> put(entry.key, toOrgJson(entry.value)) }
        }

        is McpJson.Arr -> JSONArray().apply {
            value.items.forEach { item -> put(toOrgJson(item)) }
        }

        is McpJson.Str -> value.value
        is McpJson.Bool -> value.value
        is McpJson.Num -> value.value
        McpJson.Null -> JSONObject.NULL
    }
}
