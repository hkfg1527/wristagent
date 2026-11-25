package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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

/** ARC 卡片在测试里的标识（判据要量它的边界，不靠猜）。 */
internal const val ArcCardTag = "arc-card"

/**
 * `ARC`：华为手表式**贴边卡**。
 *
 * 形状（依据 `docs/SETTINGS-REBUILD-V2.md` §1.3 与 M3E 的 edge-hugging 语言）：
 * - 卡片**外侧贴屏幕边缘**（外侧圆角 0），只有朝屏幕中心的一侧有 28dp 圆角；
 * - 圆屏会自己裁掉越界的部分，所以这里不做任何横向缩放（禁止横向形变）；
 * - **文字**仍留华为 8.1.1 的安全边距：左右 26dp、上下各 10dp；页顶 20dp、页底 6dp、长列表底部 40dp。
 *
 * 与其它四套的关键区别：宽度策略是 `EdgeToEdge`（不再受 176dp 约束），
 * 这正是旧实现做不出贴边卡的原因。
 */
@Composable
fun ArcSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
    onRowClick: ((SettingsV2Row) -> Unit)? = null,
    onSlider: ((String, Float) -> Unit)? = null,
) {
    val identity = SettingsV2Style.ARC.identity
    // 外侧直角、内侧圆角
    val edgeShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = identity.cornerRadius,
        bottomEnd = identity.cornerRadius,
    )
    val state = rememberTransformingLazyColumnState()
    // Box 只为给「右缘位置指示器」一个对齐容器：列表本身仍占满全屏。
    Box(modifier = modifier.fillMaxSize()) {
    TransformingLazyColumn(
        state = state,
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
        // 页边距照华为：顶部 20dp；水平方向不留给卡片（卡片贴边），底部 6dp + 长列表底部 40dp
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 20.dp, bottom = 6.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "h-" + section.title) {
                Text(
                    text = section.title,
                    color = identity.subtitleColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 26.dp, end = 26.dp, top = 12.dp, bottom = 6.dp),
                )
            }
            itemsIndexed(section.rows, key = { _, row -> row.key }) { rowIndex, row ->
                val motion = SettingsV2Motions.of(SettingsV2Style.ARC)
                val enter = rememberEnterProgress(rowIndex, motion)
                val edge = rememberEdgeDistance()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .background(color = identity.surface, shape = edgeShape)
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
                        .testTag(ArcCardTag),
                ) {
                    ArcRow(row = row, titleColor = identity.titleColor, valueColor = identity.subtitleColor, onSlider = onSlider)
                }
            }
            if (sectionIndex == sections.lastIndex) {
                item(key = "tail") {
                    // 华为 8.1.1：长列表滚到底部留 40vp
                    Spacer(Modifier.heightIn(min = ArcBottomTail))
                }
            }
        }
    }
        // 右缘位置指示器：官方 `ScrollIndicator` 的 `TransformingLazyColumnState` 重载。
        // ARC 的卡片贴左屏、右侧留圆角，指示器落在圆角外侧的空白带上，不压字。
        ScrollIndicator(state = state, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun ArcRow(
    row: SettingsV2Row,
    titleColor: Color,
    valueColor: Color,
    onSlider: ((String, Float) -> Unit)? = null,
) {
    // 单行制：标题左、值/开关右同一行。贴边卡本来就满宽，两行堆叠纯属浪费面积。
    // 26dp 左右安全边距保留（文字不贴圆弧）；长值自己省略，不挤死标题。
    // 滑块行例外：值留第一行，滑块独占第二行（塞行尾会把标题挤没，0.0.0.263 实测）。
    val slider = row.trailing as? RowTrailing.Slider
    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 26.dp, end = 26.dp, top = 8.dp, bottom = 8.dp),
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
                color = valueColor,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            // 滑块行：值先占第一行右侧，滑块画在第二行
            is RowTrailing.Slider -> Text(
                text = row.sliderText ?: "",
                color = titleColor,
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
                color = titleColor,
                width = null,
                modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 8.dp),
                sliderTag = com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2SliderTagPrefix + row.key,
                onChange = { v -> onSlider?.invoke(row.key, v) },
            )
        }
    }
}

/** 供测试读取：华为 8.1.1 的安全边距（vp≈dp）。 */
internal val ArcSafeInset = 26.dp

/** 长列表滚到底部时额外留白（华为 8.1.1：40vp）。 */
internal val ArcBottomTail = 40.dp

/** 供测试读取：页顶/页底留白。 */
internal val ArcTopInset = 20.dp
internal val ArcBottomInset = 6.dp
