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
 * 系统提示词查看与编辑
 */
@Composable
fun PromptViewerContent(
    customPrompt: String,
    onSavePrompt: (String) -> Unit
) {
    val context = LocalContext.current
    var editMode by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(customPrompt) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 提示词显示容器 (12sp 清晰正文)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp)
        ) {
            if (editMode) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp, lineHeight = 16.sp),
                    cursorBrush = SolidColor(AccentCyan),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (customPrompt.isNotBlank()) customPrompt else "你是运行在 Android Wear OS 智能手表上的 AI 助手。支持工具调用，回答简短精准，适配手表快速浏览。",
                    fontSize = 12.sp,
                    color = TextPrimaryColor,
                    lineHeight = 16.sp
                )
            }
        }

        // 双操作大按钮 (42dp 高度)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF222B3C))
                    .border(0.8.dp, Color(0xFF384661), RoundedCornerShape(12.dp))
                .clickable {
                    context.vibrateTick()
                    onSavePrompt("")
                    textValue = ""
                    editMode = false
                },
                contentAlignment = Alignment.Center
            ) {
                Text("恢复默认", fontSize = 13.sp, color = TextSecondaryColor)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (editMode) AccentCyanDark else Color(0xFF222B3C))
                    .border(0.8.dp, if (editMode) AccentCyan else Color(0xFF384661), RoundedCornerShape(12.dp))
                    .clickable {
                        context.vibrateTick()
                        if (editMode) {
                            onSavePrompt(textValue)
                            editMode = false
                        } else {
                            editMode = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (editMode) "保存设定" else "编辑提示词",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
