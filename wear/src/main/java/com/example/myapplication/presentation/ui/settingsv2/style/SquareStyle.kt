package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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
import com.example.myapplication.presentation.ui.settingsv2.isActionable
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

/** 发丝线左右内缩（M3_LIST 是通栏线，本套内缩，两套的线性策略必须不同）。 */
internal val SquareHairlineInset = 12.dp

/**
 * 方角行的最小高度。
 *
 * 48dp 只是触控下限，撑不起"与官方列表是两套东西"：M3_LIST 行距 57dp、本套曾是 56dp，
 * 差 2px 一屏根本看不出区别。真机先试 60dp（行距 130px = 65dp，仍只差 16px < 判据的 24px），
 * 再提到 70dp。
 *
 * **只有一个来源**：值取自 `SettingsV2Style.SQUARE.identity.rowMinHeight`。
 * 曾经这里硬写 48dp、identity 里也写 48dp，改成 60dp 时只改了一处，
 * `squareIsLooserThanM3ListByAtLeastTwelveDp` 立刻红灯（identity 仍是 48dp）。
 */
internal val SquareRowMinHeight = SettingsV2Style.SQUARE.identity.rowMinHeight

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
    onRowClick: ((SettingsV2Row) -> Unit)? = null,
) {
    val identity = SettingsV2Style.SQUARE.identity
    val state = rememberTransformingLazyColumnState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(identity.pageBackground)
            // 方角档的签名：左右各一条 1px 竖轨，把内容框成"方框"
            .drawWithContent {
                drawContent()
                val rail = 1.dp.toPx()
                drawRect(
                    color = SquareHairline,
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(rail, size.height),
                )
                drawRect(
                    color = SquareHairline,
                    topLeft = androidx.compose.ui.geometry.Offset(size.width - rail, 0f),
                    size = androidx.compose.ui.geometry.Size(rail, size.height),
                )
            }
    ) {
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
                            .then(
                                if (onRowClick != null && row.isActionable()) {
                                    Modifier.clickable { onRowClick(row) }
                                } else {
                                    Modifier
                                },
                            )
                            .testTag(SquareRowTag)
                    ) {
                        // 两行堆叠：标题一行、右值/开关一行右对齐（176dp 窄列 + 放大字号下同行必互挤）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SquareRowMinHeight)
                                .padding(vertical = 6.dp),
                        ) {
                            Text(
                                text = row.title,
                                color = identity.titleColor,
                                // titleSmall 比 M3_LIST 的 labelMedium 高一档：两套连"字级"都不同
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            row.subtitle?.let {
                                Text(
                                    text = it,
                                    color = identity.subtitleColor,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            when (val trailing = row.trailing) {
                                is RowTrailing.Value -> Text(
                                    text = trailing.text,
                                    color = identity.titleColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                    textAlign = TextAlign.End,
                                    overflow = TextOverflow.Ellipsis,
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
                        // 1px 发丝线（0.5dp @ density 2.0）。
                        // **内缩 12dp**：M3_LIST 的线是通栏的，这一条内缩，两套的"线性"策略必须不同，
                        // 否则同屏只剩对齐差异（判据见 SquareAndTerminalStyleTest 与 docs/UI-EVIDENCE.md）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = SquareHairlineInset)
                                .height(SquareHairlineHeight)
                                .background(SquareHairline),
                        )
                    }
                }
            }
        }
    }
}
