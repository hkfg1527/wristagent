package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.myapplication.presentation.ui.settingsv2.common.edgeAppearance
import com.example.myapplication.presentation.ui.settingsv2.common.rememberEdgeDistance
import com.example.myapplication.presentation.ui.settingsv2.common.reportEdgeDistance
import com.example.myapplication.presentation.ui.settingsv2.common.RotaryBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 终端行的测试标识。 */
internal const val TerminalRowTag = "terminal-row"

/** 提示符：真的终端就是 `> `。 */
internal const val TerminalPrompt = "> "

/** 块光标字符（真终端的实心光标，不是 UI 下划线）。 */
internal const val TerminalCursorBlock = "█"

/** 终端等宽字号。 */
internal val TerminalFontSize = 12.sp

/**
 * 终端行高（**行网格**）。
 *
 * 这是"像 UI 还是像终端"的分水岭：原来每行是 `heightIn(min = 48dp)` + 上下 4dp 的卡片式留白，
 * 真机行距 104px（52dp），一屏只放得下 4 行 —— 那是列表，不是终端。
 * 终端是字符网格：行高 18dp（density 2.0 下 36px），一屏 12 行，字号 12sp。
 */
internal val TerminalLineHeight = 18.dp

/** ASCII 滚动条字符（真 TUI 的滚动条是画出来的字符，不是圆角滑块）。 */
internal const val TerminalThumbCell = "▓"
internal const val TerminalTrackCell = "░"

/** 终端背景/前景的测试常量（必须与 StyleIdentity 一致）。 */
internal val TerminalBackground = SettingsV2Style.TERMINAL.identity.pageBackground
internal val TerminalForeground = SettingsV2Style.TERMINAL.identity.titleColor

/**
 * `TERMINAL`：**按真终端做**，不是"长得像终端的列表"。
 *
 * 三条硬约束（判据见 `TerminalStyleTest`）：
 * 1. **字符行网格**：所有文本行共用 [TerminalLineHeight]，一屏 12 行，行与行之间没有"卡片留白"；
 * 2. **单行制**：一行里 `> 标题` 与值同行（值右对齐成一列），副标题另起一行但仍在同一网格上；
 * 3. **零 M3 控件**：不用卡片 / 胶囊开关 / 按钮 / 表面容器 —— 开关写成 `[ ON ]`，
 *    跳转写成 `/open`，全部是等宽字符（守卫见 `terminalUsesNoMaterialComponents`）。
 *
 * 另外两处"终端语言"：右侧 `▓/░` ASCII 滚动条、末行 `> █` 块光标（1s 一循环闪烁）。
 *
 * 这套**不吸附**（TERMINAL 的 `snapScroll = false`）：终端是连续日志流，逐行吸附会破坏它。
 */
