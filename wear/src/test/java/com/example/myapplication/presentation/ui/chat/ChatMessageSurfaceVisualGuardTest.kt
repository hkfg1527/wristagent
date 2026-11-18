package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 聊天消息表面与弹性入场的源码守护；纯视觉参数不依赖 Compose 运行时。 */
class ChatMessageSurfaceVisualGuardTest {

    @Test
    fun agentSurfaceUsesAGradientAndNoPermanentBoxBorder() {
        val file = source(ComponentsPath)
        assertTrue("锚点缺失：fun AgentSquareBlurBox(", file.contains("fun AgentSquareBlurBox("))
        // 只截到下一个函数：不设终点的话锚点一改名就会返回整文件，下面的断言全退化成"文件里出现过"
        val body = file
            .substringAfter("fun AgentSquareBlurBox(")
            .substringBefore("\n@Composable\nfun ")

        assertTrue(body.contains("Brush.verticalGradient"))
        assertTrue(body.contains("Wear.Palette.Surface"))
        assertTrue(body.contains("Wear.Palette.SurfaceAlt"))
        assertTrue("只有审批态才允许出现描边", body.contains("approvalVisualState == ApprovalVisualState.NONE"))
        // 覆盖写法：1.dp / 1.5.dp / border(width = …) 都算描边。
        // 规则是"全函数只允许一处描边，且必须挂在 approvalBorder 的审批分支里"——
        // 旧断言只钉 `.border(1.dp, edge, shape)` 这一种字面量，换个宽度参数就能绕过。
        val borderCalls = Regex("""\.border\(""").findAll(body).count()
        assertEquals("描边只允许有一处（审批态分支），实际 " + borderCalls + " 处", 1, borderCalls)
        val approvalBlock = body.substringAfter("val approvalBorder = if (")
        assertTrue("那唯一的描边必须挂在审批态条件分支里", approvalBlock.contains(".border(1.dp, borderColor, shape)"))
        assertFalse(body.contains(".shadow("))
    }

    @Test
    fun userBubbleUsesAccentGradientAndAnAsymmetricTail() {
        val file = source(ComponentsPath)
        assertTrue("锚点缺失：fun UserChatBubble(", file.contains("fun UserChatBubble("))
        assertTrue("锚点缺失：fun AgentSquareBlurBox(", file.contains("fun AgentSquareBlurBox("))
        val body = file
            .substringAfter("fun UserChatBubble(")
            .substringBefore("fun AgentSquareBlurBox(")
        assertTrue("UserChatBubble 切片异常", body.length < file.length / 2)

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
