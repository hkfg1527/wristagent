package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.enterAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEnterProgress

/**
 * `M3_CARD`：谷歌健康（Google Health）式**大卡片**。
 *
 * 规格来自 `docs/SETTINGS-REBUILD-V2.md`：
 * - 一屏 1~2 张主卡，**卡最小高度 140dp**、内边距 **12dp**、卡间距 **8dp**、圆角 **26dp**；
 * - 卡内用 M3 类型标尺两档：组标题 `titleMedium`、行标题/状态 `bodyMedium`；
 * - 走**官方** `Card(transformation = SurfaceTransformation(itemScope, spec))`：
 *   `transformationSpec` 由 `rememberTransformationSpec()` 提供，这样卡片的形状与高度会随滚动位置变化
 *   （M3E 的 shape morphing），而不是自己用 graphicsLayer 手搓缩放。
 */
@Composable
fun M3CardSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
) {
    val identity = SettingsV2Style.M3_CARD.identity
    val shape = RoundedCornerShape(identity.cornerRadius)
    val transformationSpec = rememberTransformationSpec()
    val state = rememberTransformingLazyColumnState()
    TransformingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = state,
        // 磁滑吸附：与大卡片一起用官方 snapFling（守卫 SettingsV2ArchitectureGuardTest 会检查这条）
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
        // 页边距：左右 8dp（卡自己再留 12dp 内边距），上下 12dp
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(sections, key = { _, section -> section.title }) { index, section ->
            val motion = SettingsV2Motions.of(SettingsV2Style.M3_CARD)
            val enter = rememberEnterProgress(index, motion)
            Card(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = identity.rowMinHeight)
                    // 入场逐行错峰：延迟 = index × staggerMs（判据见 SettingsV2MotionTest）
                    .enterAppearance(enter, motion),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = identity.surface),
                contentPadding = PaddingValues(12.dp),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text(
                    text = section.title,
                    color = identity.titleColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.heightIn(min = 6.dp))
                section.rows.forEach { row ->
                    M3CardRow(row = row, titleColor = identity.titleColor, valueColor = identity.subtitleColor)
                }
            }
        }
    }
}

@Composable
private fun M3CardRow(
    row: SettingsV2Row,
    titleColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = titleColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(text = it, color = valueColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (val trailing = row.trailing) {
            is RowTrailing.Value -> Text(
                text = trailing.text,
                color = valueColor,
                // 176dp 列宽下，右值降到 labelMedium，给左标题留出至少 3 个字的空间
                style = MaterialTheme.typography.labelMedium,
                // 右值优先按需取宽（模型名不能被截成 deepse…）；左标题用 weight 吃剩下的空间
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            is RowTrailing.Toggle -> SwitchButton(
                checked = trailing.checked,
                onCheckedChange = trailing.onChange,
                // 标签由外部行标题承担，避免读屏重复朗读
                label = {},
            )
            is RowTrailing.Chevron, RowTrailing.None -> Unit
        }
    }
}
