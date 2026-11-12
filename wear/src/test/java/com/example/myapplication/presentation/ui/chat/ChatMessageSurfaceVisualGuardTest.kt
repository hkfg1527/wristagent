package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 聊天消息表面与弹性入场的源码守护；纯视觉参数不依赖 Compose 运行时。 */
class ChatMessageSurfaceVisualGuardTest {

    @Test
    fun agentSurfaceUsesAGradientAndNoPermanentBoxBorder() {
        val body = source(ComponentsPath).substringAfter("fun AgentSquareBlurBox(")

        assertTrue(body.contains("Brush.verticalGradient"))
        assertTrue(body.contains("Wear.Palette.Surface"))
        assertTrue(body.contains("Wear.Palette.SurfaceAlt"))
        assertTrue("只有审批态才允许出现描边", body.contains("approvalVisualState == ApprovalVisualState.NONE"))
        assertFalse(".border(1.dp, edge, shape)" in body)
        assertFalse(body.contains(".shadow("))
    }

    @Test
    fun userBubbleUsesAccentGradientAndAnAsymmetricTail() {
        val body = source(ComponentsPath)
            .substringAfter("fun UserChatBubble(")
            .substringBefore("fun AgentSquareBlurBox(")

        assertTrue(body.contains("Brush.linearGradient"))
        assertTrue(body.contains("Wear.AccentSurface"))
        assertTrue(body.contains("bottomEnd = Wear.Radius.Control / 2"))
    }

    @Test
    fun messageEntryUsesMinimalScaleAndNoTranslation() {
        val code = source(AnimatedCardPath)

        assertTrue("入场只允许从略大等比收回", code.contains("Animatable(1.02f)"))
        assertTrue(code.contains("scaleX = scale.value") && code.contains("scaleY = scale.value"))
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
