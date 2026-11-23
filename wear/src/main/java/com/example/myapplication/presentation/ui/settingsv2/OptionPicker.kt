package com.example.myapplication.presentation.ui.settingsv2

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape

/** 选项选择器的测试标识。 */
const val OptionPickerTag = "option-picker"

/** 单个选项的测试标识前缀（后面接选项 value）。 */
const val OptionPickerItemTagPrefix = "option-item-"

/** 取消入口的测试标识。 */
const val OptionPickerDismissTag = "option-dismiss"

/**
 * 选项选择器：点开一行"从几个选项里选一个"时用。
 *
 * **必须跟外层风格统一**（用户原话："我点开里面的子页面，应该也跟外面的风格统一"）：
 * - TERMINAL：字符菜单（`# 温度` + `> 0.0`），和终端页同一套语法；
 * - 其余四套：圆角卡片面板 + M3 文本，跟随该风格的配色与圆角。
 */
@Composable
fun OptionPicker(
    title: String,
    choice: RowChoice,
    style: SettingsV2Style,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val identity = style.identity
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground)
            .testTag(OptionPickerTag),
    ) {
        if (style == SettingsV2Style.TERMINAL) {
            TerminalOptionMenu(title, choice, identity, onPick, onDismiss)
        } else {
            CardOptionPanel(title, choice, identity, onPick, onDismiss)
        }
    }
}

/** 终端档：字符菜单，与终端页同一套语法（`#` 头、`>` 当前项、`─` 规则线）。 */
@Composable
private fun TerminalOptionMenu(
    title: String,
    choice: RowChoice,
    identity: StyleIdentity,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val line = TextStyle(
        color = identity.titleColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    )
    val dim = line.copy(color = identity.subtitleColor)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 30.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        BasicText("# $title", style = dim, maxLines = 1)
        BasicText("─".repeat(24), style = dim, maxLines = 1, softWrap = false)
        choice.options.forEach { option ->
            val selected = option.value == choice.selectedValue
            BasicText(
                text = (if (selected) "> " else "  ") + option.label,
                style = if (selected) line else dim,
                maxLines = 1,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag(OptionPickerItemTagPrefix + option.value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(option.value) },
            )
        }
        BasicText(
            text = "  esc 取消",
            style = dim,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .testTag(OptionPickerDismissTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
    }
}

/** 四套 Material 档：圆角卡片面板，跟随该风格的 surface / 圆角 / 配色。 */
@Composable
private fun CardOptionPanel(
    title: String,
    choice: RowChoice,
    identity: StyleIdentity,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 竖直内边距必须够大：卡片顶部落在圆弧窄区会被切角（真机截图实测）。44dp 让它整块进圆内。
            .padding(horizontal = 20.dp, vertical = 44.dp),
        shape = RoundedCornerShape(identity.cornerRadius.coerceAtLeast(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (identity.surface == Color.Transparent) Color(0xFF1F2429) else identity.surface,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = identity.titleColor,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        choice.options.forEach { option ->
            val selected = option.value == choice.selectedValue
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .testTag(OptionPickerItemTagPrefix + option.value)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onPick(option.value) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (selected) "✓" else " ",
                    color = identity.titleColor,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = option.label,
                    color = if (selected) identity.titleColor else identity.subtitleColor,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Text(
            text = "取消",
            color = identity.subtitleColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .testTag(OptionPickerDismissTag)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
        )
    }
}
