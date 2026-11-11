package com.example.myapplication.presentation

import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.GestureBindingGate
import com.example.myapplication.agent.GestureBindings
import com.example.myapplication.agent.GestureEvent
import com.example.myapplication.agent.GestureRequestQueue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 双击判据：同一颗键、两拍都短于长按阈值、间隔落在窗口里才算双击。
 *
 * 判据是纯函数：时间全部由用例给定，不需要真机、不读系统时钟、不排定时器；
 * 窗口到期的结算由 [GestureKeyResolver.onWindowExpired] 显式调用（真机上由 MainActivity 的
 * Handler 在窗口之后回调）—— 判据自己不 sleep、不阻塞。
 */
class GestureDoubleTapTest {

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

    // ------------------------------------------------------------ 成立

    @Test
    fun `窗口内的两次侧键短按产出双击`() {
        val first = press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.DOUBLE_TAP)
        assertTrue("只绑了双击，按下也得吞：第一拍被设备抢走就凑不出双击", first.consumed)

        val firstUp = press(GestureKeySource.STEM_1, phaseUp, 80L, GestureEvent.DOUBLE_TAP)
        assertNull("第一拍绝不能立刻当成单击投递", firstUp.event)
        assertNotNull("还不知道是不是双击：要欠一个窗口", firstUp.deferral)
        assertEquals(
            "第一拍抬起的欠账就是整个窗口",
            GestureKeyResolver.DEFAULT_DOUBLE_TAP_WINDOW_MS,
            firstUp.deferral!!.delayMs
        )

        // 第二拍：两次之间隔 200ms（第一拍 80ms 抬起，第二拍 280ms 按下），按住 80ms
        press(GestureKeySource.STEM_1, phaseDown, 280L, GestureEvent.DOUBLE_TAP)
        val secondUp = press(GestureKeySource.STEM_1, phaseUp, 360L, GestureEvent.DOUBLE_TAP)

