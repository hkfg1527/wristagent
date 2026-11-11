package com.example.myapplication.presentation.ui

import androidx.compose.ui.graphics.Color
import com.example.myapplication.presentation.ui.theme.Wear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主键边缘高光：三个时刻（按下 / 录音 / 发送）必须各有不同强度，且高光只用允许的三个令牌色。
 *
 * 另一半是底线：主键底色永远是 [Wear.Palette.Orb] 的 #CDD3DC —— 用户明确要求过"按钮要亮的，不要灰色"，
 * 高光只叠在那 1dp 边缘上，绝不允许拿底色去换明暗。
 */
class OrbEdgeHighlightTest {

    @Test
    fun orbBaseColorStaysTheBrightSilver() {
        assertEquals("主键底色被改暗了：用户要求按钮必须亮", Color(0xFFCDD3DC), Wear.Palette.Orb)
    }

    @Test
    fun theThreeMomentsHaveIncreasingHighlights() {
        val idle = OrbEdgeHighlight.intensityOf(BottomBlockMode.IDLE, isPressed = false)
        val recording = OrbEdgeHighlight.intensityOf(BottomBlockMode.RECORDING, isPressed = false)
        val sending = OrbEdgeHighlight.intensityOf(
            BottomBlockMode.IDLE,
            isPressed = false,
            sendFlash = 1f
        )
        val pressed = OrbEdgeHighlight.intensityOf(BottomBlockMode.IDLE, isPressed = true)

        assertEquals(OrbEdgeHighlight.IdleLevel, idle, 0.0001f)
        assertTrue("录音必须比静息亮", recording > idle)
        assertTrue("发送必须比录音亮", sending > recording)
        assertTrue("按下必须最亮", pressed > sending)
        assertEquals("最亮也只到 100%", 1f, pressed, 0.0001f)
    }

    @Test
    fun sendFlashDecaysBackToResting() {
        // 发出去那一刻闪一下，随后必须回到静息档：静息态不许留残光
        assertTrue(
            OrbEdgeHighlight.intensityOf(BottomBlockMode.IDLE, isPressed = false, sendFlash = 0.5f) >
                OrbEdgeHighlight.IdleLevel
        )
        assertEquals(
            OrbEdgeHighlight.IdleLevel,
            OrbEdgeHighlight.intensityOf(BottomBlockMode.IDLE, isPressed = false, sendFlash = 0f),
            0.0001f
        )
    }

    @Test
    fun recordingPulsesWithoutFallingBackToResting() {
        val low = OrbEdgeHighlight.intensityOf(BottomBlockMode.RECORDING, isPressed = false, pulse = 0.2f)
        val high = OrbEdgeHighlight.intensityOf(BottomBlockMode.RECORDING, isPressed = false, pulse = 1f)
        assertTrue("呼吸下限也不许和静息分不出来", low > OrbEdgeHighlight.IdleLevel)
        assertTrue("呼吸要有幅度但不超过发送档", high > low && high < OrbEdgeHighlight.SendLevel)
    }

    @Test
    fun pressingWinsOverEveryOtherMoment() {
        for (mode in BottomBlockMode.values()) {
            assertEquals(
                "按下就是最亮的那一档，任何形态都不例外",
                OrbEdgeHighlight.PressedLevel,
                OrbEdgeHighlight.intensityOf(mode, isPressed = true),
                0.0001f
            )
        }
    }

    @Test
    fun highlightOnlyUsesTheThreeAllowedTokens() {
        assertEquals(Wear.Palette.AccentTrack, OrbEdgeHighlight.toneOf(BottomBlockMode.IDLE, isPressed = false))
        assertEquals(Wear.Palette.Accent, OrbEdgeHighlight.toneOf(BottomBlockMode.RECORDING, isPressed = false))
        assertEquals(Wear.Palette.TextPrimary, OrbEdgeHighlight.toneOf(BottomBlockMode.IDLE, isPressed = true))
        // 不能借审批的红黄：高光是"反光"，不是风险提示
        val tones = BottomBlockMode.values().flatMap { mode ->
            listOf(
                OrbEdgeHighlight.toneOf(mode, isPressed = false),
                OrbEdgeHighlight.toneOf(mode, isPressed = true)
            )
        }
        assertTrue("高光色落到了风险色上", tones.none { it == Wear.Palette.Danger || it == Wear.Palette.Warning })
    }

    @Test
    fun pressedScaleIsAShrinkNotAGrow() {
        assertTrue("按压必须是缩放进去，不是放大", OrbEdgeHighlight.PressedScale < 1f)
        assertTrue("缩太多会在 52dp 键上抖", OrbEdgeHighlight.PressedScale > 0.9f)
    }
}
