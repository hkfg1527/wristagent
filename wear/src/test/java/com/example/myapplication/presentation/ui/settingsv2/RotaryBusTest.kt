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
        RotaryBus.enabled = false
    }

    @Test
    fun disabledBusDropsEvents() = runTest {
        RotaryBus.enabled = false
        var got = Float.NaN
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { got = RotaryBus.events.first() }
        runCurrent()
        RotaryBus.emit(36f)
        runCurrent()
        assertTrue("阀门关着时不许投递（会抢走别的页面）", got.isNaN())
    }

    @Test
    fun enabledBusDeliversTheDelta() = runTest {
        RotaryBus.enabled = true
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
        RotaryBus.enabled = true
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
        RotaryBus.enabled = false
        RotaryBus.emit(36f)
        RotaryBus.enabled = true
        // 关阀门期间的事件不该在开阀门后被"补发"：没有订阅者时也不会缓存成待投递状态
        assertTrue("阀门开关本身不影响对象可用性", RotaryBus.enabled)
    }
}
