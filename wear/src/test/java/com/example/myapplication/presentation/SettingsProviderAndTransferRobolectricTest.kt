package com.example.myapplication.presentation

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.settings.ProviderProfilesContent
import com.example.myapplication.presentation.ui.settings.SettingsTransferContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 两页「跨对象搬运」设置：供应商档案、设置导入导出。
 *
 * 这两页的共同点是**一次操作要动好几个字段**（档案 = 端点+密钥+模型；快照 = 四十来项偏好），
 * 所以"点了有反应"不能算过 —— 判据是搬完之后真正被别处读的那个值变了：
 * 档案切换看 `modelName`，快照导入看 `temperature`。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsProviderAndTransferRobolectricTest {

    @get:Rule
    val appearanceIsolation = SettingsAppearanceResetRule()

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    private fun clickRow(text: String) = SettingsUi.clickRow(compose, text)

    private fun clearProfiles(p: AgentPreferences) {
        p.providerProfiles().profiles.forEach { p.deleteProviderProfile(it.id) }
    }

    @Test
    fun choosingAProfileSwapsTheModelThatTheAgentActuallyUses() {
        val p = prefs()
        clearProfiles(p)

        // 两套配置用不同的模型名：切档案之后 modelName 变没变，是这套功能唯一的判据。
        p.baseUrl = "https://a.example/v1"
        p.modelName = "model-a"
        p.createProviderProfileFromCurrent("档案A")

        p.baseUrl = "https://b.example/v1"
        p.modelName = "model-b"
        p.createProviderProfileFromCurrent("档案B")

        val book = p.providerProfiles()
        assertEquals("准备阶段应有两套档案", 2, book.profiles.size)
        val idA = book.profiles.first { it.name == "档案A" }.id
        val idB = book.profiles.first { it.name == "档案B" }.id

        compose.setContent {
            MaterialTheme {
                ProviderProfilesContent(preferences = p, onBack = {}, onApplied = {})
            }
        }

        clickRow("档案A")
        assertEquals("选「档案A」后模型必须切成 model-a", "model-a", p.modelName)
        assertEquals("选「档案A」后端点必须跟着换", "https://a.example/v1", p.baseUrl)
        assertEquals("选「档案A」后必须记为当前档案", idA, p.providerProfiles().activeProfileId)

        clickRow("档案B")
        assertEquals("选「档案B」后模型必须切回 model-b", "model-b", p.modelName)
        assertEquals(idB, p.providerProfiles().activeProfileId)
        assertNotEquals("两套档案不能指向同一个模型", idA, idB)
    }

    @Test
    fun copyAndDeleteProfileChangesTheBookSize() {
        val p = prefs()
        clearProfiles(p)
        p.modelName = "model-x"
        val start = p.providerProfiles().profiles.size

        compose.setContent {
            MaterialTheme {
                ProviderProfilesContent(preferences = p, onBack = {}, onApplied = {})
            }
        }

        clickRow(context.getString(R.string.settings_providers_copy))
        assertEquals("点「复制当前配置」必须真的多一套", start + 1, p.providerProfiles().profiles.size)

        clickRow(context.getString(R.string.settings_providers_delete))
        assertEquals("点「删除当前配置」必须真的少一套", start, p.providerProfiles().profiles.size)
    }

    @Test
    fun exportThenImportRestoresATweakedPreference() {
        val p = prefs()
        p.temperature = 0.3f

        compose.setContent {
            MaterialTheme { SettingsTransferContent(preferences = p, onBack = {}, onApplied = {}) }
        }

        clickRow(context.getString(R.string.settings_transfer_export))
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val snapshot = clipboard?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue("导出必须把快照放进剪贴板，实际拿到：" + snapshot.take(40), snapshot.isNotBlank())

        // 改动一项，再导入：必须回到导出时的值
        p.temperature = 1.4f
        clickRow(context.getString(R.string.settings_transfer_import))
        assertEquals("导入必须把快照里的温度写回去", 0.3f, p.temperature, 0.0001f)
    }

    @Test
    fun importingGarbageWritesNothing() {
        val p = prefs()
        p.temperature = 0.7f
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("settings", "这不是快照，只是一句话"))

        compose.setContent {
            MaterialTheme { SettingsTransferContent(preferences = p, onBack = {}, onApplied = {}) }
        }

        clickRow(context.getString(R.string.settings_transfer_import))
        assertEquals("解析失败时一个字都不许写", 0.7f, p.temperature, 0.0001f)
    }
}
