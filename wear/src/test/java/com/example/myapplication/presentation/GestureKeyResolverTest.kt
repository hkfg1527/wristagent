package com.example.myapplication.presentation

import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.GestureBindingGate
import com.example.myapplication.agent.GestureBindings
import com.example.myapplication.agent.GestureEvent
import com.example.myapplication.agent.GestureRequestQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 物理按键事件源的判据：短按不吞系统键、长按只投递一次、未绑定不投递也不吞。
 *
 * 时间全部由用例给定，因此不依赖设备按键重复的节奏；判据本身不碰 android.view、
 * 不碰时钟、不碰队列，投递链在最后一条用例里用真实的 [GestureBindingGate] 与
 * [GestureRequestQueue] 走一遍。
 */
class GestureKeyResolverTest {

    private val resolver = GestureKeyResolver()

    private val phaseDown = GestureKeyPhase.DOWN
    private val phaseRepeat = GestureKeyPhase.REPEAT
    private val phaseUp = GestureKeyPhase.UP

    @Before
    fun clearQueue() {
        GestureRequestQueue.clear()
    }

    @After
    fun leaveQueueClean() {
        GestureRequestQueue.clear()
    }

    /** 用"这组动作绑了哪些手势"充当绑定表，避免判据知道 AgentPreferences 的存在。 */
    private fun bound(vararg events: GestureEvent): (GestureEvent) -> Boolean {
        val boundSet = events.toSet()
        return { boundSet.contains(it) }
    }

    private fun press(
        source: GestureKeySource,
        phase: GestureKeyPhase,
        atMs: Long,
        vararg events: GestureEvent
    ): GestureKeyDecision = resolver.onKey(source, phase, atMs, bound(*events))

    @Test
    fun `表冠短按不投递也不吞按键`() {
        val down = press(GestureKeySource.STEM_PRIMARY, phaseDown, 0L, GestureEvent.LONG_PRESS)
        assertNull("按下这一拍没有手势可投递", down.event)
        assertFalse("表冠按下不能吞：吞了回表盘就没了", down.consumed)

        val up = press(GestureKeySource.STEM_PRIMARY, phaseUp, 120L, GestureEvent.LONG_PRESS)
        assertNull("短按不投递", up.event)
        assertFalse("短按抬起要留给系统", up.consumed)
    }

    @Test
    fun `表冠长按跨过阈值投递一次长按`() {
        press(GestureKeySource.STEM_PRIMARY, phaseDown, 0L, GestureEvent.LONG_PRESS)
        val tick = press(GestureKeySource.STEM_PRIMARY, phaseRepeat, 500L, GestureEvent.LONG_PRESS)
        assertEquals(GestureEvent.LONG_PRESS, tick.event)
        assertTrue("长按由我们处理，这一拍不再往下传", tick.consumed)
    }

    @Test
    fun `按住不放的重复事件不重复投递`() {
        press(GestureKeySource.STEM_PRIMARY, phaseDown, 0L, GestureEvent.LONG_PRESS)
        var posted = 0
        for (atMs in listOf(500L, 550L, 600L, 650L, 700L)) {
            if (press(GestureKeySource.STEM_PRIMARY, phaseRepeat, atMs, GestureEvent.LONG_PRESS).event != null) {
                posted++
            }
        }
        assertEquals("五次重复只允许投递一次", 1, posted)
    }

    @Test
    fun `长按之后的抬起不再投递`() {
        press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.SIDE_KEY_LONG)
        assertEquals(
            GestureEvent.SIDE_KEY_LONG,
            press(GestureKeySource.STEM_1, phaseRepeat, 520L, GestureEvent.SIDE_KEY_LONG).event
        )

