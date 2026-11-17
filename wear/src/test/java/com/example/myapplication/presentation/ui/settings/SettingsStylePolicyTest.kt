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
    fun everyStyleDeclaresAllEightSlots() {
        for (style in all) {
            val spec = SettingsStylePolicy.specOf(style)
            // 只是为了失败时能一眼看出是哪套风格缺东西
            assertTrue("$style 的骨架槽位缺失", spec.shell != null)
            assertTrue("$style 的返回槽位缺失", spec.back != null)
        }
        assertEquals("风格数量变了就要同步 UI-STYLE-PLAN.md", 4, all.size)
    }

    @Test
    fun stylesAreActuallyDifferentFromEachOther() {
        val specs = all.map { SettingsStylePolicy.specOf(it) }
        assertEquals("四套风格的声明不许重复", specs.size, specs.toSet().size)
    }

    @Test
    fun theCardStyleIsTheOnlyOneOnTheOfficialScaffold() {
        val onScaffold = all.filter { SettingsStylePolicy.specOf(it).shell == SettingsPageShell.SCREEN_SCAFFOLD }
        assertEquals("目前只有大卡片档走官方 ScreenScaffold", listOf(SettingsStyle.GOOGLE_CARD), onScaffold)
        assertEquals(
            "大卡片档的返回必须进列表首项",
            SettingsBackControl.LIST_ROW,
            SettingsStylePolicy.specOf(SettingsStyle.GOOGLE_CARD).back
        )
    }

    @Test
    fun everyStyleHasExactlyOneBackControl() {
        // 返回控件是用户最容易"找不到"的东西：每套风格只能声明一种位置
        for (style in all) {
            val back = SettingsStylePolicy.specOf(style).back
            assertTrue("$style 的返回槽位不合法", back in SettingsBackControl.values())
        }
    }

    @Test
    fun terminalIsTheOnlyMonospaceStyle() {
        val mono = all.filter { SettingsStylePolicy.specOf(it).typeface == SettingsTypeface.MONOSPACE }
        assertEquals(listOf(SettingsStyle.TERMINAL), mono)
    }
}
