package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/** 选项选择器的测试标识。 */
const val OptionPickerTag = "option-picker"

/** 单个选项的测试标识前缀（后面接选项 value）。 */
const val OptionPickerItemTagPrefix = "option-item-"

/** 取消入口的测试标识。 */
const val OptionPickerDismissTag = "option-dismiss"

/**
 * 通用选项选择器：点开一行"从几个选项里选一个"时用。
 *
 * 与 [TerminalStylePicker]（终端专用的字符菜单）不同，这个服务于四套 Material 风格：
 * 顶部标题、中间可选列表、当前项加 `> ` 与 `*` 标记，点空白关闭。
 * 背景全不透明，避免底层文字 ghost（真机踩过）。
 */
@Composable
fun OptionPicker(
    title: String,
    choice: RowChoice,
    colors: StyleIdentity,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .testTag(OptionPickerTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = title,
                color = colors.titleColor,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            choice.options.forEach { option ->
                val selected = option.value == choice.selectedValue
                Text(
                    text = (if (selected) "> " else "  ") + option.label + (if (selected) "  *" else ""),
                    color = if (selected) colors.titleColor else colors.subtitleColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .testTag(OptionPickerItemTagPrefix + option.value)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onPick(option.value) },
                )
            }
            // 取消入口：不用"整屏空白点击关闭"——那个全屏 clickable 会抢走子项命中（交互测试实测）。
            Text(
                text = "  取消",
                color = colors.subtitleColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp)
                    .testTag(OptionPickerDismissTag)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            )
        }
    }
}
