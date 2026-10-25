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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import kotlinx.coroutines.launch

/**
 * 模型与端点配置内容 (纯按钮化直观调试面板)
 */
@Composable
fun ModelConfigContent(
    preferences: AgentPreferences,
    currentModel: String,
    currentBaseUrl: String,
    onSelect: (model: String, baseUrl: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var inputBaseUrl by remember { mutableStateOf(currentBaseUrl) }
    var inputApiKey by remember { mutableStateOf(preferences.apiKey) }
    var showCustomInput by remember { mutableStateOf(false) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchResultText by remember { mutableStateOf<String?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 模型一键调试切换按钮矩阵 (2x2 大按钮，一键调试)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("模型直选调试", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)

            // 第一行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "gemini-2.5-flash" to "Flash 极速",
                    "gemini-2.5-pro" to "Pro 深度"
                ).forEach { (modelId, label) ->
                    val isSel = currentModel == modelId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Color(0xFF0284C7) else Color(0xFF222B3C))
                            .border(
                                1.dp,
                                if (isSel) AccentCyan else Color(0xFF384661),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                onSelect(modelId, inputBaseUrl)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // 第二行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "deepseek-chat" to "DeepSeek 对话",
                    "deepseek-reasoner" to "DeepSeek 推理"
                ).forEach { (modelId, label) ->
                    val isSel = currentModel == modelId
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Color(0xFF0284C7) else Color(0xFF222B3C))
                            .border(
                                1.dp,
                                if (isSel) AccentCyan else Color(0xFF384661),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                onSelect(modelId, inputBaseUrl)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // 2. 端点一键切换按钮组
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("API 端点调试", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "https://api.deepseek.com" to "DeepSeek",
                    "https://generativelanguage.googleapis.com" to "Google"
                ).forEach { (url, name) ->
                    val isSel = inputBaseUrl == url
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Color(0xFF1E3A5F) else Color(0xFF222B3C))
                            .border(
                                0.8.dp,
                                if (isSel) AccentCyan else Color(0xFF384661),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                inputBaseUrl = url
                                preferences.baseUrl = url
                                onSelect(currentModel, url)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            // 自定义输入切换按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2430))
                    .border(0.6.dp, Color(0xFF333E54), RoundedCornerShape(8.dp))
                    .clickable {
                        context.vibrateTick()
                        showCustomInput = !showCustomInput
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (showCustomInput) "收起自定义输入" else "手动修改 BaseUrl / Key",
                    fontSize = 11.5.sp,
                    color = Color(0xFF94A3B8)
                )
            }

            if (showCustomInput) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicTextField(
                        value = inputBaseUrl,
                        onValueChange = {
                            inputBaseUrl = it
                            preferences.baseUrl = it
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F1219))
                            .border(0.6.dp, Color(0xFF333E54), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    )

                    BasicTextField(
                        value = inputApiKey,
                        onValueChange = {
                            inputApiKey = it
                            preferences.apiKey = it
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0F1219))
                            .border(0.6.dp, Color(0xFF333E54), RoundedCornerShape(6.dp))
                            .padding(6.dp)
                    )
                }
            }
        }

        // 3. 测速与握手 Ping 调试按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isFetching) Color(0xFF222B3A) else Color(0xFF1E3A5F))
                .border(0.8.dp, if (isFetching) Color(0xFF334155) else AccentCyan, RoundedCornerShape(12.dp))
                .clickable(enabled = !isFetching) {
                    context.vibrateTick()
                    isFetching = true
                    fetchResultText = "正在握手探测..."
                    scope.launch {
                        val (timeMs, models) = AgentDispatcher.fetchRemoteModels(inputBaseUrl, inputApiKey)
                        isFetching = false
                        if (timeMs >= 0) {
                            fetchResultText = "端点就绪 • 延迟 ${timeMs}ms (${models.size} 个可用模型)"
                            if (models.isNotEmpty()) {
                                preferences.cachedRemoteModels = models.joinToString(",")
                            }
                        } else {
                            fetchResultText = "端点握手失败，网络超时"
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFetching) "正在 Ping 测试..." else "端点测速与实时握手 Ping",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFetching) Color(0xFF94A3B8) else Color.White
            )
        }

        fetchResultText?.let {
            Text(it, fontSize = 11.5.sp, color = AccentCyan)
        }
    }
}
