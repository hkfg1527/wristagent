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
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
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
    // 预设名参与 chip 选中比较，先取本地化文案再进 remember，避免在非组合上下文里读资源。
    val presetMinimal = stringResource(R.string.settings_preset_minimal)
    val presetArchitect = stringResource(R.string.settings_preset_architect)
    val presetSecretary = stringResource(R.string.settings_preset_secretary)
    val presetCustom = stringResource(R.string.settings_prompt_custom)
    var activePreset by remember {
        mutableStateOf(
            when {
                customPrompt.isBlank() -> presetMinimal
                customPrompt.contains("架构师") -> presetArchitect
                customPrompt.contains("日常") -> presetSecretary
                else -> presetCustom
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
        WearSectionHeader(text = stringResource(R.string.settings_prompt_presets))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(
                presetMinimal to "你是运行在 Android Wear OS 智能手表上的 AI 助手。支持工具调用，回答简短精准，适配手表快速浏览。",
                presetArchitect to "你是资深系统工程架构师，回答严谨客观、具备高信息密度与技术深度，杜绝虚假与客套。"
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
                presetSecretary to "你是贴身随行腕上助理，提供精准时间、日程与日常信息整理，语言温和凝练。",
                stringResource(R.string.settings_preset_clear_test) to ""
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
            label = if (editMode) stringResource(R.string.settings_prompt_edit_hide) else stringResource(R.string.settings_prompt_edit_show),
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
                    activePreset = presetCustom
                },
                placeholder = stringResource(R.string.settings_prompt_placeholder),
                modifier = Modifier.heightIn(min = 96.dp)
            )
        }

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
