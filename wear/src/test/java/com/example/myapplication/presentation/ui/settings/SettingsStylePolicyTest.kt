package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.appearance.SettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 风格槽位表守卫（见 `docs/UI-STYLE-PLAN.md` §2）。
 *
 * 用户 2026-11-18 的原话是"大杂烩，愁得要死"：同一页里一会儿 M3E 一会儿基础。
 * 根因是 `WearAppearance.style ==` 散在 10 处，各走各的分支。
 * 这条测试钉住三件事：
 * 1. **每套风格都必须有完整声明**（8 个槽位一个不缺，编译器已经保证，这里再钉一份可读清单）；
 * 2. **风格之间真的不一样**（不是复制粘贴的四份同款）；
 * 3. **M3E 档不许出现手搓件**（开关 / 滑杆 / 单选必须走官方控件）。
 */
class SettingsStylePolicyTest {

    private val all = SettingsStyle.values().toList()

    @Test
    fun everyStyleDeclaresItsSlotsWithTheExpectedControlFamilies() {
        // 注意：不要写 `assertTrue(spec.shell != null)` 这类断言 —— 槽位是非空枚举，
        // 类型系统已经保证它不为 null，那种断言永远绿，等于没测（0.0.0.90 审查点名的三条之一）。
        assertEquals("风格数量变了就要同步 docs/UI-STYLES.md", 10, all.size)
        // 自研档里 SQUARE / TERMINAL / NEON / PAPER / GLASS / ARC 仍是自研控件；
        // BALLOON 从 S1 起改用官方开关/滑杆/单选（自研件正是用户点名的"大杂烩"）。
        for (style in listOf(
            SettingsStyle.SQUARE, SettingsStyle.TERMINAL,
            SettingsStyle.NEON, SettingsStyle.PAPER, SettingsStyle.GLASS, SettingsStyle.ARC
        )) {
            val spec = SettingsStylePolicy.specOf(style)
            assertEquals("$style 的开关应是手搓胶囊档", SettingsToggleControl.COMPACT_SWITCH, spec.toggle)
            assertEquals("$style 的滑杆应是自绘细轨", SettingsSliderControl.THIN_TRACK, spec.slider)
        }
        // 字族只有终端档例外，其余一律系统字族
        val expectedTypeface = mapOf(
            SettingsStyle.BALLOON to SettingsTypeface.DEFAULT,
            SettingsStyle.SQUARE to SettingsTypeface.DEFAULT,
            SettingsStyle.GOOGLE_CARD to SettingsTypeface.DEFAULT,
            SettingsStyle.TERMINAL to SettingsTypeface.MONOSPACE,
            SettingsStyle.M3_LIST to SettingsTypeface.DEFAULT,
            SettingsStyle.M3_TRANSFORM to SettingsTypeface.DEFAULT,
            SettingsStyle.NEON to SettingsTypeface.DEFAULT,
            SettingsStyle.PAPER to SettingsTypeface.DEFAULT,
            SettingsStyle.GLASS to SettingsTypeface.DEFAULT,
            SettingsStyle.ARC to SettingsTypeface.DEFAULT
        )
        for (style in all) {
            assertEquals("$style 的字族", expectedTypeface[style], SettingsStylePolicy.specOf(style).typeface)
        }
        for (style in listOf(SettingsStyle.M3_LIST, SettingsStyle.M3_TRANSFORM)) {
            val spec = SettingsStylePolicy.specOf(style)
            assertEquals("$style 的开关必须是官方 SwitchButton", SettingsToggleControl.OFFICIAL_SWITCH_BUTTON, spec.toggle)
            assertEquals("$style 的滑杆必须是官方 Slider", SettingsSliderControl.OFFICIAL_SEGMENTED, spec.slider)
            assertEquals("$style 的单选必须是官方 RadioButton", SettingsRadioControl.OFFICIAL_RADIO_BUTTON, spec.radio)
        }
    }

