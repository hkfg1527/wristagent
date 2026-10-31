package com.example.myapplication.presentation

import com.example.myapplication.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 语音错误提示规整单元测试 (TDD 严格红绿循环)
 * 验证：错误信息不再被静默丢弃，话术作为资源占位参数参与本地化；空/空白消息不产生提示条
 */
class SpeechErrorNoticeTest {

    @Test
    fun testNormalMessage_mapsToVoiceErrorResource() {
        val notice = SpeechErrorNotice.notice("  未识别到有效文字  ")
        assertEquals(R.string.wearchat_voice_error, notice?.textRes)
        assertEquals(listOf<Any>("未识别到有效文字"), notice?.args)
        assertEquals(true, notice?.isError)
    }

    @Test
    fun testBlankMessage_producesNoNotice() {
        assertNull(SpeechErrorNotice.notice("   "))
        assertNull(SpeechErrorNotice.notice(""))
        assertNull(SpeechErrorNotice.notice(null))
    }

    @Test
    fun testEngineMessage_isPreservedAsPlaceholderArgument() {
        val notice = SpeechErrorNotice.notice("启动语音录入失败: missing service")
        assertEquals(R.string.wearchat_voice_error, notice?.textRes)
        assertEquals(listOf<Any>("启动语音录入失败: missing service"), notice?.args)
    }

    @Test
    fun testDifferentEngineMessages_shareTheResourceAndKeepTheirOwnArgument() {
        val network = SpeechErrorNotice.notice("网络超时")
        val engine = SpeechErrorNotice.notice("  启动语音录入失败: missing service\n")

        assertEquals(R.string.wearchat_voice_error, network?.textRes)
        assertEquals(R.string.wearchat_voice_error, engine?.textRes)
        assertEquals(listOf<Any>("网络超时"), network?.args)
        assertEquals(listOf<Any>("启动语音录入失败: missing service"), engine?.args)
    }
}
