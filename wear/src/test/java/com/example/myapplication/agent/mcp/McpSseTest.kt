package com.example.myapplication.agent.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpSseTest {

    @Test
    fun framesSkipCommentsAndJoinMultiLineData() {
        val body = ": keep-alive\n\nevent: message\nid: 1\ndata: {\"a\":1}\n\ndata: {\"b\":\ndata: 2}\n\n"
        assertEquals(listOf("{\"a\":1}", "{\"b\":\n2}"), McpSse.frames(body))
    }

    @Test
    fun payloadsAcceptPlainJsonAndEventStream() {
        assertEquals(
            listOf("{\"id\":1}"),
            McpSse.payloads("{\"id\":1}\n", "application/json; charset=utf-8")
        )
        assertEquals(
            listOf("{\"id\":1}"),
            McpSse.payloads("data: {\"id\":1}\n\n", "text/event-stream")
        )
        assertTrue(McpSse.isEventStream("text/event-stream; charset=utf-8"))
        assertTrue("application/json 不是 SSE", !McpSse.isEventStream("application/json"))
    }

    @Test
    fun findsOnlyTheMatchingJsonRpcId() {
        val candidates = listOf(
            "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}",
            "{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}",
            "not json at all"
        )
        assertEquals("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}", McpSse.findResponse(candidates, 7L))
        assertNull("id 不匹配时不能随便拿一条当结果", McpSse.findResponse(candidates, 8L))
    }
}
