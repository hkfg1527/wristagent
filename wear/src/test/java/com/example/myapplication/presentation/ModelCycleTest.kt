package com.example.myapplication.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 右副键模型循环单元测试 (TDD 严格红绿循环)
 * 验证：
 * 1. 只在 preferences.cachedRemoteModels 里循环，绝不出现硬编码 gemini
 * 2. 空列表/空白列表 -> 不切换 (返回 null)
 * 3. 当前值不在列表内 (未知值) -> 回到列表首个
 * 4. 末位再切 -> 回卷到首位
 * 5. 空白项与重复项被规整
 */
class ModelCycleTest {

    private val deepseek = "deepseek-chat,deepseek-reasoner"

    @Test
    fun testParse_returnsOrderedNormalizedList() {
        assertEquals(listOf("deepseek-chat", "deepseek-reasoner"), ModelCycle.parse(deepseek))
        assertEquals(listOf("a", "b"), ModelCycle.parse("  a ,, b , a "))
        assertEquals(emptyList<String>(), ModelCycle.parse(null))
        assertEquals(emptyList<String>(), ModelCycle.parse("   ,  ,"))
    }

    @Test
    fun testNext_advancesToFollowingModel() {
        assertEquals("deepseek-reasoner", ModelCycle.next("deepseek-chat", deepseek))
    }

    @Test
    fun testNext_wrapsAroundAtEnd() {
        assertEquals("deepseek-chat", ModelCycle.next("deepseek-reasoner", deepseek))
    }

    @Test
    fun testNext_unknownCurrent_fallsBackToFirst() {
        assertEquals("deepseek-chat", ModelCycle.next("gemini-2.5-flash", deepseek))
        assertEquals("deepseek-chat", ModelCycle.next("", deepseek))
        assertEquals("deepseek-chat", ModelCycle.next(null, deepseek))
    }

    @Test
    fun testNext_emptyOrBlankCache_returnsNull() {
        assertNull(ModelCycle.next("deepseek-chat", ""))
        assertNull(ModelCycle.next("deepseek-chat", null))
        assertNull(ModelCycle.next("deepseek-chat", " , "))
    }

    @Test
    fun testNext_singleModelCache_staysOnIt() {
        assertEquals("only-model", ModelCycle.next("only-model", "only-model"))
        assertEquals("only-model", ModelCycle.next("other", "only-model"))
    }

    @Test
    fun testNext_currentWithSurroundingSpaces_isRecognized() {
        assertEquals("deepseek-reasoner", ModelCycle.next(" deepseek-chat ", deepseek))
    }

    @Test
    fun testNext_fullCycle_returnsToStartAfterTwoSteps() {
        val step1 = ModelCycle.next("deepseek-chat", deepseek)
        val step2 = ModelCycle.next(step1, deepseek)
        assertEquals("deepseek-chat", step2)
    }
}
