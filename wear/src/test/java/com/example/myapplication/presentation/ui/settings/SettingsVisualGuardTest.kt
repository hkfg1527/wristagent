package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置页视觉约束的源码守护：颜色用途、线的数量、对齐轴、焦点个数。
 *
 * "强调色只出现在选中态""全页只有一种线""行内不居中"这些约束编译期不会报错，
 * 只能扫真实源文件钉死 —— 否则下一次改动会悄悄退回旧版面。
 */
class SettingsVisualGuardTest {

    @Test
    fun rowTextStillUsesTwoSlotsEvenThoughTheCardShellIsCentred() {
        val shared = readSettings("SettingsShared.kt")
        val slot = shared
            .substringAfter("private fun SettingsSlotRow(")
            .substringBefore("private fun SettingsSubtitleLine(")
        assertFalse("行内文字必须仍是左右两槽，不是整体居中", slot.contains("Alignment.CenterHorizontally"))
        assertTrue("左槽必须继续占剩余宽度", slot.contains(".weight(1f)"))
        assertTrue("取值 / 控件必须仍落在右槽", slot.contains("trailing"))
    }

    @Test
    fun semanticColorsOnlySurviveAsTheStatusDot() {
        val filesWithSemanticColor = settingsSources()
            .filter { (_, text) ->
                text.contains("Palette.Success") || text.contains("Palette.Warning")
            }
            .keys
        assertEquals(
            "Success / Warning 只允许活在 6dp 状态点里（SettingsShared.SettingsStatusDot）：",
            setOf("SettingsShared.kt"),
            filesWithSemanticColor
        )
        val shared = readSettings("SettingsShared.kt")
        assertEquals(
            "Success 只许出现在状态点映射里一次",
            1,
            Regex("Palette\\.Success").findAll(shared).count()
        )
        assertEquals(
            "Warning 只许出现在状态点映射里一次",
            1,
            Regex("Palette\\.Warning").findAll(shared).count()
        )
        // 语义色不许再作为行文字的取值颜色：旧的语义行类型必须已经消失
        assertEquals(
            "语义色取值行必须已删除",
            emptySet<String>(),
            settingsSources().filter { (_, text) -> text.contains("SettingsSemanticRow") }.keys
        )
    }

    @Test
    fun accentOnlyPaintsSelectedStateAndAlwaysReadsTheRuntimeOverride() {
        // 强调色要么经 Wear.Accent 取（外观设置才换得动），要么就别用 —— 直接读令牌等于把换色开关做成摆设
        // 只有"运行时默认值"那一处允许碰令牌：它是外观设置的兜底值，不是页面上的涂色
        val onToken = settingsSources()
            .filter { (_, text) -> Regex("Palette\\.Accent\\b").containsMatchIn(codeOnly(text)) }
            .keys
        assertEquals("强调色的令牌兜底只许留在一处：", setOf("AppearanceSettings.kt"), onToken)
        assertEquals(
            "默认值兜底只允许一处（多一处就意味着有页面绕过 Wear.Accent 直接涂色）：",
            1,
            Regex("Palette\\.Accent\\b").findAll(codeOnly(readSettings("AppearanceSettings.kt"))).count()
        )
        val usingAccent = settingsSources()
            .filter { (_, text) -> codeOnly(text).contains("Wear.Accent") }
            .keys
        assertEquals("强调色只许出现在设计系统（选中态 / 滑轨）：", setOf("SettingsShared.kt"), usingAccent)

        val shared = readSettings("SettingsShared.kt")
        assertEquals(
            "全页强调色只允许：滑轨选中段 + 单选选中态 + 6dp 状态点 + 组标题短横 + 紧凑开关选中轨道",
            6,
            Regex("Wear\\.Accent\\b").findAll(codeOnly(shared)).count()
        )
        assertTrue("滑轨选中段必须跟随运行时强调色", shared.contains("selectedBarColor = Wear.Accent"))
        assertTrue("组标题短横必须在组合上下文读取运行时强调色", shared.contains("val accent = Wear.Accent"))
        assertTrue("组标题短横必须接收该运行时颜色", shared.contains("headerAccentMark(accent)"))
        assertTrue("紧凑开关选中轨道只染轨道", shared.contains("if (checked) Wear.Accent.copy(alpha = 0.82f)"))
        assertTrue(
            "单选圆点只在选中态上用强调色",
            shared.contains("val ring = if (selected) selectedColor else Wear.Palette.TextMuted")
        )
    }

