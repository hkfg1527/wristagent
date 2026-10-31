package com.example.myapplication.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BaiduTokenCacheTest {

    @Test
    fun storesAndReturnsTokenWithinValidity() {
        var now = 1_700_000_000_000L
        val cache = BaiduTokenCache { now }

        cache.store("AK-1", "24.token-a", 2592000L)

        assertEquals("24.token-a", cache.cached("AK-1"))
    }

    @Test
    fun dropsTokenAfterExpiryMinusSafetyMargin() {
        var now = 1_700_000_000_000L
        val cache = BaiduTokenCache { now }
        cache.store("AK-1", "24.token-a", 60L)

        now += 1_000L
        assertNull(cache.cached("AK-1"))
    }

    @Test
    fun isolatesTokensByApiKey() {
        var now = 1_700_000_000_000L
        val cache = BaiduTokenCache { now }
        cache.store("AK-1", "24.token-a", 2592000L)

        assertNull(cache.cached("AK-2"))
        assertEquals("24.token-a", cache.cached("AK-1"))
    }

    @Test
    fun invalidationClearsCachedToken() {
        var now = 1_700_000_000_000L
        val cache = BaiduTokenCache { now }
        cache.store("AK-1", "24.token-a", 2592000L)

        cache.invalidate()

        assertNull(cache.cached("AK-1"))
    }
}
