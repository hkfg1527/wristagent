package com.example.myapplication.presentation.ui.settings

import com.example.myapplication.readProjectFile
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 副标题折行规则的源码守护。
 *
 * 真机截图证据：供应商页副标题被折成「deepseek-reaso」+「ner  ••••d300」—— 英文标识符从词中间
 * 劈成两行，在列表里读起来像拼错。折行与截断策略编译期不会报错，只能扫真实源文件钉死。
 */
class SettingsSubtitleLineGuardTest {

    private val shared = readProjectFile(SettingsSharedPath)

    private val providers = readProjectFile(ProviderProfilesPath)

    @Test
    fun subtitleLineCountComesFromTheCaller() {
        // 中文说明保留默认 2 行，英文标识符由调用方传 1：副标题不许再写死行数
        val slot = shared
            .substringAfter("private fun SettingsSlotRow(")
            .substringBefore("可进入行")
        assertTrue("副标题行数必须可由调用方指定", slot.contains("subtitleMaxLines: Int = 2"))
        assertFalse("副标题不许再写死 maxLines = 2", slot.contains("maxLines = 2"))
    }

    @Test
    fun providerSubtitleIsSingleLineAndKeepsTheMaskedKey() {
        val row = providers
            .substringAfter("SettingsRadioRow(")
            .substringBefore("onSelect =")
        assertTrue("供应商副标题必须显式单行", row.contains("subtitleMaxLines = 1"))
        assertTrue("密钥掩码必须作为尾部片段传入", row.contains("subtitleTail ="))
        assertTrue("副标题本体只放模型名", row.contains("subtitle = profile.modelName"))
        assertFalse("拼成一整串会先截掉掩码", row.contains("modelName + "))
    }

    @Test
    fun theSubtitleTailIsNeverTruncated() {
        val tail = shared
            .substringAfter("text = subtitleTail")
            .substringBefore("}")
        assertTrue(
            "尾部片段溢出时直接裁掉而不是省略：掩码被截成「••…」等于没写",
            tail.contains("TextOverflow.Clip")
        )
    }

    private companion object {
        const val SettingsSharedPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/SettingsShared.kt"

        const val ProviderProfilesPath =
            "src/main/java/com/example/myapplication/presentation/ui/settings/ProviderProfilesSection.kt"
    }
}
