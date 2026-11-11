package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 副标题折行 / 截断 / 落点的源码守护。
 *
 * 真机证据（docs/device/v10）走了两轮：先折成「deepseek-reaso」+「ner  ••••d300」，改成
 * 「单行 + 尾部掩码不参与截断」之后又变成「deeps…  ••••d300」—— 模型名只剩 5 个字符，
 * deepseek-reasoner 与 deepseek-chat 再也分不开。
 *
 * 第三轮把账算清了：行内 148dp，单选圆点 18dp + 间隔 10dp 占走 28dp，副标题只剩 120dp；
 * 而 deepseek-reasoner 在 15sp 下约占 136dp —— 差 16dp，必然截掉 2~3 个字符。
 * 于是供应商页把副标题下移到第二行、独占整行 148dp；其它页的副标题是中文说明，
 * 随标签列折行本来就正常，**不许**跟着下移（改它们只是徒增纵向高度）。
 *
 * 折行、截断与"哪一页下移"编译期不会报错，只能扫真实源文件钉死。
 */
class SettingsSubtitleLineGuardTest {

    private val shared = readProjectFile(SettingsSharedPath)

    private val providers = readProjectFile(ProviderProfilesPath)

    private val tools = readProjectFile(ToolsMcpPath)

    private val netDiag = readProjectFile(NetworkDiagnosticsPath)

    private val modelConfig = readProjectFile(ModelConfigSectionPath)

    /** 行骨架本体：SettingsSlotRow 从签名到副标题行函数为止。 */
    private val slot = shared
        .substringAfter("private fun SettingsSlotRow(")
        .substringBefore("private fun SettingsSubtitleLine(")

