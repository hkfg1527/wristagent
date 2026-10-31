package com.example.myapplication.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 语音错误提示文案规整单元测试 (TDD 严格红绿循环)
 * 验证：错误信息不再被静默丢弃，可展示文案被裁剪；空/空白消息不产生提示条
 */
class SpeechErrorNoticeTest {

    @Test
    fun testNormalMessage_isTrimmed() {
        assertEquals("未识别到有效文字", SpeechErrorNotice.message("  未识别到有效文字  "))
    }

    @Test
    fun testBlankMessage_producesNoNotice() {
        assertNull(SpeechErrorNotice.message("   "))
        assertNull(SpeechErrorNotice.message(""))
        assertNull(SpeechErrorNotice.message(null))
    }

    @Test
    fun testEngineMessage_isPreservedVerbatim() {
        assertEquals("启动语音录入失败: missing service", SpeechErrorNotice.message("启动语音录入失败: missing service"))
    }
}
