package com.example.myapplication.presentation.ui

import com.example.myapplication.presentation.ui.settings.SettingsRowEnterCapMs
import com.example.myapplication.presentation.ui.settings.SettingsRowExitCapMs
import com.example.myapplication.presentation.ui.settings.SettingsRowExitStepMs
import com.example.myapplication.presentation.ui.settings.exitFraction
import com.example.myapplication.presentation.ui.settings.staggerDelayMs
import org.junit.Assert.assertEquals
import org.junit.Test

/** 逐行浮动的时间学：延迟必须线性、首行零延迟、行数多时封顶，退场采样必须单调且饱和。 */
class SettingsRowStaggerMathTest {

    @Test
    fun firstRowStartsImmediately() {
        assertEquals(0, staggerDelayMs(index = 0, stepMs = 40, maxMs = 360))
    }

    @Test
    fun middleRowGetsLinearOffset() {
        assertEquals(120, staggerDelayMs(index = 3, stepMs = 40, maxMs = 360))
    }

    @Test
    fun thirtyRowsAreCappedInsteadOfWaitingOnePointTwoSeconds() {
        assertEquals(360, staggerDelayMs(index = 30, stepMs = 40, maxMs = 360))
    }

    @Test
    fun capBoundaryHoldsAtExactHitAndBeyond() {
        assertEquals(360, staggerDelayMs(index = 9, stepMs = 40, maxMs = 360))
        assertEquals(360, staggerDelayMs(index = 10, stepMs = 40, maxMs = 360))
    }

    @Test
    fun negativeIndexIsClampedToZero() {
        assertEquals(0, staggerDelayMs(index = -4, stepMs = 40, maxMs = 360))
    }

    @Test
    fun nonPositiveStepOrCapYieldsZero() {
        assertEquals(0, staggerDelayMs(index = 5, stepMs = 0, maxMs = 360))
        assertEquals(0, staggerDelayMs(index = 5, stepMs = 40, maxMs = 0))
    }

    @Test
    fun defaultCapIsTheEnterCap() {
        assertEquals(SettingsRowEnterCapMs, staggerDelayMs(index = 100, stepMs = 40))
    }

    @Test
    fun exitConfigIsFasterAndCappedLower() {
        assertEquals(0, staggerDelayMs(index = 0, stepMs = SettingsRowExitStepMs, maxMs = SettingsRowExitCapMs))
        assertEquals(50, staggerDelayMs(index = 2, stepMs = SettingsRowExitStepMs, maxMs = SettingsRowExitCapMs))
        assertEquals(
            SettingsRowExitCapMs,
            staggerDelayMs(index = 10, stepMs = SettingsRowExitStepMs, maxMs = SettingsRowExitCapMs)
        )
    }

    @Test
    fun exitFractionIsZeroBeforeItsOwnStart() {
        assertEquals(0f, exitFraction(elapsedMs = 30f, startMs = 50, durationMs = 140), 0.0001f)
    }

    @Test
    fun exitFractionGrowsLinearly() {
        assertEquals(50f / 140f, exitFraction(elapsedMs = 100f, startMs = 50, durationMs = 140), 0.0001f)
    }

    @Test
    fun exitFractionSaturatesAtOne() {
        assertEquals(1f, exitFraction(elapsedMs = 5000f, startMs = 50, durationMs = 140), 0.0001f)
    }

    @Test
    fun exitFractionWithNonPositiveDurationJumpsToEnd() {
        assertEquals(1f, exitFraction(elapsedMs = 51f, startMs = 50, durationMs = 0), 0.0001f)
        assertEquals(0f, exitFraction(elapsedMs = 49f, startMs = 50, durationMs = 0), 0.0001f)
    }
}
