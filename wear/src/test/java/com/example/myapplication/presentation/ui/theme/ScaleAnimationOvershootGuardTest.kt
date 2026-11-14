package com.example.myapplication.presentation.ui.theme

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 硬约束的可执行版本：**凡是改变 scale 的动画必须不过冲**。
 *
 * 这条约束光靠"记得"是守不住的 —— 我做第 32 轮时才查出来两处违反：
 * 消息卡入场用 `Wear.Motion.micro()`（当时阻尼 0.85）做 scale 收缩、主键按压用 0.62 做 scale。
 * 所以这里把它变成扫描：
 * 1. 全仓库不允许出现 `dampingRatio = 0.x`，除非命中白名单且理由与 scale 无关；
 * 2. `Wear.Motion.micro()` 的阻尼必须是 1.0（它就是被 scale 动画复用的那个令牌）。
 */
class ScaleAnimationOvershootGuardTest {

    /** 允许的例外：文件 → 理由。新增一条必须在提交信息里说明它为什么不碰 scale。 */
    private val allowList = mapOf(
        "FrostedGlassComponents.kt" to "两处都是箭头旋转角度（0→90°），不是 scale",
        "ChatScreen.kt" to "驱动的点阵位移幅度（空间位移），不是 scale",
    )

    @Test
    fun noSpringOvershootsOutsideTheAllowList() {
        val offenders = mutableListOf<String>()
        for (file in watchedFiles) {
            val text = readProjectFile(file)
            if (!text.contains("dampingRatio = 0.")) continue
            val fileName = file.substringAfterLast('/')
            if (allowList.containsKey(fileName)) continue
            offenders += fileName
        }
        assertEquals(
            "出现新的过冲弹簧：如果它动画的是 scale，改成 dampingRatio = 1f；" +
                "如果确实与 scale 无关，把它加进本测试的白名单并写明理由。实际违规：",
            emptyList<String>(),
            offenders.distinct()
        )
    }

    @Test
    fun theMicroTokenItselfDoesNotOvershoot() {
        val tokens = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/theme/WearTokens.kt")
        assertTrue(
            "Wear.Motion.micro() 会被 scale 动画复用，阻尼必须是 1.0",
            tokens.contains("fun <T> micro(): SpringSpec<T> = spring(dampingRatio = 1f")
        )
    }

    @Test
    fun theMessageCardEntranceDoesNotOvershoot() {
        val card = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/chat/AnimatedMessageCard.kt")
        assertTrue("入场收缩必须走不过冲的令牌", card.contains("scale.animateTo(1f, Wear.Motion.micro())"))
    }

    @Test
    fun theThemeShipsTheOfficialStandardMotionScheme() {
        val theme = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/theme/WearMaterialTheme.kt")
        assertTrue(
            "必须用官方 MotionScheme，而且默认不能是带过冲的 expressive",
            theme.contains("motionScheme = MotionScheme.standard()")
        )
    }

    /** 动画热点文件：新增动画文件时把它加进来，扫描才有意义。 */
    private val watchedFiles = listOf(
        "src/main/java/com/example/myapplication/presentation/ui/BottomInteractiveBlock.kt",
        "src/main/java/com/example/myapplication/presentation/ui/FrostedGlassComponents.kt",
        "src/main/java/com/example/myapplication/presentation/ui/chat/ChatScreen.kt",
        "src/main/java/com/example/myapplication/presentation/ui/chat/AnimatedMessageCard.kt",
        "src/main/java/com/example/myapplication/presentation/ui/SettingsScreen.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsStyleCards.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt",
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsCardEdgeZoom.kt",
        "src/main/java/com/example/myapplication/presentation/ui/theme/WearTokens.kt",
    )
}
