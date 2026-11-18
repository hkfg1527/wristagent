package com.example.myapplication.agent.privilege

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * root 会话"超时之后还能不能继续用"。
 *
 * 真机故障（0.0.0.100 审查 + 0.0.0.103 修复）：旧实现是一个常驻单线程池，任务阻塞在
 * `readLine()`（管道读不响应 interrupt）。一条命令超时后 `future.cancel(true)` 打断不了它，
 * 于是这个线程永久占住池子 —— 之后**每条**命令都排在死任务后面逐个 15s 超时，
 * `isAvailable()` 恒 false，root 静默降级到 DIRECT。
 *
 * 这条用例就是钉住"超时后下一条命令仍能跑通、且会重开会话"。
 */
class RootShellTimeoutTest {

    @Test
    fun aTimeoutDoesNotWedgeTheNextCommand() = runBlocking {
        val opened = ArrayList<FakeSuProcess>()
        val shell = RootShell(
            timeoutMs = 400,
            availableTimeoutMs = 400,
            opener = {
                // 第一个会话"永不回应 + 杀不掉"（模拟子进程仍握着管道），之后新建的会话自动应答
                FakeSuProcess().also {
                    it.autoRespond = opened.isNotEmpty()
                    it.survivesDestroy = opened.isEmpty()
                    opened += it
                }
            }
        )

        // 第一条：假进程永不回应 → 必须超时返回，而不是永远挂着
        val started = System.currentTimeMillis()
        val timedOut = shell.run("sleep 60")
        val elapsed = System.currentTimeMillis() - started
        assertTrue("必须报超时：" + timedOut.stderr, timedOut.stderr.contains("timeout"))
        assertTrue("超时必须在 400ms 档生效，实际 " + elapsed + "ms", elapsed < 3000)

        // 第二条：必须能在新会话上正常跑通（旧实现里线程池已被死任务占满，这条会一路超时）
        val second = shell.run("id")
        assertTrue(
            "必须重开会话：opened=" + opened.size + " 写入=" + opened.lastOrNull()?.writtenLines,
            opened.size >= 2
        )
        assertTrue("超时之后 root 必须还能用：" + second.stderr, second.isSuccess)
        shell.close()
    }

    @Test
    fun aDeadSessionIsDroppedInsteadOfReusedForever() = runBlocking {
        val opened = ArrayList<FakeSuProcess>()
        val shell = RootShell(
            timeoutMs = 2000,
            availableTimeoutMs = 2000,
            opener = { FakeSuProcess().also { opened += it } }
        )

        // 第一条命令还在读的时候把进程杀掉：会话读到 EOF，返回 "root shell closed"
        val killer = Thread {
            Thread.sleep(120)
            opened.firstOrNull()?.destroy()
        }
        killer.start()
        val dead = shell.run("id")
        killer.join(2000)
        assertTrue("进程没了必须如实报死会话：" + dead.stderr, dead.stderr.contains("closed"))

        // 下一条命令必须重开一个进程，而不是继续用那个死掉的
        assertTrue("死会话必须被丢弃", opened.size >= 1)
        shell.close()
    }
}