        val up = press(GestureKeySource.STEM_1, phaseUp, 900L, GestureEvent.SIDE_KEY_LONG)
        assertNull("抬起不投递第二次", up.event)
        assertTrue("长按序列的收尾仍由我们吞掉", up.consumed)
    }

    @Test
    fun `侧键短按在抬起时投递一次短按`() {
        val down = press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.SIDE_KEY_SHORT)
        assertTrue("侧键短按绑过就吞按下，免得厂商快捷方式也响应", down.consumed)

        val up = press(GestureKeySource.STEM_1, phaseUp, 150L, GestureEvent.SIDE_KEY_SHORT)
        assertEquals(GestureEvent.SIDE_KEY_SHORT, up.event)
        assertTrue(up.consumed)
    }

    @Test
    fun `设备不送重复事件时抬起按阈值补判长按`() {
        press(GestureKeySource.STEM_2, phaseDown, 0L, GestureEvent.SIDE_KEY_LONG)
        val up = press(GestureKeySource.STEM_2, phaseUp, 700L, GestureEvent.SIDE_KEY_LONG)
        assertEquals("没有重复事件也要认得出长按", GestureEvent.SIDE_KEY_LONG, up.event)
        assertTrue(up.consumed)
    }

    @Test
    fun `返回键短按不被吞也不投递`() {
        val down = press(GestureKeySource.BACK, phaseDown, 0L, GestureEvent.LONG_PRESS)
        assertNull(down.event)
        assertFalse("返回键按下必须交给系统，否则系统收不到这次返回", down.consumed)

        val earlyRepeat = press(GestureKeySource.BACK, phaseRepeat, 300L, GestureEvent.LONG_PRESS)
        assertNull("还没到阈值，重复事件不算长按", earlyRepeat.event)
        assertFalse("没到阈值的重复事件必须放行", earlyRepeat.consumed)

        val up = press(GestureKeySource.BACK, phaseUp, 400L, GestureEvent.LONG_PRESS)
        assertNull("短按不投递", up.event)
        assertFalse("返回键短按抬起必须留给系统，返回上一页照常工作", up.consumed)
    }

    @Test
    fun `返回键长按吞掉重复与抬起`() {
        val down = press(GestureKeySource.BACK, phaseDown, 0L, GestureEvent.LONG_PRESS)
        assertFalse(down.consumed)

        val tick = press(GestureKeySource.BACK, phaseRepeat, 480L, GestureEvent.LONG_PRESS)
        assertEquals(GestureEvent.LONG_PRESS, tick.event)
        assertTrue("长按这一拍要吞掉，否则系统的长按返回会把应用退掉", tick.consumed)

        val up = press(GestureKeySource.BACK, phaseUp, 600L, GestureEvent.LONG_PRESS)
        assertNull(up.event)
        assertTrue("长按已生效，抬起不能再触发系统返回", up.consumed)
    }

    @Test
    fun `未绑定的手势既不投递也不吞按键`() {
        val unbound = resolver.onKey(GestureKeySource.STEM_1, phaseDown, 0L) { false }
        assertNull(unbound.event)
        assertFalse("没绑动作时侧键按下要留给设备", unbound.consumed)

        val shortUp = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 150L) { false }
        assertNull("没绑短按就不投递", shortUp.event)
        assertFalse(shortUp.consumed)

        resolver.onKey(GestureKeySource.STEM_PRIMARY, phaseDown, 0L) { false }
        val primaryHold = resolver.onKey(GestureKeySource.STEM_PRIMARY, phaseRepeat, 600L) { false }
        assertNull("没绑长按就不投递", primaryHold.event)
        assertFalse(primaryHold.consumed)

        resolver.onKey(GestureKeySource.BACK, phaseDown, 0L) { false }
        val backHold = resolver.onKey(GestureKeySource.BACK, phaseRepeat, 600L) { false }
        assertNull(backHold.event)
        assertFalse("没绑长按时返回键行为与没接这段代码一致", backHold.consumed)
    }

    @Test
    fun `没有按下记录的重复与孤立的抬起都被忽略`() {
        val orphanRepeat = press(GestureKeySource.STEM_1, phaseRepeat, 500L, GestureEvent.SIDE_KEY_LONG)
        assertNull(orphanRepeat.event)
        assertFalse(orphanRepeat.consumed)

        val orphanUp = press(GestureKeySource.STEM_1, phaseUp, 600L, GestureEvent.SIDE_KEY_LONG)
        assertNull("没有按下记录就没有手势", orphanUp.event)
        assertFalse(orphanUp.consumed)
    }

    @Test
    fun `丢了抬起事件后重新计时不会把旧起点算成新的长按`() {
        press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.SIDE_KEY_SHORT, GestureEvent.SIDE_KEY_LONG)
        assertEquals(
            GestureEvent.SIDE_KEY_LONG,
            press(
                GestureKeySource.STEM_1,
                phaseRepeat,
                600L,
                GestureEvent.SIDE_KEY_SHORT,
                GestureEvent.SIDE_KEY_LONG
            ).event
        )

        // 上一轮没收到抬起就来了新的按下：起点必须重置
        press(GestureKeySource.STEM_1, phaseDown, 700L, GestureEvent.SIDE_KEY_SHORT, GestureEvent.SIDE_KEY_LONG)
        val up = press(
            GestureKeySource.STEM_1,
            phaseUp,
            800L,
            GestureEvent.SIDE_KEY_SHORT,
            GestureEvent.SIDE_KEY_LONG
        )
        assertEquals("重新计时后这次只是短按", GestureEvent.SIDE_KEY_SHORT, up.event)
    }

    @Test
    fun `阈值边界按大于等于判长按`() {
        val thresholdMs = GestureKeyResolver.DEFAULT_LONG_PRESS_MS
        press(GestureKeySource.STEM_PRIMARY, phaseDown, 0L, GestureEvent.LONG_PRESS)
        val atThreshold = press(
            GestureKeySource.STEM_PRIMARY,
            phaseRepeat,
            thresholdMs,
            GestureEvent.LONG_PRESS
        )
        assertEquals(GestureEvent.LONG_PRESS, atThreshold.event)
    }

    @Test
    fun `长按阈值落在建议区间且与底栏按住说话不同值`() {
        val thresholdMs = GestureKeyResolver.DEFAULT_LONG_PRESS_MS
        assertTrue("阈值要落在 400-600ms，实际 ${thresholdMs}ms", thresholdMs in 400L..600L)
        assertTrue("不能与底栏按住说话的 340ms 撞值", thresholdMs != 340L)
    }

    @Test
    fun `识别出的长按落到真实绑定闸门与投递队列`() {
        val bindings = GestureBindings(longPress = GestureAction.STOP_TURN)
        val isBound: (GestureEvent) -> Boolean = { gesture ->
            GestureBindingGate.actionFor(bindings, gesture) != GestureAction.NONE
        }
        resolver.onKey(GestureKeySource.STEM_PRIMARY, phaseDown, 0L, isBound)
        val decision = resolver.onKey(GestureKeySource.STEM_PRIMARY, phaseRepeat, 500L, isBound)
        val event = requireNotNull(decision.event) { "绑了终止动作的长按必须产出事件" }

        GestureRequestQueue.post(GestureBindingGate.actionFor(bindings, event))
        assertTrue("dispatcher 的第一次消费要拿到终止", GestureRequestQueue.consumeStopTurn())
        assertFalse("队列只生效一次，不能被上一次长按反复打断", GestureRequestQueue.consumeStopTurn())
    }
}
