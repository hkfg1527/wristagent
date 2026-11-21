package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * TERMINAL 专用的风格选择器：**字符菜单**，不是卡片面板。
 *
 * 为什么单独做一套：点 `[ 风格 ]` 时弹出 M3 卡片面板，满屏等宽绿字突然变成 UI —— 那是这套里
 * 最破坏沉浸感的一处。这里换成 TUI 菜单：`# 页面风格` 头 + `─` 规则线 + 编号条目 + 当前项 `>`。
 *
 * 仍然遵守两条既有约束：
 * 1. 背景**全不透明**（半透明时底层文字会 ghost，真机踩过）；
 * 2. 保留 `StylePickerTag` / `StylePickerItemTagPrefix`，既有工具与测试不用改。
 */
@Composable
fun TerminalStylePicker(
    current: SettingsV2Style,
    onPick: (SettingsV2Style) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = SettingsV2Style.TERMINAL.identity
    val lineStyle = TextStyle(
        color = identity.titleColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val dimStyle = lineStyle.copy(color = identity.subtitleColor)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground)
            .testTag(StylePickerTag)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            BasicText(text = "# 页面风格", style = dimStyle, maxLines = 1)
            BasicText(text = "─".repeat(30), style = dimStyle, maxLines = 1, softWrap = false)
            SettingsV2Style.entries.forEachIndexed { index, style ->
                val mark = if (style == current) "> " else "  "
                BasicText(
                    text = "$mark${index + 1} ${style.label}",
                    style = lineStyle,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(style) }
                        .testTag(StylePickerItemTagPrefix + style.name),
                )
            }
            BasicText(text = "  esc 取消", style = dimStyle, maxLines = 1)
        }
    }
}
