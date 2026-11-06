package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.SecurityMode

/**
 * 工具集细粒度放行与 MCP 协议探针：六个内置工具改用官方 SwitchButton，
 * 点击语义、写入的偏好键与旧版逐条一致。
 */
@Composable
fun ToolsMcpContent(
    preferences: AgentPreferences,
    isMcpEnabled: Boolean,
    mcpEndpoint: String,
    onBack: () -> Unit,
    onToggleMcp: (enabled: Boolean, endpoint: String) -> Unit,
    animationsEnabled: Boolean = true,
    visionBetaEnabled: Boolean = false,
    onToggleAnimations: (Boolean) -> Unit = {},
    onToggleVisionBeta: (Boolean) -> Unit = {}
) {
    var toolDeviceStatus by remember { mutableStateOf(preferences.toolDeviceStatusEnabled) }
    var toolVibrate by remember { mutableStateOf(preferences.toolVibrateEnabled) }
    var toolMatrixDensity by remember { mutableStateOf(preferences.toolMatrixDensityEnabled) }
    var toolPowerMode by remember { mutableStateOf(preferences.toolPowerModeEnabled) }
    var toolWeather by remember { mutableStateOf(preferences.toolWeatherEnabled) }
    var toolWebSearch by remember { mutableStateOf(preferences.toolWebSearchEnabled) }
    var providerManagedSearch by remember { mutableStateOf(preferences.providerManagedSearchEnabled) }
    var subAgentEnabled by remember { mutableStateOf(preferences.subAgentEnabled) }
    // 子代理只在专家模式生效：其余模式下这一行退化成只读提示，不给可拨开关
    val subAgentEffective = preferences.securityMode == SecurityMode.EXPERT

    SettingsSubPage(
        title = stringResource(R.string.settings_tools_and_mcp),
        onBack = onBack
    ) {
        section(stringResource(R.string.settings_tools_release_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_device_status),
                secondary = "get_device_status",
                checked = toolDeviceStatus,
                onCheckedChange = { enabled ->
                    toolDeviceStatus = enabled
                    preferences.toolDeviceStatusEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_vibrate),
                secondary = "vibrate_device",
                checked = toolVibrate,
                onCheckedChange = { enabled ->
                    toolVibrate = enabled
                    preferences.toolVibrateEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_matrix_density),
                secondary = "adjust_matrix_density",
                checked = toolMatrixDensity,
                onCheckedChange = { enabled ->
                    toolMatrixDensity = enabled
                    preferences.toolMatrixDensityEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_power_mode),
                secondary = "set_system_power_mode",
                checked = toolPowerMode,
                onCheckedChange = { enabled ->
                    toolPowerMode = enabled
                    preferences.toolPowerModeEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_time_weather),
                secondary = "query_time_weather",
                checked = toolWeather,
                onCheckedChange = { enabled ->
                    toolWeather = enabled
                    preferences.toolWeatherEnabled = enabled
                }
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tool_web_search),
                secondary = "web_search",
                checked = toolWebSearch,
                onCheckedChange = { enabled ->
                    toolWebSearch = enabled
                    preferences.toolWebSearchEnabled = enabled
                }
            )
        }

        // 子代理模式：非专家模式下列表里连工具都不会出现，设置面也没有可拨的开关
        row {
            if (subAgentEffective) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_sub_agent),
                    secondary = stringResource(R.string.settings_sub_agent_secondary),
                    checked = subAgentEnabled,
                    onCheckedChange = { enabled ->
                        subAgentEnabled = enabled
                        preferences.subAgentEnabled = enabled
                    }
                )
            } else {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_sub_agent),
                    value = stringResource(R.string.settings_sub_agent_requires_expert)
                )
            }
        }

        section(stringResource(R.string.settings_web_tools_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_tools_provider_search),
                secondary = stringResource(R.string.settings_tools_provider_search_secondary),
                checked = providerManagedSearch,
                onCheckedChange = { enabled ->
                    providerManagedSearch = enabled
                    preferences.providerManagedSearchEnabled = enabled
                }
            )
        }

        section(stringResource(R.string.settings_mcp_protocol))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_mcp_probe),
                checked = isMcpEnabled,
                onCheckedChange = { enabled ->
                    onToggleMcp(enabled, mcpEndpoint)
                }
            )
        }

        row { SettingsCaptionRow(text = mcpEndpoint, mono = true) }

        // 界面动效与内测入口：这一页是所有"功能开关"的归口，语义上比推理参数页更贴
        section(stringResource(R.string.settings_ui_and_beta_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_animations),
                secondary = stringResource(R.string.settings_animations_secondary),
                checked = animationsEnabled,
                onCheckedChange = onToggleAnimations
            )
        }

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_vision_beta),
                secondary = stringResource(R.string.settings_vision_beta_secondary),
                checked = visionBetaEnabled,
                onCheckedChange = onToggleVisionBeta
            )
        }
    }
}
