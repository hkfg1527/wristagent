package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleAuthThrottleTest {

    @Test
    fun fiveFailuresLockTheConsole() {
        val throttle = ConsoleAuthThrottle()
        repeat(4) { throttle.onFailure(1_000L) }
        assertFalse(throttle.isLocked(1_000L))
        throttle.onFailure(1_000L)
        assertTrue(throttle.isLocked(1_000L))
    }

    @Test
    fun lockExpiresAfterSixtySeconds() {
        val throttle = ConsoleAuthThrottle()
        repeat(5) { throttle.onFailure(0L) }
        assertTrue(throttle.isLocked(59_999L))
        assertFalse(throttle.isLocked(60_000L))
        assertEquals(1_000L, throttle.remainingLockMs(59_000L))
    }

    @Test
    fun successClearsTheFailureCount() {
        val throttle = ConsoleAuthThrottle()
        repeat(4) { throttle.onFailure(0L) }
        throttle.onSuccess()
        repeat(4) { throttle.onFailure(0L) }
        assertFalse("成功一次后计数必须清零", throttle.isLocked(0L))
    }

    @Test
    fun lockIsCountedFromTheMomentOfTheFifthFailure() {
        val throttle = ConsoleAuthThrottle()
        repeat(5) { throttle.onFailure(10_000L) }
        assertTrue(throttle.isLocked(69_999L))
        assertFalse(throttle.isLocked(70_000L))
    }
}
