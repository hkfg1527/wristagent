package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsSlider
import com.example.myapplication.presentation.ui.settingsv2.isActionable
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.enterAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.edgeAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEdgeDistance
import com.example.myapplication.presentation.ui.settingsv2.common.reportEdgeDistance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEnterProgress

/**
 * `M3_LIST`：官方列表形态 —— 无卡面、左标题右值/开关、行间 1px 分隔。
 *
 * 与 `M3_CARD` 的区别就在"没有容器"：一屏能放 5~6 行，密度最高。
 * 走官方 `TransformingLazyColumn` + `snapFlingBehavior`（磁滑吸附）与
 * `rememberTransformationSpec()`（行随滚动做高度/透明度变化），不自己写 fling。
 */
@Composable
fun M3ListSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
    onRowClick: ((SettingsV2Row) -> Unit)? = null,
    onSlider: ((String, Float) -> Unit)? = null,
) {
    val identity = SettingsV2Style.M3_LIST.identity
    val state = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground),
    ) {
        TransformingLazyColumn(
            state = state,
            // 磁滑：官方吸附惯性（松手吸附到某项居中），不自己算 snapOffset
            flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
            // 右缘留 20dp 而不是 12dp：给官方指示器让车道（实测指示器占 x=445..459，
            // 12dp 时行值右沿到 x=440，只差 5px）。
            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 20.dp, bottom = 8.dp),
        ) {
            sections.forEach { section ->
                item(key = "h-" + section.title) {
                    Text(
                        text = section.title,
                        color = identity.subtitleColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(section.rows, key = { _, row -> row.key }) { rowIndex, row ->
                val motion = SettingsV2Motions.of(SettingsV2Style.M3_LIST)
                val enter = rememberEnterProgress(rowIndex, motion)
                // 边缘淡出/缩放：行自己上报离屏幕中心的比例（不用官方 scrollProgress，理由见 common 层注释）
                val edge = rememberEdgeDistance()
                    Column(modifier = Modifier.fillMaxWidth()) {
                        M3ListRow(
                            row = row,
                            identity = identity,
                            spec = transformationSpec,
                            onSlider = onSlider,
                            modifier = Modifier
                                .enterAppearance(enter, motion)
                                .reportEdgeDistance(edge)
                                .edgeAppearance(edge.value, motion)
                                .then(
                                    if (onRowClick != null && row.isActionable()) {
                                        Modifier.clickable { onRowClick(row) }
                                    } else {
                                        Modifier
                                    },
                                ),
                        )
                        if (identity.dividers) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFF23282E)),
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

@Composable
private fun M3ListRow(
    row: SettingsV2Row,
    identity: com.example.myapplication.presentation.ui.settingsv2.StyleIdentity,
    spec: androidx.wear.compose.material3.lazy.TransformationSpec,
    modifier: Modifier = Modifier,
    onSlider: ((String, Float) -> Unit)? = null,
) {
    // 单行制：标题左、值/开关右同一行。两行堆叠时一屏只 3~4 行，
    // 圆屏上又空又散（圆屏截图评审后统一改）。标题与值各占一半，长值自己省略。
    //
    // 滑块行例外：值留在第一行右侧，滑块单独占第二行（塞行尾会把标题挤成「温…」，0.0.0.263 真机实测）。
    val slider = row.trailing as? RowTrailing.Slider
    // Column 只负责"给滑块留第二行"；高度约束与内边距**仍然挂在 Row 上**，
    // 顺序保持 `heightIn(min) → padding`（反过来会让每行多出 8dp，实测行距 92px 变 103px）。
    Column(modifier = modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = identity.rowMinHeight)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                color = identity.titleColor,
                style = MaterialTheme.typography.labelMedium,
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
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
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
            // 滑块行：值先占第一行右侧，滑块在第二行（见上）
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
                onChange = { v -> onSlider?.invoke(row.key, v) },
            )
        }
    }
}

/** 供测试用的常量：列表行底色必须等于页面底色（无卡面）。 */
internal val M3ListPageBackground: Color = SettingsV2Style.M3_LIST.identity.pageBackground

/** 供测试用的常量：列表行表面必须完全透明（没有卡片容器）。 */
internal val M3ListSurfaceAlpha: Float = SettingsV2Style.M3_LIST.identity.surface.alpha

/** 供测试用的常量：列表行最小高度。 */
internal val M3ListRowMinHeight = SettingsV2Style.M3_LIST.identity.rowMinHeight
