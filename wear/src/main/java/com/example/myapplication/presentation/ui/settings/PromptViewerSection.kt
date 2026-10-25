package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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

/**
 * 系统提示词查看与编辑 (纯按钮化快捷人设调试)
 */
@Composable
fun PromptViewerContent(
    customPrompt: String,
    onSavePrompt: (String) -> Unit
) {
    val context = LocalContext.current
    var editMode by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(customPrompt) }
    var activePreset by remember {
        mutableStateOf(
            when {
                customPrompt.isBlank() -> "极简助手"
                customPrompt.contains("架构师") -> "架构师"
                customPrompt.contains("日常") -> "生活秘书"
                else -> "自定义"
            }
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
        // 预设角色一键调试矩阵 (2x2 大按钮)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("预设角色一键调试", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "极简助手" to "你是运行在 Android Wear OS 智能手表上的 AI 助手。支持工具调用，回答简短精准，适配手表快速浏览。",
                    "架构师" to "你是资深系统工程架构师，回答严谨客观、具备高信息密度与技术深度，杜绝虚假与客套。"
                ).forEach { (label, promptText) ->
                    val isSel = activePreset == label
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
                                activePreset = label
                                textValue = promptText
                                onSavePrompt(promptText)
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "生活秘书" to "你是贴身随行腕上助理，提供精准时间、日程与日常信息整理，语言温和凝练。",
                    "清空测试" to ""
                ).forEach { (label, promptText) ->
                    val isSel = activePreset == label
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
                                activePreset = label
                                textValue = promptText
                                onSavePrompt(promptText)
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

        // 自定义微调展开按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E2536))
                .border(0.6.dp, Color(0xFF333E54), RoundedCornerShape(10.dp))
                .clickable {
                    context.vibrateTick()
                    editMode = !editMode
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (editMode) "收起自定义编辑" else "手动修改提示词文本",
                fontSize = 12.sp,
                color = Color(0xFF94A3B8)
            )
        }

        if (editMode) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F1219))
                    .border(0.8.dp, Color(0xFF333E54), RoundedCornerShape(10.dp))
                    .padding(8.dp)
            ) {
                BasicTextField(
                    value = textValue,
                    onValueChange = {
                        textValue = it
                        onSavePrompt(it)
                        activePreset = "自定义"
                    },
                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp, lineHeight = 15.sp),
                    cursorBrush = SolidColor(AccentCyan),
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
