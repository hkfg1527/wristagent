package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R

/** 预设角色正文：与旧版内联文本逐字一致，属于业务数据而非界面文案。 */
private const val PromptMinimal =
    "你是运行在 Android Wear OS 智能手表上的 AI 助手。支持工具调用，回答简短精准，适配手表快速浏览。"
private const val PromptArchitect =
    "你是资深系统工程架构师，回答严谨客观、具备高信息密度与技术深度，杜绝虚假与客套。"
private const val PromptSecretary =
    "你是贴身随行腕上助理，提供精准时间、日程与日常信息整理，语言温和凝练。"

/**
 * 系统提示词查看与编辑：预设角色改为单选行，自定义正文走输入对话框。
 * 写入回调 [onSavePrompt] 的触发时机与旧版一致——每次文本变化立即写回。
 */
@Composable
fun PromptViewerContent(
    customPrompt: String,
    onBack: () -> Unit,
    onSavePrompt: (String) -> Unit
) {
    var editorOpen by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(customPrompt) }
    // 预设名参与选中比较，先取本地化文案再进 remember，避免在非组合上下文里读资源。
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

    val presets = listOf(
        presetMinimal to PromptMinimal,
        presetArchitect to PromptArchitect,
        presetSecretary to PromptSecretary,
        stringResource(R.string.settings_preset_clear_test) to ""
    )

    fun applyPreset(label: String, prompt: String) {
        activePreset = label
        textValue = prompt
        onSavePrompt(prompt)
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_system_prompt),
        onBack = onBack,
        overlay = {
            if (editorOpen) {
                SettingsTextDialog(
                    title = stringResource(R.string.settings_system_prompt),
                    initial = textValue,
                    placeholder = stringResource(R.string.settings_prompt_placeholder),
                    onValueChange = { value ->
                        textValue = value
                        onSavePrompt(value)
                        activePreset = presetCustom
                    },
                    onDismiss = { editorOpen = false }
                )
            }
        }
    ) {
        section(stringResource(R.string.settings_prompt_presets))

        presets.forEach { (label, prompt) ->
            row {
                SettingsRadioRow(
                    label = label,
                    selected = activePreset == label,
                    onSelect = {
                        applyPreset(label, prompt)
                    }
                )
            }
        }

        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_system_prompt),
                value = if (textValue.isBlank()) {
                    stringResource(R.string.settings_prompt_default)
                } else {
                    stringResource(R.string.settings_prompt_custom)
                },
                onClick = {
                    editorOpen = true
                }
            )
        }
    }
}
