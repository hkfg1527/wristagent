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
    fun commandIsIsolatedFromStdinAndTheSentinelCannotStickToOutput() {
        // 两个真实踩过的坑（0.0.0.100 审查）：
        // 1) 命令读 stdin（cat / read / 嵌套 sh）会吞掉**下一条命令**，整个会话后续全错位；
        // 2) 命令输出不带尾换行时（printf hi），哨兵会粘成 "hitoken:0"，classify 认不出，读循环卡到超时。
        val (session, writes) = session(listOf("hi", "", "$token:0"))

        session.run("printf hi")

        val sent = writes.first()
        assertTrue("命令必须隔离 stdin：$sent", sent.contains("</dev/null"))
        assertTrue("哨兵前必须先打一个换行：$sent", sent.contains("printf '\\n' ;"))
    }

    @Test
    fun commandIsWrappedWithSentinelCarryingExitCode() {
        val (session, writes) = session(listOf("$token:0"))

        session.run("id")

        assertEquals(1, writes.size)
        val sent = writes.first()
        assertTrue("命令必须以换行结尾，否则 shell 不会执行", sent.endsWith("\n"))
        assertTrue("必须把 stderr 并入 stdout，否则错误信息读不到", sent.contains("2>&1"))
        // 退出码必须先存进变量再补换行：直接 `echo "token:$?"` 取到的是 printf 的退出码（恒 0），
        // 所有失败命令都会被回成 exit_code=0（0.0.0.113 审查发现）。
        assertTrue("必须先保存 \$? 再 printf：" + sent, sent.contains("__rc=\$?"))
        assertTrue("哨兵要回显保存下来的退出码：" + sent, sent.contains(token + ":\$__rc"))
        assertTrue(
            "printf 补换行必须发生在保存 \$? 之后：" + sent,
            sent.indexOf("__rc=\$?") < sent.indexOf("printf")
        )
    }
}
