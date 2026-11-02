package com.example.myapplication.agent.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** baseUrl → 主机名：体检的所有请求都打在这个主机上，解析错就会体检错目标。 */
class NetworkTargetTest {

    @Test
    fun plainHttpsUrlYieldsHost() {
        assertEquals("api.deepseek.com", NetworkTarget.hostOf("https://api.deepseek.com"))
    }

    @Test
    fun missingSchemeIsTreatedAsHttps() {
        assertEquals("api.deepseek.com", NetworkTarget.hostOf("api.deepseek.com/v1/chat"))
    }

    @Test
    fun portAndPathAreStripped() {
        assertEquals("1.2.3.4", NetworkTarget.hostOf("http://1.2.3.4:8080/x"))
    }

    @Test
    fun credentialsAreStripped() {
        assertEquals("host.example", NetworkTarget.hostOf("https://user:pw@host.example/a/b"))
    }

    @Test
    fun hostIsLowerCased() {
        assertEquals("api.deepseek.com", NetworkTarget.hostOf("https://API.DeepSeek.COM/v1"))
    }

    @Test
    fun bracketedIpv6LiteralLosesItsBrackets() {
        assertEquals("2001:db8::1", NetworkTarget.hostOf("https://[2001:db8::1]:8443/v1"))
    }

    @Test
    fun blankInputYieldsNull() {
        assertNull(NetworkTarget.hostOf(""))
        assertNull(NetworkTarget.hostOf("   "))
    }

    @Test
    fun unparsableInputYieldsNull() {
        assertNull(NetworkTarget.hostOf("https://not a url"))
    }
}
