package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyStyleTest {
    @Test
    fun storedValuesAreUniqueAndUnknownFallsBackToBalanced() {
        assertEquals(
            ReplyStyle.values().size,
            ReplyStyle.values().map { it.storageKey }.toSet().size
        )
        assertEquals(ReplyStyle.BALANCED, ReplyStyle.fromStored(null))
        assertEquals(ReplyStyle.BALANCED, ReplyStyle.fromStored("wild"))
        ReplyStyle.values().forEach { assertEquals(it, ReplyStyle.fromStored(it.storageKey)) }
    }

    @Test
    fun everyStyleHasItsOwnStorageKey() {
        val keys = ReplyStyle.values().map { it.storageKey }
        assertTrue(keys.all { it.isNotBlank() })
    }
}
