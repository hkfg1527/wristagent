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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.StyleIdentity

/**
 * `M3_CARD`：谷歌健康（Google Health）那种**大卡片**。
 *
 * 一屏 1~2 张卡，一张卡负责一组设置：卡内是"组标题 + 若干行"，行尾直接放值/开关。
 * 全部用官方 `androidx.wear.compose.material3` 组件（Card / SwitchButton / Text），不手搓。
 *
 * 这个文件只读 [StyleIdentity]，不判断风格、不 import 其它 style 文件。
 */
@Composable
fun M3CardSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
) {
    val identity = SettingsV2Style.M3_CARD.identity
    val shape = RoundedCornerShape(identity.cornerRadius)
    TransformingLazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections, key = { it.title }) { section ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                shape = shape,
                colors = CardDefaults.cardColors(containerColor = identity.surface),
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = section.title,
                        color = identity.titleColor,
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.heightIn(min = 6.dp))
                    section.rows.forEach { row ->
                        M3CardRow(row = row, identity = identity)
                    }
                }
            }
        }
    }
}

@Composable
private fun M3CardRow(row: SettingsV2Row, identity: StyleIdentity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, color = identity.titleColor, style = MaterialTheme.typography.bodyMedium)
            row.subtitle?.let {
                Text(text = it, color = identity.subtitleColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (val trailing = row.trailing) {
            is RowTrailing.Value -> Text(
                text = trailing.text,
                color = identity.subtitleColor,
                style = MaterialTheme.typography.bodySmall,
            )
            is RowTrailing.Toggle -> SwitchButton(
                checked = trailing.checked,
                onCheckedChange = trailing.onChange,
                // 标签由外部行标题承担，这里给空标签避免重复朗读
                label = {},
            )
            is RowTrailing.Chevron, RowTrailing.None -> Unit
        }
    }
}