    /** 副标题行本体：两个落点（标签列内 / 第一行之下）共用这一份圆点与省略号规则。 */
    private val subtitleLine = shared
        .substringAfter("private fun SettingsSubtitleLine(")
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
        assertFalse("副标题行本身不许再写死 maxLines = 2", subtitleLine.contains("maxLines = 2"))
    }

    @Test
    fun theSubtitleOwnsTheWholeLine() {
        // 上一轮的病灶：右侧再挂一段不参与截断的固定片段，模型名被挤到「deeps…」
        assertFalse("副标题右侧不许再有固定尾段槽位", shared.contains("subtitleTail"))
        assertFalse("副标题不许用 Clip 硬裁：信息会直接消失", subtitleLine.contains("TextOverflow.Clip"))
        assertTrue(
            "副标题必须尾部省略号，且行数走调用方参数",
            subtitleLine.contains("maxLines = subtitleMaxLines") &&
                subtitleLine.contains("overflow = TextOverflow.Ellipsis")
        )
    }

    @Test
    fun providerSubtitleOwnsAFullWidthSecondRow() {
        // —— 上一轮的契约逐条保留：单行、不拼串、不掩码、有密钥时就是模型名
        assertTrue("供应商副标题必须显式单行", providerRow.contains("subtitleMaxLines = 1"))
        assertFalse("供应商页不许再把副标题拼成一整串", providerRow.contains("modelName + "))
        assertFalse("密钥掩码不许再占副标题宽度", providerRow.contains("subtitleTail"))
        assertFalse("供应商页不许再算掩码", providers.contains("maskSecret"))
        assertTrue("有密钥时副标题就是模型名", providerSubtitle.contains("profile.modelName"))

        // —— 本轮契约：副标题在下方独占整行
        assertTrue("供应商行必须把副标题下移到第二行", providerRow.contains("subtitleBelowRow = true"))
        assertTrue(
            "下移必须是一个默认关闭的开关，而不是换掉整行的实现",
            shared.contains("subtitleBelowRow: Boolean = false")
        )
        assertEquals(
            "行骨架与 SettingsRadioRow 两处默认值都必须是 false（其它页零影响）",
            2,
            "subtitleBelowRow: Boolean = false".toRegex().findAll(shared).count()
        )
        assertTrue(
            "默认分支必须保持副标题留在标签列内（现有布局原样）",
            slot.contains("if (subtitle != null && !subtitleBelowRow)")
        )
        assertTrue("下移分支必须存在", slot.contains("if (subtitle != null && subtitleBelowRow)"))

        // 位置：下移分支排在"标签 + 行尾控件"那一行之后，才是"落在下方"而不是"落在旁边"
        val trailingIndex = slot.indexOf("if (trailing != null)")
        val belowIndex = slot.indexOf("if (subtitle != null && subtitleBelowRow)")
        assertTrue("找不到行尾控件分支", trailingIndex > 0)
        assertTrue("找不到下移分支", belowIndex > 0)
        assertTrue("副标题下移分支必须排在标签行之后", belowIndex > trailingIndex)

        // 宽度：那一块必须以 fillMaxWidth 撑满行内宽（176dp 内容盒 − 两侧 14dp = 148dp）
        val belowBranch = slot
            .substringAfter("if (subtitle != null && subtitleBelowRow) {")
            .substringBefore("}")
        assertTrue("下移的副标题必须撑满整行内宽", belowBranch.contains("fillMaxWidth()"))
        assertTrue(
            "下移的副标题与上一行之间要保留行距",
            belowBranch.contains("padding(top = SettingsRowLineGap)")
        )
        assertFalse(
            "下移的副标题不许再和行尾圆点挤在同一行（不许出现 weight 抢宽度）",
            belowBranch.contains("weight(")
        )
    }

    @Test
    fun subtitleBelowRowIsOnlyUsedByProviderProfiles() {
        val files = settingsSourceFiles()
        assertTrue("必须能枚举到 settings 源码文件，否则这条守护是空转", files.size >= 20)

        val names = files.map { it.name }
        // 四个带副标题的页面都在枚举范围内：它们是中文说明，2 行换行本来就正常，不许跟着下移
        listOf(
            "VoiceAsrSection.kt",
            "MemoryAndSecuritySections.kt",
            "ModelConfigSection.kt",
            "WatchSettingsSections.kt"
        ).forEach { assertTrue("枚举必须覆盖 " + it, names.contains(it)) }

        val optedIn = files
            .filter { it.readText().contains("subtitleBelowRow = true") }
            .map { it.name }
            .sorted()
        assertEquals(
            "只有供应商页允许把副标题下移到第二行，其它 section 一个都不许扩散",
            listOf("ProviderProfilesSection.kt"),
            optedIn
        )
    }

    @Test
    fun missingKeyTakesTheSubtitleAndShowsAWarningDot() {
        // 没密钥是异常：文案必须出现，语义色只落在 6dp 状态点上，不涂文字；位置随副标题一起下移
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
            "副标题行必须能把语义色点画在自己这一行上（下移后点跟文字一起走）",
            subtitleLine.contains("if (subtitleStatus != null)")
        )
        assertTrue(
            "状态点复用既有 6dp 语义色机制",
            subtitleLine.contains("SettingsStatusDot(tone = subtitleStatus")
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

    /** settings 包下全部 .kt 源文件：扩散检查必须看到每一页，包括 [SettingsSharedPath] 自己。 */
    private fun settingsSourceFiles(): List<File> =
        listOf(File(SettingsSourceDir), File("wear/" + SettingsSourceDir))
            .firstOrNull { it.isDirectory }
            ?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".kt") }
            ?.sortedBy { it.name }
            ?: error("找不到 settings 源码目录（cwd=" + File(".").absolutePath + "）")

    private companion object {
        const val SettingsSourceDir =
            "src/main/java/com/example/myapplication/presentation/ui/settings"

        const val SettingsSharedPath = SettingsSourceDir + "/SettingsShared.kt"

        const val ProviderProfilesPath = SettingsSourceDir + "/ProviderProfilesSection.kt"

        const val ToolsMcpPath = SettingsSourceDir + "/ToolsMcpSection.kt"

        const val NetworkDiagnosticsPath = SettingsSourceDir + "/NetworkDiagnosticsSection.kt"

        const val ModelConfigSectionPath = SettingsSourceDir + "/ModelConfigSection.kt"
    }
}
