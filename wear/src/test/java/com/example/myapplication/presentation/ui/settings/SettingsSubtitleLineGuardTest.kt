package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 长说明的完整性与允许下移的真实业务场景。 */
class SettingsSubtitleLineGuardTest {
    private val shared = readProjectFile(SettingsSharedPath)
    private val providers = readProjectFile(ProviderProfilesPath)
    private val tools = readProjectFile(ToolsMcpPath)
    private val netDiag = readProjectFile(NetworkDiagnosticsPath)
    private val modelConfig = readProjectFile(ModelConfigSectionPath)
    private val slot = shared
        .substringAfter("private fun SettingsSlotRow(")
        .substringBefore("private fun SettingsSubtitleLine(")
    private val subtitleLine = shared
        .substringAfter("private fun SettingsSubtitleLine(")
        .substringBefore("可进入行")
    private val providerRow = providers
        .substringAfter("SettingsRadioRow(")
        .substringBefore("onSelect =")
    private val providerSubtitle = providerRow
        .substringAfter("subtitle =")
        .substringBefore("subtitleStatus =")

    @Test
    fun longSubtitlesWrapFullyWhileMachineIdentifiersKeepTheirExplicitLimit() {
        assertTrue(slot.contains("subtitleMaxLines: Int = 2"))
        assertTrue(subtitleLine.contains("wrapsFully = belowRow && subtitleMaxLines > 1"))
        assertTrue(subtitleLine.contains("maxLines = if (wrapsFully) Int.MAX_VALUE else subtitleMaxLines"))
        assertTrue(
            subtitleLine.contains(
                "overflow = if (wrapsFully) TextOverflow.Visible else TextOverflow.Ellipsis"
            )
        )
        assertFalse("完整说明不得 Clip 或固定两行省略", subtitleLine.contains("TextOverflow.Clip"))
        assertTrue("供应商模型名仍显式单行", providerRow.contains("subtitleMaxLines = 1"))
    }

    @Test
    fun providerSubtitleStillOwnsAFullWidthSecondRow() {
        assertFalse(providerRow.contains("modelName + "))
        assertFalse(providerRow.contains("subtitleTail"))
        assertFalse(providers.contains("maskSecret"))
        assertTrue(providerSubtitle.contains("profile.modelName"))
        assertTrue(providerRow.contains("subtitleBelowRow = true"))
        assertTrue(slot.contains("if (subtitle != null && !subtitleBelowRow)"))
        assertTrue(slot.contains("if (subtitle != null && subtitleBelowRow)"))

        val trailingIndex = slot.indexOf("if (trailing != null)")
        val belowIndex = slot.indexOf("if (subtitle != null && subtitleBelowRow)")
        assertTrue(trailingIndex > 0 && belowIndex > trailingIndex)
        val belowBranch = slot
            .substringAfter("if (subtitle != null && subtitleBelowRow) {")
            .substringBefore("}")
        assertTrue(belowBranch.contains("fillMaxWidth()"))
        assertTrue(belowBranch.contains("top = SettingsRowLineGap"))
        assertTrue("子项说明仍跟随子项左槽线", belowBranch.contains("if (subItem) SettingsSubItemIndent"))
        assertFalse(belowBranch.contains("weight("))
    }

    @Test
    fun longTextOptInsAreLimitedToDocumentedSettingsScenarios() {
        val files = settingsSourceFiles()
        assertTrue("守护必须枚举到全部设置源文件", files.size >= 20)
        val names = files.map { it.name }
        val expected = listOf(
            "HyperParamsSection.kt",
            "MemoryAndSecuritySections.kt",
            "ProviderProfilesSection.kt",
            "ToolsMcpSection.kt",
            "VoiceAsrSection.kt"
        )
        expected.forEach { assertTrue("缺少长文本场景 $it", names.contains(it)) }
        val optedIn = files
            .filter { it.readText().contains("subtitleBelowRow = true") }
            .map { it.name }
            .sorted()
        assertEquals("下移只允许登记的长说明，不能扩散成装饰性排版", expected, optedIn)
    }

