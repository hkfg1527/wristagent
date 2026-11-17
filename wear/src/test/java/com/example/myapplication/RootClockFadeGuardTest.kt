package com.example.myapplication

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 根页曲面时钟必须随滚动淡出。
 *
 * 为什么要有这条：六套风格的根页都把 `TimeText` 当**显式叠层**画在内容之上
 * （本项目没有 `AppScaffold`，官方那套"滚动时收走时钟"的托管用不了）。
 * 只要忘了做淡出，内容就会滑到时钟底下 —— 真机实测叠在 y≈10..30，肉眼就是两个字压在一起
 * （0.0.0.72 之前的 `style-googlecard-root.png`、`style-terminal-root.png` 都能看到）。
 *
 * 0.0.0.74 的修法是按官方行为补上淡出：用项目已有的 `isAtScrollTop()` 判据给透明度，
 * **不改任何布局尺寸**。这条守卫钉住"它还在"。
 */
class RootClockFadeGuardTest {

    private val screen = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/SettingsScreen.kt"
    )

    @Test
    fun theRootClockFadesOutWhenTheListLeavesTheTop() {
        assertTrue(
            "根页时钟必须按'是否停在顶部'计算透明度（否则内容会滑到时钟底下）",
            screen.contains("derivedStateOf { if (listState.isAtScrollTop()) 1f else 0f }")
        )
        assertTrue(
            "透明度必须真的作用在 TimeText 上",
            screen.contains("graphicsLayer { alpha = clockAlpha }")
        )
    }

    @Test
    fun theClockIsNotPositionedByBumpingLayoutPadding() {
        // 修法选择也钉住：如果哪天有人改回"把 contentPadding 抬到 34dp"这种改布局的做法，
        // 首屏留白会整体下移，那是用户的审美决定，不该在重构里偷偷发生。
        assertTrue(
            "根页上边距必须仍是 RootContentTopPadding（24dp，官方 10% 屏高）",
            screen.contains("top = RootContentTopPadding")
        )
    }
}