@Composable
fun TerminalSettings(
    sections: List<SettingsV2Section>,
    modifier: Modifier = Modifier,
) {
    val identity = SettingsV2Style.TERMINAL.identity
    val state = rememberTransformingLazyColumnState()

    val lineStyle = TextStyle(
        color = identity.titleColor,
        fontFamily = FontFamily.Monospace,
        fontSize = TerminalFontSize,
        // 必须显式给 lineHeight：等宽字体默认行高约 22sp，会把 18dp(36px) 的行撑到 44px，
        // 真机实测行距 44px（应为 36px）。这里让它与行网格严格一致。
        lineHeight = TerminalLineHeight.value.sp,
    )
    val dimStyle = TextStyle(
        color = identity.subtitleColor,
        fontFamily = FontFamily.Monospace,
        fontSize = TerminalFontSize,
        lineHeight = TerminalLineHeight.value.sp,
    )

    // 列表里的基础行数（分区头 + 行）。提示符是钉在底部的，**不算**列表项。
    val baseItems = remember(sections) { sections.sumOf { 1 + it.rows.size } }

    // 表冠：按"行"滚动（真终端语义），不是像素级滑动
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val linePx = with(LocalDensity.current) { TerminalLineHeight.toPx() }
    var rotaryRemainder by remember { mutableStateOf(0f) }
    // 真终端的行是"可执行对象"：点一下就有输出。这里做本地态 + 末尾回显（最多留 3 行）。
    val toggles = remember { mutableStateMapOf<String, Boolean>() }
    val echoes = remember { mutableStateListOf<String>() }
    fun pushEcho(line: String) {
        echoes.add(line)
        while (echoes.size > 3) echoes.removeAt(0)
    }
    // 真终端打印输出后会**跟随到底**：不跟随的话，用户点了命令却看不到结果
    // （上一轮实测：点 /open 后要手动滑 6 次才看到 `# open …`）。
    // 用 scrollToItem 而不是 animateScrollToItem：`tail -f` 是瞬间跳到底，不做平滑滚动。
    LaunchedEffect(echoes.size) {
        if (echoes.isNotEmpty()) {
            state.scrollToItem(baseItems + echoes.size - 1)
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    // 表冠：Activity 层送来的滚动（不依赖焦点）。进入本页才打开安全阀，离开立刻关掉，
    // 免得把聊天页的表冠滚动一起吞了。
    DisposableEffect(Unit) {
        RotaryBus.enabled = true
        onDispose { RotaryBus.enabled = false }
    }
    LaunchedEffect(Unit) {
        RotaryBus.events.collect { delta ->
            val (rest, lines) = rotaryLinesToScroll(rotaryRemainder, delta, linePx)
            rotaryRemainder = rest
            if (lines != 0) state.scrollBy(lines * linePx)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(identity.pageBackground)
            .onRotaryScrollEvent { event ->
                val (rest, lines) = rotaryLinesToScroll(rotaryRemainder, event.verticalScrollPixels, linePx)
                rotaryRemainder = rest
                // 取证用：表冠事件是否真的到达这里（真机 dump 位移为 0 时，靠这条日志区分"没到"与"到了没滚"）
                android.util.Log.i(
                    TerminalRotaryTag,
                    "delta=${event.verticalScrollPixels} rest=$rest lines=$lines",
                )
                if (lines != 0) {
                    scope.launch { state.scrollBy(lines * linePx) }
                }
                true
            }
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        TransformingLazyColumn(
            state = state,
            // 无 snapFling：终端连续滚动
            contentPadding = PaddingValues(start = 10.dp, end = 16.dp, top = 4.dp, bottom = 26.dp),
            // 显式 0 间距：默认的 4dp(8px) 项目间距会把行网格切成 36/44 交替（真机实测：
            // 项内 96->132 = 36px 正常，项间 132->176 = 44px）。字符网格必须是常数。
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            sections.forEach { section ->
                item(key = "h-" + section.title) {
                    // 分区头 = 终端的注释行，用暗绿，让它看起来像输出的一部分
                    TerminalGridLine {
                        BasicText(
                            text = "# " + section.title,
                            style = dimStyle,
                            maxLines = 1,
                        )
                    }
                }
                itemsIndexed(section.rows, key = { _, row -> row.key }) { _, row ->
                    val edge = rememberEdgeDistance()
                    // 真终端的行**不淡入**：它是被"打印"出来的，不是从下往上浮进来的。
                    // 只保留边缘反馈（滚动到屏幕边时压暗），入场动画交给"回显追加"来表达。
                    val checked = toggles[row.key]
                        ?: (row.trailing as? RowTrailing.Toggle)?.checked
                        ?: false
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .reportEdgeDistance(edge)
                            .edgeAppearance(edge.value, SettingsV2Motions.of(SettingsV2Style.TERMINAL))
                            .clickable(enabled = row.trailing is RowTrailing.Toggle || row.trailing is RowTrailing.Chevron) {
                                when (val trailing = row.trailing) {
                                    is RowTrailing.Toggle -> {
                                        toggles[row.key] = !checked
                                        pushEcho("# ${row.title} -> ${if (!checked) "ON" else "OFF"}")
                                    }
                                    is RowTrailing.Chevron -> pushEcho("# open ${row.title}")
                                    else -> Unit
                                }
                            }
                            .testTag(TerminalRowTag),
                    ) {
                        TerminalGridLine {
                            TerminalLine(
                                row = row,
                                lineStyle = lineStyle,
                                dimStyle = dimStyle,
                                toggleChecked = checked,
                            )
                        }
                        // 副标题另起一行，仍落在同一网格上：`  └ 说明`
                        row.subtitle?.let {
                            TerminalGridLine {
                                BasicText(
                                    text = "  └ $it",
                                    style = dimStyle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            // 回显：真终端的每条命令都会在末尾留下输出，最多留 3 行
            echoes.forEach { line ->
                item(key = "echo-" + line) {
                    TerminalGridLine {
                        BasicText(text = line, style = dimStyle, maxLines = 1)
                    }
                }
            }
            // 末行不放进列表：真终端的提示符是**钉在底部**的，不该跟着内容滚走
        }
        // 钉在底部的状态条：左 `> █`（块光标）+ 右状态段 `[2/4] 26L`，整行不透明，内容从其后方滚过。
        // 状态段随滚动更新 —— CLI 的底栏就该告诉你"现在在哪、还有多少行"。
        TerminalPromptLine(
            lineStyle = lineStyle,
            isActive = state.isScrollInProgress,
            statusText = "[${zoneOfAnchor(sections, state.anchorItemIndex, echoes.size)}/${sections.size}] "
                + "${sections.sumOf { it.rows.size }}L",
            modifier = Modifier.align(Alignment.BottomStart),
        )
        TerminalScrollBar(
            anchorIndex = state.anchorItemIndex,
            totalItems = baseItems + echoes.size,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(10.dp)
                .padding(top = 4.dp, bottom = 4.dp, end = 2.dp),
        )
    }
}

/**
 * 当前锚点项落在第几个分区（1 起）。
 *
 * 每个分区在列表里占 `1（分区头）+ rows.size` 个项；回显追加在最后，不影响分区归属。
 * 纯函数，便于单测（`TerminalStatusTest`）。
 */
internal fun zoneOfAnchor(
    sections: List<SettingsV2Section>,
    anchorItemIndex: Int,
    echoCount: Int = 0,
): Int {
    var start = 0
    sections.forEachIndexed { index, section ->
        val size = 1 + section.rows.size
        if (anchorItemIndex < start + size) return index + 1
        start += size
    }
    // 锚点在回显区（或越界）时算最后一个分区
    return sections.size.coerceAtLeast(1)
}

/**
 * 一行字符网格：**由外层 Box 强制高度并裁剪**，不依赖字体度量。
 *
 * 为什么不是给 `BasicText` 加 `.height()`：等宽字体在 12sp 下的固有行高实测约 44px，
 * 比网格的 36px 高，`Row.height(36)` 压不住它（真机行距一直是 44px）。这里把高度和裁剪
 * 放在外层，字体想多高都只能被裁在网格里 —— 终端就是这样工作的（字符格子决定一切）。
 */
@Composable
private fun TerminalGridLine(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TerminalLineHeight)
            .clipToBounds(),
    ) {
        content()
    }
}

/**
 * 单行制的一行：`> 标题` 靠左，值/开关靠右对齐成同一列。
 *
 * 为什么用 `weight(1f) + TextAlign.End` 而不是"补空格到固定列"：等宽字体里中文是双宽字形，
 * 按字符数补空格算不准列宽；两者在真机上的效果都是"值右对齐成一条竖线"，后者更可靠。
 */
@Composable
private fun TerminalLine(
    row: SettingsV2Row,
    lineStyle: TextStyle,
    dimStyle: TextStyle,
    toggleChecked: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // 标题占剩余宽度（超出就自己截断，别挤值列）；值取**固有宽度**，靠 SpaceBetween 推到最右。
        // 不能用 fillMaxWidth(0.62f)：Row 里它算的是"扣掉标题后的剩余宽度"，
        // 真机上把 deepseek-v4-pro 截成了 `deepseek-`、BALANCED 截成了 `BALANCE`。
        //
        // `> ` 是**可交互行的提示符**，不是每条输出都加的前缀：只读信息（模型/温度）只是缩进两格，
        // 可执行行（开关/跳转）才有 `>` —— TUI 里就是这么区分"这行能不能按"的。
        val actionable = row.trailing is RowTrailing.Toggle || row.trailing is RowTrailing.Chevron
        BasicText(
            text = (if (actionable) TerminalPrompt else "  ") + row.title,
            style = lineStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        // 真终端里没有胶囊开关：开关是 `[ ON ] / [ OFF ]`，跳转是 `/open`
        val right: Pair<String, Boolean>? = when (val trailing = row.trailing) {
            // 只读值降成暗绿：让"你能按的"和"你只能看的"一眼分开
            is RowTrailing.Value -> trailing.text to true
            // 开关状态的来源是本地态（点一下翻一次），初值取数据层
            is RowTrailing.Toggle -> (if (toggleChecked) "[ ON  ]" else "[ OFF ]") to false
            is RowTrailing.Chevron -> "/open" to true
            RowTrailing.None -> null
        }
        if (right != null) {
            BasicText(
                text = right.first,
                // BasicText 没有 textAlign 形参：对齐必须写在 TextStyle 里
                style = (if (right.second) dimStyle else lineStyle)
                    .copy(textAlign = TextAlign.End),
                maxLines = 1,
            )
        }
    }
}

/**
 * 末行：`> █`。**有活动才闪，静止常亮。**
 *
 * 为什么不无条件闪：无限动画会让界面永远不进入 idle，`uiautomator dump` 直接报
 * `ERROR: could not get idle state`，release-check / style-shots 的取数链路全断（实测 6/8）。
 * 所以闪烁由 [isActive]（= 列表正在滚动，表冠或手指都算）门控：滚动时 8Hz 快闪，
 * 停下立刻常亮 —— 既保留"活着"的信号，又把 idle 窗口还给工具链。
 */
@Composable
private fun TerminalPromptLine(
    lineStyle: TextStyle,
    isActive: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    var cursorOn by remember { mutableStateOf(true) }
    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                delay(220)
                cursorOn = !cursorOn
            }
        } else {
            cursorOn = true
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(TerminalLineHeight)
            // 整行不透明：内容从输入行后面滚过去，这是真终端的行为。
            // 第一版只给文字铺了底色（还铺在 height 之前），结果块光标照样压在 `> 安全策略` 上。
            .background(SettingsV2Style.TERMINAL.identity.pageBackground)
            .testTag(TerminalPromptTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = TerminalPrompt + if (cursorOn) TerminalCursorBlock else " ",
                style = lineStyle,
                maxLines = 1,
                // 内边距加在文字上（不是 Box 上），否则底色会被一起缩进去
                modifier = Modifier.padding(start = 10.dp),
            )
            // 右侧状态段：`[2/4] 26L`（第几分区 / 总行数），CLI 底栏的常见信息
            BasicText(
                text = statusText,
                style = lineStyle.copy(color = SettingsV2Style.TERMINAL.identity.subtitleColor),
                maxLines = 1,
                modifier = Modifier.padding(end = 18.dp),
            )
        }
    }
}

/**
 * 右侧 ASCII 滚动条：`▓` 是滑块、`░` 是轨道，一列字符，不画任何图形。
 *
 * 行数固定 12，滑块位置由锚点项在总项数中的比例决定（不求精确，求"像 TUI"）。
 */
@Composable
private fun TerminalScrollBar(
    anchorIndex: Int,
    totalItems: Int,
    modifier: Modifier = Modifier,
) {
    val cells = 12
    val thumbSize = ((cells.toFloat() * cells) / totalItems.coerceAtLeast(cells))
        .toInt().coerceIn(1, cells)
    val maxStart = (cells - thumbSize).coerceAtLeast(0)
    val progress = if (totalItems > cells) {
        (anchorIndex.toFloat() / (totalItems - cells).toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val thumbStart = (progress * maxStart).toInt().coerceIn(0, maxStart)
    val bar = buildString {
        repeat(cells) { i ->
            append(if (i in thumbStart until (thumbStart + thumbSize)) TerminalThumbCell else TerminalTrackCell)
            if (i != cells - 1) append('\n')
        }
    }
    BasicText(
        text = bar,
        style = TextStyle(
            color = SettingsV2Style.TERMINAL.identity.subtitleColor,
            fontFamily = FontFamily.Monospace,
            fontSize = TerminalFontSize,
            lineHeight = TerminalLineHeight.value.sp,
        ),
        maxLines = cells,
        modifier = modifier.testTag(TerminalScrollBarTag),
    )
}

/** ASCII 滚动条的测试标识。 */
internal const val TerminalScrollBarTag = "terminal-scrollbar"

/**
 * 表冠滚动的"按行量化"：把累计像素换成整行数，余量留在累积器里。
 *
 * 真终端（`less` / `more`）是一行一行翻的，不是像素级滑动。表冠每转过一小段就滚**一整行**，
 * 这样内容永远落在字符网格上，不会出现"半行悬空"。
 *
 * @param accumulated 上一次留下的余量（|余量| 恒 < linePx）
 * @param delta 本次表冠给出的像素位移（正 = 向下滚）
 * @return first = 新的余量，second = 这次要滚的行数（可为 0/正/负）
 */
internal fun rotaryLinesToScroll(
    accumulated: Float,
    delta: Float,
    linePx: Float,
): Pair<Float, Int> {
    require(linePx > 0f) { "行高必须为正：$linePx" }
    val total = accumulated + delta
    val lines = (total / linePx).toInt() // 向零取整：不足一行不滚
    return (total - lines * linePx) to lines
}

/** 底部提示符的测试标识。 */
internal const val TerminalPromptTag = "terminal-prompt"

/** 表冠日志 tag（真机取证用）。 */
internal const val TerminalRotaryTag = "TerminalRotary"