    @Test
    fun subPagesUseTheOfficialScrollingEdgeButton() {
        val shared = readSettings("SettingsShared.kt")
        assertTrue("必须使用官方 ScreenScaffold", shared.contains("ScreenScaffold("))
        assertTrue("必须使用官方 EdgeButton", shared.contains("EdgeButton("))
        assertTrue("EdgeButton 必须跟随列表滚动", shared.contains(".scrollable("))
        assertTrue(shared.contains("buttonSize = EdgeButtonSize.ExtraSmall"))
        assertFalse("不得回退到自绘左上角返回键", shared.contains("SettingsBackButton(onClick"))
    }

    @Test
    fun onlyOneKindOfHairlineExistsAndItIsDrawnInsideGroupCardsOnly() {
        val shared = readSettings("SettingsShared.kt")
        assertFalse("整行分隔线必须已删除", shared.contains("fun SettingsRowDivider"))
        // 并卡后子项短线并入组内发丝线（只有左端缩进不同），独立的 SettingsSubItemDivider 必须已删除
        assertFalse("子项短线必须已并入组内发丝线", shared.contains("fun SettingsSubItemDivider"))
        assertFalse("子项短线调用点必须已删除", shared.contains("SettingsSubItemDivider()"))
        // 全目录只有一种线的声明：组内发丝线
        val occurrences = settingsSources().values.sumOf {
            Regex("fun SettingsGroupHairline").findAll(it).count()
        }
        assertEquals("组内发丝线只允许一处声明：", 1, occurrences)
        // 线只画在"行与行之间"：纯函数钉住，组尾 / 单行组 / 卡外行不画
        assertTrue(
            "线的绘制必须走当前行槽位判定",
            Regex("drawsGroupHairline\\(\\s*slot\\s*\\)").containsMatchIn(shared)
        )
    }

    @Test
    fun sectionHeadersAreNeutralAndNeverAccented() {
        val shared = readSettings("SettingsShared.kt")
        val header = shared.substringAfter("fun SettingsSectionHeader(").take(400)
        assertFalse("组标题不许再用强调色", header.contains("Palette.Accent"))
        assertTrue("组标题走中性档", header.contains("SettingsSectionStyle"))
        assertFalse("组标题不许再画装饰线", header.contains("SectionAccent"))
    }

    @Test
    fun rootPageHasExactlyOneHeavierElement() {
        val root = readProjectFile(
            "src/main/java/com/example/myapplication/presentation/ui/SettingsScreen.kt"
        )
        assertEquals(
            "根页只许有一个焦点元素（当前模型名）：",
            1,
            Regex("style = SettingsFocusStyle").findAll(root).count()
        )
        assertTrue("焦点块必须画出模型名", root.contains("SettingsFocusBlock("))
        assertEquals("根页不许自带 sp 字号", 0, Regex("\\d+\\.sp").findAll(root).count())
    }

    @Test
    fun promptBodySplitsOnBlankLinesAndNeverIndentsTheFirstLine() {
        assertEquals(listOf("第一段", "第二段"), promptParagraphs("第一段\n\n第二段"))
        assertEquals(listOf("a", "b"), promptParagraphs("\n\n a \n\n\n b \n\n"))
        assertEquals(listOf("line1\nline2"), promptParagraphs("line1\nline2"))
        assertEquals(emptyList<String>(), promptParagraphs("   \n\n  "))
        assertTrue(promptParagraphs("  正文").first().startsWith("正"))
    }

    @Test
    fun probeTickerShowsWholeSeconds() {
        assertEquals("0s", elapsedSecondsLabel(0L))
        assertEquals("0s", elapsedSecondsLabel(999L))
        assertEquals("1s", elapsedSecondsLabel(1000L))
        assertEquals("3s", elapsedSecondsLabel(3500L))
        assertEquals(ProbePhase.RUNNING, ProbePhase.valueOf("RUNNING"))
        assertEquals(4, ProbePhase.values().size)
    }

    /** 去掉注释再扫：文档里提到令牌名不算"页面在用它"。 */
    private fun codeOnly(text: String): String =
        text.replace(Regex("/\\*[\\s\\S]*?\\*/"), " ").replace(Regex("//[^\n]*"), " ")

    private fun readSettings(name: String): String = readProjectFile(SettingsDir + name)

    private fun settingsSources(): Map<String, String> {
        val dir = listOf(File(SettingsDir), File("wear", SettingsDir)).firstOrNull { it.isDirectory }
            ?: error("找不到设置页源码目录 " + SettingsDir)
        return dir.listFiles { file -> file.extension == "kt" }
            .orEmpty()
            .sortedBy { it.name }
            .associate { it.name to it.readText() }
    }

    private companion object {
        const val SettingsDir = "src/main/java/com/example/myapplication/presentation/ui/settings/"
    }
}
