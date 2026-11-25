package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.clickable
import com.example.myapplication.presentation.ui.settingsv2.common.CircleFit
import androidx.wear.compose.material3.Slider
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.example.myapplication.presentation.ui.settingsv2.RowTrailing
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsSlider
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2SliderTagPrefix
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Row
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Section
import com.example.myapplication.presentation.ui.settingsv2.flatRowIndex
import com.example.myapplication.presentation.ui.settingsv2.SettingsV2Style
import com.example.myapplication.presentation.ui.settingsv2.common.SettingsV2Motions
import com.example.myapplication.presentation.ui.settingsv2.common.rememberStyleMotion
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
    /** 滑块连续回调：key + 新值（写回偏好后重组）。 */
    onSlider: ((String, Float) -> Unit)? = null,
    /** 从选项子页返回时要滚回的行 key（null = 不用滚）。 */
    scrollToKey: String? = null,
    onScrollConsumed: (() -> Unit)? = null,
) {
    val identity = SettingsV2Style.M3_CARD.identity
    val shape = RoundedCornerShape(identity.cornerRadius)
    val transformationSpec = rememberTransformationSpec()
    val state = rememberTransformingLazyColumnState()
    // 从子页返回：滚回原来那一行（不做的话会被弹回顶部）
    LaunchedEffect(scrollToKey) {
        val target = scrollToKey ?: return@LaunchedEffect
        val index = flatRowIndex(sections, target)
        if (index >= 0) {
            state.requestScrollToItem(index)
            onScrollConsumed?.invoke()
        }
    }

    // 拍平成一个列表：分区眉标 + 该项一张小卡。这样一屏能同时看到更多设置项。
    data class Item(val header: String? = null, val row: SettingsV2Row? = null, val key: String)
    val items = buildList {
        sections.forEach { section ->
            add(Item(header = section.title, key = "h-" + section.title))
            section.rows.forEach { row -> add(Item(row = row, key = row.key)) }
        }
    }

    // Box 只为给「右缘位置指示器」一个对齐容器：列表本身仍占满全屏。
    Box(modifier = modifier.fillMaxSize()) {
    TransformingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = state,
        // 磁滑吸附（守卫 SettingsV2ArchitectureGuardTest 会检查这条）
        flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(items, key = { it.key }) { item ->
            val motion = rememberStyleMotion(SettingsV2Style.M3_CARD)
            val enter = rememberEnterProgress(items.indexOf(item), motion)
            // 每一项都过 CircleFit：中间吃满圆屏弦长，滚到上下两端自动收边，
            // 不再被圆弧切掉（真机截图里 banner/底栏被切就是这个原因）。
            CircleFit(modifier = Modifier.fillMaxWidth()) {
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
                } else {
                    val row = item.row
                    if (row != null) {
                        // 滑块行**不能**用可点 Card：`Card(onClick=…)` 会在整张卡上挂一层 clickable，
                        // 抢走滑块的拖动（真机 0.0.0.264：点/左拖/右拖都不动；JVM 的 SetProgress 却是通的）。
                        // 滑块行本来也没有"点一下"的语义，所以换成不带 onClick 的 Card。
                        val cardModifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .testTag("m3card-row")
                            .enterAppearance(enter, motion)
                        val content: @Composable () -> Unit = {
                            M3CardRow(
                                row = row,
                                sectionTitle = sections.firstOrNull { s -> s.rows.any { it.key == row.key } }?.title,
                                onSlider = onSlider,
                                titleColor = identity.titleColor,
                                valueColor = identity.subtitleColor,
                            )
                        }
                        if (row.trailing is RowTrailing.Slider) {
                            // 滑块行不用可点 Card：整卡挂 clickable 对"拖动"没有好处，语义上也讲不通
                            // （点一下这行不该发生任何事）。**注意**：真机 0.0.0.264 实测，
                            // 即便换成不可点 Card / 去掉 SurfaceTransformation / 换成普通 Box /
                            // 去掉 CircleFit，M3_CARD 的滑块仍然收不到拖动（onSliderChange 一次都不触发），
                            // 而同一滑块在 M3_LIST 里能拖。根因未定位，见 docs/CONTEXT.md §0.5 的待办。
                            Card(
                                modifier = cardModifier,
                                shape = shape,
                                colors = CardDefaults.cardColors(containerColor = identity.surface),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) { content() }
                        } else if (row.choiceOption) {
                            // 选项行：用 Modifier.selectable 而不是 Card(onClick) + 外层 semantics。
                            // 真机 a11y 树里 `selectable` 会投影成 **checkable=true / checked=true**（实测），
                            // 而外层补的 `selected` 属性根本不进投影。
                            Card(
                                modifier = cardModifier.selectable(
                                    selected = row.selected,
                                    role = androidx.compose.ui.semantics.Role.RadioButton,
                                    onClick = { onRowClick?.invoke(row) },
                                ),
                                shape = shape,
                                colors = CardDefaults.cardColors(containerColor = identity.surface),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) { content() }
                        } else {
                            Card(
                                onClick = { if (row.isActionable()) onRowClick?.invoke(row) },
                                modifier = cardModifier,
                                shape = shape,
                                colors = CardDefaults.cardColors(containerColor = identity.surface),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) { content() }
                        }
                    }
                }
            }
        }
    }
        // 右缘位置指示器：官方 `ScrollIndicator` 的 `TransformingLazyColumnState` 重载
        // （1.7.1 里确实有这个重载，不是把 ScalingLazyListState 硬塞进来）。
        ScrollIndicator(state = state, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** 分区 → 强调色（彩色图标 = 用户要的"视觉中心"）。 */
private fun accentOf(section: String?): androidx.compose.ui.graphics.Color = when (section) {
    "连得上" -> androidx.compose.ui.graphics.Color(0xFF30D158)
    "让它能做事" -> androidx.compose.ui.graphics.Color(0xFF0A84FF)
    "让它听懂我" -> androidx.compose.ui.graphics.Color(0xFFFF9F0A)
    "让它答得好" -> androidx.compose.ui.graphics.Color(0xFFFF375F)
    else -> androidx.compose.ui.graphics.Color(0xFF8E8E93)
}

/** 分区 → 图标字符（没有图形资源时用字符画圆标，效果等同应用图标）。 */
private fun glyphOf(section: String?): String = when (section) {
    "连得上" -> "连"
    "让它能做事" -> "做"
    "让它听懂我" -> "听"
    "让它答得好" -> "答"
    else -> "·"
}

@Composable
private fun M3CardRow(
    row: SettingsV2Row,
    sectionTitle: String?,
    titleColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    onSlider: ((String, Float) -> Unit)? = null,
) {
    val slider = row.trailing as? RowTrailing.Slider
    // 滑块行分两行画：第一行仍是「图标 + 标题/副标题 + 当前值」，第二行才是滑块。
    // 之前滑块塞在行尾（104dp = 208px），把 176dp 卡片里的标题和副标题挤成「温…」「越…」——
    // 真机 0.0.0.263 截图可见。滑块本身需要宽度，标题也需要宽度，只能分两行。
    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 彩色图标：28dp 实心圆 + 白色字符。用户要的"彩色图标作为视觉中心"。
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(accentOf(sectionTitle)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = glyphOf(sectionTitle),
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.width(8.dp))
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
            // 滑块行：值先占第一行右侧，滑块挪到下一行（见上）
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
            // 全宽滑块：官方 `Slider`（连续量不用 −/+ 步进器，用户要的是"进度条那种"）
            SettingsSlider(
                trailing = slider,
                text = null,
                color = titleColor,
                width = null,
                modifier = Modifier.padding(top = 4.dp),
                sliderTag = SettingsV2SliderTagPrefix + row.key,
                label = row.title,
                onChange = { v -> onSlider?.invoke(row.key, v) },
            )
        }
    }
}
