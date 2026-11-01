package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences

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
    onToggleMcp: (enabled: Boolean, endpoint: String) -> Unit
) {
    var toolDeviceStatus by remember { mutableStateOf(preferences.toolDeviceStatusEnabled) }
    var toolVibrate by remember { mutableStateOf(preferences.toolVibrateEnabled) }
    var toolMatrixDensity by remember { mutableStateOf(preferences.toolMatrixDensityEnabled) }
    var toolPowerMode by remember { mutableStateOf(preferences.toolPowerModeEnabled) }
    var toolWeather by remember { mutableStateOf(preferences.toolWeatherEnabled) }
    var toolWebSearch by remember { mutableStateOf(preferences.toolWebSearchEnabled) }

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
    }
}
