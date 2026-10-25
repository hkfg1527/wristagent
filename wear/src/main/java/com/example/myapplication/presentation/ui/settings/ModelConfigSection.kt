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
 * 模型与端点配置内容 (支持实时拉取端点与测速)
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
    var isEditingUrl by remember { mutableStateOf(false) }
    var isEditingKey by remember { mutableStateOf(false) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchResultText by remember { mutableStateOf<String?>(null) }
    var fetchedModels by remember {
        mutableStateOf(
            preferences.cachedRemoteModels.split(",").filter { it.isNotBlank() }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Base URL 配置项 (触控区域提升至 44dp+)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF181C26))
                .border(0.8.dp, Color(0xFF2C3547), RoundedCornerShape(12.dp))
                .clickable {
                    context.vibrateTick()
                    isEditingUrl = !isEditingUrl
                }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("API Base URL", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                    Text(if (isEditingUrl) "收起" else "编辑", fontSize = 11.sp, color = TextSecondaryColor)
                }
                if (isEditingUrl) {
                    BasicTextField(
                        value = inputBaseUrl,
                        onValueChange = {
                            inputBaseUrl = it
                            preferences.baseUrl = it
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Text(inputBaseUrl, fontSize = 12.sp, color = TextPrimaryColor, maxLines = 1)
                }
            }
        }

        // API Key 状态项 (44dp+ 触控)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF181C26))
                .border(0.8.dp, Color(0xFF2C3547), RoundedCornerShape(12.dp))
                .clickable {
                    context.vibrateTick()
                    isEditingKey = !isEditingKey
                }
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("API Key", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AccentCyan)
                    Text(if (isEditingKey) "收起" else "编辑", fontSize = 11.sp, color = TextSecondaryColor)
                }
                if (isEditingKey) {
                    BasicTextField(
                        value = inputApiKey,
                        onValueChange = {
                            inputApiKey = it
                            preferences.apiKey = it
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                        cursorBrush = SolidColor(AccentCyan),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                } else {
                    val masked = if (inputApiKey.length > 8) {
                        "${inputApiKey.take(4)}...${inputApiKey.takeLast(4)}"
                    } else if (inputApiKey.isNotBlank()) {
                        "••••••••"
                    } else {
                        "未配置 API Key"
                    }
                    Text(masked, fontSize = 12.sp, color = TextPrimaryColor)
                }
            }
        }

        // 测速并拉取模型按钮 (44dp 触控标准)
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
                    fetchResultText = "探测端点中..."
                    scope.launch {
                        val (timeMs, models) = AgentDispatcher.fetchRemoteModels(inputBaseUrl, inputApiKey)
                        isFetching = false
                        if (timeMs >= 0 && models.isNotEmpty()) {
                            fetchResultText = "探测成功 • 延迟 ${timeMs}ms"
                            fetchedModels = models
                            preferences.cachedRemoteModels = models.joinToString(",")
                        } else {
                            fetchResultText = "端点握手失败，使用预置"
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFetching) "正在连接端点..." else "测速并实时拉取模型",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isFetching) Color(0xFF94A3B8) else Color.White
            )
        }

        fetchResultText?.let {
            Text(it, fontSize = 11.sp, color = AccentCyan)
        }

        Text(
            text = "可用模型 (点击即选)：",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondaryColor,
            modifier = Modifier.align(Alignment.Start)
        )

        val displayList = if (fetchedModels.isNotEmpty()) {
            fetchedModels
        } else {
            listOf("deepseek-chat", "deepseek-reasoner", "gemini-2.5-flash", "claude-3-5-sonnet")
        }

        displayList.forEach { modelId ->
            val isSelected = currentModel == modelId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF141720))
                    .border(
                        width = if (isSelected) 1.2.dp else 0.6.dp,
                        color = if (isSelected) AccentCyan else Color(0xFF2C3547),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable {
                        context.vibrateTick()
                        onSelect(modelId, inputBaseUrl)
                    }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = modelId,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                    )
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AccentCyanDark)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("生效中", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
