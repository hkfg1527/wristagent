package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 端口漂移的判定：真实端口与首选端口的关系只有三种，界面的三句话各对应一条。
 *
 * 这一层是纯函数，不碰 ConsoleServer 也不碰 Compose —— 漂移与否的判据因此可以单独钉死，
 * 不必等到真机上 8080 恰好被占用才验证。
 */
class ConsolePortStatusTest {

    @Test
    fun driftedOnlyWhenTheRealPortDiffersFromThePreferredOne() {
        assertEquals(ConsolePortStatus.STABLE, ConsolePortStatus.of(8080, 8080))
        // 8080 被占用后的两个后备端口都必须是漂移，而不是只有 8081
        assertEquals(ConsolePortStatus.DRIFTED, ConsolePortStatus.of(8080, 8081))
        assertEquals(ConsolePortStatus.DRIFTED, ConsolePortStatus.of(8080, 8082))

        // 判定与 8080 无耦合：首选端口换成别的，结论只由两个数字的关系决定
        assertEquals(ConsolePortStatus.STABLE, ConsolePortStatus.of(9000, 9000))
        assertEquals(ConsolePortStatus.DRIFTED, ConsolePortStatus.of(9000, 8080))
    }

    @Test
    fun theNotRunningSentinelWinsOverEveryPortComparison() {
        // ConsoleServer 未运行时把 port 记成 -1：这一行只能是"未运行"，不是"漂移"
        assertEquals(ConsolePortStatus.NOT_RUNNING, ConsolePortStatus.of(8080, -1))
        assertNotEquals(ConsolePortStatus.DRIFTED, ConsolePortStatus.of(8080, -1))
        assertEquals(ConsolePortStatus.NOT_RUNNING, ConsolePortStatus.of(8081, -1))
        // 0 不是合法监听端口（内核临时端口对外不可见），同样按未运行处理
        assertEquals(ConsolePortStatus.NOT_RUNNING, ConsolePortStatus.of(8080, 0))
        assertNotEquals(ConsolePortStatus.DRIFTED, ConsolePortStatus.of(8080, 0))
    }
}
