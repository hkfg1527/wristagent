package com.example.myapplication.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * externalPrompt 一次性消费语义单元测试 (TDD 严格红绿循环)
 * 验证：
 * 1. 只有进入主界面 (ready) 且文本非空才消费
 * 2. 同一 seq 的 prompt 只触发一次，重组不重复触发
 * 3. 新的 intent (新 seq，同文本) 可再次触发
 */
class ExternalPromptConsumptionTest {

    @Test
    fun testNullPrompt_neverConsumed() {
        assertFalse(ExternalPromptGate.shouldConsume(null, ready = true, lastConsumedSeq = null))
    }

    @Test
    fun testNotReady_notConsumed() {
        val prompt = ExternalPrompt(seq = 1L, text = "查一下电量")
        assertFalse(ExternalPromptGate.shouldConsume(prompt, ready = false, lastConsumedSeq = null))
    }

    @Test
    fun testBlankText_notConsumed() {
        val prompt = ExternalPrompt(seq = 1L, text = "   ")
        assertFalse(ExternalPromptGate.shouldConsume(prompt, ready = true, lastConsumedSeq = null))
    }

    @Test
    fun testReadyAndFresh_consumedExactlyOnce() {
        val prompt = ExternalPrompt(seq = 1L, text = "查一下电量")
        assertTrue(ExternalPromptGate.shouldConsume(prompt, ready = true, lastConsumedSeq = null))
        // 消费后记录 seq，重组/重放不得再次触发
        assertFalse(ExternalPromptGate.shouldConsume(prompt, ready = true, lastConsumedSeq = 1L))
    }

    @Test
    fun testNewSeqWithSameText_consumedAgain() {
        assertTrue(ExternalPromptGate.shouldConsume(ExternalPrompt(seq = 2L, text = "查一下电量"), ready = true, lastConsumedSeq = 1L))
    }

    @Test
    fun testConsumedSeqIsMonotonicAfterClear() {
        // 回清 externalPrompt 后 (prompt = null) 不得误消费
        assertFalse(ExternalPromptGate.shouldConsume(null, ready = true, lastConsumedSeq = 3L))
        assertTrue(ExternalPromptGate.shouldConsume(ExternalPrompt(seq = 4L, text = "再来一条"), ready = true, lastConsumedSeq = 3L))
    }
}
