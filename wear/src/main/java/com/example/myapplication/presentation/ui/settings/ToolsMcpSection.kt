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
            Triple("get_device_status", "电量与温度感知", toolDeviceStatus) to {
                toolDeviceStatus = !toolDeviceStatus
                preferences.toolDeviceStatusEnabled = toolDeviceStatus
            },
            Triple("vibrate_device", "马达振动提醒", toolVibrate) to {
                toolVibrate = !toolVibrate
                preferences.toolVibrateEnabled = toolVibrate
            },
            Triple("adjust_matrix_density", "表盘点阵间距", toolMatrixDensity) to {
                toolMatrixDensity = !toolMatrixDensity
                preferences.toolMatrixDensityEnabled = toolMatrixDensity
            },
            Triple("set_system_power_mode", "省电模式设置", toolPowerMode) to {
                toolPowerMode = !toolPowerMode
                preferences.toolPowerModeEnabled = toolPowerMode
            },
            Triple("query_time_weather", "时间与天气感知", toolWeather) to {
                toolWeather = !toolWeather
                preferences.toolWeatherEnabled = toolWeather
            },
            Triple("web_search", "真实网络搜索", toolWebSearch) to {
                toolWebSearch = !toolWebSearch
                preferences.toolWebSearchEnabled = toolWebSearch
            }
        )

        WearSectionHeader(text = "端侧工具放行")

        toolItems.forEach { (meta, toggle) ->
            val (name, label, enabled) = meta
            SettingsChoiceRow(
                title = label,
                subtitle = name,
                value = if (enabled) "放行" else "拦截",
                selected = enabled,
                onClick = {
                    context.vibrateTick()
                    toggle()
                }
            )
        }

        WearSectionHeader(text = "MCP 协议")

        SettingsChoiceRow(
            title = "MCP 协议探针",
            value = if (isMcpEnabled) "已连接" else "未启用",
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
