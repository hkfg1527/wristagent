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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsSlider
import com.example.myapplication.presentation.ui.settingsv2.common.CircleFit
import com.example.myapplication.presentation.ui.settingsv2.isActionable
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.flatRowIndex
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.rememberStyleMotion
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
    /** 滑块连续回调：key + 新值（写回偏好后重组）。 */
    onSlider: ((String, Float) -> Unit)? = null,
    /** 从选项子页返回时要滚回的行 key（null = 不用滚）。 */
    scrollToKey: String? = null,
    onScrollConsumed: (() -> Unit)? = null,
) {
    val identity = SettingsV2Style.SQUARE.identity
    val state = rememberTransformingLazyColumnState()
    LaunchedEffect(scrollToKey) {
        val target = scrollToKey ?: return@LaunchedEffect
        val index = flatRowIndex(sections, target)
        if (index >= 0) {
            state.requestScrollToItem(index)
            onScrollConsumed?.invoke()
        }
    }
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
        // 提到列表外：分区标题的 CircleFit 也要用它的下限（各风格不同）
        val motion = rememberStyleMotion(SettingsV2Style.SQUARE)
        TransformingLazyColumn(
            state = state,
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
            // 右缘留 20dp 而不是 12dp：官方指示器占 x=445..459（466px 屏实测），
            // 12dp 时行值右沿到 x=440，两者只差 5px，读起来像粘在一起。20dp 让出 16px 车道。
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
        ) {
            sections.forEach { section ->
                item(key = "h-" + section.title) {
                    // 圆屏收边：分区标题靠近上下两端时会被圆弧切字（0.0.0.263 圆图实测）
                    CircleFit(modifier = Modifier.fillMaxWidth(), minScale = motion.edgeScaleMin) {
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
                }
                itemsIndexed(section.rows, key = { _, row -> row.key }) { rowIndex, row ->
                val enter = rememberEnterProgress(rowIndex, motion)
                val edge = rememberEdgeDistance()
                    CircleFit(modifier = Modifier.fillMaxWidth(), minScale = motion.edgeScaleMin) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .enterAppearance(enter, motion)
                            .reportEdgeDistance(edge)
                            .edgeAppearance(edge.value, motion)
                            .then(
                                when {
                                        row.choiceOption -> Modifier.selectable(
                                            selected = row.selected,
                                            role = androidx.compose.ui.semantics.Role.RadioButton,
                                            onClick = { onRowClick?.invoke(row) },
                                        )
                                        onRowClick != null && row.isActionable() -> Modifier.clickable { onRowClick(row) }
                                        else -> Modifier
                                    },
                            )
                            .testTag(SquareRowTag)
                    ) {
                        // 单行制：标题左、值/开关右同一行。零圆角 + 内缩发丝线是这套的辨识度，
                        // 两行堆叠不是 —— 那只会让圆屏一屏只剩 2 行。
                        // 滑块行：值留在第一行右侧，滑块独占第二行（塞行尾会把标题挤没，0.0.0.263 实测）
                        val slider = row.trailing as? RowTrailing.Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = SquareRowMinHeight)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            when (val trailing = row.trailing) {
                                is RowTrailing.Value -> Text(
                                    text = trailing.text,
                                    color = identity.titleColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    textAlign = TextAlign.End,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(start = 6.dp),
                                )
                                is RowTrailing.Toggle -> SwitchButton(
                                    checked = trailing.checked,
                                    onCheckedChange = trailing.onChange,
                                    label = {},
                                )
                                is RowTrailing.Chevron -> Text(
                                    text = "›",
                                    color = identity.subtitleColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                // 滑块行：值先占第一行右侧，滑块画在下一行
                                is RowTrailing.Slider -> Text(
                                    text = row.sliderText ?: "",
                                    color = identity.titleColor,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                )
                                RowTrailing.None -> Unit
                            }
                        }
                        if (slider != null) {
                            SettingsSlider(
                                trailing = slider,
                                text = null,
                                color = identity.titleColor,
                                width = null,
                                sliderTag = com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2SliderTagPrefix + row.key,
                label = row.title,
                                onChange = { v -> onSlider?.invoke(row.key, v) },
                            )
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
        // 右缘位置指示器：官方 `ScrollIndicator` 的 `TransformingLazyColumnState` 重载
        ScrollIndicator(state = state, modifier = Modifier.align(Alignment.CenterEnd))
    }
}
