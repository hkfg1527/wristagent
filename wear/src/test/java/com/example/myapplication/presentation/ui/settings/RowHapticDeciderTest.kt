package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.presentation.ui.rotaryScrollDelta
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 主设置页震动策略：换中心条目才震、同索引不震、120ms 内不重复震。 */
class RowHapticDeciderTest {

    @Test
    fun indexChangeTicks() {
        assertTrue(
            RowHapticDecider.shouldTick(
                previousIndex = 2,
                newIndex = 3,
                nowMs = 1_000L,
                lastTickMs = RowHapticDecider.NeverTicked
            )
        )
    }

    @Test
    fun sameIndexNeverTicks() {
        assertFalse(
            RowHapticDecider.shouldTick(
                previousIndex = 4,
                newIndex = 4,
                nowMs = 9_000L,
                lastTickMs = RowHapticDecider.NeverTicked
            )
        )
        assertFalse(
            RowHapticDecider.shouldTick(
                previousIndex = 4,
                newIndex = 4,
                nowMs = 9_500L,
                lastTickMs = 9_000L
            )
        )
    }

    @Test
    fun ticksAreSuppressedInsideTheMinInterval() {
        assertFalse(
            RowHapticDecider.shouldTick(
                previousIndex = 3,
                newIndex = 4,
                nowMs = 1_119L,
                lastTickMs = 1_000L
            )
        )
    }

    @Test
    fun ticksResumeOnceTheMinIntervalElapsed() {
        assertTrue(
            RowHapticDecider.shouldTick(
                previousIndex = 3,
                newIndex = 4,
                nowMs = 1_120L,
                lastTickMs = 1_000L
            )
        )
    }

    @Test
    fun rapidConsecutiveChangesTickOnlyOnce() {
        var previousIndex = 0
        var lastTickMs = RowHapticDecider.NeverTicked
        var ticks = 0
        for (step in 1..5) {
            val nowMs = 1_000L + (step - 1) * 25L
            if (RowHapticDecider.shouldTick(previousIndex, step, nowMs, lastTickMs)) {
                ticks++
                lastTickMs = nowMs
            }
            previousIndex = step
        }
        assertEquals(1, ticks)
    }

    @Test
    fun consecutiveChangesAfterTheWindowTickAgain() {
        var previousIndex = 0
        var lastTickMs = RowHapticDecider.NeverTicked
        var ticks = 0
        for (step in 1..3) {
            val nowMs = 1_000L + (step - 1) * 200L
            if (RowHapticDecider.shouldTick(previousIndex, step, nowMs, lastTickMs)) {
                ticks++
                lastTickMs = nowMs
            }
            previousIndex = step
        }
        assertEquals(3, ticks)
    }

    @Test
    fun crownTicksAreScaledByTheSharedGain() {
        assertEquals(6f, rotaryScrollDelta(2f), 0.001f)
        assertEquals(-3f, rotaryScrollDelta(-1f), 0.001f)
        assertEquals(0f, rotaryScrollDelta(0f), 0.001f)
    }
}
