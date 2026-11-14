package com.example.myapplication.agent

import com.example.myapplication.agent.net.DiagnosticTimeouts
import com.example.myapplication.agent.remote.ConsoleAuthThrottle
import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「连得上」分区的两个新设置：体检探测超时（net_diag_timeout_ms）与控制台防爆破上限（console_max_failures）。
 *
 * 体检超时是"网络慢的时候体检别自己先超时"；防爆破上限是"别让局域网里一台机器把合法用户关在门外"。
 */
class ConnectivityLimitsTest {

    @Test
    fun theDiagnosticTimeoutSplitsIntoTwoProbes() {
        val t = DiagnosticTimeouts.of(3_000)
        assertEquals(3_000, t.tcpMs)
        assertEquals("双栈探测比单栈快一档", 2_500, t.stackMs)
    }

    @Test
    fun theDiagnosticTimeoutIsClampedToOneThroughTenSeconds() {
        assertEquals(1_000, DiagnosticTimeouts.of(0).tcpMs)
        assertEquals(10_000, DiagnosticTimeouts.of(99_999).tcpMs)
        assertEquals("下限下探时双栈不能变成 0", 500, DiagnosticTimeouts.of(1_000).stackMs)
    }

    @Test
    fun theDiagnosticsEngineUsesTheConfiguredTimeouts() {
        val source = readProjectFile("src/main/java/com/example/myapplication/agent/net/NetworkDiagnostics.kt")
        assertTrue("TCP 探测必须用参数", source.contains("connectTcp(host, HTTPS_PORT, timeouts.tcpMs)"))
        assertTrue("双栈探测必须用参数", source.contains("timeouts.stackMs, address = it"))
        assertTrue(
            "不许有第二份夹取规则",
            source.contains("fun of(preferenceMs: Int): DiagnosticTimeouts")
        )
    }

    @Test
    fun theConsoleThrottleTakesItsLimitFromThePreference() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals("出厂值就是控制台自己的常量", ConsoleAuthThrottle.MAX_FAILURES, p.consoleMaxFailures)
        p.consoleMaxFailures = 1
        assertEquals(AgentPreferences.MIN_CONSOLE_MAX_FAILURES, p.consoleMaxFailures)
        p.consoleMaxFailures = 99
        assertEquals(AgentPreferences.MAX_CONSOLE_MAX_FAILURES, p.consoleMaxFailures)
        p.consoleMaxFailures = 8
        assertEquals(8, p.consoleMaxFailures)
    }

    @Test
    fun theBridgeHandsTheLimitToTheThrottle() {
        val bridge = readProjectFile("src/main/java/com/example/myapplication/agent/remote/ConsoleBridge.kt")
        assertTrue(
            "控制台必须按偏好构造防爆破节流器（上限与锁定窗口都要读偏好）",
            bridge.contains("maxFailures = preferences.consoleMaxFailures") &&
                bridge.contains("lockDurationMs = preferences.consoleLockoutMinutes * 60_000L")
        )
    }
}
