package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
 * 推理超参数调优控制台 (大触控区域、物理振动步进器、2x2 Token 网格)
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
        // 1. Temperature 步进微调器 (大尺寸双按钮，触觉马达回馈)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 减少按钮 (46dp 标准大触控)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
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

                    // 增加按钮 (46dp 标准大触控)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
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
            }
        }

        // 2. Max Output Tokens (2x2 舒适网格替代原有拥挤的 4 项一排)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("最大输出长度限制", fontSize = 12.sp, color = TextSecondaryColor)

                // 第一行 (150, 300)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(150 to "150 节能", 300 to "300 适中").forEach { (tokens, label) ->
                        val isSel = maxTokens == tokens
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF1E293B) else Color(0xFF222B3C))
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

                // 第二行 (600, 1200)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(600 to "600 完整", 1200 to "1200 极长").forEach { (tokens, label) ->
                        val isSel = maxTokens == tokens
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) Color(0xFF1E293B) else Color(0xFF222B3C))
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

        // 3. 流式传输开关 (48dp 大尺寸触控)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .clickable {
                    context.vibrateTick()
                    isStream = !isStream
                    preferences.streamMode = isStream
                    notifyChange()
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("真实 SSE 流式传输", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryColor)
                    Text(
                        text = if (isStream) "首字即刻上屏 (推荐)" else "整段生成后一次性返回",
                        fontSize = 11.sp,
                        color = TextSecondaryColor
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isStream) AccentCyanDark else Color(0xFF334155))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isStream) "已开启" else "已关闭",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
