package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import kotlin.math.floor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 组内点线的几何：沿格距排点、右端 24dp 不排、末点不许被右端余量切掉半颗。
 *
 * 三个数字都钉在单测里而不是靠量截图 —— 点距 / 收法 / 点径一旦被顺手调一下，
 * 页面就会退回"随手排的一条虚线"。
 */
class SettingsDotRuleTest {

    @Test
    fun spacingIsTheLatticeStepAndTheRightInsetIsTheHairlineRule() {
        // 格距 16dp（docs/UI-DOTMATRIX.md 二 · 网格规范）；右端收法与发丝线同一条
        assertEquals(16f, SettingsDotRule.SpacingDp, 0.001f)
        assertEquals(24f, SettingsDotRule.RightInsetDp, 0.001f)
        assertEquals(
            SettingsDesignMath.DividerRightInsetDp,
            SettingsDotRule.RightInsetDp,
            0.001f
        )
    }

    @Test
    fun theMeasuredRowCarriesEightDotsStartingOnTheLeftSlotLine() {
        // 轨道 148dp = 内容盒 176 − 左槽线 14 − 右槽线 14；可用宽 = 148 − 24 = 124dp
        assertEquals(8, SettingsDotRule.dotCount(148f))
        // 首点圆心就是左槽线本身（偏移 0），末点圆心 7 × 16 = 112dp
        assertEquals(0f, SettingsDotRule.dotOffsetDp(0), 0.001f)
        assertEquals(112f, SettingsDotRule.dotOffsetDp(7), 0.001f)
        // 末点右缘 113.5dp 仍在可用宽 124dp 以内：一颗都没被切
        assertTrue(SettingsDotRule.dotOffsetDp(7) + SettingsDotRule.RadiusDp <= 124f)
    }

    @Test
    fun subItemTrackStopsAtTheSameRightEdge() {
        // 子项组上沿从 12dp 起笔：轨道 150dp，可用 126dp，右端仍收在同一条线上
        assertEquals(8, SettingsDotRule.dotCount(150f))
        assertEquals(112f, SettingsDotRule.dotOffsetDp(7), 0.001f)
        assertTrue(SettingsDotRule.dotOffsetDp(7) + SettingsDotRule.RadiusDp <= 126f)
    }

    @Test
    fun aDotThatOverlapsTheRightMarginIsDroppedInsteadOfBeingClipped() {
        // 轨道 152dp → 可用 128dp 恰好是 8 个间距：候选第 9 颗圆心在 128、右缘 129.5，越界 → 整颗丢弃
        assertEquals("骑在右端边界上的点必须丢弃", 8, SettingsDotRule.dotCount(152f))
        // 与"先取商再 +1"的写法对照：那种写法会给出 9 颗，也就是半个点被裁
        assertEquals(9, (128f / SettingsDotRule.SpacingDp).toInt() + 1)
        // 再宽 1.5dp（可用 129.5dp）才放得下第 9 颗
        assertEquals(9, SettingsDotRule.dotCount(153.5f))
    }

    @Test
    fun noDotEverCrossesTheReservedMargin() {
        var width = 40f
        while (width <= 240f) {
            val count = SettingsDotRule.dotCount(width)
            if (count > 0) {
                val lastCentre = SettingsDotRule.dotOffsetDp(count - 1)
                assertTrue(
                    "width=" + width + " 末点越界",
                    lastCentre + SettingsDotRule.RadiusDp <= width - SettingsDotRule.RightInsetDp + 0.001f
                )
            }
            width += 0.5f
        }
    }

    @Test
    fun widerTracksNeverCarryFewerDots() {
        var previous = 0
        var width = 0f
        while (width <= 260f) {
            val count = SettingsDotRule.dotCount(width)
            assertTrue("width=" + width + " 变宽后点数不许减少", count >= previous)
            previous = count
            width += 4f
        }
    }

    @Test
    fun degenerateWidthsAndSpacingsYieldNoDots() {
        // 连一颗都放不下：轨道等于右端余量、零宽、负宽
        assertEquals(0, SettingsDotRule.dotCount(24f))
        assertEquals(0, SettingsDotRule.dotCount(0f))
        assertEquals(0, SettingsDotRule.dotCount(-100f))
        // 间距 <= 0 时排布无意义，返回 0 而不是除零 / 死循环
        assertEquals(0, SettingsDotRule.dotCount(148f, 0f))
        assertEquals(0, SettingsDotRule.dotCount(148f, -16f))
    }

    @Test
    fun offsetFollowsTheSpacingAndNegativeIndexesCollapseToTheSlotLine() {
        assertEquals(48f, SettingsDotRule.dotOffsetDp(3), 0.001f)
        assertEquals(16f, SettingsDotRule.dotOffsetDp(2, 8f), 0.001f)
        assertEquals(0f, SettingsDotRule.dotOffsetDp(0, 8f), 0.001f)
        assertEquals(0f, SettingsDotRule.dotOffsetDp(-2), 0.001f)
    }

    @Test
    fun radiusIsOneAndAHalfSoTheMarkReadsAsADotNotADash() {
        assertEquals(1.5f, SettingsDotRule.RadiusDp, 0.001f)
        assertEquals(3f, SettingsDotRule.dotRadiusDp() * 2f, 0.001f)
        // 直径必须远小于间距的四分之一以下，否则一串点会连成短横线
        assertTrue(SettingsDotRule.dotRadiusDp() * 2f < SettingsDotRule.SpacingDp / 4f)
        // 缩放档位只按比例放大，非正值退化为 0（不画）
        assertEquals(6f, SettingsDotRule.dotRadiusDp(2f) * 2f, 0.001f)
        assertEquals(0f, SettingsDotRule.dotRadiusDp(0f), 0.001f)
    }

