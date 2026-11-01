package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleTokenTest {

    @Test
    fun generatedTokenIs32LowercaseHex() {
        val token = ConsoleToken.generate()
        assertEquals(ConsoleToken.LENGTH, token.length)
        assertTrue("非十六进制: " + token, token.all { it in '0'..'9' || it in 'a'..'f' })
        assertTrue(ConsoleToken.isValidFormat(token))
    }

    @Test
    fun generatedTokensDifferBetweenCalls() {
        assertNotEquals(ConsoleToken.generate(), ConsoleToken.generate())
    }

    @Test
    fun formatRejectsForeignShapes() {
        assertFalse(ConsoleToken.isValidFormat(null))
        assertFalse(ConsoleToken.isValidFormat(""))
        assertFalse(ConsoleToken.isValidFormat("abc"))
        assertFalse(ConsoleToken.isValidFormat("zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"))
        assertFalse(ConsoleToken.isValidFormat("ab12cd34ef56ab78cd90ef12ab34cd5"))
        assertTrue(ConsoleToken.isValidFormat("AB12CD34EF56AB78CD90EF12AB34CD56"))
    }

    @Test
    fun matchesOnlyExactToken() {
        val token = ConsoleToken.generate()
        assertTrue(ConsoleToken.matches(token, token))
        assertFalse(ConsoleToken.matches(token, token.dropLast(1)))
        assertFalse(ConsoleToken.matches(token, token + "0"))
        assertFalse(ConsoleToken.matches(token, token.uppercase()))
        assertFalse(ConsoleToken.matches(token, null))
        assertFalse(ConsoleToken.matches("", ""))
    }

    @Test
    fun maskKeepsOnlyEdges() {
        assertEquals("ab12…cd56", ConsoleToken.mask("ab12cd34ef56ab78cd90ef12ab34cd56"))
        assertEquals("…", ConsoleToken.mask("short"))
        assertEquals("…", ConsoleToken.mask(null))
    }
}
