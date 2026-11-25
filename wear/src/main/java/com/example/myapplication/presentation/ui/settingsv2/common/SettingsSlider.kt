package com.example.myapplication.presentation.ui.settingsv2.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
    /** `null` = 占满给它的整行（滑块独占一行时用），否则定宽挂行尾。 */
    width: Dp? = 104.dp,
    modifier: Modifier = Modifier,
    /** 打在**内部 Slider** 上的测试标识（`SetProgress` 在 Slider 节点上，不在外层 Row 上）。 */
    sliderTag: String? = null,
    onChange: (Float) -> Unit = trailing.onChange,
) {
    Row(
        // 定宽挂行尾时标题只剩一半；独占一行时铺满卡片（真机 0.0.0.263 实测：塞行尾会把标题挤成「温…」）
        modifier = modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Slider(
            value = trailing.value,
            onValueChange = onChange,
            valueRange = trailing.range,
            steps = trailing.steps,
            modifier = Modifier.weight(1f).then(if (sliderTag != null) Modifier.testTag(sliderTag) else Modifier),
        )
        // text 为 null 时不在这行重复显示数值（第一行右侧已经显示）
        if (text != null) {
            Text(
                text = text,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/** 滑块测试标识前缀：v2-slider-<row key>。一页有多个滑块时，SetProgress 必须精确落到某一个。 */
const val SettingsV2SliderTagPrefix = "v2-slider-"