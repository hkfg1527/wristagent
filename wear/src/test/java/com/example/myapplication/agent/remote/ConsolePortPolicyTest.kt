package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsolePortPolicyTest {

    @Test
    fun candidatesWalkForwardFromPreferredPort() {
        assertEquals(listOf(8080, 8081, 8082), ConsolePortPolicy.candidates())
        assertEquals(listOf(9000, 9001), ConsolePortPolicy.candidates(preferred = 9000, attempts = 2))
        assertEquals(listOf(8080), ConsolePortPolicy.candidates(attempts = 1))
        assertTrue(ConsolePortPolicy.candidates(attempts = 0).isEmpty())
    }

    @Test
    fun selectPrefersFirstBindablePort() {
        assertEquals(8080, ConsolePortPolicy.select { true })
        assertEquals(8081, ConsolePortPolicy.select { it >= 8081 })
        assertEquals(8082, ConsolePortPolicy.select { it >= 8082 })
    }

    @Test
    fun selectReturnsMinusOneWhenEveryCandidateIsBusy() {
        assertEquals(-1, ConsolePortPolicy.select { false })
        assertEquals(-1, ConsolePortPolicy.select(preferred = 9000, attempts = 2) { false })
    }
}
