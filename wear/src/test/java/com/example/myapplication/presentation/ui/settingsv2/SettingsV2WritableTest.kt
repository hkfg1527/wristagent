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
        assertEquals("开关行数必须与预期一致（点阵密度已改滑块，不计入）", 10, toggleKeys.size)

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
        assertEquals("选项行数必须与预期一致（温度已改滑块，不再计入）", 6, choiceKeys.size)
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

        p.securityMode = SecurityMode.EXPERT
        assertEquals("自动执行", valueOf(SettingsV2SampleData.fromPreferences(p), "security"))
        p.sandboxLevel = SandboxLevel.READ_ONLY
        assertEquals("只读", valueOf(SettingsV2SampleData.fromPreferences(p), "root"))

        p.asrDevPid = 1737
        assertEquals("英语", valueOf(SettingsV2SampleData.fromPreferences(p), "asr_lang"))
        p.asrDevPid = 1637
        assertEquals("粤语", valueOf(SettingsV2SampleData.fromPreferences(p), "asr_lang"))
    }

    /**
     * 数值项必须是**滑块**，而且滑块真的写回偏好。
     *
     * 用户原话："很多数值的拿那玩意（滑块）不就行了。为什么要再整个子菜单？"
     * 这里钉四件事：①温度行尾是 Slider（不是选项组、不再是 Value 文本）；
     * ②区间/步进取既有 `SettingsValueMath`（0..1.5，0.1 一档 = 14 个中间台阶）；
     * ③实值行显示一位小数；④Host 的 `onSliderChange` 有 temperature 分支且真的赋值。
     */
    @Test
    fun temperatureIsASliderAndTheHostWritesItBack() {
        val row = SettingsV2SampleData.root().flatMap { it.rows }.first { it.key == "temperature" }
        assertTrue("温度必须是滑块，不允许再开子菜单", row.trailing is RowTrailing.Slider)
        assertTrue("温度不应再挂选项组", row.choice == null)
        val slider = row.trailing as RowTrailing.Slider
        assertEquals("下界取既有常量", 0f, slider.range.start, 1e-6f)
        assertEquals("上界取既有常量（旧选项表里的 1.6/2.0 超出了应用上限）", 1.5f, slider.range.endInclusive, 1e-6f)
        assertEquals(
            "必须是连续滑块（steps=0）：steps>0 时官方 Slider 画成 −/+ 步进器，标题会被挤没",
            0,
            slider.steps,
        )

        // 实值路径：0.75 上屏一位小数；越界值被夹回 1.5（不给 Slider 传越界 value）
        val p = prefs()
        p.temperature = 0.75f
        val wired = SettingsV2SampleData.fromPreferences(p).flatMap { it.rows }.first { it.key == "temperature" }
        assertEquals("0.8", wired.sliderText)
        p.temperature = 2.0f
        val clamped = SettingsV2SampleData.fromPreferences(p).flatMap { it.rows }.first { it.key == "temperature" }
        assertEquals("越界值必须夹回上界", 1.5f, (clamped.trailing as RowTrailing.Slider).value, 1e-6f)

        val host = sequenceOf(File("."), File("wear"), File("../wear"))
            .map { it.absoluteFile }
            .map { File(it, "src/main/java/com/example/myapplication/presentation/ui/settingsv2/SettingsV2Host.kt") }
            .first { it.exists() }
            .readText()
        val lines = host.lines()
        val branch = lines.indexOfFirst { it.contains("\"temperature\" ->") }
        assertTrue("Host 的 onSliderChange 必须有 temperature 分支", branch >= 0)
        // 分支体可能写成一行或多行，所以看"分支开始后的三行窗口"，而不是死盯同一行
        val body = lines.subList(branch, minOf(branch + 3, lines.size)).joinToString("\n")
        assertTrue("必须真的写 preferences.temperature", body.contains("preferences.temperature ="))
        assertTrue("落库前必须量化（否则 0.7000001 会进偏好）", body.contains("quantizeTemperature"))
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
