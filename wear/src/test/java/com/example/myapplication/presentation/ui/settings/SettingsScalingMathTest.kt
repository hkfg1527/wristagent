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
            assertTrue(
                path + " 必须按风格选 scalingParams：大卡片档走官方缩放，其余三套保持平坦",
                source.contains("cardStyleScalingParams") && source.contains("settingsScalingParams")
            )
        }
    }

    @Test
    fun theCardStyleKeepsTheLibraryFlatBecauseTheZoomOwnsTheGeometry() {
        val source = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsScaling.kt"
        )
        // 两套风格现在共用同一份平坦参数：缩放不再交给库（库会给 3113px 的整块挂图层，实测每帧 10.7ms）
        assertTrue("必须保留 cardStyleScalingParams 这个入口", source.contains("fun cardStyleScalingParams"))
        assertTrue("两套必须走同一份平坦参数", source.contains("private fun flatScalingParams"))
        assertFalse(
            "不得再把缩放交给库：那会给 432×3113 的整块挂图层",
            source.contains("edgeScale = 0.84f")
        )
        // 边缘几何的所有权在 CardEdgeZoom，它的常量由 SettingsCardEdgeZoomTest 钉住
        assertTrue("文件里必须写明所有权归属", source.contains("CardEdgeZoom"))
    }

    @Test
    fun theHelperDisablesUniformScalingAndKeepsOnlyTheOfficialFade() {
        val source = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsScaling.kt"
        )
        assertTrue("必须继续使用官方工厂", source.contains("ScalingLazyColumnDefaults.scalingParams("))
        assertTrue("统一缩放必须关闭，宽度交给圆弦几何", source.contains("edgeScale = 1f"))
        assertTrue("相邻行不得用不同透明度，否则接缝", source.contains("edgeAlpha = 1f"))
        assertFalse("本地系数对象必须删除", source.contains("object SettingsScalingMath"))
    }
}
