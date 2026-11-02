package com.example.myapplication.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 推理努力的三档取值、脏值回落，以及"供应商不支持就忽略"的发送策略。 */
class ReasoningEffortTest {

    @Test
    fun levelsMapToRequestValues() {
        assertEquals("low", ReasoningEffort.LOW.apiValue)
        assertEquals("medium", ReasoningEffort.MEDIUM.apiValue)
        assertEquals("high", ReasoningEffort.HIGH.apiValue)
    }

    @Test
    fun storedValueFallsBackToMedium() {
        assertEquals(ReasoningEffort.MEDIUM, ReasoningEffort.fromStored(null))
        assertEquals(ReasoningEffort.MEDIUM, ReasoningEffort.fromStored(""))
        assertEquals(ReasoningEffort.MEDIUM, ReasoningEffort.fromStored("极强"))
    }

    @Test
    fun storedValueRoundTripsEveryLevel() {
        ReasoningEffort.values().forEach { effort ->
            assertEquals(effort, ReasoningEffort.fromStored(effort.name))
        }
    }

    @Test
    fun reasoningModelCarriesTheEffortIntoTheBody() {
        assertEquals("high", ReasoningEffortPolicy.bodyValue("deepseek-reasoner", ReasoningEffort.HIGH))
        assertEquals("low", ReasoningEffortPolicy.bodyValue("deepseek-r1-distill", ReasoningEffort.LOW))
    }

    @Test
    fun plainChatModelGetsNoEffortField() {
        assertNull(ReasoningEffortPolicy.bodyValue("deepseek-chat", ReasoningEffort.HIGH))
        assertNull(ReasoningEffortPolicy.bodyValue("gpt-4o-mini", ReasoningEffort.MEDIUM))
    }

    @Test
    fun unsupportedFieldRejectionIsRecognised() {
        assertTrue(
            ReasoningEffortPolicy.isUnsupportedFieldError(
                400,
                "{\"error\":{\"message\":\"Unrecognized request argument supplied: reasoning_effort\"}}"
            )
        )
        assertTrue(
            ReasoningEffortPolicy.isUnsupportedFieldError(
                422,
                "{\"error\":{\"message\":\"Unsupported parameter: reasoning_effort\"}}"
            )
        )
    }

    @Test
    fun unrelatedServerErrorsAreNotTreatedAsUnsupportedField() {
        assertFalse(
            ReasoningEffortPolicy.isUnsupportedFieldError(
                401,
                "{\"error\":{\"message\":\"Authentication Fails, Your api key is invalid\"}}"
            )
        )
        assertFalse(ReasoningEffortPolicy.isUnsupportedFieldError(500, "reasoning_effort temporarily down"))
    }
}
