package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滚动缩放的取值守护。
 *
 * 产品要求回到 M3E 官方圆屏行为：边缘行随曲率缩小并淡出。
 * 不再自维护 edgeScale / edgeAlpha，直接使用 Wear Compose 官方默认值，
 * 避免库升级后本地系数与官方观感漂移。
 */
class SettingsScalingMathTest {

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
    fun theHelperUsesOfficialDefaultsWithoutLocalOverrides() {
        val source = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsScaling.kt"
        )
        assertTrue("必须直接调用官方工厂", source.contains("ScalingLazyColumnDefaults.scalingParams()"))
        assertFalse("不许再覆盖官方 edgeScale", Regex("edgeScale\\s*=").containsMatchIn(source))
        assertFalse("不许再覆盖官方 edgeAlpha", Regex("edgeAlpha\\s*=").containsMatchIn(source))
        assertFalse("本地系数对象必须删除", source.contains("object SettingsScalingMath"))
    }
}
