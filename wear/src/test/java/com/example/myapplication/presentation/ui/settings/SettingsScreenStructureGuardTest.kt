package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置根页的结构守护：组标题数量与顺序、[SettingsSubDialog] 的每个值都必须有 `when` 分支。
 *
 * 断言对象是真实源文件 SettingsScreen.kt：枚举值与 `when` 分支的一致性只存在于文件文本里，
 * 编译期不会因此报错（非穷尽 `when` 在语句位置只是警告），只能靠源码扫描钉死。
 */
class SettingsScreenStructureGuardTest {

    @Test
    fun rootPageRendersExactlyThreeGroupHeaders() {
        val rendered = groupHeaderCall.findAll(readSettingsScreen())
            .map { it.groupValues[1] }
            .toList()
        assertEquals("根页组标题必须恰好三个且按 模型 / 语音与工具 / 系统 排序：", expectedSections, rendered)
    }

    @Test
    fun everyGroupHeaderHasTextInBothLocales() {
        for (locale in listOf("values", "values-zh")) {
            val xml = readProjectFile("src/main/res/" + locale + "/strings_settings.xml")
            for (name in expectedSections) {
                val pattern = Regex("<string name=\"" + name + "\">(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
                val text = pattern.find(xml)?.groupValues?.get(1)
                assertTrue(locale + "/strings_settings.xml 缺少组标题 " + name, text != null)
                assertTrue(locale + " 的组标题 " + name + " 不能为空", text != null && text.isNotBlank())
            }
        }
    }

    @Test
    fun everySubDialogValueHasAWhenBranch() {
        val source = readSettingsScreen()
        val missing = missingBranches(source, SettingsSubDialog.values().map { it.name })
        assertEquals("SettingsSubDialog 新增值必须在 SettingsScreen 的 when 里补分支（否则点进去是空白页）：", emptyList<String>(), missing)
    }

    @Test
    fun noWhenBranchReferencesARemovedValue() {
        val source = readSettingsScreen()
        val branches = branchLabel.findAll(source).map { it.groupValues[1] }.toList()
        val declared = SettingsSubDialog.values().map { it.name }
        assertEquals("when 里出现了枚举没有的分支：", emptyList<String>(), branches.filter { it !in declared })
        assertEquals("分支数必须与枚举值数量一致（漏项即失败）：", declared.size, branches.toSet().size)
    }

    @Test
    fun theBranchScannerReportsAMissingBranch() {
        // 三角验证：把 ABOUT 的分支改名，扫描器必须立刻报出 ABOUT 缺失，而不是恒返回空表
        val broken = readSettingsScreen().replace("SettingsSubDialog.ABOUT ->", "SettingsSubDialog.SOMETHING_ELSE ->")
        val names = SettingsSubDialog.values().map { it.name }
        assertEquals(listOf("ABOUT"), missingBranches(broken, names))
        assertEquals("可用作对照：未改动的源文件必须无缺失", emptyList<String>(), missingBranches(readSettingsScreen(), names))
    }

    private fun missingBranches(source: String, names: List<String>): List<String> =
        names.filter { name -> !Regex("SettingsSubDialog\\." + name + "\\s*->").containsMatchIn(source) }

    private fun readSettingsScreen(): String = readProjectFile(SettingsScreenPath)

    private companion object {
        const val SettingsScreenPath = "src/main/java/com/example/myapplication/presentation/ui/SettingsScreen.kt"

        val groupHeaderCall = Regex("SettingsSectionHeader\\(\\s*text\\s*=\\s*stringResource\\(R\\.string\\.(settings_section_[a-z_]+)\\)")
        val branchLabel = Regex("SettingsSubDialog\\.([A-Z_]+)\\s*->")

        val expectedSections = listOf(
                "settings_section_models",
                "settings_section_voice_tools",
                "settings_section_system"
        )
    }
}
