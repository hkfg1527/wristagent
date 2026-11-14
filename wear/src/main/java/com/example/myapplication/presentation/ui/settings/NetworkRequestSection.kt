package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.roundToInt

/**
 * 「网络与请求」子页：把原来堆在根页上的 5 个密集控件搬进独立一页。
 *
 * 信息架构规范（docs/SETTINGS-IA.md §1.2）要求根页只放「状态 + 一击直达」，
 * 滑杆与开关这类要反复调的东西属于子页。根页因此只留一行入口，值直接报超时秒数。
 */
@Composable
fun NetworkRequestContent(
    preferences: AgentPreferences,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var requestTimeout by remember { mutableStateOf(preferences.requestTimeoutSeconds) }
    var requestRetries by remember { mutableStateOf(preferences.requestRetries) }
    var retryBackoff by remember { mutableStateOf(preferences.retryBackoffMs) }
    var wifiOnly by remember { mutableStateOf(preferences.wifiOnlyRequests) }
    var hapticsOn by remember { mutableStateOf(preferences.hapticsEnabled) }

    SettingsSubPage(
        title = stringResource(R.string.settings_sub_network_request),
        heroLabel = stringResource(R.string.settings_sub_network_request),
        onBack = onBack,
        hero = {
            SettingsHeroCard(
                label = stringResource(R.string.settings_section_network),
                value = requestTimeout.toString() + "s",
                accent = Wear.CardTools,
                ring = ((requestTimeout - 10f) / 170f).coerceIn(0f, 1f),
                meta = stringResource(
                    R.string.settings_card_meta_network,
                    requestRetries,
                    retryBackoff
                )
            )
        }
    ) {
        section(stringResource(R.string.settings_section_network))
        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_net_timeout),
                value = requestTimeout.toFloat(),
                valueRange = 10f..180f,
                steps = 16,
                valueText = requestTimeout.toString() + "s",
                onValueChange = { raw ->
                    val snapped = raw.roundToInt()
                    if (snapped != requestTimeout) {
                        requestTimeout = snapped
                        preferences.requestTimeoutSeconds = snapped
                        context.vibrateTick()
                    }
                }
            )
        }
        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_net_retries),
                value = requestRetries.toFloat(),
                valueRange = 0f..3f,
                steps = 2,
                valueText = requestRetries.toString() + "×",
                onValueChange = { raw ->
                    val snapped = raw.roundToInt()
                    if (snapped != requestRetries) {
                        requestRetries = snapped
                        preferences.requestRetries = snapped
                        context.vibrateTick()
                    }
                }
            )
        }
        row {
            SettingsSliderRow(
                label = stringResource(R.string.settings_net_backoff),
                value = retryBackoff.toFloat(),
                valueRange = 0f..5000f,
                steps = 24,
                valueText = retryBackoff.toString() + "ms",
                onValueChange = { raw ->
                    val snapped = (raw / 200f).roundToInt() * 200
                    if (snapped != retryBackoff) {
                        retryBackoff = snapped
                        preferences.retryBackoffMs = snapped
                        context.vibrateTick()
                    }
                }
            )
        }
        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_wifi_only),
                secondary = stringResource(R.string.settings_wifi_only_secondary),
                checked = wifiOnly,
                onCheckedChange = { enabled ->
                    wifiOnly = enabled
                    preferences.wifiOnlyRequests = enabled
                }
            )
        }
        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_haptics),
                secondary = stringResource(R.string.settings_haptics_secondary),
                checked = hapticsOn,
                onCheckedChange = { enabled ->
                    hapticsOn = enabled
                    preferences.hapticsEnabled = enabled
                }
            )
        }
    }
}
