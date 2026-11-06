package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 轮数策略：无硬上限，但能量提示只提示一次，空转必须先提示再收口。 */
class AgentLoopPolicyTest {

    @Test
    fun energyHintFiresOnceAtOrBeyondTheThreshold() {
        assertEquals(40, AgentLoopPolicy.ENERGY_HINT_AFTER_TURNS)
        assertFalse(AgentLoopPolicy.shouldInjectEnergyHint(0, false))
        assertFalse("阈值前一轮不提示", AgentLoopPolicy.shouldInjectEnergyHint(39, false))
        assertTrue("到达阈值即提示", AgentLoopPolicy.shouldInjectEnergyHint(40, false))
        assertFalse("已经提示过就不再提示", AgentLoopPolicy.shouldInjectEnergyHint(40, true))
        assertFalse("第 80 轮也不再重复提示", AgentLoopPolicy.shouldInjectEnergyHint(80, true))
    }

    @Test
    fun energyHintTextNamesTheTurnCountAndAsksToSummarize() {
        val text = AgentLoopPolicy.energyHintText(40)
        assertTrue("提示必须带实际轮数，收到：" + text, text.contains("40"))
        assertTrue("提示必须要求总结收尾，收到：" + text, text.contains("总结"))
        assertTrue("提示必须是终止性要求，收到：" + text, text.contains("结束"))
    }

    @Test
    fun idleSpinNudgesThenStops() {
        assertEquals(IdleSpinDecision.CONTINUE, AgentLoopPolicy.idleSpinDecision(0))
        assertEquals(IdleSpinDecision.CONTINUE, AgentLoopPolicy.idleSpinDecision(2))
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(3))
        assertEquals(IdleSpinDecision.NUDGE, AgentLoopPolicy.idleSpinDecision(4))
        assertEquals(IdleSpinDecision.STOP, AgentLoopPolicy.idleSpinDecision(5))
    }

    @Test
    fun detectorCountsConsecutiveIdenticalCallsAndResetsOnChange() {
        val detector = IdleSpinDetector()
        assertEquals(IdleSpinDecision.CONTINUE, detector.observe("get_device_status#{}"))
        assertEquals(IdleSpinDecision.CONTINUE, detector.observe("get_device_status#{}"))
        assertEquals(IdleSpinDecision.NUDGE, detector.observe("get_device_status#{}"))
        assertEquals("换了调用立即归零", IdleSpinDecision.CONTINUE, detector.observe("web_search#{\"query\":\"a\"}"))
        assertEquals(IdleSpinDecision.CONTINUE, detector.observe("get_device_status#{}"))
        detector.reset()
        assertEquals("reset 后从头数", IdleSpinDecision.CONTINUE, detector.observe("get_device_status#{}"))
    }
}
