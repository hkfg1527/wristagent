package com.example.myapplication.agent.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootSessionTest {

    private val token = "abcdef0123456789"

    private class QueuedLines(lines: List<String>) {
        private val queue = ArrayDeque(lines)
        fun next(): String? = queue.removeFirstOrNull()
    }

    private fun session(lines: List<String>, writes: MutableList<String> = ArrayList()):
        Pair<RootSession, MutableList<String>> {
        val source = QueuedLines(lines)
        return RootSession({ writes += it }, { source.next() }, token) to writes
    }

    @Test
    fun collectsOutputAndExitCode() {
        val (session, _) = session(listOf("uid=0(root) gid=0(root)", "$token:0"))

        val result = session.run("id")

        assertEquals("uid=0(root) gid=0(root)", result.stdout)
        assertEquals("退出码必须取自哨兵行", 0, result.exitCode)
        assertTrue(result.isSuccess)
    }

    @Test
    fun reportsNonZeroExitCode() {
        val (session, _) = session(listOf("rm: Permission denied", "$token:1"))

        val result = session.run("rm /data/x")

        assertEquals(1, result.exitCode)
        assertFalse("非零退出不能算成功", result.isSuccess)
        assertTrue(result.stdout.contains("Permission denied"))
    }

    @Test
    fun lineThatOnlyLooksLikeTheMarkerStaysAsOutput() {
        val (session, _) = session(listOf("$token:not-a-code", "$token:7"))

        val result = session.run("echo $token:not-a-code")

        assertEquals("非数字后缀不能当哨兵", 7, result.exitCode)
        assertTrue(result.stdout.contains("not-a-code"))
    }

    @Test
    fun closedStreamIsReportedAsDeadShellInsteadOfSuccess() {
        val (session, _) = session(listOf("partial output"))

        val result = session.run("sleep 100")

        assertFalse("流关闭说明 shell 已死，不能返回成功", result.isSuccess)
    }

    @Test
    fun secondCommandDoesNotSeeTheFirstOutput() {
        val (session, _) = session(
            listOf("first-out", "$token:0", "second-out", "$token:3")
        )

        val first = session.run("a")
        val second = session.run("b")

        assertEquals("first-out", first.stdout)
        assertEquals(0, first.exitCode)
        assertEquals("第二条命令只能拿到自己的输出", "second-out", second.stdout)
        assertEquals(3, second.exitCode)
    }

    @Test
    fun commandIsWrappedWithSentinelCarryingExitCode() {
        val (session, writes) = session(listOf("$token:0"))

        session.run("id")

        assertEquals(1, writes.size)
        val sent = writes.first()
        assertTrue("命令必须以换行结尾，否则 shell 不会执行", sent.endsWith("\n"))
        assertTrue("必须把 stderr 并入 stdout，否则错误信息读不到", sent.contains("2>&1"))
        assertTrue("必须带哨兵回显", sent.contains(token + ":\$?"))
    }
}
