package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滚动缩放的取值守护。
 *
 * 回归背景（真机 uiautomator dump）：同一子页两条同文案节点量到 254x45 px 与 236x41 px，
 * 差 7.6% / 9.8% —— 行仍在按滚动位置缩放。根因不是 contentPadding，而是 [ScalingLazyColumn]
 * 从来没传过 scalingParams，跑的是库默认 edgeScale = 0.75 + 边缘淡出。
 * 这两条断言把"关掉缩放"钉死在源码上，避免下次又只改注释不改参数。
 */
class SettingsScalingMathTest {

    @Test
    fun edgeScaleIsExactlyOne() {
        assertEquals("边缘行不允许再缩放", 1f, SettingsScalingMath.EdgeScale, 0f)
    }

    @Test
    fun edgeAlphaIsExactlyOne() {
        assertEquals("边缘行不允许再淡出", 1f, SettingsScalingMath.EdgeAlpha, 0f)
    }

    @Test
    fun bothSettingListsPassTheScalingParams() {
        val files = listOf(
            "src/main/java/com/example/myapplication/presentation/ui/SettingsScreen.kt",
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"
        )
        for (path in files) {
            val source = readProjectFile(path)
            assertEquals(
                path + " 必须显式传 scalingParams，否则走库默认 0.75 缩放：",
                1,
                Regex("scalingParams\\s*=\\s*settingsScalingParams\\(\\)").findAll(source).count()
            )
        }
    }

    @Test
    fun theHelperPinsBothCoefficients() {
        val source = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsScaling.kt"
        )
        assertTrue("缩放系数必须来自 SettingsScalingMath", source.contains("edgeScale = SettingsScalingMath.EdgeScale"))
        assertTrue("不透明度系数必须来自 SettingsScalingMath", source.contains("edgeAlpha = SettingsScalingMath.EdgeAlpha"))
    }
}
