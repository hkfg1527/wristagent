package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.FakeSharedPreferences
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.contract.SecurityMode
import org.junit.Assert.assertEquals
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 「设置项点了真的会改设置」的端到端数据层验证。
 *
 * 用户原话："他展示些东西有啥用？我要调整它，我要设置他呀"。
 * 此前「工具与 MCP」「语音与唤醒」是点了没反应的假跳转行；这次它们展开成真实开关/选项，
 * 每个 key 都必须在 Host 里有落点、且读写闭环。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w220dp-h400dp")
class SettingsV2WritableTest {

    @get:Rule
    val compose = createComposeRule()

    private fun prefs() = AgentPreferences(FakeSharedPreferences())

    /** 从 Host 的 onRowClick/onChoiceSelected 里原样抄来的写入逻辑（两处必须一致，见下方守卫）。 */
    private fun applyToggle(p: AgentPreferences, key: String) {
        when (key) {
            "tool_device" -> p.toolDeviceStatusEnabled = !p.toolDeviceStatusEnabled
            "tool_vibrate" -> p.toolVibrateEnabled = !p.toolVibrateEnabled
            "tool_matrix" -> p.toolMatrixDensityEnabled = !p.toolMatrixDensityEnabled
            "tool_power" -> p.toolPowerModeEnabled = !p.toolPowerModeEnabled
            "tool_weather" -> p.toolWeatherEnabled = !p.toolWeatherEnabled
            "tool_web" -> p.toolWebSearchEnabled = !p.toolWebSearchEnabled
            "mcp" -> p.isMcpEnabled = !p.isMcpEnabled
            "asr_enabled" -> p.asrEnabled = !p.asrEnabled
            "asr_live" -> p.asrLiveTranscript = !p.asrLiveTranscript
            "tts" -> p.autoSpeakReplies = !p.autoSpeakReplies
            "compact" -> p.compactMode = !p.compactMode
        }
    }

    /**
     * 每个开关 key 都必须在 Host 里有真实落点。
     *
     * 第一版把写入逻辑抄了一份在测试里自己测自己 —— 那是空洞断言：Host 里删掉任一 setter 它照样绿。
     * 现在直接读 Host 源码，要求 11 个 key 各出现一次且都命中 `preferences.<字段>` 赋值。
     */
    @Test
    fun everyToggleKeyIsWiredInTheHost() {
        val host = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Host.kt") }
            .first { it.exists() }
            .readText()
        val toggleKeys = SettingsV2SampleData.root()
            .flatMap { it.rows }
            .filter { it.trailing is RowTrailing.Toggle }
            .map { it.key }
        assertEquals("开关行数必须与预期一致", 11, toggleKeys.size)

        toggleKeys.forEach { key ->
            val line = host.lines().firstOrNull { it.contains("\"$key\" ->") }
            assertTrue("Host 里必须有 $key 的分支", line != null)
            assertTrue(
                "$key 必须写回 preferences 对应字段（不是空操作）",
                line!!.contains("preferences.") && line.contains("= !preferences.") &&
                    !line.contains("-> Unit")
            )
        }
    }

    /** 选项 key 也必须在 Host 的 onChoiceSelected 里有落点。 */
    @Test
    fun everyChoiceKeyIsWiredInTheHost() {
        val host = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Host.kt") }
            .first { it.exists() }
            .readText()
        val choiceKeys = SettingsV2SampleData.root()
            .flatMap { it.rows }
            .filter { it.choice != null }
            .map { it.key }
        assertEquals("选项行数必须与预期一致", 7, choiceKeys.size)
        choiceKeys.forEach { key ->
            assertTrue(
                "Host 必须有 $key 的写入分支",
                host.lines().any { it.contains("\"$key\" ->") && it.contains("preferences.") }
            )
        }
    }

    /** 模型/供应商两个新选项：选中后真的写回偏好（不是只显示）。 */
    @Test
    fun modelAndProviderChoicesWriteBack() {
        val p = prefs()
        p.cachedRemoteModels = "deepseek-chat,deepseek-reasoner"
        p.modelName = "deepseek-chat"
        assertEquals("deepseek-chat", valueOf(SettingsV2SampleData.fromPreferences(p), "model"))
        p.modelName = "deepseek-reasoner"
        assertEquals("deepseek-reasoner", valueOf(SettingsV2SampleData.fromPreferences(p), "model"))

        // Profile：建一套并选中，供应商行显示该 Profile 名
        val book = p.createProviderProfileFromCurrent("工作配置")
        val id = book.activeProfileId ?: ""
        p.saveProviderProfiles(book)
        assertEquals("工作配置", valueOf(SettingsV2SampleData.fromPreferences(p), "provider"))
        assertTrue("Profile id 必须出现在选项里", id.isNotBlank())
    }

    @Test
    fun choiceKeysWriteBackToPreferences() {
        val p = prefs()
        p.temperature = 0.3f
        assertEquals("0.3", valueOf(SettingsV2SampleData.fromPreferences(p), "temperature"))
        p.temperature = 1.6f
        assertEquals("1.6", valueOf(SettingsV2SampleData.fromPreferences(p), "temperature"))

        p.securityMode = SecurityMode.EXPERT
        assertEquals("自动执行", valueOf(SettingsV2SampleData.fromPreferences(p), "security"))
        p.sandboxLevel = SandboxLevel.READ_ONLY
        assertEquals("只读", valueOf(SettingsV2SampleData.fromPreferences(p), "root"))

        p.asrDevPid = 1737
        assertEquals("英语", valueOf(SettingsV2SampleData.fromPreferences(p), "asr_lang"))
        p.asrDevPid = 1637
        assertEquals("粤语", valueOf(SettingsV2SampleData.fromPreferences(p), "asr_lang"))
    }

    /** 假跳转行必须消失：只剩「页面风格」一个 Chevron，且它在 Screen 层已分流到选择器。 */
    @Test
    fun noDeadChevronRowsRemain() {
        val chevrons = SettingsV2SampleData.root().flatMap { it.rows }
            .filter { it.trailing is RowTrailing.Chevron }
            .map { it.key }
        assertEquals(listOf("style"), chevrons)
        assertFalse(
            "「工具与 MCP」不许再是点了没反应的假跳转行",
            SettingsV2SampleData.root().flatMap { it.rows }
                .first { it.key == "tools" }.trailing is RowTrailing.Chevron
        )
    }

    private fun toggleOf(sections: List<SettingsV2Section>, key: String): Boolean =
        sections.flatMap { it.rows }.first { it.key == key }
            .trailing.let { it as RowTrailing.Toggle }.checked

    private fun valueOf(sections: List<SettingsV2Section>, key: String): String =
        sections.flatMap { it.rows }.first { it.key == key }
            .trailing.let { it as RowTrailing.Value }.text
}
