package com.example.myapplication.agent.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 真机实测过的缺陷：`Content-Type: application/json`（不带 charset）时，NanoHTTPD 按 US-ASCII 解 body，
 * 「中文探针-ABC」落库后读回来是 12 个 U+FFFD。这里钉住补 charset 的规则。
 */
class ConsoleCharsetTest {

    @Test
    fun jsonWithoutCharsetGetsUtf8() {
        assertEquals("application/json; charset=utf-8", ConsoleCharset.withUtf8("application/json"))
    }

    @Test
    fun declaredCharsetIsNeverOverwritten() {
        assertEquals("application/json; charset=gbk", ConsoleCharset.withUtf8("application/json; charset=gbk"))
        assertEquals("application/json; charset=UTF-8", ConsoleCharset.withUtf8("application/json; charset=UTF-8"))
    }

    @Test
    fun multipartKeepsItsBoundaryHeader() {
        val multipart = "multipart/form-data; boundary=----WebKitFormBoundaryabc"
        assertEquals(multipart, ConsoleCharset.withUtf8(multipart))
    }

    @Test
    fun missingOrBlankHeaderStaysAsIs() {
        assertNull(ConsoleCharset.withUtf8(null))
        assertEquals("", ConsoleCharset.withUtf8(""))
        assertEquals("   ", ConsoleCharset.withUtf8("   "))
    }

    @Test
    fun surroundingWhitespaceIsTrimmed() {
        assertEquals("application/json; charset=utf-8", ConsoleCharset.withUtf8("  application/json  "))
    }
}
