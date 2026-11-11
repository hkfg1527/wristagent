package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 副标题折行 / 截断策略的源码守护。
 *
 * 真机证据（docs/device/v10）走了两轮：先折成「deepseek-reaso」+「ner  ••••d300」，改成
 * 「单行 + 尾部掩码不参与截断」之后又变成「deeps…  ••••d300」—— 模型名只剩 5 个字符，
 * deepseek-reasoner 与 deepseek-chat 再也分不开。这一行里最重要的信息是模型名，宽度必须让给它：
 * 单行 + 尾部省略号；密钥只在**缺失**时以文字 + 6dp 语义色点出现。
 *
 * 折行与截断策略编译期不会报错，只能扫真实源文件钉死。
 */
class SettingsSubtitleLineGuardTest {

    private val shared = readProjectFile(SettingsSharedPath)

    private val providers = readProjectFile(ProviderProfilesPath)

    private val tools = readProjectFile(ToolsMcpPath)

    private val netDiag = readProjectFile(NetworkDiagnosticsPath)

    private val modelConfig = readProjectFile(ModelConfigSectionPath)

    /** 行骨架本体：从函数签名到下一个公共行（可进入行）的文档注释为止。 */
    private val slot = shared
        .substringAfter("private fun SettingsSlotRow(")
        .substringBefore("可进入行")

    /** 供应商页那一行的参数表：从 SettingsRadioRow( 到 onSelect = 之前。 */
    private val providerRow = providers
        .substringAfter("SettingsRadioRow(")
        .substringBefore("onSelect =")

    /** 副标题表达式本身（= 到副标题语义色参数之前），用来确认两个分支都在。 */
    private val providerSubtitle = providerRow
        .substringAfter("subtitle =")
        .substringBefore("subtitleStatus =")

    @Test
    fun subtitleLineCountComesFromTheCaller() {
        // 中文说明保留默认 2 行，英文标识符由调用方传 1：副标题不许再写死行数
        assertTrue("副标题行数必须可由调用方指定", slot.contains("subtitleMaxLines: Int = 2"))
        assertFalse("副标题不许再写死 maxLines = 2", slot.contains("maxLines = 2"))
    }

    @Test
    fun theSubtitleOwnsTheWholeLine() {
        // 上一轮的病灶：右侧再挂一段不参与截断的固定片段，模型名被挤到「deeps…」
        assertFalse("副标题右侧不许再有固定尾段槽位", slot.contains("subtitleTail"))
        assertFalse("副标题不许用 Clip 硬裁：信息会直接消失", slot.contains("TextOverflow.Clip"))
        assertTrue(
            "副标题必须尾部省略号，且行数走调用方参数",
            slot.contains("maxLines = subtitleMaxLines") && slot.contains("overflow = TextOverflow.Ellipsis")
        )
    }

    @Test
    fun providerSubtitleIsTheModelNameOnASingleLine() {
        assertTrue("供应商副标题必须显式单行", providerRow.contains("subtitleMaxLines = 1"))
        assertFalse("供应商页不许再把副标题拼成一整串", providerRow.contains("modelName + "))
        assertFalse("密钥掩码不许再占副标题宽度", providerRow.contains("subtitleTail"))
        assertFalse("供应商页不许再算掩码", providers.contains("maskSecret"))
        assertTrue("有密钥时副标题就是模型名", providerSubtitle.contains("profile.modelName"))
    }

    @Test
    fun missingKeyTakesTheSubtitleAndShowsAWarningDot() {
        // 没密钥是异常：文案必须出现，语义色只落在 6dp 状态点上，不涂文字
        assertTrue(
            "缺密钥时副标题必须显示「密钥未填写」",
            providerSubtitle.contains("R.string.settings_asr_key_missing")
        )
        assertTrue("缺密钥必须由 apiKey 是否为空判定", providers.contains("profile.apiKey.isBlank()"))
        assertTrue(
            "缺密钥必须挂 WARN 语义点",
            providerRow.contains("SettingsStatusTone.WARN")
        )
        assertTrue(
            "行骨架必须能把语义色点画在副标题行上",
            slot.contains("if (subtitleStatus != null)")
        )
        assertTrue(
            "状态点复用既有 6dp 语义色机制",
            slot.contains("SettingsStatusDot(tone = subtitleStatus")
        )
    }

    @Test
    fun machineCaptionsDoNotWrapMidWord() {
        assertTrue(
            "说明行的行数必须由调用方指定（默认仍是 2 行）",
            shared.contains("maxLines: Int = 2") && shared.contains("maxLines = maxLines")
        )
        assertTrue(
            "探针明细是机器串：单行 + 尾部省略号",
            netDiag.contains("SettingsCaptionRow(text = result.detail, mono = true, maxLines = 1)")
        )
        val modelCaption = modelConfig
            .substringAfter("R.string.settings_model_not_in_list")
            .substringBefore("}")
        assertTrue("含原始模型 id 的提示文案不许折行", modelCaption.contains("maxLines = 1"))
        assertTrue(
            "endpoint URL 可以在 / 处折行：保持默认 2 行",
            tools.contains("SettingsCaptionRow(text = mcpEndpoint, mono = true)")
        )
    }

    private companion object {
        const val SettingsSharedPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"

        const val ProviderProfilesPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/ProviderProfilesSection.kt"

        const val ToolsMcpPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/ToolsMcpSection.kt"

        const val NetworkDiagnosticsPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/NetworkDiagnosticsSection.kt"

        const val ModelConfigSectionPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/ModelConfigSection.kt"
    }
}
