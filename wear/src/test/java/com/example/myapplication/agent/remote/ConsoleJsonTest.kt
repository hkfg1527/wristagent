package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConsoleJsonTest {

    @Test
    fun parsesNestedObjectsArraysAndScalars() {
        val parsed = ConsoleJson.parseObject(
            """{"a":1,"b":{"c":[true,null,"x",2]},"f":0.5,"neg":-3,"s":"中文"}"""
        )
        assertEquals(1, parsed!!["a"])
        val b = parsed["b"] as Map<*, *>
        val c = b["c"] as List<*>
        assertEquals(true, c[0])
        assertNull(c[1])
        assertEquals("x", c[2])
        assertEquals(2, c[3])
        assertEquals(0.5, parsed["f"] as Double, 1e-9)
        assertEquals(-3, parsed["neg"])
        assertEquals("中文", parsed["s"])
        assertEquals("中文", ConsoleJson.parseObject("{\"s\":\"中\\u6587\"}")!!["s"])
    }

    @Test
    fun rejectsMalformedOrNonObjectJson() {
        assertNull(ConsoleJson.parseObject("{oops"))
        assertNull(ConsoleJson.parseObject("{\"a\":}"))
        assertNull(ConsoleJson.parseObject("{\"a\":1}trailing"))
        assertNull(ConsoleJson.parseObject("[1,2]"))
        assertNull(ConsoleJson.parseObject(""))
        assertNull(ConsoleJson.parseObject("{\"a\":1,}"))
    }

    @Test
    fun writesCompactJsonWithEscapes() {
        val text = ConsoleJson.write(
            linkedMapOf<String, Any?>(
                "k" to "a\"b\nc",
                "n" to 3,
                "b" to false,
                "z" to null,
                "l" to listOf(1, 2),
            )
        )
        assertEquals("""{"k":"a\"b\nc","n":3,"b":false,"z":null,"l":[1,2]}""", text)
    }

    @Test
    fun roundTripsUnicodeAndIntegralFloats() {
        val text = ConsoleJson.write(linkedMapOf<String, Any?>("u" to "中文", "f" to 16f, "t" to 0.7f))
        assertEquals("""{"u":"中文","f":16,"t":0.7}""", text)
        assertEquals("中文", ConsoleJson.parseObject(text)!!["u"])
    }
}