package com.example.myapplication.presentation

import com.example.myapplication.presentation.ui.ModelLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模型短标签与网格切分单元测试
 * 覆盖：去供应商前缀 / 去首位字母前缀 / 数字段保留 / 空白归一 / 空列表三态 / 行切分无尾空行
 */
class ModelLabelsTest {

    @Test
    fun label_deepseekChat_returnsChat() {
        assertEquals("Chat", ModelLabels.label("deepseek-chat"))
    }

    @Test
    fun label_deepseekReasoner_returnsReasoner() {
        assertEquals("Reasoner", ModelLabels.label("deepseek-reasoner"))
    }

    @Test
    fun label_providerSlashOmittedAndLeadingAlphaDropped() {
        assertEquals("4o", ModelLabels.label("openai/gpt-4o"))
    }

    @Test
    fun label_doubleProviderPrefix_returnsChat() {
        assertEquals("Chat", ModelLabels.label("deepseek/deepseek-chat"))
    }

    @Test
    fun label_numericSegmentKeptAsIs() {
        assertEquals("2.5 Flash", ModelLabels.label("acme-2.5-flash"))
    }

    @Test
    fun label_multipleWordsCapitalized() {
        assertEquals("Pro Max", ModelLabels.label("acme-pro-max"))
    }

    @Test
    fun label_singleToken_capitalizedOnly() {
        assertEquals("Deepseek", ModelLabels.label("deepseek"))
    }

    @Test
    fun label_singleTokenWithDigit_capitalized() {
        assertEquals("O3", ModelLabels.label("o3"))
    }

    @Test
    fun label_nullAndBlank_returnEmpty() {
        assertEquals("", ModelLabels.label(null))
        assertEquals("", ModelLabels.label(""))
        assertEquals("", ModelLabels.label("   "))
        assertEquals("", ModelLabels.label("/"))
        assertEquals("", ModelLabels.label("/   "))
    }

    @Test
    fun label_surroundingWhitespaceTrimmed() {
        assertEquals("Chat", ModelLabels.label("  deepseek-chat  "))
    }

    @Test
    fun isEmpty_threeStates() {
        assertTrue(ModelLabels.isEmpty(null))
        assertTrue(ModelLabels.isEmpty(emptyList()))
        assertFalse(ModelLabels.isEmpty(listOf("deepseek-chat")))
    }

    @Test
    fun rows_oddCount_lastRowHasSingleItem() {
        assertEquals(
            listOf(listOf("a", "b"), listOf("c", "d"), listOf("e")),
            ModelLabels.rows(listOf("a", "b", "c", "d", "e"), perRow = 2)
        )
    }

    @Test
    fun rows_evenCount_noTrailingEmptyRow() {
        val result = ModelLabels.rows(listOf("a", "b", "c", "d"), perRow = 2)
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), result)
        assertEquals(2, result.size)
    }

    @Test
    fun rows_defaultTwoModels_singleRow() {
        assertEquals(
            listOf(listOf("deepseek-chat", "deepseek-reasoner")),
            ModelLabels.rows(listOf("deepseek-chat", "deepseek-reasoner"))
        )
    }

    @Test
    fun rows_degenerateInputs_returnEmpty() {
        assertTrue(ModelLabels.rows(emptyList(), perRow = 2).isEmpty())
        assertTrue(ModelLabels.rows(null, perRow = 2).isEmpty())
        assertTrue(ModelLabels.rows(listOf("a", "b"), perRow = 0).isEmpty())
        assertTrue(ModelLabels.rows(listOf("a", "b"), perRow = -3).isEmpty())
    }
}
