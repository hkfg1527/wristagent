package com.example.myapplication.presentation

import androidx.compose.ui.graphics.Color
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.WearAppearance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * 隔离规则本身的行为测试（不依赖 JUnit 的用例执行顺序）。
 *
 * 用 `ExternalResource.apply()` 把语句包一层，就能分别验证"用例执行前复位"与"执行后复位"——
 * 如果只在用例里断言初始值，那两个用例谁先跑都能通过，等于什么都没验。
 */
class SettingsAppearanceResetRuleTest {

    private val description = Description.createTestDescription(
        SettingsAppearanceResetRuleTest::class.java,
        "statement"
    )

    @Test
    fun theRuleResetsBeforeTheBodyRuns() {
        val rule = SettingsAppearanceResetRule()
        var styleInside: SettingsStyle? = null
        var scaleInside: Float? = null
        var accentInside: Color? = Color.Red

        // 先把单例弄脏，再包一层语句
        WearAppearance.style = SettingsStyle.M3_TRANSFORM
        WearAppearance.fontScale = 1.3f
        WearAppearance.accentOverride = Color.Red

        val statement = rule.apply(
            object : Statement() {
                override fun evaluate() {
                    styleInside = WearAppearance.style
                    scaleInside = WearAppearance.fontScale
                    accentInside = WearAppearance.accentOverride
                }
            },
            description
        )
        statement.evaluate()

        assertEquals("用例执行前必须把风格复位", SettingsStyle.Default, styleInside)
        assertEquals("字号也必须复位", 1f, scaleInside!!, 0.001f)
        assertNull("强调色覆盖必须清空", accentInside)
    }

    @Test
    fun theRuleCleansUpAfterTheBodyDirtiesTheState() {
        val rule = SettingsAppearanceResetRule()
        val statement = rule.apply(
            object : Statement() {
                override fun evaluate() {
                    // 模拟"某个用例中途把风格点了过去"
                    WearAppearance.style = SettingsStyle.M3_LIST
                    WearAppearance.fontScale = 1.15f
                    WearAppearance.accentOverride = Color.Green
                }
            },
            description
        )
        statement.evaluate()

        assertEquals("用例弄脏后必须复位，否则会污染后面的测试类", SettingsStyle.Default, WearAppearance.style)
        assertEquals(1f, WearAppearance.fontScale, 0.001f)
        assertNull(WearAppearance.accentOverride)
    }
}
