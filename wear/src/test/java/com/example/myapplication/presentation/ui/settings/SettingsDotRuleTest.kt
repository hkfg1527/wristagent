package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsGroupLineMathTest {
    @Test
    fun regularAndSubItemTracksShareTheSameRightInset() {
        assertEquals(124f, SettingsGroupLineMath.lengthDp(148f), 0.001f)
        assertEquals(126f, SettingsGroupLineMath.lengthDp(150f), 0.001f)
        assertEquals(24f, SettingsDesignMath.DividerRightInsetDp, 0.001f)
    }

    @Test
    fun degenerateTracksProduceNoLineInsteadOfNegativeGeometry() {
        assertEquals(0f, SettingsGroupLineMath.lengthDp(24f), 0.001f)
        assertEquals(0f, SettingsGroupLineMath.lengthDp(0f), 0.001f)
        assertEquals(0f, SettingsGroupLineMath.lengthDp(-20f), 0.001f)
    }

    @Test
    fun separatorIsExactlyOnePhysicalPixelAtEveryDensity() {
        assertEquals(1f, SettingsGroupLineMath.StrokeWidthPx, 0.001f)
    }
}

class SettingsGroupLineGuardTest {
    private val shared = readProjectFile(SettingsSharedPath)
    private val hairline = shared
        .substringAfter("private fun SettingsGroupHairline(")
        .substringBefore("internal fun SettingsCardRow(")
    private val headerLayout = shared
        .substringAfter("private fun Modifier.settingsSectionHeaderLayout(")
        .substringBefore("/** 行内：左槽标签")
    private val cardRow = shared
        .substringAfter("internal fun SettingsCardRow(")
        .substringBefore("可点行的按下底色")
    private val pressHelper = shared.substringAfter("private fun pressedRowBackground(")

    @Test
    fun groupHairlineIsOneBorderStrokeAndNoLongerANoiseOfDots() {
        assertTrue(hairline.contains("drawLine("))
        assertTrue(hairline.contains("Wear.Palette.Border"))
        assertTrue(hairline.contains("strokeWidth = SettingsGroupLineMath.StrokeWidthPx"))
        assertTrue(hairline.contains("SettingsGroupLineMath.lengthDp(size.width / density)"))
        assertFalse(hairline.contains("drawCircle("))
        assertFalse(hairline.contains("SettingsDotRule"))
    }

    @Test
    fun lineStillOnlyExistsBetweenRowsInsideACard() {
        assertEquals(1, Regex("fun SettingsGroupHairline").findAll(shared).count())
        assertTrue(Regex("drawsGroupHairline\\(\\s*slot\\s*\\)").containsMatchIn(shared))
        assertTrue(shared.contains("start = LocalSettingsGroupHairlineStart.current"))
        assertFalse(hairline.contains("Wear.Accent"))
    }

    @Test
    fun headerDashUsesAccentWithoutMovingTheTitleSlot() {
        val drawAt = headerLayout.indexOf("drawBehind")
        val titleIndentAt = headerLayout.indexOf("padding(start = SettingsRowSidePadding)")
        assertTrue(titleIndentAt > 0)
        assertTrue(drawAt in 1 until titleIndentAt)
        assertTrue("强调色在组合上下文读取后传入绘制", shared.contains("val accent = Wear.Accent"))
        assertTrue(shared.contains("settingsSectionHeaderLayout(isFirstSection, accent)"))
        assertTrue(headerLayout.contains("isFirstSection: Boolean, accent: Color"))
        assertTrue(headerLayout.contains("headerAccentMark(accent)"))
        assertTrue(headerLayout.contains("SettingsSectionHeaderMark.mark(size.height / density)"))
        assertTrue(headerLayout.contains("color = accent"))
        assertTrue(headerLayout.contains("drawRect("))
        assertFalse(headerLayout.contains("HeaderDotCount"))
        assertFalse(headerLayout.contains("drawCircle("))
    }

    @Test
    fun pressFeedbackOnlyChangesTheCurvedSurfaceColor() {
        assertTrue(cardRow.contains("collectIsPressedAsState()"))
        assertTrue(cardRow.contains("indication = null"))
        assertTrue("按压只改曲面填充色", cardRow.contains("drawPath(path = path, color = rowBackground)"))
        assertFalse("按压不得加边框", cardRow.contains("border("))
        assertTrue("组内行必须按屏幕弦长计算比例", cardRow.contains("SettingsCurveMath.halfWidthDp"))
        assertTrue("横向与纵向必须使用同一个比例", cardRow.contains("scaleX = scale") && cardRow.contains("scaleY = scale"))
        assertFalse("禁止只做横向拉伸", cardRow.contains("scaleX = SettingsCurveMath.contentScaleX"))
        assertTrue(pressHelper.contains("Wear.Motion.quick()"))
        assertTrue(pressHelper.contains("lift.snapTo(1f)"))
        assertTrue(pressHelper.contains("Wear.Palette.SurfaceAlt"))
    }

    private companion object {
        const val SettingsSharedPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"
    }
}
