package com.example.myapplication.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentLoopPolicy
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.settings.HyperParamsContent
import com.example.myapplication.presentation.ui.settings.NetworkRequestContent
import com.example.myapplication.presentation.ui.settings.SettingsValueMath
import com.example.myapplication.presentation.ui.settings.applySettingsStyle
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 网络与请求、模型参数两页的数值项。
 *
 * 这两页的滑杆吸附规则各不相同（整秒 / 整次 / 200ms / 0.05 / 走台阶），
 * 而且**每一条都有真实消费者**：超时与重试喂给 `AgentDispatcher`，top_p 与 maxTokens 进请求体，
 * 空转止损喂给 `IdleSpinDetector`。所以判据统一是"拨完之后偏好里的真值"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsNetworkAndHyperRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun prefs(): AgentPreferences = AgentPreferences(context)

    @Before
    fun forceThinTrackStyle() {
        applySettingsStyle(context, SettingsStyle.BALLOON)
    }

    private fun setSlider(label: String, target: Float, expectedRange: ClosedFloatingPointRange<Float>) =
        SettingsUi.setSlider(compose, label, target, expectedRange)

    private fun showNetwork(p: AgentPreferences) {
        compose.setContent {
            MaterialTheme {
                NetworkRequestContent(
                    preferences = p,
                    privilegeManager = PrivilegeManager(context),
                    baseUrl = { p.baseUrl },
                    onBack = {}
                )
            }
        }
    }

    private fun showHyperParams(p: AgentPreferences) {
        compose.setContent {
            MaterialTheme { HyperParamsContent(preferences = p, onBack = {}) }
        }
    }

    @Test
    fun requestTimeoutSliderSnapsToWholeSecond() {
        val p = prefs()
        p.requestTimeoutSeconds = 40
        showNetwork(p)
        setSlider(context.getString(R.string.settings_net_timeout), 55.4f, 10f..180f)
        assertEquals("拨到 55.4 必须吸附成 55 秒", 55, p.requestTimeoutSeconds)
    }

    @Test
    fun retriesSliderSnapsToWholeCount() {
        val p = prefs()
        p.requestRetries = 1
        showNetwork(p)
        setSlider(context.getString(R.string.settings_net_retries), 2.4f, 0f..3f)
        assertEquals("拨到 2.4 必须吸附成 2 次", 2, p.requestRetries)
    }

    @Test
    fun backoffSliderSnapsTo200ms() {
        val p = prefs()
        p.retryBackoffMs = 800
        showNetwork(p)
        setSlider(context.getString(R.string.settings_net_backoff), 3300f, 0f..5000f)
        assertEquals("拨到 3300ms 必须吸附成 3400ms（200ms 一档）", 3400, p.retryBackoffMs)
    }

    @Test
    fun topPSliderSnapsToFivePercent() {
        val p = prefs()
        p.samplingTopP = 0.9f
        showHyperParams(p)
        setSlider(
            context.getString(R.string.settings_top_p),
            0.63f,
            AgentPreferences.MIN_SAMPLING_TOP_P..AgentPreferences.MAX_SAMPLING_TOP_P
        )
        assertEquals("拨到 0.63 必须吸附成 0.65", 0.65f, p.samplingTopP, 0.0001f)
    }

    @Test
    fun maxTokensSliderWalksTheLadder() {
        val p = prefs()
        p.maxOutputTokens = 300
        showHyperParams(p)
        // 这一根的值是"台阶序号"不是 token 数：拨到第 3 级必须落在 TokenLadder[3]
        setSlider(
            context.getString(R.string.settings_max_tokens),
            3f,
            0f..SettingsValueMath.TokenLadder.lastIndex.toFloat()
        )
        assertEquals(
            "拨到第 3 级必须落在 TokenLadder[3] = " + SettingsValueMath.TokenLadder[3],
            SettingsValueMath.TokenLadder[3],
            p.maxOutputTokens
        )
    }

    @Test
    fun idleSpinStopSliderSnapsToWholeNumber() {
        val p = prefs()
        p.idleSpinStopAfter = AgentLoopPolicy.MIN_IDLE_SPIN_STOP
        showHyperParams(p)
        setSlider(
            context.getString(R.string.settings_idle_spin_stop),
            5.6f,
            AgentLoopPolicy.MIN_IDLE_SPIN_STOP.toFloat()..AgentLoopPolicy.MAX_IDLE_SPIN_STOP.toFloat()
        )
        assertEquals("拨到 5.6 必须吸附成 6 轮", 6, p.idleSpinStopAfter)
    }
}
