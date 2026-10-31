package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AgentPreferencesDefaultsTest {

    @Test
    fun maxOutputTokensDefaultIsNoLongerTruncating() {
        assertEquals(2048, AgentPreferences.DEFAULT_MAX_OUTPUT_TOKENS)
    }

    @Test
    fun asrDefaultsAreSafeAndDisabled() {
        assertEquals("SYSTEM", AgentPreferences.DEFAULT_ASR_PROVIDER)
        assertEquals(1537, AgentPreferences.DEFAULT_ASR_DEV_PID)
        assertFalse(AgentPreferences.DEFAULT_ASR_ENABLED)
        assertEquals("", AgentPreferences.DEFAULT_ASR_KEY)
    }

    @Test
    fun asrStorageKeysAreStableForMigration() {
        assertEquals("asr_provider", AgentPreferences.KEY_ASR_PROVIDER)
        assertEquals("asr_baidu_api_key", AgentPreferences.KEY_ASR_BAIDU_API_KEY)
        assertEquals("asr_baidu_secret_key", AgentPreferences.KEY_ASR_BAIDU_SECRET_KEY)
        assertEquals("asr_dev_pid", AgentPreferences.KEY_ASR_DEV_PID)
        assertEquals("asr_enabled", AgentPreferences.KEY_ASR_ENABLED)
    }
}
