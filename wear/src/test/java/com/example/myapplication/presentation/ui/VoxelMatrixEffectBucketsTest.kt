package com.example.myapplication.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 点阵批绘制分组的纯函数契约：
 * alpha→桶号的边界与量化误差、桶数上限、半径分组、类别判定优先级、退化输入。
 */
class VoxelMatrixEffectBucketsTest {

    @Test
    fun alphaBucketHitsEveryBoundaryAndClampsOutsideRange() {
        assertEquals(0, dotAlphaBucket(0f))
        assertEquals(0, dotAlphaBucket(1f / 6f - 0.001f))
        assertEquals(1, dotAlphaBucket(1f / 6f))
        assertEquals(3, dotAlphaBucket(0.55f))
        assertEquals(5, dotAlphaBucket(0.999f))
        assertEquals(5, dotAlphaBucket(1f))
        assertEquals(5, dotAlphaBucket(2.4f))
        assertEquals(0, dotAlphaBucket(-0.7f))
    }

    @Test
    fun bucketRepresentativeStaysWithinEighthOfOriginalAlpha() {
        var maxError = 0f
        var i = 0
        while (i <= 1000) {
            val alpha = i / 1000f
            val rebuilt = dotBucketAlpha(dotAlphaBucket(alpha))
            val error = abs(rebuilt - alpha)
            if (error > maxError) maxError = error
            assertTrue("alpha=$alpha rebuilt=$rebuilt", error <= 1f / 8f)
            i++
        }
        assertTrue("maxError=$maxError", maxError <= 1f / 12f + 0.001f)
    }

    @Test
    fun groupIndexNeverEscapesTheFlatBucketTable() {
        var category = 0
        while (category < DOT_CATEGORY_COUNT) {
            for (alpha in listOf(-1f, 0f, 0.02f, 0.5f, 1f, 7f)) {
                for (extra in listOf(-3f, 0f, 0.4f, 12f, 1e6f)) {
                    val index = dotGroupIndex(category, alpha, extra, quantumPx = 1.6f)
                    assertTrue("index=$index", index in 0 until DOT_GROUP_COUNT)
                }
            }
            category++
        }
        assertEquals(30, DOT_GROUPS_PER_CATEGORY)
        assertEquals(150, DOT_GROUP_COUNT)
    }

    @Test
    fun radiusBucketRoundsToNearestStepAndClampsAtTheTop() {
        val quantum = 1.6f
        assertEquals(0, dotRadiusBucket(0f, quantum))
        assertEquals(0, dotRadiusBucket(0.7f, quantum))
        assertEquals(1, dotRadiusBucket(0.8f, quantum))
        assertEquals(2, dotRadiusBucket(3.2f, quantum))
        assertEquals(DOT_RADIUS_BUCKETS - 1, dotRadiusBucket(100f, quantum))
        assertEquals(0, dotRadiusBucket(-2f, quantum))
    }

    @Test
    fun degenerateQuantumCollapsesToStaticRadiusBucket() {
        assertEquals(0, dotRadiusBucket(2.5f, 0f))
        assertEquals(0, dotRadiusBucket(2.5f, -1f))
        assertEquals(0, dotRadiusBucket(0f, 0f))
    }

    @Test
    fun dynamicRadiusSeparatesIntoItsOwnGroupFromStaticRadius() {
        val quantum = 1.6f
        val staticGroup = dotGroupIndex(DOT_CAT_BASE, 0.5f, 0f, quantum)
        val swellGroup = dotGroupIndex(DOT_CAT_BASE, 0.5f, 2f * quantum, quantum)
        assertNotEquals(staticGroup, swellGroup)
        // 同一组输入必须稳定映射到同一组
        assertEquals(staticGroup, dotGroupIndex(DOT_CAT_BASE, 0.5f, 0f, quantum))
        assertEquals(swellGroup, dotGroupIndex(DOT_CAT_BASE, 0.5f, 2f * quantum - 0.01f, quantum))
    }

    @Test
    fun everyCategoryAlphaRadiusTripleMapsToADistinctGroup() {
        val seen = HashSet<Int>()
        var category = 0
        while (category < DOT_CATEGORY_COUNT) {
            var bucket = 0
            while (bucket < DOT_ALPHA_BUCKETS) {
                var radius = 0
                while (radius < DOT_RADIUS_BUCKETS) {
                    val index = dotGroupIndex(
                        category = category,
                        alpha = dotBucketAlpha(bucket),
                        extraRadiusPx = radius * 1.6f,
                        quantumPx = 1.6f,
                    )
                    assertTrue("dup index $index", seen.add(index))
                    radius++
                }
                bucket++
            }
            category++
        }
        assertEquals(DOT_GROUP_COUNT, seen.size)
    }

