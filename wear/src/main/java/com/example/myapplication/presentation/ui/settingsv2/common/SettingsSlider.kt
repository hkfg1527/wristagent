package com.example.myapplication.presentation.ui.settingsv2.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing

/**
 * 连续量行（点阵密度等）的滑块尾巴 —— 五套 Material 风格共用。
 *
 * 用官方 `Slider`（M3E 控件）而不是开关：`matrixSpacingDp` 是 0..64 的连续值，
 * 开关只有两态，会把可调范围直接压成 0/1。这是语义错误，不只是"不好看"。
 * 右侧显示当前值，避免只能靠滑杆位置猜数字。
 */
@Composable
fun SettingsSlider(
    trailing: RowTrailing.Slider,
    text: String?,
    color: Color,
    width: Dp = 104.dp,
    modifier: Modifier = Modifier,
    onChange: (Float) -> Unit = trailing.onChange,
) {
    Row(
        modifier = modifier.width(width),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = trailing.value,
            onValueChange = onChange,
            valueRange = trailing.range,
            steps = trailing.steps,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text ?: trailing.value.toInt().toString(),
            color = color,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
