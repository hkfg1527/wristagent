package com.example.myapplication.presentation.ui.theme

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 调色板的守门测试：设计红线变成红灯。
 *
 * 背景：改这一版之前 Wear.Palette 有 19 个色值，其中 7 个是近乎重复的灰
 * (8B95A5 / 737D8C / 5F6B7A / A3ADBD / A8B2C0 / CDD3DC / F5F7FA)，
 * 2 个是几乎同色的红 (Critical FB6B4B / Danger FF6B6B)，
 * 且正文灰 TextMuted 5F6B7A 对纯黑只有 3.86:1，低于 WCAG AA 的 4.5:1。
 * 色板漂移不会自己被发现，所以在这里钉死四条：数量、重复度、死 token、对比度。
 */
class PaletteAuditTest {

    @Test
    fun paletteStaysWithinFourteenTokens() {
        assertTrue(
            "调色板膨胀到 " + palette.size + " 个色值（上限 " + MAX_TOKENS + "）。新颜色必须合并进已有 token，不能新增。当前：" + palette.keys,
            palette.size <= MAX_TOKENS
        )
    }

    @Test
    fun noTwoTokensAreNearlyTheSameColor() {
        val close = mutableListOf<String>()
        val names = palette.keys.toList()
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val d = distance(palette.getValue(names[i]), palette.getValue(names[j]))
                if (d < MIN_DISTANCE) {
                    close += names[i] + " 与 " + names[j] + " 距离 " + (d * 10).toInt() / 10.0
                }
            }
        }
        assertEquals("这些色值肉眼分不出来，等于没有分层：", emptyList<String>(), close)
    }

    @Test
    fun everyTokenIsActuallyUsedByPageCode() {
        val sources = mainSources().filterNot { it.name == "WearTokens.kt" }
        val dead = palette.keys.filter { name ->
            val pattern = Regex("Palette\\." + Regex.escape(name) + "\\b")
            sources.none { pattern.containsMatchIn(it.readText()) }
        }
        assertEquals("这些 token 没有任何页面在用（死 token 会让人以为还有选择余地）：", emptyList<String>(), dead)
    }

    @Test
    fun bodyTextTokensMeetWcagAaOnBlack() {
        val bg = palette.getValue("Bg")
        listOf("TextPrimary", "TextSecondary", "TextMuted").forEach { name ->
            val ratio = contrast(palette.getValue(name), bg)
            assertTrue(
                name + " 对 Bg 只有 " + (ratio * 100).toInt() / 100.0 + ":1，低于 WCAG AA 4.5:1",
                ratio >= 4.5
            )
        }
    }

    private fun distance(a: Long, b: Long): Double {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    private fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val v = ((argb shr shift) and 0xFF) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    private fun mainSources(): List<File> {
        val root = listOf(File("src/main/java"), File("wear/src/main/java"))
            .firstOrNull { it.exists() } ?: error("找不到主源码目录")
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    private companion object {
        const val MAX_TOKENS = 14
        const val MIN_DISTANCE = 20.0

        val palette: Map<String, Long> = run {
            val source = readProjectFile(
                "src/main/java/com/example/myapplication/presentation/ui/theme/WearTokens.kt"
            )
            val block = source.substringAfter("object Palette {").substringBefore("object Radius")
            Regex("val (\\w+) = Color\\(0x([0-9A-Fa-f]{8})\\)")
                .findAll(block)
                .associate { it.groupValues[1] to it.groupValues[2].toLong(16) }
        }
    }
}
