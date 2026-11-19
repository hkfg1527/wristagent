package com.example.myapplication.agent.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JSON 解析器的嵌套深度上限。
 *
 * 回归背景（0.0.0.113 审查，高）：`McpJson` 的 `parseValue` 是**无限深递归**，
 * 而 `McpClient` / `McpSse` 只 catch `McpJsonException` —— 远端回一段 `[[[[…` 就是
 * `StackOverflowError`：那是 `Error` 不是 `Exception`，工具循环的 catch 拦不住，整个 App 直接崩。
 * 控制台侧的 `ConsoleJson` 早有 `MAX_DEPTH = 32`，这里一直漏着。
 */
class McpJsonDepthTest {

    @Test
    fun deeplyNestedArraysAreRejectedInsteadOfBlowingTheStack() {
        val deep = "[".repeat(200) + "]".repeat(200)
        val error = runCatching { McpJson.parse(deep) }.exceptionOrNull()
        assertTrue("必须抛 McpJsonException（而不是 StackOverflowError）：" + error, error is McpJson.McpJsonException)
        assertTrue("错误信息要说明是嵌套层数问题：" + error?.message, error?.message?.contains("嵌套") == true)
    }

    @Test
    fun deeplyNestedObjectsAreRejectedToo() {
        val deep = "{\"a\":".repeat(200) + "1" + "}".repeat(200)
        val error = runCatching { McpJson.parse(deep) }.exceptionOrNull()
        assertTrue("对象嵌套同样要挡住：" + error, error is McpJson.McpJsonException)
    }

    @Test
    fun aNormalProtocolMessageStillParses() {
        // 真实 MCP 报文大约 4~6 层，远在 32 层以内
        val normal = """
            {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"echo","inputSchema":
            {"type":"object","properties":{"text":{"type":"string"}}}}]}}
        """.trimIndent().replace("\n", "")
        val parsed = McpJson.parse(normal)
        assertEquals("2.0", parsed.field("jsonrpc")?.stringValue())
    }
}
