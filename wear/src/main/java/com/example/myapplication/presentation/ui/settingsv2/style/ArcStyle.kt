package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
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
    TransformingLazyColumn(
        state = state,
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
        // 页边距照华为：顶部 20dp；水平方向不留给卡片（卡片贴边），底部 6dp + 长列表底部 40dp
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 20.dp, bottom = 6.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        sections.forEach { section ->
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
                        .testTag(ArcCardTag),
                ) {
                    ArcRow(row = row, titleColor = identity.titleColor, valueColor = identity.subtitleColor)
                }
            }
            item(key = "sp-" + section.title) {
                // 长列表滚到底部时的额外留白（华为 40vp 规则的一部分）
                Spacer(Modifier.heightIn(min = 8.dp))
            }
        }
    }
}

@Composable
private fun ArcRow(
    row: SettingsV2Row,
    titleColor: Color,
    valueColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            // 华为 8.1.1：左右 26vp 安全边距（文字不贴边，卡片本体贴边）
            .padding(start = 26.dp, end = 26.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = row.title, color = titleColor, style = MaterialTheme.typography.bodyLarge)
            row.subtitle?.let {
                Text(text = it, color = valueColor, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(8.dp))
        when (val trailing = row.trailing) {
            is RowTrailing.Value -> Text(
                text = trailing.text,
                color = valueColor,
                style = MaterialTheme.typography.labelLarge,
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

/** 供测试读取：华为 8.1.1 的安全边距（vp≈dp）。 */
internal val ArcSafeInset = 26.dp

/** 供测试读取：页顶/页底留白。 */
internal val ArcTopInset = 20.dp
internal val ArcBottomInset = 6.dp
