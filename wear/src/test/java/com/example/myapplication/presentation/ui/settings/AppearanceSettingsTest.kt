package com.example.myapplication.presentation.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.example.myapplication.presentation.FakeSharedPreferences
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.contrastRatio
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * 外观设置的"真被消费"证明：改档 -> 取到的字号/强调色真的变了，而且能跨进程回读。
 *
 * 这里不打桩被测逻辑：写的是真的 SharedPreferences 语义（[FakeSharedPreferences] 只替换
 * Android 平台存储后端），读的是界面真正会读的 [WearAppearance]。
 */
class AppearanceSettingsTest {

    private lateinit var prefs: FakeSharedPreferences

    @Before
    fun setUp() {
        WearAppearance.reset()
        AppearanceBootstrap.reset()
        prefs = FakeSharedPreferences()
    }

    @After
    fun tearDown() {
        WearAppearance.reset()
        AppearanceBootstrap.reset()
    }

    // ---------- 字号档 ----------

    @Test
    fun thereAreExactlyThreeFontTiersAndStandardIsIdentity() {
        val tiers = FontScaleTier.values().toList()
        assertEquals(3, tiers.size)
        assertEquals(1f, FontScaleTier.STANDARD.scale, 0.0001f)
        // 严格递增：档与大档之间必须能看出差别
        assertTrue(tiers[0].scale < tiers[1].scale)
        assertTrue(tiers[1].scale < tiers[2].scale)
    }

    @Test
    fun nearestSnapsEveryStoredValueBackOntoATier() {
        FontScaleTier.values().forEach { tier ->
            assertEquals(tier, FontScaleTier.nearest(tier.scale))
        }
        // 旧版本写坏的中间值不能把界面卡在两档之间
        assertEquals(FontScaleTier.STANDARD, FontScaleTier.nearest(0.5f))
        assertEquals(FontScaleTier.XLARGE, FontScaleTier.nearest(1.9f))
    }

    @Test
    fun choosingALargerTierChangesTheFontSizeActuallyHandedToThePage() {
        val ladder = listOf(12f, 15f, 18f, 24f, 30f)
        val standard = ladder.map { WearAppearance.scaleSp(it) }
        assertEquals(ladder, standard)

        AppearanceStore(prefs).saveFontScale(FontScaleTier.LARGE.scale)
        AppearanceStore(prefs).install()

        assertEquals(FontScaleTier.LARGE.scale, WearAppearance.fontScale, 0.0001f)
        assertEquals(17.25f, WearAppearance.scaleSp(15f), 0.0001f)
        assertEquals(27.6f, WearAppearance.scaleSp(24f), 0.01f)
        // 缩放是整体乘法：5 级字阶的相邻大小关系必须原样保留
        val scaled = ladder.map { WearAppearance.scaleSp(it) }
        assertEquals(scaled.sorted(), scaled)
        assertEquals(5, scaled.toSet().size)
        ladder.zip(scaled).forEach { (base, scaledValue) ->
            assertEquals(base * FontScaleTier.LARGE.scale, scaledValue, 0.0001f)
        }
    }

    @Test
    fun fontTierSurvivesAProcessRestart() {
        AppearanceStore(prefs).saveFontScale(FontScaleTier.XLARGE.scale)
        // 新实例 = 新进程：只从存储回读
        assertEquals(FontScaleTier.XLARGE.scale, AppearanceStore(prefs).fontScale(), 0.0001f)
        AppearanceStore(prefs).install()
        assertEquals(1.30f, WearAppearance.fontScale, 0.0001f)
    }

    // ---------- 强调色档 ----------

    @Test
    fun thereAreFivePresetsAndTheDefaultIsTheTokenItself() {
        assertEquals(5, AccentPreset.values().size)
        assertEquals(Wear.Palette.Accent.toArgb(), AccentPreset.Default.color.toArgb())
        // 没有覆盖时，强调色必须逐位等于令牌 #4FC3F7
        assertNull(WearAppearance.accentOverride)
        assertEquals(Wear.Palette.Accent, WearAppearance.accent)
    }

    @Test
    fun everyPresetIsFarEnoughFromTheOthersToBeToldApart() {
        val names = AccentPreset.values().toList()
        val close = mutableListOf<String>()
        for (i in names.indices) {
            for (j in i + 1 until names.size) {
                val d = rgbDistance(names[i].color, names[j].color)
                if (d < MIN_PRESET_DISTANCE) close += names[i].name + "/" + names[j].name + "=" + d.toInt()
            }
        }
        assertEquals("预设色肉眼分不出来就等于只有一档可选：", emptyList<String>(), close)
    }

    @Test
    fun everyPresetStaysReadableAsTextOnEverySettingsSurface() {
        val surfaces = listOf(Wear.Palette.Bg, Wear.Palette.Surface, Wear.Palette.SurfaceAlt)
        AccentPreset.values().forEach { preset ->
            surfaces.forEach { surface ->
                val ratio = contrastRatio(preset.color, surface)
                assertTrue(
                    preset.name + " 在 " + surface + " 上只有 " + (ratio * 100).toInt() / 100f + ":1，低于 AA 4.5:1",
                    ratio >= 4.5f
                )
            }
        }
    }

    @Test
    fun choosingAPresetChangesTheAccentActuallyReadByThePage() {
        AppearanceStore(prefs).saveAccent(AccentPreset.VIOLET.color)
        AppearanceStore(prefs).install()

        assertEquals(AccentPreset.VIOLET.color, WearAppearance.accent)
        assertNotEquals(Wear.Palette.Accent, WearAppearance.accent)
        assertEquals(AccentPreset.VIOLET, AccentPreset.of(WearAppearance.accent))

        // 再换一档，界面读到的值必须跟着走（不是只写进存储）
        val store = AppearanceStore(prefs)
        store.saveAccent(AccentPreset.AMBER.color)
        store.install()
        assertEquals(AccentPreset.AMBER.color, WearAppearance.accent)
    }

    @Test
    fun accentOverrideSurvivesAProcessRestartAndCanFallBackToTheToken() {
        AppearanceStore(prefs).saveAccent(AccentPreset.MINT.color)
        assertEquals(AccentPreset.MINT.color, AppearanceStore(prefs).accent())

        // 回到默认：清掉覆盖后读到的必须是令牌值
        WearAppearance.reset()
        assertNull(AppearanceStore(FakeSharedPreferences()).accent())
        assertEquals(Wear.Palette.Accent, WearAppearance.accent)
    }

    @Test
    fun unknownStoredAccentFallsBackToTheDefaultPresetInsteadOfCrashing() {
        // 将来令牌换色时，旧存储里的值不再属于任何预设档：必须落到默认档
        assertEquals(AccentPreset.Default, AccentPreset.of(Color(0xFF123456)))
        assertEquals(AccentPreset.Default, AccentPreset.of(Wear.Palette.Accent))
    }

    private fun rgbDistance(a: Color, b: Color): Double {
        val x = a.toArgb()
        val y = b.toArgb()
        val dr = ((x shr 16) and 0xFF) - ((y shr 16) and 0xFF)
        val dg = ((x shr 8) and 0xFF) - ((y shr 8) and 0xFF)
        val db = (x and 0xFF) - (y and 0xFF)
        return sqrt((dr * dr + dg * dg + db * db).toDouble())
    }

    private companion object {
        /** 与 PaletteAuditTest 同一条判据：RGB 欧氏距离低于 20 就分不出来。 */
        const val MIN_PRESET_DISTANCE = 20.0
    }
}
