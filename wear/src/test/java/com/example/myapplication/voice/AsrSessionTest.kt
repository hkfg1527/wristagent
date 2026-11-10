package com.example.myapplication.voice

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 麦克风是否真的被释放由 /api 设备侧 dumpsys audio 验证；
 * 单测固定的是 [AsrSession] 自己的契约：取消 = 置位标志 + 停掉 Job。
 */
class AsrSessionTest {

    @Test
    fun cancelSetsFlagAndStopsRunningJob() = runTest {
        val session = AsrSession(backgroundScope)

        session.launch { awaitCancellation() }
        testScheduler.advanceUntilIdle()
        assertTrue("会话启动后应处于运行中", session.isRunning)

        session.cancel()
        testScheduler.advanceUntilIdle()

        assertTrue("取消标志必须置位，否则结果回调仍会上报", session.isCancelled)
        assertFalse("取消后 Job 必须失活，阻塞录音靠它判断退出", session.isRunning)
    }

    @Test
    fun nextSessionClearsCancelledFlag() = runTest {
        val session = AsrSession(this)
        session.cancel()
        assertTrue("取消后标志应为真", session.isCancelled)

        session.launch { }
        testScheduler.advanceUntilIdle()

        assertFalse("新会话必须清掉上一次的取消标志，否则后续录音全部静默失效", session.isCancelled)
    }

    @Test
    fun blockThatFinishesLeavesSessionIdle() = runTest {
        val session = AsrSession(this)
        var ran = false

        session.launch { ran = true }
        testScheduler.advanceUntilIdle()

        assertTrue("块体必须被执行", ran)
        assertFalse("自然结束后不应报告运行中", session.isRunning)
        assertFalse("自然结束不属于取消", session.isCancelled)
    }

    @Test
    fun relaunchCancelsPreviousJob() = runTest {
        val session = AsrSession(backgroundScope)
        var firstCancelled = false

        session.launch {
            try {
                awaitCancellation()
            } catch (e: CancellationException) {
                firstCancelled = true
                throw e
            }
        }
        // coroutines-test 1.9 的 advanceUntilIdle 不推进 backgroundScope 里的任务：
        // 只有在测试体让出调度（yield）时后台协程才会真正跑起来。这里补上让出，
        // 断言不变 —— 否则第一次会话根本没启动，取消与否都观测不到。
        testScheduler.advanceUntilIdle()
        yield()
        assertTrue("首次启动后应处于运行中", session.isRunning)

        session.launch { awaitCancellation() }
        testScheduler.advanceUntilIdle()
        yield()

        assertTrue(
            "重开必须先取消上一次的转写 Job：否则两条录音同时读麦克风，丢弃的那次也会把结果送上屏",
            firstCancelled
        )
    }
}
