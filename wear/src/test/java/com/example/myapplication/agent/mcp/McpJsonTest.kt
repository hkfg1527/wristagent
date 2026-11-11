package com.example.myapplication.agent.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class McpJsonTest {

    @Test
    fun writesCompactJsonAndReadsItBack() {
        val value = McpJson.obj(
            "jsonrpc" to McpJson.Str("2.0"),
            "id" to McpJson.Num(1.0),
            "ok" to McpJson.Bool(true),
            "nothing" to McpJson.Null,
            "items" to McpJson.Arr(listOf(McpJson.Str("a"), McpJson.Num(2.5)))
        )
        assertEquals(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"ok\":true,\"nothing\":null,\"items\":[\"a\",2.5]}",
            McpJson.write(value)
        )
        assertEquals(value, McpJson.parse(McpJson.write(value)))
    }

    @Test
    fun escapesQuotesBackslashesAndControlChars() {
        val text = "he said \"hi\"\nline2\tend\\path"
        val written = McpJson.write(McpJson.Str(text))
        assertTrue("引号必须转义：" + written, written.contains("\\\""))
        assertTrue("换行必须转义：" + written, written.contains("\\n"))
        assertTrue("制表符必须转义：" + written, written.contains("\\t"))
        assertTrue("反斜杠必须转义：" + written, written.contains("\\\\"))
        assertEquals(text, McpJson.parse(written).stringValue())
        assertEquals("\"中文\"", McpJson.write(McpJson.Str("中文")))
    }

    @Test
    fun parsesNestedDocumentAndUnicodeEscapes() {
        val raw = "{\"a\":{\"b\":[1,-2.5,true,null]},\"text\":\"\\u4e2d\\u6587\",\"empty\":{}}"
        val parsed = McpJson.parse(raw)
        val list = parsed.field("a")!!.field("b")!!.arrayItems()
        assertEquals(1.0, list[0].numberValue()!!, 0.0001)
        assertEquals(-2.5, list[1].numberValue()!!, 0.0001)
        assertEquals(true, list[2].boolValue())
        assertTrue(list[3] is McpJson.Null)
        assertEquals("中文", parsed.field("text")!!.stringValue())
        assertTrue(parsed.field("empty")!!.objectFields().isEmpty())
    }

    @Test
    fun rejectsMalformedJson() {
        val broken = listOf("", "{", "{\"a\":}", "{'a':1}", "[1,]", "{\"a\":1}extra", "tru")
        for (raw in broken) {
            try {
                McpJson.parse(raw)
                fail("必须拒绝非法 JSON：" + raw)
            } catch (_: McpJson.McpJsonException) {
                // 期望：抛可判定的解析异常，而不是随机的运行时异常
            }
        }
    }
}
