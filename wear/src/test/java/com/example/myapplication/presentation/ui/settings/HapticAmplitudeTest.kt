package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 触觉强度（设置项 haptic_amplitude）的语义与真实接线。
 *
 * 强度用官方 primitive 的 scale 表达，所以三档必须严格递增且都落在 (0,1] 内 ——
 * scale 越界会被系统忽略，用户看到"选了强却更轻"就是这么来的。
 */
class HapticAmplitudeTest {

    @Test
    fun unknownStorageFallsBackToStandard() {
        assertEquals(HapticAmplitude.STANDARD, HapticAmplitude.fromStorage(null))
        assertEquals(HapticAmplitude.STANDARD, HapticAmplitude.fromStorage(""))
        assertEquals(HapticAmplitude.STANDARD, HapticAmplitude.fromStorage("BOGUS"))
    }

    @Test
    fun everyLevelRoundTripsThroughItsName() {
        for (level in HapticAmplitude.entries) {
            assertEquals(level, HapticAmplitude.fromStorage(level.name))
        }
    }

    @Test
    fun theThreeLevelsAreStrictlyIncreasingAndInsideTheLegalRange() {
        val scales = HapticAmplitude.entries.map { it.scale }
        assertEquals("三档必须递增", scales.sorted(), scales)
        assertTrue("必须严格递增", scales[0] < scales[1] && scales[1] < scales[2])
        for (s in scales) {
            assertTrue("scale 必须在 (0,1] 内，实际 " + s, s > 0f && s <= 1f)
        }
        assertEquals("最强档就是系统原强度", 1f, scales.last(), 0.0001f)
    }

    @Test
    fun bothHapticHelpersReadTheConfiguredLevel() {
        val shared = readProjectFile("src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt")
        val occurrences = Regex(Regex.escape("preferences.hapticAmplitude.scale")).findAll(shared).count()
        assertTrue("刻度震动与确认震动都必须读档位，实际 " + occurrences + " 处", occurrences >= 2)
        assertTrue(
            "必须用官方 primitive 组合，而不是自造波形",
            shared.contains("VibrationEffect.startComposition()") &&
                shared.contains("addPrimitive(")
        )
    }
}
