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
                // 卡内边距 12dp -> 10dp、标题下留白 6dp -> 2dp、行竖向留白 3dp -> 2dp：
                // 目标是让第二张卡在 466px 屏上露出更多，暗示"下面还有"（实测原卡高 205dp，屏高 233dp）
                contentPadding = PaddingValues(10.dp),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                Text(
                    text = section.title,
                    color = identity.titleColor,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.heightIn(min = 2.dp))
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
    // 176dp 卡片 + 用户可能放大字号：标题与右值同一行必然互相挤（实测 deepseek… / 回复…）。
    // 所以有值/开关时改成两行：第一行标题，第二行右值（右对齐）或开关（右对齐）。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 2.dp),
    ) {
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when (val trailing = row.trailing) {
            is RowTrailing.Value -> Text(
                text = trailing.text,
                color = titleColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
            is RowTrailing.Toggle -> Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                SwitchButton(
                    checked = trailing.checked,
                    onCheckedChange = trailing.onChange,
                    label = {},
                )
            }
            is RowTrailing.Chevron, RowTrailing.None -> Unit
        }
    }
}
