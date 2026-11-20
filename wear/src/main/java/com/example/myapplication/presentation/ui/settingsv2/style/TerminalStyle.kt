package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
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

/** 终端行的测试标识。 */
internal const val TerminalRowTag = "terminal-row"

/** 行首提示符（选中行用双尖括号由数据层决定，这里只负责单尖括号前缀）。 */
internal const val TerminalPrompt = "> "

/** 终端等宽字号。 */
internal val TerminalFontSize = 13.sp

/** 终端背景/前景的测试常量（必须与 StyleIdentity 一致）。 */
internal val TerminalBackground = SettingsV2Style.TERMINAL.identity.pageBackground
internal val TerminalForeground = SettingsV2Style.TERMINAL.identity.titleColor

/**
 * `TERMINAL`：近黑底 + 等宽绿字 + `> ` 前缀。
 *
 * 这套**不吸附**（`snapScroll = false`）：命令行是连续滚动的，逐行吸附会破坏"日志流"的感觉。
 * 所以这里不传 `snapFlingBehavior`，用 `TransformingLazyColumn` 的默认惯性。
 */
@Composable
fun TerminalSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
) {
    val identity = SettingsV2Style.TERMINAL.identity
    val state = rememberTransformingLazyColumnState()
    val rowStyle = TextStyle(
        color = identity.titleColor,
        fontFamily = FontFamily.Monospace,
        fontSize = TerminalFontSize,
    )
    val headStyle = TextStyle(
        color = identity.subtitleColor,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
    )
    Box(modifier = modifier.fillMaxSize().background(identity.pageBackground)) {
        TransformingLazyColumn(
            state = state,
            // 无 snapFling：连续滚动
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        ) {
            sections.forEach { section ->
                item(key = "h-" + section.title) {
                    BasicText(
                        text = "# " + section.title,
                        style = headStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 4.dp),
                    )
                }
                itemsIndexed(section.rows, key = { _, row -> row.key }) { rowIndex, row ->
                val motion = SettingsV2Motions.of(SettingsV2Style.TERMINAL)
                val enter = rememberEnterProgress(rowIndex, motion)
                val edge = rememberEdgeDistance()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = identity.rowMinHeight)
                            .enterAppearance(enter, motion)
                            .reportEdgeDistance(edge)
                            .edgeAppearance(edge.value, motion)
                            .padding(vertical = 4.dp)
                            .testTag(TerminalRowTag),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicText(text = TerminalPrompt + row.title, style = rowStyle)
                            row.subtitle?.let {
                                BasicText(
                                    text = "# " + it,
                                    style = headStyle,
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        when (val trailing = row.trailing) {
                            is RowTrailing.Value -> BasicText(
                                text = trailing.text,
                                style = rowStyle,
                            )
                            // 终端里没有胶囊开关：状态用 [ ON ] / [ OFF ] 文本表达
                            is RowTrailing.Toggle -> BasicText(
                                text = if (trailing.checked) "[ ON ]" else "[ OFF ]",
                                style = rowStyle,
                            )
                            is RowTrailing.Chevron, RowTrailing.None -> Unit
                        }
                    }
                }
            }
        }
    }
}