        assertEquals(GestureEvent.DOUBLE_TAP, secondUp.event)
        assertTrue(secondUp.consumed)
        assertNull("双击已经成立，不许再欠窗口", secondUp.deferral)
    }

    // ------------------------------------------------------------ 间隔超窗

    @Test
    fun `间隔超过窗口不算双击`() {
        press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.DOUBLE_TAP)
        assertNotNull(press(GestureKeySource.STEM_1, phaseUp, 80L, GestureEvent.DOUBLE_TAP).deferral)

        // 抬起后 400ms 才按下（> 300ms 窗口）：这是两次独立的短按
        press(GestureKeySource.STEM_1, phaseDown, 480L, GestureEvent.DOUBLE_TAP)
        val secondUp = press(GestureKeySource.STEM_1, phaseUp, 560L, GestureEvent.DOUBLE_TAP)

        assertNotEquals(GestureEvent.DOUBLE_TAP, secondUp.event)
        assertNull("只绑了双击时，落单的短按不投递", secondUp.event)
    }

    @Test
    fun `间隔超窗时第一拍按短按结算第二拍也是普通短按`() {
        val isBound = bound(GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_SHORT)
        resolver.onKey(GestureKeySource.STEM_1, phaseDown, 0L, isBound)
        resolver.onKey(GestureKeySource.STEM_1, phaseUp, 80L, isBound)

        // 窗口到期（80 + 300 = 380）后结算：第一拍就是一次普通短按
        val settled = resolver.onWindowExpired(GestureKeySource.STEM_1, 390L, isBound)
        assertEquals(GestureEvent.SIDE_KEY_SHORT, settled.event)

        // 之后 480ms 按下、560ms 抬起：间隔早就超窗，仍然只是短按
        resolver.onKey(GestureKeySource.STEM_1, phaseDown, 480L, isBound)
        val second = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 560L, isBound)

        assertNotEquals(GestureEvent.DOUBLE_TAP, second.event)
        assertNotNull(second.deferral)
        assertEquals(GestureEvent.SIDE_KEY_SHORT, resolver.onWindowExpired(GestureKeySource.STEM_1, 900L, isBound).event)
    }

    // ------------------------------------------------------------ 第二拍变长按

    @Test
    fun `第二次按成长按不算双击`() {
        press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        assertNotNull(
            press(GestureKeySource.STEM_1, phaseUp, 80L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG).deferral
        )

        // 第二拍在窗口内按下（280），却按住不放：520ms 时跨过长按阈值
        press(GestureKeySource.STEM_1, phaseDown, 280L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        val longPress = press(GestureKeySource.STEM_1, phaseRepeat, 800L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)

        assertEquals(GestureEvent.SIDE_KEY_LONG, longPress.event)
        assertNotEquals(GestureEvent.DOUBLE_TAP, longPress.event)

        val up = press(GestureKeySource.STEM_1, phaseUp, 900L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        assertNull("长按已投递，抬起只收尾", up.event)
        assertTrue(up.consumed)
        assertNull(
            "长按抢走序列后，第一拍的欠账必须作废",
            resolver.onWindowExpired(GestureKeySource.STEM_1, 1200L, bound(GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)).event
        )
    }

    // ------------------------------------------------------------ 长按不误判

    @Test
    fun `长按不会被误判成双击的第一拍`() {
        press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        val longPress = press(GestureKeySource.STEM_1, phaseRepeat, 500L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        assertEquals(GestureEvent.SIDE_KEY_LONG, longPress.event)

        val up = press(GestureKeySource.STEM_1, phaseUp, 600L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        assertNull(up.event)
        assertNull("长按序列不许留下待结算的短按", up.deferral)

        // 紧接着再短按一次：没有欠账就应该是一拍的开始，不是双击
        press(GestureKeySource.STEM_1, phaseDown, 700L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        val nextUp = press(GestureKeySource.STEM_1, phaseUp, 780L, GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_LONG)
        assertNotEquals(GestureEvent.DOUBLE_TAP, nextUp.event)
        assertNull(nextUp.event)
        assertNotNull("这一拍自己成了新的第一拍", nextUp.deferral)
    }

    // ------------------------------------------------------------ 窗口结算

    @Test
    fun `窗口到期后第一拍仍然作为短按投递`() {
        val isBound = bound(GestureEvent.DOUBLE_TAP, GestureEvent.SIDE_KEY_SHORT)
        resolver.onKey(GestureKeySource.STEM_1, phaseDown, 0L, isBound)
        val up = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 80L, isBound)
        assertNull("还在窗口里，不许立刻当单击", up.event)

        val early = resolver.onWindowExpired(GestureKeySource.STEM_1, 180L, isBound)
        assertNull("回调早到不许结算", early.event)
        assertEquals("早到就按剩余时间再欠一次", 200L, early.deferral!!.delayMs)

        val settled = resolver.onWindowExpired(GestureKeySource.STEM_1, 390L, isBound)
        assertEquals(GestureEvent.SIDE_KEY_SHORT, settled.event)
        assertNull("结算过就没有第二次", resolver.onWindowExpired(GestureKeySource.STEM_1, 500L, isBound).event)
    }

    // ------------------------------------------------------------ 重复事件

    @Test
    fun `重复事件不参与双击判定`() {
        press(GestureKeySource.STEM_1, phaseDown, 0L, GestureEvent.DOUBLE_TAP)
        press(GestureKeySource.STEM_1, phaseUp, 80L, GestureEvent.DOUBLE_TAP)

        val orphanRepeat = press(GestureKeySource.STEM_1, phaseRepeat, 150L, GestureEvent.DOUBLE_TAP)
        assertNull("没有按下记录的重复事件什么也不产出", orphanRepeat.event)

        press(GestureKeySource.STEM_1, phaseDown, 280L, GestureEvent.DOUBLE_TAP)
        assertNull("第二拍按住 120ms 时的重复事件不许提前判长按", press(GestureKeySource.STEM_1, phaseRepeat, 400L, GestureEvent.DOUBLE_TAP).event)

        // 重复事件既没把窗口搞乱，也没把第二拍变成"长按"
        assertEquals(GestureEvent.DOUBLE_TAP, press(GestureKeySource.STEM_1, phaseUp, 500L, GestureEvent.DOUBLE_TAP).event)
    }

    // ------------------------------------------------------------ 未绑定 / 系统键

    @Test
    fun `未绑定时双击既不投递也不吞按键`() {
        val down = resolver.onKey(GestureKeySource.STEM_1, phaseDown, 0L) { false }
        assertFalse("没绑动作时侧键按下要留给设备", down.consumed)

        val up = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 80L) { false }
        assertNull(up.event)
        assertFalse(up.consumed)
        assertNull("没绑双击就不许欠窗口", up.deferral)

        val expired = resolver.onWindowExpired(GestureKeySource.STEM_1, 500L) { false }
        assertNull(expired.event)
        assertFalse(expired.consumed)

        resolver.onKey(GestureKeySource.STEM_1, phaseDown, 600L) { false }
        val secondUp = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 680L) { false }
        assertNull("两次短按也不能凑出双击", secondUp.event)
        assertFalse(secondUp.consumed)

        assertEquals("未绑定不许往队列里投任何东西", emptyList<GestureAction>(), GestureRequestQueue.drain())
    }

    @Test
    fun `系统键的短按即使绑了双击也不被抢走`() {
        listOf(GestureKeySource.STEM_PRIMARY, GestureKeySource.BACK).forEach { source ->
            val down = press(source, phaseDown, 0L, GestureEvent.DOUBLE_TAP)
            assertFalse(source.name + " 的按下必须留给系统", down.consumed)

            val up = press(source, phaseUp, 100L, GestureEvent.DOUBLE_TAP)
            assertNull(up.event)
            assertFalse("短按必须交还系统，否则回表盘 / 返回上一页就没了", up.consumed)
            assertNull(source.name + " 不该产生双击欠账", up.deferral)
        }
    }

    // ------------------------------------------------------------ 参数与端到端

    @Test
    fun `双击窗口落在 280 到 320ms 且短于长按阈值`() {
        val window = GestureKeyResolver.DEFAULT_DOUBLE_TAP_WINDOW_MS
        assertTrue("窗口要落在 280-320ms，实际 ${window}ms", window in 280L..320L)
        assertTrue(
            "窗口必须短于长按阈值，否则一次按住会同时被认成长按与第一拍",
            window < GestureKeyResolver.DEFAULT_LONG_PRESS_MS
        )
    }

    @Test
    fun `双击经真实绑定闸门落成动作`() {
        val bindings = GestureBindings(doubleTap = GestureAction.SWITCH_MODEL)
        val isBound: (GestureEvent) -> Boolean = { gesture ->
            GestureBindingGate.actionFor(bindings, gesture) != GestureAction.NONE
        }

        resolver.onKey(GestureKeySource.STEM_1, phaseDown, 0L, isBound)
        val firstUp = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 80L, isBound)
        assertNull("第一拍不许自己变成动作", firstUp.event)
        assertEquals("第一拍只能欠窗口，队列此刻必须是空的", emptyList<GestureAction>(), GestureRequestQueue.drain())

        resolver.onKey(GestureKeySource.STEM_1, phaseDown, 280L, isBound)
        val secondUp = resolver.onKey(GestureKeySource.STEM_1, phaseUp, 360L, isBound)
        val event = requireNotNull(secondUp.event) { "绑了切模型的双击必须产出事件" }

        GestureRequestQueue.post(GestureBindingGate.actionFor(bindings, event))
        assertEquals(listOf(GestureAction.SWITCH_MODEL), GestureRequestQueue.drain())
    }
}
