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
    /** 卡片边缘缩放：等比缩小的新归属地（行不再缩放）。 */
    private val cardZoom = readProjectFile(
        "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsCardEdgeZoom.kt"
    )

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
        // 0.0.0.46：等比缩小的责任从"行"下沉到"卡"。
        // 组内行已经在一张官方 Card 里，行再各自按屏幕弦长缩放，卡内文字就会左右不齐（真机可见）。
        // 现在行这一层完全不缩放，只缩整张卡 —— 下面接着钉卡那一侧仍然等比、仍然只缩不放。
        assertFalse("卡片内部的行不得再各自缩放", cardRow.contains("SettingsCurveMath.halfWidthDp"))
        assertTrue(
            "等比缩小改由卡片承担",
            cardZoom.contains("scaleX = scale") && cardZoom.contains("scaleY = scale")
        )
        // 这条项目里是"只淡出不缩放"：MinScale 恒为 1.0（见 CONTEXT §1 与硬约束），
        // 所以这里钉的是"卡不横向形变"，不是"缩到 70%"。
        assertTrue("卡片边缘不得缩放（只淡出）", cardZoom.contains("const val MinScale = 1.00f"))
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
