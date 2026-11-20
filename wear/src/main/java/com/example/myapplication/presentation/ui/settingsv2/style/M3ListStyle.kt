package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
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
                            modifier = Modifier
                                .enterAppearance(enter, motion)
                                .reportEdgeDistance(edge)
                                .edgeAppearance(edge.value, motion),
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
    }
}

@Composable
private fun M3ListRow(
    row: SettingsV2Row,
    identity: com.example.myapplication.presentation.ui.settingsv2.StyleIdentity,
    spec: androidx.wear.compose.material3.lazy.TransformationSpec,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = identity.rowMinHeight)
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
                Text(text = it, color = identity.subtitleColor, style = MaterialTheme.typography.labelSmall)
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
}

/** 供测试用的常量：列表行底色必须等于页面底色（无卡面）。 */
internal val M3ListPageBackground: Color = SettingsV2Style.M3_LIST.identity.pageBackground

/** 供测试用的常量：列表行表面必须完全透明（没有卡片容器）。 */
internal val M3ListSurfaceAlpha: Float = SettingsV2Style.M3_LIST.identity.surface.alpha

/** 供测试用的常量：列表行最小高度。 */
internal val M3ListRowMinHeight = SettingsV2Style.M3_LIST.identity.rowMinHeight
