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
    // 预设名参与选中比较，先取本地化文案再进比较，避免在非组合上下文里读资源。
    val presetMinimal = stringResource(R.string.settings_preset_minimal)
    val presetArchitect = stringResource(R.string.settings_preset_architect)
    val presetSecretary = stringResource(R.string.settings_preset_secretary)
    val presetClear = stringResource(R.string.settings_preset_clear_test)

    val rolePresets = listOf(
        presetMinimal to PromptMinimal,
        presetArchitect to PromptArchitect,
        presetSecretary to PromptSecretary
    )
    // 选中态由当前正文精确反推：点哪一行就亮哪一行，正文被手改过就一行都不亮。
    // 用 contains 猜会在正文里恰好出现"架构师"时误判；更糟的是极简助手正文永远匹配不上，
    // 而空正文（初始态 / 预置默认）反倒把极简助手点亮 —— 屏上的选中项与真值完全相反。
    val selectedPreset = rolePresets.firstOrNull { it.second == customPrompt }?.first

    SettingsSubPage(
        title = stringResource(R.string.settings_system_prompt),
        onBack = onBack,
        overlay = {
            if (editorOpen) {
                SettingsTextDialog(
                    title = stringResource(R.string.settings_system_prompt),
                    initial = customPrompt,
                    placeholder = stringResource(R.string.settings_prompt_placeholder),
                    onValueChange = { value -> onSavePrompt(value) },
                    onDismiss = { editorOpen = false }
                )
            }
        }
    ) {
        section(stringResource(R.string.settings_prompt_presets))

        // 清空行是把正文写空的动作，不是一种"当前取值"：正文为空时它是初始态，不该高亮成已选
        (rolePresets + (presetClear to "")).forEach { (label, prompt) ->
            row {
                SettingsRadioRow(
                    label = label,
                    selected = selectedPreset == label,
                    onSelect = { onSavePrompt(prompt) }
                )
            }
        }

        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_system_prompt),
                value = if (customPrompt.isBlank()) {
                    stringResource(R.string.settings_prompt_default)
                } else {
                    stringResource(R.string.settings_prompt_custom)
                },
                onClick = { editorOpen = true }
            )
        }

        // 角色卡 / 提示词库 / 开场白是多记录列表与长文本：表端只读，并把去向写在行里
        section(stringResource(R.string.settings_web_only_header))

        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_role_cards_web)
            )
        }
        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_prompt_library_web)
            )
        }
        row {
            SettingsWebOnlyRow(
                form = WebSettingsForm.WEB_ONLY,
                raw = stringResource(R.string.settings_opening_line_web)
            )
        }
    }
}