    @Test
    fun aSecondTrackWidthTriangulatesTheFormula() {
        // 不同尺寸的第二组数据：轨道 176dp → 可用 152dp → floor((152 − 1.5) / 16) = 9 → 10 颗
        assertEquals(10, SettingsDotRule.dotCount(176f))
        assertEquals(144f, SettingsDotRule.dotOffsetDp(9), 0.001f)
        assertTrue(SettingsDotRule.dotOffsetDp(9) + SettingsDotRule.RadiusDp <= 152f)
        // 手算对照：点数 = floor((轨道 − 收法 − 半径) / 间距) + 1
        assertEquals(
            floor((176f - SettingsDotRule.RightInsetDp - SettingsDotRule.RadiusDp) / SettingsDotRule.SpacingDp).toInt() + 1,
            SettingsDotRule.dotCount(176f)
        )
    }
}

/**
 * 点线的源码守护：绘制方式换成点、但"线只画在卡内行与行之间"的判定、标题的左槽线、
 * 按下态只改底色这三条不变量必须逐字留在真实源文件里。
 */
class SettingsDotLineGuardTest {

    private val shared = readProjectFile(SettingsSharedPath)

    /** 组内点线本体：从函数签名到下一个函数（卡片行）之前。 */
    private val hairline = shared
        .substringAfter("private fun SettingsGroupHairline(")
        .substringBefore("internal fun SettingsCardRow(")

    /** 组标题排版：点画在槽沟里，标题自己的左缩进排在它之后。 */
    private val headerLayout = shared
        .substringAfter("private fun Modifier.settingsSectionHeaderLayout()")
        .substringBefore("/** 组标题签名点的直径")

    /** 卡片行本体：按下态只换底色。 */
    private val cardRow = shared
        .substringAfter("internal fun SettingsCardRow(")
        .substringBefore("可点行的按下底色")

    /** 按下底色的回落曲线。 */
    private val pressHelper = shared.substringAfter("private fun pressedRowBackground(")

    @Test
    fun groupHairlineIsDrawnAsDotsThroughThePureRule() {
        assertTrue("点数必须由 SettingsDotRule 算", hairline.contains("SettingsDotRule.dotCount("))
        assertTrue("点是画出来的圆", hairline.contains("drawCircle("))
        assertTrue("颜色仍是 Border 令牌", hairline.contains("Wear.Palette.Border"))
        assertFalse("旧的一根实底发丝线必须已撤掉", hairline.contains("background(Wear.Palette.Border)"))
        // 不引入新色、不碰强调色
        assertFalse(hairline.contains("Wear.Accent"))
    }

    @Test
    fun theLineStillOnlyExistsBetweenRowsInsideACard() {
        assertEquals("组内点线只允许一处声明", 1, Regex("fun SettingsGroupHairline").findAll(shared).count())
        assertTrue(
            "点线只能画在 drawsGroupHairline 判定通过的那一支里",
            Regex("drawsGroupHairline\\(LocalSettingsRowSlot\\.current\\)\\)\\s*\\{\\s*SettingsGroupHairline\\(\\)")
                .containsMatchIn(shared)
        )
        assertTrue(
            "画不画仍由 drawsGroupHairline 判定",
            shared.contains("SettingsRowComposition.drawsGroupHairline(LocalSettingsRowSlot.current)")
        )
        assertTrue(
            "左端缩进仍由 groupHairlineStartIndentDp 给",
            shared.contains("start = LocalSettingsGroupHairlineStart.current")
        )
        assertTrue(
            "首点圆心落在左槽线上（偏移 0 就是槽线）",
            shared.contains("SettingsDotRule.dotOffsetDp(index)")
        )
    }

    @Test
    fun headerSignatureSitsInTheGutterSoTheTitleStaysOnTheLeftSlotLine() {
        val drawAt = headerLayout.indexOf("drawBehind")
        val titleIndentAt = headerLayout.indexOf("padding(start = SettingsRowSidePadding)")
        assertTrue("组标题必须仍然自带左槽线 14dp", titleIndentAt > 0)
        assertTrue("点必须先于标题缩进绘制（即画在左槽沟里，不占布局宽度）", drawAt in 1 until titleIndentAt)
        assertTrue("签名点走中性色", shared.contains("color = Wear.Palette.TextMuted"))
        assertFalse("组标题不许用强调色", headerLayout.contains("Wear.Accent"))
        assertEquals("签名字符只加这一处（三颗点）", 1, Regex("private const val HeaderDotCount = 3").findAll(shared).count())
    }

    @Test
    fun pressFeedbackOnlyChangesTheRowBackground() {
        assertTrue("按下态必须来自交互源", cardRow.contains("collectIsPressedAsState()"))
        assertTrue("涟漪关掉：反馈只由底色承担", cardRow.contains("indication = null"))
        assertTrue("按下态只换底色", cardRow.contains("background(rowBackground)"))
        assertFalse("不许缩放整行", cardRow.contains("graphicsLayer"))
        assertFalse("不许缩放整行", cardRow.contains("scale("))
        assertFalse("不许边框闪烁", cardRow.contains("border("))
        assertTrue("松开走 Motion.quick() 回落", pressHelper.contains("Wear.Motion.quick()"))
        assertTrue("按下立刻到位（snap 而不是再等一次 tween）", pressHelper.contains("lift.snapTo(1f)"))
        assertTrue("按下态用的是既有令牌色", pressHelper.contains("Wear.Palette.SurfaceAlt"))
    }

    private companion object {
        const val SettingsSharedPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"
    }
}