package com.example.myapplication.agent.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** 跨进程返回值必须逐字节还原：退出码、stdout、stderr 三段都不能串位。 */
class UserServiceProtocolTest {

    @Test
    fun roundTripKeepsExitCodeAndBothStreams() {
        val raw = UserServiceProtocol.format(0, "uid=0(root) gid=0(root)", "")
        val parsed = UserServiceProtocol.parse(raw)
        assertEquals(0, parsed.exitCode)
        assertEquals("uid=0(root) gid=0(root)", parsed.stdout)
        assertEquals("", parsed.stderr)
    }

    @Test
    fun newlinesAndNonZeroExitSurviveTheRoundTrip() {
        val raw = UserServiceProtocol.format(127, "line one\nline two", "sh: cmd: not found\n")
        val parsed = UserServiceProtocol.parse(raw)
        assertEquals(127, parsed.exitCode)
        assertEquals("line one\nline two", parsed.stdout)
        assertEquals("sh: cmd: not found\n", parsed.stderr)
    }

    @Test
    fun outputThatLooksLikeAHeaderDoesNotConfuseTheParser() {
        val hostile = "0\n3\n0\nexit=0"
        val raw = UserServiceProtocol.format(3, hostile, "tail")
        val parsed = UserServiceProtocol.parse(raw)
        assertEquals(3, parsed.exitCode)
        assertEquals(hostile, parsed.stdout)
        assertEquals("tail", parsed.stderr)
    }

    @Test
    fun emptyStreamsRoundTripToEmptyStrings() {
        val parsed = UserServiceProtocol.parse(UserServiceProtocol.format(1, "", ""))
        assertEquals(1, parsed.exitCode)
        assertEquals("", parsed.stdout)
        assertEquals("", parsed.stderr)
    }

    @Test
    fun garbageReplyBecomesAFailureInsteadOfThrowing() {
        val parsed = UserServiceProtocol.parse("garbage")
        assertEquals(-1, parsed.exitCode)
        assertNotEquals("", parsed.stderr)
    }

    @Test
    fun negativeLengthIsRejected() {
        val parsed = UserServiceProtocol.parse("0\n-5\n0\nabc")
        assertEquals(-1, parsed.exitCode)
        assertNotEquals("", parsed.stderr)
    }

    @Test
    fun lengthBeyondThePayloadIsRejected() {
        val parsed = UserServiceProtocol.parse("0\n999\n0\nabc")
        assertEquals(-1, parsed.exitCode)
        assertNotEquals("", parsed.stderr)
    }
}
