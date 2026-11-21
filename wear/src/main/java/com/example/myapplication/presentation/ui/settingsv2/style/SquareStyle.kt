package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.enterAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEnterProgress
import com.example.myapplication.presentation.ui.settingsv2.common.edgeAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEdgeDistance
import com.example.myapplication.presentation.ui.settingsv2.common.reportEdgeDistance

/** 方角行的测试标识。 */
internal const val SquareRowTag = "square-row"

/** 发丝线颜色（1px）。 */
internal val SquareHairline = Color(0xFF3A4048)

/** 发丝线厚度：**1px**（density 2.0 下即 0.5dp）。 */
internal val SquareHairlineHeight = 0.5.dp

/** 方角行的最小高度。 */
// 48dp 是触控下限，不能再小；与 M3_LIST 的区分靠居中分区头 + 更亮的发丝线
internal val SquareRowMinHeight = 48.dp

/**
 * `SQUARE`：方角档 —— 0 圆角 + **1px 发丝线**，无卡面。
 *
 * 规格：行最小高 48dp；组标题**居中**（`centeredText = true` 是本套与列表档的可见差别）；
 * 分隔线用 0.5dp（在 density 2.0 的表上等于 1 个物理像素），不是 1dp。
 */
@Composable
fun SquareSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
) {
    val identity = SettingsV2Style.SQUARE.identity
    val state = rememberTransformingLazyColumnState()
    Box(modifier = modifier.fillMaxWidth().background(identity.pageBackground)) {
        TransformingLazyColumn(
            state = state,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            sections.forEach { section ->
                item(key = "h-" + section.title) {
                    Text(
                        text = section.title,
                        color = identity.subtitleColor,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                    )
                }
                itemsIndexed(section.rows, key = { _, row -> row.key }) { rowIndex, row ->
                val motion = SettingsV2Motions.of(SettingsV2Style.SQUARE)
                val enter = rememberEnterProgress(rowIndex, motion)
                val edge = rememberEdgeDistance()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .enterAppearance(enter, motion)
                            .reportEdgeDistance(edge)
                            .edgeAppearance(edge.value, motion)
                            .testTag(SquareRowTag)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SquareRowMinHeight)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = row.title,
                                    color = identity.titleColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                row.subtitle?.let {
                                    Text(
                                        text = it,
                                        color = identity.subtitleColor,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            when (val trailing = row.trailing) {
                                is RowTrailing.Value -> Text(
                                    text = trailing.text,
                                    color = identity.subtitleColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                is RowTrailing.Toggle -> SwitchButton(
                                    checked = trailing.checked,
                                    onCheckedChange = trailing.onChange,
                                    label = {},
                                )
                                is RowTrailing.Chevron, RowTrailing.None -> Unit
                            }
                        }
                        // 1px 发丝线（0.5dp @ density 2.0）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(SquareHairlineHeight)
                                .background(SquareHairline),
                        )
                    }
                }
            }
        }
    }
}