    @Test
    fun stylesAreActuallyDifferentFromEachOther() {
        val specs = all.map { SettingsStylePolicy.specOf(it) }
        assertEquals("四套风格的声明不许重复", specs.size, specs.toSet().size)
    }

    @Test
    fun theCardStyleIsTheOnlyOneOnTheOfficialScaffold() {
        val onScaffold = all.filter { SettingsStylePolicy.specOf(it).shell == SettingsPageShell.SCREEN_SCAFFOLD }
        // 0.0.0.58 起官方两套也走 ScreenScaffold（渲染宿主：OfficialListSubPage）
        assertEquals(
            "走官方 ScreenScaffold 的应当是大卡片 + 两套官方件",
            listOf(SettingsStyle.GOOGLE_CARD, SettingsStyle.M3_LIST, SettingsStyle.M3_TRANSFORM),
            onScaffold
        )
        assertEquals(
            "大卡片档的返回必须进列表首项",
            SettingsBackControl.LIST_ROW,
            SettingsStylePolicy.specOf(SettingsStyle.GOOGLE_CARD).back
        )
    }

    @Test
    fun everyStyleHasExactlyOneBackControl() {
        // 返回控件是用户最容易"找不到"的东西：位置逐套钉死，改一处就必须同步文档并上机核对。
        // 旧写法 `back in SettingsBackControl.values()` 被类型系统保证恒真 —— 整个用例是空测试。
        val expected = mapOf(
            SettingsStyle.BALLOON to SettingsBackControl.EDGE_BUTTON,
            SettingsStyle.SQUARE to SettingsBackControl.SQUARE_EDGE_BUTTON,
            SettingsStyle.GOOGLE_CARD to SettingsBackControl.LIST_ROW,
            SettingsStyle.TERMINAL to SettingsBackControl.EDGE_BUTTON,
            SettingsStyle.M3_LIST to SettingsBackControl.LIST_ROW,
            SettingsStyle.M3_TRANSFORM to SettingsBackControl.LIST_ROW,
            SettingsStyle.NEON to SettingsBackControl.EDGE_BUTTON,
            SettingsStyle.PAPER to SettingsBackControl.LIST_ROW,
            SettingsStyle.GLASS to SettingsBackControl.EDGE_BUTTON,
            SettingsStyle.ARC to SettingsBackControl.LIST_ROW
        )
        for (style in all) {
            assertEquals(
                "$style 的返回控件位置变了 → 同步 docs/UI-STYLES.md 并上机核对",
                expected[style],
                SettingsStylePolicy.specOf(style).back
            )
        }
    }

    @Test
    fun everyStyleHasItsOwnCardCornerRadius() {
        // 以前所有风格共用 Wear.Radius.Card(24dp)：气泡和方角看起来一样，正是用户说的"大杂烩"。
        val radius = SettingsStyle.values().associateWith { SettingsStylePolicy.specOf(it).cardRadiusDp }
        assertEquals("气泡必须是最软的圆角", 32f, radius[SettingsStyle.BALLOON])
        assertEquals("方角必须是直角", 0f, radius[SettingsStyle.SQUARE])
        assertEquals("终端也必须是直角（命令行语义）", 0f, radius[SettingsStyle.TERMINAL])
        assertEquals("手帐近纸边", 6f, radius[SettingsStyle.PAPER])
        assertEquals("弧列最大圆角之一", 28f, radius[SettingsStyle.ARC])
        // 十套里至少要有四种不同的圆角，否则"独立视觉"是假的
        assertTrue(
            "圆角必须真的分档，实际=" + radius.values.sorted().distinct(),
            radius.values.toSet().size >= 4
        )
    }

    @Test
    fun terminalIsTheOnlyMonospaceStyle() {
        val mono = all.filter { SettingsStylePolicy.specOf(it).typeface == SettingsTypeface.MONOSPACE }
        assertEquals(listOf(SettingsStyle.TERMINAL), mono)
    }
}
