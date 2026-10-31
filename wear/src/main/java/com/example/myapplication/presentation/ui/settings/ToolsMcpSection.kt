package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 工具集细粒度放行控制与 MCP 协议探针。
 */
@Composable
fun ToolsMcpContent(
    preferences: AgentPreferences,
    isMcpEnabled: Boolean,
    mcpEndpoint: String,
    onToggleMcp: (enabled: Boolean, endpoint: String) -> Unit
) {
    val context = LocalContext.current
    var toolDeviceStatus by remember { mutableStateOf(preferences.toolDeviceStatusEnabled) }
    var toolVibrate by remember { mutableStateOf(preferences.toolVibrateEnabled) }
    var toolMatrixDensity by remember { mutableStateOf(preferences.toolMatrixDensityEnabled) }
    var toolPowerMode by remember { mutableStateOf(preferences.toolPowerModeEnabled) }
    var toolWeather by remember { mutableStateOf(preferences.toolWeatherEnabled) }
    var toolWebSearch by remember { mutableStateOf(preferences.toolWebSearchEnabled) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        val toolItems = listOf(
            Triple("get_device_status", stringResource(R.string.settings_tool_device_status), toolDeviceStatus) to {
                toolDeviceStatus = !toolDeviceStatus
                preferences.toolDeviceStatusEnabled = toolDeviceStatus
            },
            Triple("vibrate_device", stringResource(R.string.settings_tool_vibrate), toolVibrate) to {
                toolVibrate = !toolVibrate
                preferences.toolVibrateEnabled = toolVibrate
            },
            Triple("adjust_matrix_density", stringResource(R.string.settings_tool_matrix_density), toolMatrixDensity) to {
                toolMatrixDensity = !toolMatrixDensity
                preferences.toolMatrixDensityEnabled = toolMatrixDensity
            },
            Triple("set_system_power_mode", stringResource(R.string.settings_tool_power_mode), toolPowerMode) to {
                toolPowerMode = !toolPowerMode
                preferences.toolPowerModeEnabled = toolPowerMode
            },
            Triple("query_time_weather", stringResource(R.string.settings_tool_time_weather), toolWeather) to {
                toolWeather = !toolWeather
                preferences.toolWeatherEnabled = toolWeather
            },
            Triple("web_search", stringResource(R.string.settings_tool_web_search), toolWebSearch) to {
                toolWebSearch = !toolWebSearch
                preferences.toolWebSearchEnabled = toolWebSearch
            }
        )

        WearSectionHeader(text = stringResource(R.string.settings_tools_release_header))

        toolItems.forEach { (meta, toggle) ->
            val (name, label, enabled) = meta
            SettingsChoiceRow(
                title = label,
                subtitle = name,
                value = if (enabled) stringResource(R.string.settings_tool_allowed) else stringResource(R.string.settings_tool_blocked),
                selected = enabled,
                onClick = {
                    context.vibrateTick()
                    toggle()
                }
            )
        }

        WearSectionHeader(text = stringResource(R.string.settings_mcp_protocol))

        SettingsChoiceRow(
            title = stringResource(R.string.settings_mcp_probe),
            value = if (isMcpEnabled) stringResource(R.string.settings_mcp_connected) else stringResource(R.string.settings_mcp_disabled),
            selected = isMcpEnabled,
            onClick = {
                context.vibrateTick()
                onToggleMcp(!isMcpEnabled, mcpEndpoint)
            }
        )

        BasicText(
            text = mcpEndpoint,
            style = Wear.TextMono,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
