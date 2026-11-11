package com.example.myapplication.agent.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpEndpointPolicyTest {

    private fun accept(raw: String): McpEndpoint {
        val verdict = McpEndpointPolicy.parse(raw)
        assertTrue("本应接受：" + raw + "，实际 " + verdict, verdict is McpEndpointVerdict.Accept)
        return (verdict as McpEndpointVerdict.Accept).endpoint
    }

    private fun rejectReason(raw: String): String {
        val verdict = McpEndpointPolicy.parse(raw)
        assertTrue("本应拒绝：" + raw + "，实际 " + verdict, verdict is McpEndpointVerdict.Reject)
        return (verdict as McpEndpointVerdict.Reject).reason
    }

    @Test
    fun acceptsHttpAndHttpsAndFillsDefaultPorts() {
        assertEquals("http://127.0.0.1:8000/sse", accept("  http://127.0.0.1:8000/sse  ").url)
        assertEquals(80, accept("http://example.org/mcp").port)
        assertEquals(443, accept("https://example.org/mcp").port)
        assertEquals("https://example.org/mcp", accept("https://example.org/mcp").url)
        assertEquals("https://example.org:8443/mcp", accept("https://example.org:8443/mcp").url)
        assertTrue(accept("http://127.0.0.1:8000/sse").isLoopback)
        assertFalse(accept("http://mcp.example.org/sse").isLoopback)
    }

    @Test
    fun rejectsEveryNonHttpScheme() {
        for (raw in listOf("", "   ", "ftp://example.org/sse", "file:///etc/passwd", "ws://example.org/sse", "javascript:alert(1)", "example.org/sse", "http:///no-host")) {
            val reason = rejectReason(raw)
            assertTrue("拒绝理由必须说清原因：" + reason, reason.isNotEmpty())
        }
        assertTrue(rejectReason("ftp://example.org/sse").contains("ftp"))
        assertTrue("缺协议要单独说清楚", rejectReason("example.org/sse").contains("协议"))
    }

    @Test
    fun followsOnlySameOriginRedirects() {
        val origin = accept("http://127.0.0.1:8000/sse")

        val relative = McpEndpointPolicy.redirect(origin, 302, "/sse2")
        assertTrue(relative is McpRedirect.Follow)
        assertEquals("http://127.0.0.1:8000/sse2", (relative as McpRedirect.Follow).endpoint.url)

        val crossHost = McpEndpointPolicy.redirect(origin, 302, "http://evil.example:8000/sse")
        assertTrue("跨主机重定向必须拒绝", crossHost is McpRedirect.Refuse)
        val reason = (crossHost as McpRedirect.Refuse).reason
        assertTrue("理由要记下原主机：" + reason, reason.contains("127.0.0.1"))
        assertTrue("理由要记下被指向的主机：" + reason, reason.contains("evil.example"))

        val downgrade = McpEndpointPolicy.redirect(accept("https://mcp.example/sse"), 302, "http://mcp.example/sse")
        assertTrue("https 降级到 http 必须拒绝", downgrade is McpRedirect.Refuse)

        val upgrade = McpEndpointPolicy.redirect(accept("http://mcp.example/sse"), 302, "https://mcp.example/sse")
        assertTrue("同主机 http -> https 升级放行", upgrade is McpRedirect.Follow)

        val crossPort = McpEndpointPolicy.redirect(origin, 302, "http://127.0.0.1:9000/sse")
        assertTrue("同主机换端口也是换源，必须拒绝", crossPort is McpRedirect.Refuse)
    }

    @Test
    fun refusesRedirectWithoutLocation() {
        val origin = accept("http://127.0.0.1:8000/sse")
        val missing = McpEndpointPolicy.redirect(origin, 302, null)
        assertTrue(missing is McpRedirect.Refuse)
        assertTrue((missing as McpRedirect.Refuse).reason.contains("Location"))
    }
}
