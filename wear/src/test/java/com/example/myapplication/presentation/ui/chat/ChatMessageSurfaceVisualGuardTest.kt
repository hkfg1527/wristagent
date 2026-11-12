package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 聊天消息表面与弹性入场的源码守护；纯视觉参数不依赖 Compose 运行时。 */
class ChatMessageSurfaceVisualGuardTest {

    @Test
    fun agentSurfaceUsesGroupRadiusWithoutLosingItsSurfaceOrBorder() {
        val body = source(ComponentsPath).substringAfter("fun AgentSquareBlurBox(")

        assertTrue(body.contains("val shape = RoundedCornerShape(Wear.Radius.Group)"))
        assertTrue(body.contains(".background(Wear.Palette.Surface)"))
        assertTrue(body.contains(".border(1.dp, edge, shape)"))
        assertFalse(body.contains(".shadow("))
    }

    @Test
    fun userBubbleKeepsItsAccentSurface() {
        val body = source(ComponentsPath)
            .substringAfter("fun UserChatBubble(")
            .substringBefore("fun AgentSquareBlurBox(")

        assertTrue(body.contains(".background(Wear.AccentSurface)"))
    }

    @Test
    fun messageEntryUsesMinimalScaleAndNoTranslation() {
        val code = source(AnimatedCardPath)

        assertTrue(code.contains("Animatable(0.99f)"))
        assertTrue(code.contains("scale.animateTo(1f, Wear.Motion.micro())"))
        assertTrue(code.contains("alpha.animateTo(1f, Wear.Motion.base())"))
        assertFalse(code.contains("translationX"))
        assertFalse(code.contains("translationY"))
    }

    private fun source(path: String): String = readProjectFile(path)
        .replace(Regex("/\\*[\\s\\S]*?\\*/"), " ")
        .replace(Regex("//[^\n]*"), " ")

    private companion object {
        const val ComponentsPath =
            "src/main/java/com/example/myapplication/presentation/ui/FrostedGlassComponents.kt"
        const val AnimatedCardPath =
            "src/main/java/com/example/myapplication/presentation/ui/chat/AnimatedMessageCard.kt"
    }
}
