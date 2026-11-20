package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 霓虹档的配色与发光几何（P7 实测发现"只有深灰卡片、没有发光"后补的判据）。
 *
 * 这里钉三件事：颜色就是规格那三个值、发光层越往外越淡、层数与步长固定。
 * 渲染是否真的画到屏幕上，由真机像素判据（饱和度 > 80、青色分量占主导）负责。
 */
class NeonPaletteTest {

    @Test
    fun theColoursMatchTheWrittenSpec() {
        assertEquals("主描边青", androidx.compose.ui.graphics.Color(0xFF00E5FF), NeonPalette.Stroke)
        assertEquals("次色品红", androidx.compose.ui.graphics.Color(0xFFFF2D95), NeonPalette.Accent)
        assertEquals("底板", androidx.compose.ui.graphics.Color(0xFF05070A), NeonPalette.Background)
        assertEquals("发光用主色", NeonPalette.Stroke, NeonPalette.Glow)
        assertEquals("描边宽度", 1.5.dp, NeonPalette.StrokeWidth)
    }

    @Test
    fun theGlowGetsFainterOutwards() {
        val layers = NeonPalette.glowLayers()
        assertEquals("默认四层", 4, layers.size)
        // 宽度递增
        for (i in 1 until layers.size) {
            assertTrue(
                "第 $i 层应比前一层更宽：${layers[i - 1].first} -> ${layers[i].first}",
                layers[i].first > layers[i - 1].first
            )
        }
        // 透明度递减
        for (i in 1 until layers.size) {
            assertTrue(
                "第 $i 层应比前一层更淡：${layers[i - 1].second} -> ${layers[i].second}",
                layers[i].second < layers[i - 1].second
            )
        }
        assertTrue("最内层必须可见（alpha > 0.2）", layers.first().second > 0.2f)
    }

    @Test
    fun theLayerCountIsClampedToASaneRange() {
        assertEquals("0 层会被夹到 1", 1, NeonPalette.glowLayers(count = 0).size)
        assertEquals("超量会被夹到 8", 8, NeonPalette.glowLayers(count = 99).size)
    }
}
