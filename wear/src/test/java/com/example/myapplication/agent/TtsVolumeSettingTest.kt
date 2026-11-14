package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 播报音量（设置项 tts_volume）的取值语义。
 *
 * 音量只在 ReplySpeaker.speak 里被消费（官方 per-utterance 参数 KEY_PARAM_VOLUME）。
 * 这里钉住三件事：出厂值、越界夹取、以及 NaN（控制台写坏的值）必须退回出厂值 ——
 * 否则 putFloat 会把 NaN 塞给引擎，用户听到的是"没声音"而不是"设错了"。
 */
class TtsVolumeSettingTest {

    @Test
    fun theFactoryValuePassesThrough() {
        assertEquals(0.8f, ReplySpeaker.volumeFor(0.8f), 0.0001f)
        assertEquals(0.3f, ReplySpeaker.volumeFor(0.3f), 0.0001f)
        assertEquals(1f, ReplySpeaker.volumeFor(1f), 0.0001f)
    }

    @Test
    fun outOfRangeValuesAreClampedToAudibleRange() {
        assertEquals(0.1f, ReplySpeaker.volumeFor(0f), 0.0001f)
        assertEquals(0.1f, ReplySpeaker.volumeFor(-3f), 0.0001f)
        assertEquals(1f, ReplySpeaker.volumeFor(5f), 0.0001f)
    }

    @Test
    fun nanFallsBackToTheFactoryValue() {
        assertEquals(
            AgentPreferences.DEFAULT_TTS_VOLUME,
            ReplySpeaker.volumeFor(Float.NaN),
            0.0001f
        )
    }

    @Test
    fun thePreferenceClampMatchesTheEngineClamp() {
        assertEquals(AgentPreferences.MIN_TTS_VOLUME, ReplySpeaker.volumeFor(0f), 0.0001f)
        assertEquals(AgentPreferences.MAX_TTS_VOLUME, ReplySpeaker.volumeFor(99f), 0.0001f)
    }
}