    @Test
    fun groupMeanAlphaIsUnbiasedOverItsMembers() {
        assertEquals(0.3f, dotGroupAlpha(sumAlpha = 1.5f, memberCount = 5, bucket = 1), 1e-6f)
        assertEquals(0.25f, dotGroupAlpha(sumAlpha = 0.25f, memberCount = 1, bucket = 1), 1e-6f)
        assertEquals(0.05f, dotGroupAlpha(sumAlpha = 0.6f, memberCount = 12, bucket = 0), 1e-6f)
    }

    @Test
    fun emptyGroupFallsBackInsteadOfDividingByZero() {
        assertEquals(dotBucketAlpha(2), dotGroupAlpha(sumAlpha = 0f, memberCount = 0, bucket = 2), 1e-6f)
        assertEquals(7f, dotGroupRadius(sumRadius = 0f, memberCount = 0, fallbackRadius = 7f), 1e-6f)
        assertFalse(dotGroupAlpha(0f, 0, 3).isNaN())
        assertFalse(dotGroupRadius(0f, 0, 0f).isNaN())
    }

    @Test
    fun groupMeanRadiusKeepsStaticDotsExactAndSwellUnbiased() {
        // 三个静态半径点（无动态放大）→ 组代表值必须还原成原半径，分毫不差
        assertEquals(4.6f, dotGroupRadius(sumRadius = 4.6f * 3f, memberCount = 3, fallbackRadius = 0f), 1e-6f)
        // 组内半径 4f / 5f / 6f → 代表值取均值 5f
        assertEquals(5f, dotGroupRadius(sumRadius = 15f, memberCount = 3, fallbackRadius = 0f), 1e-6f)
    }

    @Test
    fun dotsInsideOneAlphaBucketShareAGroupEvenWhenRawAlphaDiffers() {
        val quantum = 1.2f
        val low = dotGroupIndex(DOT_CAT_BASE, 0.20f, 0f, quantum)
        val high = dotGroupIndex(DOT_CAT_BASE, 0.30f, 0f, quantum)
        assertEquals(low, high)
        assertNotEquals(low, dotGroupIndex(DOT_CAT_BASE, 0.40f, 0f, quantum))
    }

    @Test
    fun approvalProgressOverridesEveryOtherCategory() {
        assertEquals(
            DOT_CAT_BASE,
            dotCategory(approvalT = 0.2f, approvedGreen = true, inCascade = true, glintAlpha = 0.9f, shockAlpha = 0.9f),
        )
        assertEquals(
            DOT_CAT_BASE,
            dotCategory(approvalT = 0.011f, approvedGreen = false, inCascade = false, glintAlpha = 0f, shockAlpha = 0f),
        )
    }

    @Test
    fun categoryFollowsTheOriginalWhenBranchPrecedence() {
        assertEquals(
            DOT_CAT_GREEN,
            dotCategory(0f, approvedGreen = true, inCascade = true, glintAlpha = 0.9f, shockAlpha = 0.9f),
        )
        assertEquals(
            DOT_CAT_CASCADE,
            dotCategory(0f, approvedGreen = false, inCascade = true, glintAlpha = 0.9f, shockAlpha = 0.9f),
        )
        assertEquals(
            DOT_CAT_GLINT,
            dotCategory(0f, approvedGreen = false, inCascade = false, glintAlpha = 0.9f, shockAlpha = 0.9f),
        )
        assertEquals(
            DOT_CAT_SHOCK,
            dotCategory(0f, approvedGreen = false, inCascade = false, glintAlpha = 0f, shockAlpha = 0.06f),
        )
    }

    @Test
    fun glintAndShockThresholdsAreStrictlyAboveFiveHundredths() {
        assertEquals(
            DOT_CAT_BASE,
            dotCategory(0f, approvedGreen = false, inCascade = false, glintAlpha = 0.05f, shockAlpha = 0.05f),
        )
        assertEquals(
            DOT_CAT_GLINT,
            dotCategory(0f, approvedGreen = false, inCascade = false, glintAlpha = 0.051f, shockAlpha = 0f),
        )
        assertEquals(
            DOT_CAT_SHOCK,
            dotCategory(0f, approvedGreen = false, inCascade = false, glintAlpha = 0f, shockAlpha = 0.051f),
        )
    }
}
