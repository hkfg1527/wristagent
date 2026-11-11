package com.example.myapplication.presentation.ui

import androidx.compose.ui.graphics.Color
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.accentRailOf
import com.example.myapplication.presentation.ui.theme.contrastRatio
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
        // 运行时强调色在纯函数里读不到（投影是 @Composable 的），所以强调色由调用方传进来
        assertEquals(
            "静息档必须是强调色的轨道位投影，不是写死的默认蓝令牌",
            accentRailOf(Wear.Palette.Accent),
            OrbEdgeHighlight.toneOf(BottomBlockMode.IDLE, isPressed = false, accent = Wear.Palette.Accent)
        )
        assertEquals(
            "录音档就是运行时强调色本身",
            Wear.Palette.Accent,
            OrbEdgeHighlight.toneOf(BottomBlockMode.RECORDING, isPressed = false, accent = Wear.Palette.Accent)
        )
        assertEquals(
            Wear.Palette.TextPrimary,
            OrbEdgeHighlight.toneOf(BottomBlockMode.IDLE, isPressed = true, accent = Wear.Palette.Accent)
        )
        // 默认蓝下的轨道位落在 #3588AD 附近（±3 吸收库内 Oklab 换算的末位差）。
        // 这个数刻意与旧的 AccentTrack #3788AD 对齐 —— 换色要能跟随，但不能让默认配色面目全非。
        val defaultRail = OrbEdgeHighlight.toneOf(
            BottomBlockMode.IDLE,
            isPressed = false,
            accent = Wear.Palette.Accent
        )
        assertEquals("轨道位 R 不是 Bg 与强调色 0.765 的 Oklab 混合", 0x35 / 255f, defaultRail.red, 3f / 255f)
        assertEquals("轨道位 G 不是 Bg 与强调色 0.765 的 Oklab 混合", 0x88 / 255f, defaultRail.green, 3f / 255f)
        assertEquals("轨道位 B 不是 Bg 与强调色 0.765 的 Oklab 混合", 0xAD / 255f, defaultRail.blue, 3f / 255f)
        for (mode in BottomBlockMode.values()) {
            // 静止档只有两种：录音档是强调色本身（键上那圈要亮），其余一律是轨道位投影。
            // 之前这里写死「所有静止档都等于轨道位」，与上面 RECORDING 的显式断言自相矛盾。
            val expectedRest = if (mode == BottomBlockMode.RECORDING) {
                Wear.Palette.Accent
            } else {
                accentRailOf(Wear.Palette.Accent)
            }
            assertEquals(
                "静止档只有「轨道位投影」与「录音档强调色」两种",
                expectedRest,
                OrbEdgeHighlight.toneOf(mode, isPressed = false, accent = Wear.Palette.Accent)
            )
            assertEquals(
                "按下档永远是 TextPrimary，与强调色无关",
                Wear.Palette.TextPrimary,
                OrbEdgeHighlight.toneOf(mode, isPressed = true, accent = Wear.Palette.Accent)
            )
        }
        // 换一个强调色：静息/录音档必须跟着变，且静息档仍在 Bg 与强调色之间
        val green = Color(0xFF4ADE80)
        assertEquals(
            green,
            OrbEdgeHighlight.toneOf(BottomBlockMode.RECORDING, isPressed = false, accent = green)
        )
        val railContrast = contrastRatio(accentRailOf(green), Wear.Palette.Bg)
        assertTrue("静息档暗到和背景分不出来", railContrast >= 3f)
        assertTrue(
            "静息档亮到和强调色同档了",
            railContrast < contrastRatio(green, Wear.Palette.Bg)
        )
        // 不能借审批的红黄：高光是"反光"，不是风险提示
        val tones = BottomBlockMode.values().flatMap { mode ->
            listOf(
                OrbEdgeHighlight.toneOf(mode, isPressed = false, accent = Wear.Palette.Accent),
                OrbEdgeHighlight.toneOf(mode, isPressed = true, accent = Wear.Palette.Accent)
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
