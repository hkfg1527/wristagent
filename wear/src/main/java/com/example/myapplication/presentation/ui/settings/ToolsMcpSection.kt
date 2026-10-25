package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences

/**
 * 工具集细粒度权限控制与 MCP 协议探针
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
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
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

        toolItems.forEach { (meta, toggle) ->
            val (name, label, enabled) = meta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF161A24))
                    .border(0.8.dp, Color(0xFF2C3548), RoundedCornerShape(12.dp))
                    .clickable {
                        context.vibrateTick()
                        toggle()
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                        Text(name, fontSize = 11.sp, color = TextSecondaryColor)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (enabled) Color(0xFF10B981) else Color(0xFF334155))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (enabled) "放行" else "拦截",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // MCP 协议控制项 (52dp 大尺寸触控)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2C3548), RoundedCornerShape(12.dp))
                .clickable {
                    context.vibrateTick()
                    onToggleMcp(!isMcpEnabled, mcpEndpoint)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MCP 协议探针", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Text(mcpEndpoint, fontSize = 11.sp, color = TextSecondaryColor)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isMcpEnabled) Color(0xFF10B981) else Color(0xFF334155))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (isMcpEnabled) "已连接" else "未启用",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
