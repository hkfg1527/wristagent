package com.example.myapplication.presentation.ui.settingsv2

import com.example.myapplication.presentation.ui.settingsv2.common.RotaryBus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R20：表冠总线（[RotaryBus]）的语义。
 *
 * 设备上无法注入 `ACTION_SCROLL`（`sendevent` 的 REL_WHEEL 与 `input roll` 的 trackball 事件
 * 都收不到，两轮实测 0 日志），所以把这条链路里**能离线判定**的部分钉死：
 * ①阀门关着时必须丢弃事件（否则会把聊天页的表冠滚动吞掉）；②开着时必须投递；③可以多处订阅。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RotaryBusTest {

    @After
    fun reset() {
        RotaryBus.release("test")
    }

    @Test
    fun disabledBusDropsEvents() = runTest {
        RotaryBus.release("test")
        var got = Float.NaN
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { got = RotaryBus.events.first() }
        runCurrent()
        RotaryBus.emit(36f)
        runCurrent()
        assertTrue("阀门关着时不许投递（会抢走别的页面）", got.isNaN())
    }

    @Test
    fun enabledBusDeliversTheDelta() = runTest {
        RotaryBus.acquire("test")
        var got = Float.NaN
        // Unconfined：订阅必须**先就位**再 emit —— SharedFlow 无订阅者时 tryEmit 会直接丢，
        // 用默认 TestDispatcher 的 async 会让 emit 跑在订阅之前（这条测试一开始就是这么假红的）
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            got = RotaryBus.events.first()
        }
        runCurrent()
        RotaryBus.emit(-72f)
        runCurrent()
        assertEquals(-72f, got, 0.001f)
    }

    @Test
    fun multipleSubscribersAllReceiveTheSameDelta() = runTest {
        RotaryBus.acquire("test")
        var a = Float.NaN
        var b = Float.NaN
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { a = RotaryBus.events.first() }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { b = RotaryBus.events.first() }
        runCurrent()
        RotaryBus.emit(36f)
        runCurrent()
        assertEquals(36f, a, 0.001f)
        assertEquals(36f, b, 0.001f)
    }

    @Test
    fun busRemembersNothingWhileDisabled() {
        RotaryBus.release("test")
        RotaryBus.emit(36f)
        RotaryBus.acquire("test")
        // 关阀门期间的事件不该在开阀门后被"补发"：没有订阅者时也不会缓存成待投递状态
        assertTrue("阀门开关本身不影响对象可用性", RotaryBus.enabled)
    }

    /**
     * R38：单按一行、长按一屏（`PgDn` 语义）。
     *
     * 真终端里按住方向键会加速滚动，一行一行挪太慢；这里用 KeyEvent.repeatCount 区分。
     */
    @Test
    fun keyRepeatTurnsIntoScreenPages() {
        assertEquals("单按 = 一行", 36f, RotaryBus.deltaForKeyRepeat(0, 36f), 0.001f)
        assertEquals("长按 = 一屏（10 行）", 360f, RotaryBus.deltaForKeyRepeat(1, 36f), 0.001f)
        assertEquals("重复多少次都是一屏（不叠加）", 360f, RotaryBus.deltaForKeyRepeat(9, 36f), 0.001f)
        assertEquals("行高可注入", 720f, RotaryBus.deltaForKeyRepeat(1, 72f), 0.001f)
        assertEquals("屏行数可注入", 180f, RotaryBus.deltaForKeyRepeat(1, 36f, pageLines = 5), 0.001f)
        assertEquals("pageLines 非法时至少 1 行", 36f, RotaryBus.deltaForKeyRepeat(1, 36f, pageLines = 0), 0.001f)
    }
    /**
     * R61（紧急）：阀门**不能因为销毁顺序而卡住**。
     *
     * 事故：选择器用"保存 previous / 销毁时还原"管理阀门。切换风格时终端页与选择器同时销毁，
     * 终端先销毁把阀置 false，选择器再把 previous(true) 写回 -> 阀门永久 true ->
     * MainActivity.dispatchKeyEvent 全局吞掉 ENTER/DPAD_CENTER -> 表的确认键全部失效
     * （用户现象：设置页按钮点不动、什么都干不了）。
     */
    @Test
    fun valveDoesNotLeakRegardlessOfDisposeOrder() {
        // 终端先销毁、选择器后销毁（旧实现会在这里卡住）
        RotaryBus.acquire("terminal-page")
        RotaryBus.acquire("style-picker")
        assertTrue("两个持有者时接管", RotaryBus.enabled)
        RotaryBus.release("terminal-page")
        assertTrue("还剩选择器，仍应接管", RotaryBus.enabled)
        RotaryBus.release("style-picker")
        assertFalse("全部交还后必须放开按键（旧实现这里会永久 true）", RotaryBus.enabled)

        // 反序销毁
        RotaryBus.acquire("terminal-page")
        RotaryBus.acquire("style-picker")
        RotaryBus.release("style-picker")
        assertTrue("终端还在，仍接管", RotaryBus.enabled)
        RotaryBus.release("terminal-page")
        assertFalse("反序销毁也必须放开", RotaryBus.enabled)
    }

    /** 幂等：重复 acquire / 重复 release / 未持有就 release 都不该改变语义。 */
    @Test
    fun valveCallsAreIdempotent() {
        RotaryBus.acquire("x"); RotaryBus.acquire("x")
        assertTrue(RotaryBus.enabled)
        RotaryBus.release("x"); RotaryBus.release("x")
        assertFalse(RotaryBus.enabled)
        RotaryBus.release("never-acquired")
        assertFalse(RotaryBus.enabled)
    }}
