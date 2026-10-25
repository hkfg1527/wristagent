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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import kotlin.math.roundToInt

/**
 * 推理超参数调优控制台 (全按钮化直观调试)
 */
@Composable
fun HyperParamsContent(
    preferences: AgentPreferences,
    onParamsChanged: ((temp: Float, maxTokens: Int, stream: Boolean) -> Unit)? = null
) {
    val context = LocalContext.current
    var temp by remember { mutableFloatStateOf(preferences.temperature) }
    var maxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var isStream by remember { mutableStateOf(preferences.streamMode) }

    fun notifyChange() {
        onParamsChanged?.invoke(temp, maxTokens, isStream)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Temperature 步进与预设调试按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("采样温度 (Temperature)", fontSize = 12.sp, color = TextSecondaryColor)
                    Text(
                        text = "%.1f".format(temp),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan
                    )
                }

                // 步进按钮 (46dp 标准大触控)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222B3C))
                            .border(0.8.dp, Color(0xFF384661), RoundedCornerShape(12.dp))
                            .clickable {
                                context.vibrateTick()
                                val next = (temp - 0.1f).coerceIn(0.0f, 1.5f)
                                temp = (next * 10).roundToInt() / 10f
                                preferences.temperature = temp
                                notifyChange()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "− 0.1",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryColor
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222B3C))
                            .border(0.8.dp, Color(0xFF384661), RoundedCornerShape(12.dp))
                            .clickable {
                                context.vibrateTick()
                                val next = (temp + 0.1f).coerceIn(0.0f, 1.5f)
                                temp = (next * 10).roundToInt() / 10f
                                preferences.temperature = temp
                                notifyChange()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+ 0.1",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryColor
                        )
                    }
                }

                // 温度一键调试预设按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        0.2f to "极速 0.2",
                        0.7f to "平衡 0.7",
                        1.2f to "发散 1.2"
                    ).forEach { (presetTemp, label) ->
                        val isSel = (temp * 10).roundToInt() == (presetTemp * 10).roundToInt()
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) Color(0xFF0284C7) else Color(0xFF1E2536))
                            .border(
                                0.6.dp,
                                if (isSel) AccentCyan else Color(0xFF333E54),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                temp = presetTemp
                                preferences.temperature = presetTemp
                                notifyChange()
                            },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }

        // 2. Max Output Tokens (2x2 大药丸按钮网格)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("最大输出 Token 调试", fontSize = 12.sp, color = TextSecondaryColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(150 to "150 节能", 300 to "300 适中").forEach { (tokens, label) ->
                        val isSel = maxTokens == tokens
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF0284C7) else Color(0xFF222B3C))
                                .border(
                                    width = if (isSel) 1.2.dp else 0.6.dp,
                                    color = if (isSel) AccentCyan else Color(0xFF384661),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    context.vibrateTick()
                                    maxTokens = tokens
                                    preferences.maxOutputTokens = tokens
                                    notifyChange()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(600 to "600 完整", 1200 to "1200 深度").forEach { (tokens, label) ->
                        val isSel = maxTokens == tokens
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF0284C7) else Color(0xFF222B3C))
                                .border(
                                    width = if (isSel) 1.2.dp else 0.6.dp,
                                    color = if (isSel) AccentCyan else Color(0xFF384661),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    context.vibrateTick()
                                    maxTokens = tokens
                                    preferences.maxOutputTokens = tokens
                                    notifyChange()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }
        }

        // 3. 流式传输一键切换大按钮组
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("传输协议调试", fontSize = 12.sp, color = TextSecondaryColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isStream) Color(0xFF0284C7) else Color(0xFF222B3C))
                            .border(
                                1.dp,
                                if (isStream) AccentCyan else Color(0xFF384661),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                isStream = true
                                preferences.streamMode = true
                                notifyChange()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "开启 SSE 流式",
                            fontSize = 12.sp,
                            fontWeight = if (isStream) FontWeight.Bold else FontWeight.Medium,
                            color = if (isStream) Color.White else Color(0xFFCBD5E1)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isStream) Color(0xFF0284C7) else Color(0xFF222B3C))
                            .border(
                                1.dp,
                                if (!isStream) AccentCyan else Color(0xFF384661),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                isStream = false
                                preferences.streamMode = false
                                notifyChange()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "关闭流式",
                            fontSize = 12.sp,
                            fontWeight = if (!isStream) FontWeight.Bold else FontWeight.Medium,
                            color = if (!isStream) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}
