package com.example.myapplication.presentation

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.Density
import com.example.myapplication.presentation.ui.EdgeOverscroll
import com.example.myapplication.presentation.ui.ListOverscrollSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 越界手感的契约：
 *  - 视觉是弹簧：松手就弹回 0，不会一直夹着；
 *  - 蓄力是棘轮：松手后短时间内再推可以叠加，够了就切页；
 *  - 放置超过保持期，蓄力清零，重新开始计。
 *
 * Animatable 需要 MonotonicFrameClock，纯 JVM 测试里没有，所以注入一个按 16ms 步进的假时钟。
 */
class EdgeOverscrollTest {

    private val density = Density(2f) // 1dp = 2px，阈值 48dp = 96px

    /** 每取一帧前进 16ms，弹簧动画才能在测试里真正收敛。 */
    private class SteppingFrameClock : MonotonicFrameClock {
        private var frameTimeNanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            frameTimeNanos += 16_000_000L
            return onFrame(frameTimeNanos)
        }
    }

    private class Recorder {
        var crosses = 0
        val vibrations = mutableListOf<Int>()
    }

    private fun create(recorder: Recorder, scope: CoroutineScope): EdgeOverscroll =
        EdgeOverscroll(
            scope = scope,
            density = density,
            overflowSign = -1f,
            atEdge = { true },
            onCross = { recorder.crosses++ },
            vibrate = { recorder.vibrations += it }
        )

    private fun testScope(): CoroutineScope =
        CoroutineScope(Dispatchers.Unconfined) + SteppingFrameClock()

    @Test
    fun `三次 20dp 的推入在松手后仍能叠加到阈值并切页`() = runBlocking {
        val recorder = Recorder()
        val scroll = create(recorder, testScope())
        repeat(3) {
            scroll.push(40f)   // 20dp
            scroll.release()   // 每次松手都弹回，但蓄力保留
            delay(120)         // 间隔远小于保持期 900ms
        }
        assertEquals(1, recorder.crosses)
    }

    @Test
    fun `单次不到阈值不切页`() = runBlocking {
        val recorder = Recorder()
        val scroll = create(recorder, testScope())
        scroll.push(40f)
        assertEquals(0, recorder.crosses)
    }

    @Test
    fun `松手后视觉弹簧归零_不会一直夹着`() = runBlocking {
        val recorder = Recorder()
        val scroll = create(recorder, testScope())
        scroll.push(80f)
        delay(60)
        assertTrue("推入后应该被拉开", scroll.pullOffsetPx < -1f)
        scroll.release()
        delay(800)
        assertTrue("松手后应回到 0，实际=${scroll.pullOffsetPx}", abs(scroll.pullOffsetPx) < 1f)
    }

    @Test
    fun `超过保持期后蓄力清零_重新开始计`() = runBlocking {
        val recorder = Recorder()
        val scroll = create(recorder, testScope())
        scroll.push(40f)
        scroll.release()
        delay(ListOverscrollSpec.HoldBeforeDecayMs + 300) // 等衰减完成
        scroll.push(40f) // 只剩这一次的 20dp，不够阈值
        assertEquals(0, recorder.crosses)
    }

    @Test
    fun `首次触达只震一次_过阈值再震一次并有先后区别`() = runBlocking {
        val recorder = Recorder()
        val scroll = create(recorder, testScope())
        scroll.push(40f)
        assertEquals("首次触达边界应震一次", 1, recorder.vibrations.size)
        scroll.push(40f)
        assertEquals("未过阈值前不应再震", 1, recorder.vibrations.size)
        scroll.push(40f) // 累计 60dp > 48dp
        assertEquals("过阈值应再震一次", 2, recorder.vibrations.size)
        assertTrue("两次震动的强度/类型应不同", recorder.vibrations[0] != recorder.vibrations[1])
    }
}
