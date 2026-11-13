package com.example.myapplication.agent

import com.example.myapplication.agent.remote.ConsoleFields
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 网络与触觉五项：默认值、写入口的夹取、跨实例持久化、控制台登记。
 *
 * 这几项都有真实消费点（AgentDispatcher 的读超时/重试/仅 WiFi，以及 vibrateTick 的触觉闸门），
 * 所以夹取逻辑必须落在属性里：控制台、导入导出、设置页三条写入路径共用同一处校验。
 */
class NetworkSettingsPreferenceTest {

    @Test
    fun defaultsMatchTheDocumentedValues() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        assertEquals(40, prefs.requestTimeoutSeconds)
        assertEquals(1, prefs.requestRetries)
        assertEquals(800, prefs.retryBackoffMs)
        assertFalse(prefs.wifiOnlyRequests)
        assertTrue(prefs.hapticsEnabled)
    }

    @Test
    fun timeoutIsClampedIntoTheSupportedWindow() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.requestTimeoutSeconds = 5
        assertEquals(10, prefs.requestTimeoutSeconds)
        prefs.requestTimeoutSeconds = 999
        assertEquals(180, prefs.requestTimeoutSeconds)
        prefs.requestTimeoutSeconds = 45
        assertEquals(45, prefs.requestTimeoutSeconds)
    }

    @Test
    fun retriesAndBackoffAreClamped() {
        val prefs = AgentPreferences(FakeSharedPreferences())
        prefs.requestRetries = 9
        assertEquals(3, prefs.requestRetries)
        prefs.requestRetries = -2
        assertEquals(0, prefs.requestRetries)
        prefs.retryBackoffMs = -100
        assertEquals(0, prefs.retryBackoffMs)
        prefs.retryBackoffMs = 99999
        assertEquals(5000, prefs.retryBackoffMs)
    }

    @Test
    fun valuesSurviveANewRepositoryOverTheSameStore() {
        val store = FakeSharedPreferences()
        val first = AgentPreferences(store)
        first.requestTimeoutSeconds = 90
        first.requestRetries = 2
        first.retryBackoffMs = 1200
        first.wifiOnlyRequests = true
        first.hapticsEnabled = false

        val second = AgentPreferences(store)
        assertEquals(90, second.requestTimeoutSeconds)
        assertEquals(2, second.requestRetries)
        assertEquals(1200, second.retryBackoffMs)
        assertTrue(second.wifiOnlyRequests)
        assertFalse(second.hapticsEnabled)
    }

    @Test
    fun everyNewKeyIsRegisteredInTheConsoleWithItsRange() {
        val keys = listOf(
            "requestTimeoutSeconds",
            "requestRetries",
            "retryBackoffMs",
            "wifiOnlyRequests",
            "hapticsEnabled"
        )
        keys.forEach { key ->
            val field = ConsoleFields.ALL.firstOrNull { it.key == key }
            assertTrue(key + " 必须登记进控制台字段表", field != null)
            assertFalse(key + " 不该是只读字段", field!!.readOnly)
        }
        val timeout = ConsoleFields.ALL.first { it.key == "requestTimeoutSeconds" }
        assertEquals(10.0, timeout.min!!, 0.001)
        assertEquals(180.0, timeout.max!!, 0.001)
        val retries = ConsoleFields.ALL.first { it.key == "requestRetries" }
        assertEquals(3.0, retries.max!!, 0.001)
    }
}
