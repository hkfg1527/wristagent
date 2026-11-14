package com.example.myapplication.presentation.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 表冠增益的算术语义。
 *
 * 消费者只有两处：根页与全部子页的 `onRotaryScrollEvent`（`dispatchRawDelta`）。
 * Wear 的 `verticalScrollPixels` 与 `dispatchRawDelta` 都是 Float，所以这条链路全程 Float，
 * 不做取整 —— 取整会让慢速转动变成一顿一顿。
 */
class RotaryScrollGainTest {

    @Test
    fun theDefaultGainIsOneToOne() {
        assertEquals(0f, rotaryDeltaFor(0f, 1f), 0.0001f)
        assertEquals(16f, rotaryDeltaFor(16f, 1f), 0.0001f)
        assertEquals(-16f, rotaryDeltaFor(-16f, 1f), 0.0001f)
    }

    @Test
    fun theGainScalesTheDeltaLinearly() {
        assertEquals(32f, rotaryDeltaFor(16f, 2f), 0.0001f)
        assertEquals(8f, rotaryDeltaFor(16f, 0.5f), 0.0001f)
        assertEquals(-45f, rotaryDeltaFor(-15f, 3f), 0.0001f)
    }

    @Test
    fun outOfRangeGainsAreClampedInsteadOfBlowingUpTheList() {
        assertEquals(8f, rotaryDeltaFor(16f, 0.01f), 0.0001f)
        assertEquals(48f, rotaryDeltaFor(16f, 100f), 0.0001f)
    }

    @Test
    fun fractionalDeltasSurviveSoSlowTurnsStaySmooth() {
        assertEquals(1.5f, rotaryDeltaFor(3f, 0.5f), 0.0001f)
        assertEquals(7.35f, rotaryDeltaFor(7f, 1.05f), 0.0001f)
    }
}
