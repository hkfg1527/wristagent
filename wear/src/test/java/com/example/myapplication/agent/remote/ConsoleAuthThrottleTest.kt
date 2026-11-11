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

    @Test
    fun failuresFromOnePeerDoNotLockAnother() {
        val throttle = ConsoleAuthThrottle()
        repeat(5) { throttle.onFailure(1_000L, "10.0.0.9") }
        assertTrue("攻击者自己必须被锁", throttle.isLocked(1_000L, "10.0.0.9"))
        assertFalse("同一局域网里合法用户不得被邻居锁死", throttle.isLocked(1_000L, "192.168.1.20"))
    }

    @Test
    fun successFromOnePeerDoesNotClearAnotherPeersFailures() {
        val throttle = ConsoleAuthThrottle()
        repeat(4) { throttle.onFailure(0L, "10.0.0.9") }
        throttle.onSuccess("192.168.1.20")
        throttle.onFailure(0L, "10.0.0.9")
        assertTrue("别的来源成功一次不能给攻击者清零", throttle.isLocked(0L, "10.0.0.9"))
    }

    @Test
    fun peerTableHasAnUpperBound() {
        val throttle = ConsoleAuthThrottle()
        for (index in 0 until 5_000) {
            throttle.onFailure(1_000L, "peer-" + index)
        }
        assertTrue("限流桶表必须有上界，否则伪造大量来源就能撑爆内存", throttle.trackedPeers() <= 256)
    }
}
