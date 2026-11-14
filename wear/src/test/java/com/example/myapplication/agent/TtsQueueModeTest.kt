package com.example.myapplication.agent

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 播报队列语义（设置项 tts_queue_mode）的取值与真实接线。
 *
 * 两个常量必须真的不同 —— 如果哪天有人把两者都写成 QUEUE_FLUSH，
 * "排队"这个选项会变成静默失效的假开关。
 */
class TtsQueueModeTest {

    @Test
    fun unknownStorageFallsBackToInterrupt() {
        assertEquals(TtsQueueMode.INTERRUPT, TtsQueueMode.fromStorage(null))
        assertEquals(TtsQueueMode.INTERRUPT, TtsQueueMode.fromStorage(""))
        assertEquals(TtsQueueMode.INTERRUPT, TtsQueueMode.fromStorage("NOPE"))
    }

    @Test
    fun everyModeRoundTripsThroughItsName() {
        for (mode in TtsQueueMode.entries) {
            assertEquals(mode, TtsQueueMode.fromStorage(mode.name))
        }
    }

    @Test
    fun theTwoModesMapToDifferentEngineConstants() {
        assertNotEquals(
            "打断与排队必须是不同的引擎常量",
            TtsQueueMode.INTERRUPT.queueConstant,
            TtsQueueMode.QUEUE.queueConstant
        )
    }

    @Test
    fun speakUsesTheConfiguredModeInsteadOfAHardcodedConstant() {
        val speaker = readProjectFile("src/main/java/com/example/myapplication/agent/ReplySpeaker.kt")
        assertTrue(
            "speak 必须用档位里的常量，不能硬写 QUEUE_FLUSH",
            speaker.contains("queueMode.queueConstant")
        )
        assertTrue(
            "硬写的 QUEUE_FLUSH 不应该再出现在 speak 调用里",
            !speaker.contains("speak(text, TextToSpeech.QUEUE_FLUSH")
        )
    }

    @Test
    fun theViewModelPassesTheMode() {
        val viewModel = readProjectFile("src/main/java/com/example/myapplication/presentation/ChatViewModel.kt")
        assertTrue(
            "ChatViewModel 必须把队列语义交给播报器",
            viewModel.contains("display.ttsQueueMode")
        )
    }
}
