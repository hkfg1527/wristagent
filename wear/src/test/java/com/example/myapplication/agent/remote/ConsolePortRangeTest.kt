package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Test

/** 端口策略只允许 1..65535：0 是内核临时端口，越界端口会让 NanoHTTPD 抛错或让 port 报 0。 */
class ConsolePortRangeTest {

    @Test
    fun normalPreferredPortKeepsTheDefaultFallbackChain() {
        assertEquals(listOf(8080, 8081, 8082), ConsolePortPolicy.candidates())
    }

    @Test
    fun candidatesSkipPortZeroAndOutOfRangeValues() {
        assertEquals("0 是临时端口，候选里不能出现它", listOf(1, 2), ConsolePortPolicy.candidates(0, 3))
        assertEquals("负数端口一律跳过", emptyList<Int>(), ConsolePortPolicy.candidates(-5, 3))
        assertEquals("超过 65535 的候选必须截断", listOf(65534, 65535), ConsolePortPolicy.candidates(65534, 3))
    }

    @Test
    fun selectReturnsMinusOneWhenNothingCanBeBound() {
        assertEquals(-1, ConsolePortPolicy.select(preferred = 65536, attempts = 2) { true })
        assertEquals(65535, ConsolePortPolicy.select(preferred = 65534, attempts = 3) { it == 65535 })
    }
}
