package com.example.myapplication.presentation.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 开关行与其行尾胶囊开关。
 *
 * 从 `SettingsShared.kt` 拆出来：开关是"一个布尔值 + 一个 44×26dp 控件"的完整小部件，
 * 与设置页的排版、卡片、滑杆都不相干。
 */
/** 开关行：左槽标签 + 右槽 44×26dp 胶囊开关；不再使用会吞掉整行宽度的 SwitchButton。 */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(secondary),
    /** 副标题行数上限：[SettingsSplitToggleRow] 用 2 行，其余行铺满（MAX_VALUE）。 */
    subtitleMaxLines: Int = Int.MAX_VALUE
) {
    SettingsSlotRow(
        title = label,
        subtitle = secondary,
        subtitleMaxLines = subtitleMaxLines,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = { SettingsCompactSwitch(checked = checked) }
    )
}

@Composable
private fun SettingsCompactSwitch(checked: Boolean) {
    val stateLabel = stringResource(
        if (checked) R.string.settings_switch_on else R.string.settings_switch_off
    )
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Wear.Motion.micro(),
        label = "settingsSwitchThumb"
    )
    val shape = CircleShape
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(shape)
            .background(if (checked) Wear.Accent.copy(alpha = 0.82f) else Wear.Palette.SurfaceAlt)
            .border(1.dp, if (checked) Wear.Accent else Wear.Palette.Border, shape)
            .semantics {
                role = Role.Switch
                stateDescription = stateLabel
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (4f + 18f * progress).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Wear.Palette.Orb else Wear.Palette.TextSecondary)
        )
    }
}

/**
 * 二值枚举行（分段开关语义）：与 [SettingsSwitchRow] **同一份实现**，只把副标题行数收到 2 行。
 *
 * 历史注释说这里走的是官方 `TextToggleButton` —— 实际不是：Wear M3 1.7.1 的
 * `TextToggleButton` 是整宽胶囊，会吞掉整行，右槽位置就没了。这里用的是行尾 44×26dp 的手搓开关，
 * 与 [SettingsSwitchRow] 完全一致。2026-11-17 审查把这份复制出来的函数体收成一次委托。
 */
@Composable
fun SettingsSplitToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(secondary)
) {
    SettingsSwitchRow(
        label = label,
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        secondary = secondary,
        subtitleBelowRow = subtitleBelowRow,
        subtitleMaxLines = 2
    )
}
