package com.example.myapplication.presentation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.settings.MemoryManagerContent
import com.example.myapplication.presentation.ui.settings.SecurityGuardrailContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 安全档位与记忆开关：两处"点了就该立刻改变行为"的设置，用 Robolectric 在 JVM 上钉住。
 *
 * 安全档位尤其不能靠肉眼：它决定工具调用要不要弹确认，选错档位是真实风险。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN")
class SettingsSecurityAndMemoryRobolectricTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Test
    fun everySecurityModeIsReportedToTheCaller() {
        val modes = listOf(
            SecurityMode.NOVICE to R.string.settings_security_novice_title,
            SecurityMode.STANDARD to R.string.settings_security_standard_title,
            SecurityMode.EXPERT to R.string.settings_security_expert_title,
        )
        var picked: SecurityMode? = null
        compose.setContent {
            MaterialTheme {
                SecurityGuardrailContent(
                    currentMode = SecurityMode.STANDARD,
                    onBack = {},
                    onSelect = { picked = it },
                )
            }
        }
        for ((mode, res) in modes) {
            picked = null
            val label = context.getString(res)
            SettingsUi.clickRow(compose, label)
            assertEquals("点「" + label + "」必须把这一档交给调用方", mode, picked)
        }
    }

    @Test
    fun memorySwitchWritesPreference() {
        val p = AgentPreferences(context)
        p.memoryEnabled = false
        compose.setContent { MaterialTheme { MemoryManagerContent(preferences = p, onBack = {}) } }
        val label = context.getString(R.string.settings_memory_enabled)
        SettingsUi.clickRow(compose, label)
        assertTrue("点「" + label + "」必须落到 prefs.memoryEnabled", p.memoryEnabled)
    }
}
