package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 系统提示词查看与编辑：预设角色直选 + 自定义文本微调。
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
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = "预设角色")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(
                "极简助手" to "你是运行在 Android Wear OS 智能手表上的 AI 助手。支持工具调用，回答简短精准，适配手表快速浏览。",
                "架构师" to "你是资深系统工程架构师，回答严谨客观、具备高信息密度与技术深度，杜绝虚假与客套。"
            ).forEach { (label, promptText) ->
                WearChip(
                    label = label,
                    selected = activePreset == label,
                    onClick = {
                        context.vibrateTick()
                        activePreset = label
                        textValue = promptText
                        onSavePrompt(promptText)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(
                "生活秘书" to "你是贴身随行腕上助理，提供精准时间、日程与日常信息整理，语言温和凝练。",
                "清空测试" to ""
            ).forEach { (label, promptText) ->
                WearChip(
                    label = label,
                    selected = activePreset == label,
                    onClick = {
                        context.vibrateTick()
                        activePreset = label
                        textValue = promptText
                        onSavePrompt(promptText)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SettingsActionButton(
            label = if (editMode) "收起自定义编辑" else "手动修改提示词文本",
            onClick = {
                context.vibrateTick()
                editMode = !editMode
            }
        )

        if (editMode) {
            SettingsInput(
                value = textValue,
                onValueChange = {
                    textValue = it
                    onSavePrompt(it)
                    activePreset = "自定义"
                },
                placeholder = "输入系统提示词",
                modifier = Modifier.heightIn(min = 96.dp)
            )
        }

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
