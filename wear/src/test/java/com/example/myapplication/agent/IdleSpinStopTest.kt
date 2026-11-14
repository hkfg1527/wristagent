package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空转收口轮次（设置项 idle_spin_stop_after）的语义与真实接线。
 *
 * 这个旋钮决定"同一份调用重复几次就停手"：调太激进会打断正常重试，调太宽容等于放任死循环，
 * 所以夹取必须在策略里做，而且主循环与子代理必须共用同一份判据。
 */
class IdleSpinStopTest {

    @Test
    fun theFactoryBehaviourIsUnchanged() {
        // 出厂 5：3 次提示、5 次停手
        assertEquals(IdleSpinDecision.CONTINUE, AgentLoopPolicy.idleSpinDecision(2))
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(3))
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(4))
        assertEquals(IdleSpinDecision.STOP, AgentLoopPolicy.idleSpinDecision(5))
        assertEquals(IdleSpinDecision.STOP, AgentLoopPolicy.idleSpinDecision(9))
    }

    @Test
    fun aCustomStopAlsoMovesTheNudge() {
        // 调到 3：第 2 次就该提示，第 3 次收口（提示永远在停手前 2 轮）
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(2, stopAfter = 3))
        assertEquals(IdleSpinDecision.STOP, AgentLoopPolicy.idleSpinDecision(3, stopAfter = 3))
        // 调到 8：第 7 次仍是提示
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(7, stopAfter = 8))
        assertEquals(IdleSpinDecision.STOP, AgentLoopPolicy.idleSpinDecision(8, stopAfter = 8))
    }

    @Test
    fun outOfRangeStopsAreClampedInsideThePolicy() {
        // 1（越界下界）夹到 2：第二次相同调用就停
        assertEquals(IdleSpinDecision.STOP, AgentLoopPolicy.idleSpinDecision(2, stopAfter = 1))
        // 99 夹到 8：第 7 次还只是提示
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(7, stopAfter = 99))
    }

    @Test
    fun theDetectorUsesTheConfiguredStop() {
        val detector = IdleSpinDetector(stopAfter = 2)
        assertEquals(IdleSpinDecision.CONTINUE, detector.observe("a#1"))
        assertEquals(IdleSpinDecision.STOP, detector.observe("a#1"))
    }

    @Test
    fun bothLoopsShareTheSamePreference() {
        val dispatcher = readProjectFile("src/main/java/com/example/myapplication/agent/AgentDispatcher.kt")
        assertTrue(
            "主循环必须读偏好",
            dispatcher.contains("IdleSpinDetector(preferences.idleSpinStopAfter)")
        )
        assertTrue(
            "子代理 runner 也必须收到同一个阈值",
            dispatcher.contains("idleSpinStopAfter = preferences.idleSpinStopAfter")
        )
        val runner = readProjectFile("src/main/java/com/example/myapplication/agent/SubAgentRunner.kt")
        assertTrue("子代理内部必须用它构造检测器", runner.contains("IdleSpinDetector(idleSpinStopAfter)"))
    }

    @Test
    fun thePreferenceIsClampedToTwoThroughEight() {
        val p = AgentPreferences(FakeSharedPreferences())
        assertEquals(AgentLoopPolicy.IDLE_SPIN_STOP_AFTER, p.idleSpinStopAfter)
        p.idleSpinStopAfter = 0
        assertEquals(AgentLoopPolicy.MIN_IDLE_SPIN_STOP, p.idleSpinStopAfter)
        p.idleSpinStopAfter = 50
        assertEquals(AgentLoopPolicy.MAX_IDLE_SPIN_STOP, p.idleSpinStopAfter)
    }
}
