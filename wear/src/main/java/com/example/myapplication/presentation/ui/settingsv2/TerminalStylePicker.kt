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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.presentation.ui.settingsv2.common.RotaryBus
import com.example.myapplication.presentation.ui.settingsv2.style.TerminalLineHeight
import com.example.myapplication.presentation.ui.settingsv2.style.rotaryLinesToScroll
import androidx.compose.ui.platform.LocalDensity

/**
 * 菜单光标移动：把"当前下标"按行步进夹在合法区间内（纯函数，便于单测）。
 */
internal fun movePickerCursor(index: Int, lines: Int, size: Int): Int =
    if (size <= 0) 0 else (index + lines).coerceIn(0, size - 1)

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

    // 表冠 / Enter 也必须能用在这个菜单上。
    //
    // 真机实测过的漏洞：菜单打开后音量键被 Activity 吞掉（阀门是终端页打开的），但菜单本身不订阅，
    // 于是"按了没反应"，而 Enter 反而落到菜单背后的终端上去执行行。所以这里自己接管两条通道。
    val size = SettingsV2Style.entries.size
    var cursor by remember(current) { mutableStateOf(current.ordinal.coerceIn(0, size - 1)) }
    val linePx = with(LocalDensity.current) { TerminalLineHeight.toPx() }
    DisposableEffect(Unit) {
        val previous = RotaryBus.enabled
        RotaryBus.enabled = true
        onDispose { RotaryBus.enabled = previous }
    }
    LaunchedEffect(Unit) {
        var acc = 0f
        RotaryBus.events.collect { delta ->
            val (next, lines) = rotaryLinesToScroll(acc, delta, linePx)
            acc = next
            if (lines != 0) cursor = movePickerCursor(cursor, lines, size)
        }
    }
    LaunchedEffect(Unit) {
        RotaryBus.activations.collect {
            SettingsV2Style.entries.getOrNull(cursor)?.let(onPick)
        }
    }
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
                // `>` 跟着**光标**走（表冠可移动），当前生效的风格用尾部 `*` 标注 ——
                // 两者是不同概念：光标是"你正指着哪一项"，`*` 是"现在用的是哪一项"。
                val mark = if (index == cursor) "> " else "  "
                val currentMark = if (style == current) " *" else ""
                BasicText(
                    text = "$mark${index + 1} ${style.label}$currentMark",
                    style = if (index == cursor) lineStyle.copy(color = identity.pageBackground, background = identity.titleColor) else lineStyle,
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
