package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 单选行与它的圆点。
 *
 * 从 `SettingsShared.kt` 拆出来：单选是"一个选项 + 一颗圆点"的完整小部件，
 * 子项版（[SettingsSubRadioRow]）只是把 `subItem` 打开的那一份委托。
 */
/**
 * 单选行：标签左槽 + 行尾圆点，不用官方 RadioButton（它自带另一套内边距与左对齐）。
 *
 * [dangerSelected] 只染**选中态那个圆点**：删数据 / 全权限 / 从不确认这类选项，
 * 危险语义落在控件本身上，不涂整行文字。
 *
 * 副标题带机器标识符（模型名 / endpoint）时传 [subtitleMaxLines] = 1：
 * 单行 + 尾部省略号，绝不把标识符从词中间劈成两行。
 *
 * [subtitleBelowRow] = true 时副标题改画在"标签 + 圆点"那一行的下方，横向独占整行内宽；
 * 行尾圆点因此不再从副标题嘴里分走 18dp + 10dp。默认 false，别的页一行都不用改。
 */
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    subtitleStatus: SettingsStatusTone? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(subtitle),
    dangerSelected: Boolean = false,
    dotColor: Color? = null,
    /** 子项：左槽线右移 12dp，字号与右槽线不变（[SettingsSubRadioRow] 就是它的一份薄封装）。 */
    subItem: Boolean = false
) {
    // 官方档：整行交给官方 RadioButton（会丢掉 dangerSelected / dotColor，差异见 SettingsOfficialControls 注释）。
    if (SettingsStylePolicy.current().radio == SettingsRadioControl.OFFICIAL_RADIO_BUTTON) {
        OfficialRadioRow(
            label = label,
            secondary = subtitle,
            selected = selected,
            onSelect = onSelect,
            modifier = modifier
        )
        return
    }
    SettingsSlotRow(
        title = label,
        subtitle = subtitle,
        subtitleMaxLines = subtitleMaxLines,
        subtitleStatus = subtitleStatus,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier,
        onClick = onSelect,
        subItem = subItem,
        trailing = {
            SettingsRadioDot(selected = selected, danger = dangerSelected, dotColor = dotColor)
        }
    )
}

/**
 * 单选圆点：空心环 + 选中实心点，只表达状态，不做容器。
 *
 * 全页强调色的唯一落点就在"选中"这一件事上：未选中是中性 TextMuted，选中是运行时强调色
 * （危险选项为 Danger，且只染这一点）。[dotColor] 只给强调色档用：未选中时留一颗该档颜色
 * 的小点做预览，环仍是中性色 —— "选中"和"这个颜色长什么样"因此不会互相冒充。
 */
@Composable
private fun SettingsRadioDot(selected: Boolean, danger: Boolean = false, dotColor: Color? = null) {
    val selectedColor = dotColor ?: (if (danger) Wear.Palette.Danger else Wear.Accent)
    val ring = if (selected) selectedColor else Wear.Palette.TextMuted
    Box(
        modifier = Modifier
            .size(SettingsRadioDotSize)
            .clip(CircleShape)
            .border(SettingsRadioStroke, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(SettingsRadioDotSize / 2)
                    .clip(CircleShape)
                    .background(selectedColor)
            )
        } else if (dotColor != null) {
            // 未选中的预设色只留一颗小色点做预览：环仍是中性色，"选中"和"这个颜色长什么样"不互相冒充
            Box(
                modifier = Modifier
                    .size(SettingsRadioDotSize / 3)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = SettingsPreviewDotAlpha))
            )
        }
    }
}

private val SettingsRadioDotSize = 18.dp
private val SettingsRadioStroke = 2.dp

/** 未选中预设色点的预览透明度：够看清颜色，又不至于被误读成"已选中"。 */
private const val SettingsPreviewDotAlpha = 0.75f

/** 子项单选行：与 [SettingsRadioRow] 同一套圆点，只把左槽线右移 12dp（字号与右槽线不变）。 */
@Composable
fun SettingsSubRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(subtitle),
    dangerSelected: Boolean = false,
    dotColor: Color? = null
) {
    // 一份实现 + 一个参数，不再复制整段（2026-11-17 审查：这两行曾是逐字重复的两份）
    SettingsRadioRow(
        label = label,
        selected = selected,
        onSelect = onSelect,
        modifier = modifier,
        subtitle = subtitle,
        subtitleMaxLines = subtitleMaxLines,
        subtitleBelowRow = subtitleBelowRow,
        dangerSelected = dangerSelected,
        dotColor = dotColor,
        subItem = true
    )
}