    @Test
    fun requiredSectionsConnectRealExplanationsRatherThanEmptyDecorativeRows() {
        val memory = readProjectFile(MemoryAndSecurityPath)
        val voice = readProjectFile(VoiceAsrPath)
        val hyper = readProjectFile(HyperParamsPath)
        assertTrue(memory.contains("subtitle = stringResource(descRes)"))
        assertTrue(memory.contains("settings_context_compress_secondary"))
        assertTrue(memory.contains("settings_memory_enabled_secondary"))
        assertTrue(voice.contains("subtitle = stringResource(descriptionRes)"))
        assertTrue(tools.contains("settings_tools_provider_search_secondary"))
        assertTrue(tools.contains("subtitle = stringResource(R.string.settings_sub_agent_requires_expert)"))
        assertTrue(hyper.contains("secondary = stringResource(R.string.settings_model_default_thinking_secondary)"))
        assertFalse("模型参数说明不能退化成被省略的独立短 caption", hyper.contains("settings_model_default_thinking_secondary)) }"))
    }

    @Test
    fun longValuesMoveAfterTheSubtitleAndRemainFullyReadable() {
        assertTrue(slot.contains("SettingsTextLayout.shouldPlaceValueBelowRow(title, value)"))
        assertTrue(slot.contains("if (value != null && !valueBelowRow)"))
        assertTrue(slot.contains("if (value != null && valueBelowRow)"))
        val valueBranch = slot.substringAfter("if (value != null && valueBelowRow) {")
        assertTrue(valueBranch.contains("maxLines = Int.MAX_VALUE"))
        assertTrue(valueBranch.contains("overflow = TextOverflow.Visible"))
        assertTrue(valueBranch.contains("textAlign = TextAlign.Start"))
    }

    @Test
    fun missingKeyKeepsItsWarningDotBesideTheCompleteSubtitle() {
        assertTrue(providerSubtitle.contains("R.string.settings_asr_key_missing"))
        assertTrue(providers.contains("profile.apiKey.isBlank()"))
        assertTrue(providerRow.contains("SettingsStatusTone.WARN"))
        assertTrue(subtitleLine.contains("if (subtitleStatus != null)"))
        assertTrue(subtitleLine.contains("SettingsStatusDot(tone = subtitleStatus"))
    }

    @Test
    fun captionsWrapFullyByDefaultButMachineCaptionsKeepSingleLineOptOuts() {
        assertTrue(shared.contains("maxLines: Int = Int.MAX_VALUE"))
        assertTrue(
            shared.contains(
                "overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Visible else TextOverflow.Ellipsis"
            )
        )
        assertTrue(netDiag.contains("SettingsCaptionRow(text = result.detail, mono = true, maxLines = 1)"))
        val modelCaption = modelConfig
            .substringAfter("R.string.settings_model_not_in_list")
            .substringBefore("}")
        assertTrue(modelCaption.contains("maxLines = 1"))
        assertTrue("MCP endpoint 使用完整折行默认值", tools.contains("SettingsCaptionRow(text = mcpEndpoint, mono = true)"))
    }

    private fun settingsSourceFiles(): List<File> =
        listOf(File(SettingsSourceDir), File("wear/" + SettingsSourceDir))
            .firstOrNull { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".kt") }
            ?.sortedBy { it.name }
            ?: error("找不到 settings 源码目录")

    private companion object {
        const val SettingsSourceDir = "src/main/java/com/example/myapplication/presentation/ui/settings"
        const val SettingsSharedPath = SettingsSourceDir + "/SettingsShared.kt"
        const val ProviderProfilesPath = SettingsSourceDir + "/ProviderProfilesSection.kt"
        const val ToolsMcpPath = SettingsSourceDir + "/ToolsMcpSection.kt"
        const val NetworkDiagnosticsPath = SettingsSourceDir + "/NetworkDiagnosticsSection.kt"
        const val ModelConfigSectionPath = SettingsSourceDir + "/ModelConfigSection.kt"
        const val MemoryAndSecurityPath = SettingsSourceDir + "/MemoryAndSecuritySections.kt"
        const val VoiceAsrPath = SettingsSourceDir + "/VoiceAsrSection.kt"
        const val HyperParamsPath = SettingsSourceDir + "/HyperParamsSection.kt"
    }
}
