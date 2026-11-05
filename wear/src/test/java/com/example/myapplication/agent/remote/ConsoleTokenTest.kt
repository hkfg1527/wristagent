package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleTokenTest {

    @Test
    fun generatedTokenIsFourDigits() {
        val token = ConsoleToken.generate()
        assertEquals(ConsoleToken.LENGTH, token.length)
        assertEquals(4, token.length)
        assertTrue("必须全是数字: " + token, token.all { it in '0'..'9' })
        assertTrue(ConsoleToken.isValidFormat(token))
    }

    @Test
    fun generatedTokensDifferBetweenCalls() {
        assertNotEquals(ConsoleToken.generate(), ConsoleToken.generate())
    }

    @Test
    fun formatAcceptsOnlyFourDigits() {
        assertTrue(ConsoleToken.isValidFormat("0000"))
        assertTrue(ConsoleToken.isValidFormat("9137"))
        assertFalse(ConsoleToken.isValidFormat(null))
        assertFalse(ConsoleToken.isValidFormat(""))
        assertFalse(ConsoleToken.isValidFormat("123"))
        assertFalse(ConsoleToken.isValidFormat("12345"))
        assertFalse(ConsoleToken.isValidFormat("12a4"))
        assertFalse(ConsoleToken.isValidFormat("ab12cd34ef56ab78cd90ef12ab34cd56"))
    }

    @Test
    fun matchesOnlyExactToken() {
        val token = ConsoleToken.generate()
        assertTrue(ConsoleToken.matches(token, token))
        assertFalse(ConsoleToken.matches(token, token.dropLast(1)))
        assertFalse(ConsoleToken.matches(token, token + "0"))
        assertFalse(ConsoleToken.matches(token, null))
        assertFalse(ConsoleToken.matches("", ""))
    }

    @Test
    fun maskShowsShortCodeAsIsAndFoldsLongOnes() {
        assertEquals("4821", ConsoleToken.mask("4821"))
        assertEquals("ab12\u2026cd56", ConsoleToken.mask("ab12cd34ef56ab78cd90ef12ab34cd56"))
        assertEquals("\u2026", ConsoleToken.mask("short"))
        assertEquals("\u2026", ConsoleToken.mask(null))
    }
}
