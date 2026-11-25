package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.clickable
import com.example.myapplication.presentation.ui.settingsv2.common.CircleFit
import androidx.wear.compose.material3.Slider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsSlider
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.enterAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEnterProgress
import com.example.myapplication.presentation.ui.settingsv2.isActionable

/**
 * `M3_CARD`：**一行一张小卡**，圆屏上把横向面积吃满。
 *
 * 旧版把 13 个设置项塞进 4 张 140dp 大卡，首屏只 3 行，值还被压成 12px ——
 * 那是"把清单做成海报"，不是设置页。现在：
 * - 每个设置项一张独立卡（最小 44dp），一屏 5~6 项，信息密度翻倍；
 * - 满宽 + 官方 `SurfaceTransformation`：中间项满宽，滚到圆弧边缘自动缩小贴合，
 *   这才是 edge-hugging 的意义（定宽 + clipToBounds 把它废掉了）；
 * - 分区标题降为 12px 眉标，不再与设置项争夺视觉焦点；
 * - 「页面风格」行本身就是换风格入口，底部不再挂按钮。
 */
@Composable
fun M3CardSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
    onRowClick: ((SettingsV2Row) -> Unit)? = null,
    /** 滑块连续回调：key + 新值（写回偏好后重组）。 */
    onSlider: ((String, Float) -> Unit)? = null,
) {
    val identity = SettingsV2Style.M3_CARD.identity
    val shape = RoundedCornerShape(identity.cornerRadius)
    val transformationSpec = rememberTransformationSpec()
    val state = rememberTransformingLazyColumnState()

    // 拍平成一个列表：分区眉标 + 该项一张小卡。这样一屏能同时看到更多设置项。
    data class Item(val header: String? = null, val row: SettingsV2Row? = null, val key: String)
    val items = buildList {
        sections.forEach { section ->
            add(Item(header = section.title, key = "h-" + section.title))
            section.rows.forEach { row -> add(Item(row = row, key = row.key)) }
        }
    }

    // Box 只为给「右缘位置指示器」一个对齐容器：列表本身仍占满全屏。
    Box(modifier = modifier.fillMaxSize()) {
    TransformingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        // 磁滑吸附（守卫 SettingsV2ArchitectureGuardTest 会检查这条）
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items, key = { it.key }) { item ->
            val motion = SettingsV2Motions.of(SettingsV2Style.M3_CARD)
            val enter = rememberEnterProgress(items.indexOf(item), motion)
            // 每一项都过 CircleFit：中间吃满圆屏弦长，滚到上下两端自动收边，
            // 不再被圆弧切掉（真机截图里 banner/底栏被切就是这个原因）。
            CircleFit(modifier = Modifier.fillMaxWidth()) {
                if (item.header != null) {
                    Text(
                        text = item.header,
                        color = identity.subtitleColor,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 6.dp, top = 6.dp, bottom = 2.dp)
                            .enterAppearance(enter, motion),
                    )
                } else {
                    val row = item.row
                    if (row != null) {
                        Card(
                            onClick = { if (row.isActionable()) onRowClick?.invoke(row) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 40.dp)
                                .testTag("m3card-row")
                                .enterAppearance(enter, motion),
                            shape = shape,
                            colors = CardDefaults.cardColors(containerColor = identity.surface),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            M3CardRow(
                                row = row,
                                sectionTitle = sections.firstOrNull { s -> s.rows.any { it.key == row.key } }?.title,
                                onSlider = onSlider,
                                titleColor = identity.titleColor,
                                valueColor = identity.subtitleColor,
                            )
                        }
                    }
                }
            }
        }
    }
        // 右缘位置指示器：官方 `ScrollIndicator` 的 `TransformingLazyColumnState` 重载
        // （1.7.1 里确实有这个重载，不是把 ScalingLazyListState 硬塞进来）。
        ScrollIndicator(state = state, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** 分区 → 强调色（彩色图标 = 用户要的"视觉中心"）。 */
private fun accentOf(section: String?): androidx.compose.ui.graphics.Color = when (section) {
    "连得上" -> androidx.compose.ui.graphics.Color(0xFF30D158)
    "让它能做事" -> androidx.compose.ui.graphics.Color(0xFF0A84FF)
    "让它听懂我" -> androidx.compose.ui.graphics.Color(0xFFFF9F0A)
    "让它答得好" -> androidx.compose.ui.graphics.Color(0xFFFF375F)
    else -> androidx.compose.ui.graphics.Color(0xFF8E8E93)
}

/** 分区 → 图标字符（没有图形资源时用字符画圆标，效果等同应用图标）。 */
private fun glyphOf(section: String?): String = when (section) {
    "连得上" -> "连"
    "让它能做事" -> "做"
    "让它听懂我" -> "听"
    "让它答得好" -> "答"
    else -> "·"
}

@Composable
private fun M3CardRow(
    row: SettingsV2Row,
    sectionTitle: String?,
    titleColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    onSlider: ((String, Float) -> Unit)? = null,
) {
    // 单行制 + 彩色圆标（视觉中心）：图标 = 分区强调色的圆，内放一个汉字。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 彩色图标：28dp 实心圆 + 白色字符。用户要的"彩色图标作为视觉中心"。
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentOf(sectionTitle)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyphOf(sectionTitle),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = titleColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(
                    text = it,
                    color = valueColor,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (val trailing = row.trailing) {
            is RowTrailing.Value -> Text(
                text = trailing.text,
                color = titleColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.weight(1f).padding(start = 6.dp),
            )
            is RowTrailing.Toggle -> SwitchButton(
                checked = trailing.checked,
                onCheckedChange = trailing.onChange,
                label = {},
            )
            is RowTrailing.Chevron -> Text(
                text = "›",
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            // 连续量用官方滑块（M3E 控件），不是开关。
            is RowTrailing.Slider -> SettingsSlider(trailing, row.sliderText, titleColor, onChange = { v -> onSlider?.invoke(row.key, v) })
            RowTrailing.None -> Unit
        }
    }
}
