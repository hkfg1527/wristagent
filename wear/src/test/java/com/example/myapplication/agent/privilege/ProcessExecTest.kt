package com.example.myapplication.agent.privilege

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 子进程输出的收集：**必须有上限**。
 *
 * 回归背景（0.0.0.100 审查）：`drain()` 以前用 `reader.readText()` 无上限灌进 StringBuilder ——
 * `logcat` / `dumpsys` 这类命令 15 秒能产出数十 MB，手表堆只有数百 MB，直接把进程 OOM 掉。
 * 工具层虽然还会 `take(4000)`，但那是**读完之后**才截。
 */
class ProcessExecTest {

    private fun processWith(lines: Int, charsPerLine: Int): FakeSuProcess {
        val fake = FakeSuProcess()
        repeat(lines) { fake.send("x".repeat(charsPerLine)) }
        fake.destroy() // 关流 → 读线程排空后拿 EOF，不会为了"等下一行"挂住
        return fake
    }

    @Test
    fun hugeOutputIsCappedInsteadOfLoadedWhole() {
        val fake = processWith(lines = 300, charsPerLine = 2048)
        val result = ProcessExec.collect(fake, timeoutMs = 5000)
        assertTrue("必须读到东西（否则这条用例什么也没验）", result.stdout.isNotEmpty())
        assertTrue(
            "输出必须被截到上限以内，实际 " + result.stdout.length + " 字符",
            result.stdout.length <= 256 * 1024
        )
    }

    @Test
    fun smallOutputIsPassedThroughUnchanged() {
        val fake = FakeSuProcess()
        fake.send("uid=0(root)")
        fake.destroy()
        val result = ProcessExec.collect(fake, timeoutMs = 5000)
        assertEquals("uid=0(root)", result.stdout)
    }
}
