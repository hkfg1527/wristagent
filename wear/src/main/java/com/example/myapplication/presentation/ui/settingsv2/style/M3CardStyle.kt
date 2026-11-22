package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.clickable
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
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
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

    TransformingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = state,
        // 磁滑吸附（守卫 SettingsV2ArchitectureGuardTest 会检查这条）
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items, key = { it.key }) { item ->
            val motion = SettingsV2Motions.of(SettingsV2Style.M3_CARD)
            val enter = rememberEnterProgress(items.indexOf(item), motion)
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
                return@items
            }
            val row = item.row ?: return@items
            Card(
                onClick = { if (row.isActionable()) onRowClick?.invoke(row) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .testTag("m3card-row")
                    .enterAppearance(enter, motion),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = identity.surface),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                transformation = SurfaceTransformation(transformationSpec),
            ) {
                M3CardRow(
                    row = row,
                    titleColor = identity.titleColor,
                    valueColor = identity.subtitleColor,
                )
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
    // 单行制：标题在左、值/开关在右同一行；标题与值各占一半，长值自己省略而不挤死标题。
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
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
            RowTrailing.None -> Unit
        }
    }
}
