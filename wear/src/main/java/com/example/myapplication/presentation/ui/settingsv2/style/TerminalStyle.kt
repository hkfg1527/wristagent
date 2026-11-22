package com.example.myapplication.presentation.ui.settingsv2.style

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
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
import com.example.myapplication.presentation.ui.settingsv2.StyleEntryTag
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
internal const val TerminalValueTag = "terminal-value"
internal const val TerminalBannerTag = "terminal-banner"

/**
 * banner 前缀：**必须短到不换行**。
 *
 * 第一版是 `wristagent · TERMINAL · v`（27 字符前缀 + 版本号），实测被屏幕宽度截断成
 * `wristagent · TERMINAL▨` —— version 完全看不见（dump 里文本仍是完整的，所以截图才看得见问题）。
 * 现在 12 字符 + 版本号（共 21），12sp 等宽下约 300px，内容列约 394px 放得下；
 * 守卫 `bannerFitsOnOneLine` 把上限定在 26 字符（实测可容纳约 27）。
 */
internal const val TerminalHelpTag = "terminal-help"
internal const val TerminalHelpLine = "# [ ON ] 开关 · /open"
internal const val TerminalValveOwner = "terminal-page"
internal const val TerminalBannerPrefix = "wristagent v"

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
    onStylePicker: (() -> Unit)? = null,
    /** 选项行：点它或按 Enter 时交给宿主的选项列表。 */
    onChoice: ((SettingsV2Row) -> Unit)? = null,
    /** 开关行：把"拨了哪个开关"交给宿主写回偏好（本地 toggles 只负责即时反白）。 */
    onToggle: ((SettingsV2Row) -> Unit)? = null,
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

    // 列表里的基础行数（分区规则线 + 分区头 + 行）。提示符是钉在底部的，**不算**列表项。
    val baseItems = remember(sections) { 1 + sections.sumOf { 2 + it.rows.size } } // +1 = banner
    // 焦点行（反白）：视口中间那一项，只对可操作行生效
    val absIndex = remember(sections) { absoluteItemIndex(sections) }

    // 焦点行**粘住**：只在用户滚动时重算，程序化"跟随到底"不改变它。
    // 否则执行一条命令后输出把列表顶到底，锚点一跳、焦点就换行，再按 Enter 执行的是另一条（真机实测）。
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var programmaticScroll by remember { mutableStateOf(false) }
    LaunchedEffect(state.isScrollInProgress, state.anchorItemIndex) {
        if (state.isScrollInProgress && !programmaticScroll) {
            firstActionableAfter(sections, state.anchorItemIndex)?.let { selectedKey = it.key }
        }
    }
    val focusedRow = remember(sections, selectedKey) {
        selectedKey?.let { key -> sections.flatMap { it.rows }.firstOrNull { it.key == key } }
            ?: firstActionableAfter(sections, 0)
    }

    // 表冠：按"行"滚动（真终端语义），不是像素级滑动
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val linePx = with(LocalDensity.current) { TerminalLineHeight.toPx() }
    var rotaryRemainder by remember { mutableStateOf(0f) }
    // 真终端的行是"可执行对象"：点一下就有输出。这里做本地态 + 末尾回显（最多留 3 行）。
    val toggles = remember { mutableStateMapOf<String, Boolean>() }
    val echoes = remember { mutableStateListOf<EchoLine>() }
    var echoSeq by remember { mutableStateOf(0L) }
    fun pushEcho(line: String) {
        echoSeq += 1
        appendCappedEcho(echoes, EchoLine(echoSeq, line))
    }
    // 执行一行：点它、或按 Enter 执行当前焦点行，走的是同一条逻辑。
    // 选项行优先交给宿主的选项列表（否则只会打印一行 # open，什么都改不了）。
    fun activate(row: SettingsV2Row) {
        if (row.choice != null) {
            onChoice?.invoke(row)
            return
        }
        val checked = toggles[row.key] ?: (row.trailing as? RowTrailing.Toggle)?.checked ?: false
        when (row.trailing) {
            is RowTrailing.Toggle -> {
                toggles[row.key] = !checked
                onToggle?.invoke(row)
                pushEcho("# ${row.title} -> ${if (!checked) "ON" else "OFF"}")
            }
            is RowTrailing.Chevron -> pushEcho("# open ${row.title}")
            else -> Unit
        }
    }

    // Activity 层的 Enter 兜底（不依赖焦点）：Compose 的 onKeyEvent 在焦点丢失时收不到键事件。
    // **必须校验可见性**：页面可能仍留在组合里（切到聊天页时阀门会残留为 true），
    // 那时若不校验，隐藏的终端页会全局吞掉 ENTER（真机踩到：按 ENTER 没反应且回显不在屏上）。
    val hostView = LocalView.current
    val latestFocused = rememberUpdatedState(focusedRow)
    LaunchedEffect(Unit) {
        RotaryBus.activations.collect {
            // 取证用：把三个决定成败的状态一起打出来，失败时可归因（enabled / 可见 / 焦点行）
            android.util.Log.i(
                TerminalRotaryTag,
                "activation enabled=${RotaryBus.enabled} shown=${hostView.isShown} focused=${latestFocused.value?.key}",
            )
            if (hostView.isShown) latestFocused.value?.let { activate(it) }
        }
    }
    // 真终端打印输出后会**跟随到底**：不跟随的话，用户点了命令却看不到结果
    // （上一轮实测：点 /open 后要手动滑 6 次才看到 `# open …`）。
    // 用 scrollToItem 而不是 animateScrollToItem：`tail -f` 是瞬间跳到底，不做平滑滚动。
    LaunchedEffect(echoes.size) {
        if (echoes.isNotEmpty()) {
            // 标记成程序化滚动：这次跳转不许改动焦点行（否则执行完一条命令，焦点就换行了）
            programmaticScroll = true
            state.scrollToItem(baseItems + echoes.size - 1)
            programmaticScroll = false
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // 窗口重新获得焦点时必须**重新申请**：从后台切回（或从别的页面回来）后 Compose 焦点会丢，
    // 此时 Enter 会漏给系统、被系统当成"回桌面"处理 —— 真机实测按 ENTER 直接把 App 送到后台。
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused) {
        if (windowFocused) focusRequester.requestFocus()
    }

    // 表冠：Activity 层送来的滚动（不依赖焦点）。进入本页才打开安全阀，离开立刻关掉，
    // 免得把聊天页的表冠滚动一起吞了。
    DisposableEffect(Unit) {
        RotaryBus.acquire(TerminalValveOwner)
        onDispose { RotaryBus.release(TerminalValveOwner) }
    }
    LaunchedEffect(Unit) {
        RotaryBus.events.collect { delta ->
            val (rest, lines) = rotaryLinesToScroll(rotaryRemainder, delta, linePx)
            rotaryRemainder = rest
            if (lines != 0) state.scrollBy(lines * linePx)
        }
    }
    // 焦点行会随滚动变；Enter 通过 onKeyEvent 直接读它（同一帧内的最新值，无需 rememberUpdatedState）

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
            .focusable()
            // Enter 执行焦点行。必须接在 Compose 这一层：键事件先到 View 再漏到 Activity.onKeyDown，
            // 而 ENTER / SPACE / DPAD_CENTER 会被 Compose 焦点系统先吃掉（真机实测 Activity 那层收不到，
            // VOLUME 键才漏得过去 —— 这也是表冠走 Activity、Enter 走 Compose 的原因）。
            
    ) {
        TransformingLazyColumn(
            state = state,
            // 无 snapFling：终端连续滚动
            contentPadding = PaddingValues(start = 10.dp, end = 16.dp, top = 4.dp, bottom = 26.dp),
            // 显式 0 间距：默认的 4dp(8px) 项目间距会把行网格切成 36/44 交替（真机实测：
            // 项内 96->132 = 36px 正常，项间 132->176 = 44px）。字符网格必须是常数。
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // 开机 banner：真终端进来先打一行身份（这也是终端里唯一能看到版本号的地方）
            item(key = "banner") {
                TerminalGridLine {
                    BasicText(
                        text = TerminalBannerPrefix + com.example.myapplication.BuildConfig.VERSION_NAME,
                        style = dimStyle,
                        maxLines = 1,
                        modifier = Modifier.testTag(TerminalBannerTag),
                    )
                }
            }
            sections.forEach { section ->
                item(key = "r-" + section.title) {
                    // ASCII 规则线：`vim` / `less` 那种 banner 式分节，用字符铺出来，不画笔。
                    // 30 个 `─` ≈ 430px，正好铺满内容列（466 - 左右 26 - 滚动条 10）。
                    TerminalGridLine {
                        BasicText(
                            text = TerminalRule.repeat(TerminalRuleCells),
                            style = dimStyle,
                            maxLines = 1,
                            softWrap = false,
                            // 纯装饰：别让 TalkBack 把 30 个 `─` 念出来（真机开读屏时它会当内容读）
                            modifier = Modifier.clearAndSetSemantics { },
                        )
                    }
                }
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
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                enabled = row.trailing is RowTrailing.Toggle || row.trailing is RowTrailing.Chevron,
                            ) {
                                activate(row)
                            }
                            .testTag(TerminalRowTag)
                            // 读屏语义：`> 工具与 MCP   /open` 这种字符画对 TalkBack 只是乱码，
                            // 得说清楚"这行能干什么、现在是什么状态"。只读行不加描述（它只是信息）。
                            .semantics {
                                when (row.trailing) {
                                    is RowTrailing.Chevron -> contentDescription = "${row.title}，打开"
                                    is RowTrailing.Toggle -> contentDescription = "${row.title}，${if (checked) "开" else "关"}"
                                    else -> Unit
                                }
                            },
                    ) {
                        TerminalGridLine {
                            TerminalLine(
                                row = row,
                                lineStyle = lineStyle,
                                dimStyle = dimStyle,
                                toggleChecked = checked,
                                // 焦点行反白：只反白可操作行（只读信息没有"当前"概念）
                                focused = row.key == focusedRow?.key,
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
            echoes.forEach { echo ->
                item(key = "echo-" + echo.id) {
                    TerminalGridLine {
                        BasicText(text = echo.text, style = dimStyle, maxLines = 1)
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
            // 行数**包含回显**：回显也是行，滚动条总数早就把它算进去了（totalItems = baseItems + echoes），
            // 标签却只数配置行 —— 两处数字对不上是真的不一致，不是取舍。
            statusText = "${zoneOfAnchor(sections, state.anchorItemIndex, echoes.size)}/${sections.size}"
                + "·${sections.sumOf { it.rows.size } + echoes.size}L",
            onStylePicker = onStylePicker,
            onHelp = { pushEcho(TerminalHelpLine) },
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
    var start = 1 // banner 占首位
    sections.forEachIndexed { index, section ->
        val size = 2 + section.rows.size // 规则线 + 分区头 + 行
        if (anchorItemIndex < start + size) return index + 1
        start += size
    }
    // 锚点在回显区（或越界）时算最后一个分区
    return sections.size.coerceAtLeast(1)
}

/**
 * 每一行在列表里的**绝对项序号**（分区规则线 + 分区头 + 各行，按声明顺序累加）。
 *
 * 焦点行（反白）要按"视口中间那一项"算，所以必须能反查每行的绝对序号；纯函数便于单测。
 */
internal fun absoluteItemIndex(sections: List<SettingsV2Section>): Map<String, Int> = buildMap {
    var index = 1 // banner 占首位
    sections.forEach { section ->
        index += 2 // 规则线 + 分区头
        section.rows.forEach { row ->
            put(row.key, index)
            index++
        }
    }
}

/**
 * 锚点之后的第一条**可操作行**（TUI 里"当前可选项"的语义）。
 *
 * 第一版按"视口中间那一项"算，真机上中间那项是只读行（模型/温度），于是焦点行一个都没有。
 * 返回值是行对象本身：焦点要**粘住**它，而不是每次按锚点重算（否则执行命令后输出跟随到底，
 * 锚点一跳、焦点就跑到别的行上，再按 Enter 执行的是另一条命令 —— 真机实测踩到）。
 */
internal fun firstActionableAfter(sections: List<SettingsV2Section>, anchorItemIndex: Int): SettingsV2Row? {
    var index = 1 // banner 占首位
    sections.forEach { section ->
        index += 2 // 规则线 + 分区头
        section.rows.forEach { row ->
            val actionable = row.trailing is RowTrailing.Toggle || row.trailing is RowTrailing.Chevron
            if (actionable && index >= anchorItemIndex) return row
            index++
        }
    }
    return null
}

/** 兼容旧签名：返回焦点行的绝对项序号（无焦点时为 null）。 */
internal fun focusItemIndex(sections: List<SettingsV2Section>, anchorItemIndex: Int): Int? {
    val target = firstActionableAfter(sections, anchorItemIndex) ?: return null
    return absoluteItemIndex(sections)[target.key]
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
    focused: Boolean = false,
) {
    val identity = SettingsV2Style.TERMINAL.identity
    // 反白：底色亮绿、文字近黑（htop / nmtui 的"当前行"）
    val fg = if (focused) identity.pageBackground else lineStyle.color
    val dimFg = if (focused) identity.pageBackground else dimStyle.color
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focused) Modifier.background(identity.titleColor) else Modifier),
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
            style = lineStyle.copy(color = fg),
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
                modifier = Modifier.testTag(TerminalValueTag),
                // BasicText 没有 textAlign 形参：对齐必须写在 TextStyle 里
                style = (if (right.second) dimStyle else lineStyle)
                    .copy(textAlign = TextAlign.End, color = if (right.second) dimFg else fg),
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
    onStylePicker: (() -> Unit)?,
    onHelp: (() -> Unit)?,
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
            // 右侧聚簇：状态段 + 换风格入口**同一行**。
            // 不要用固定 78dp 预留位（第一版这么干，`[ 风格 ]` 被挤出屏幕、状态被截成 `[1/4]`）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 状态段本身就是帮助入口（点它打印"哪些东西可点"）——不额外占宽度，
                // 因为底栏已经放不下第四个元素（加 `[ ? ]` 后 `[ 风格 ]` 被裁成 `[`，真机截图可见）
                BasicText(
                    text = statusText,
                    style = lineStyle.copy(color = SettingsV2Style.TERMINAL.identity.subtitleColor),
                    maxLines = 1,
                    modifier = Modifier
                        .then(
                            if (onHelp != null) {
                                Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { onHelp() }
                                    .testTag(TerminalHelpTag)
                            } else {
                                Modifier
                            },
                        ),
                )
                if (onStylePicker != null) {
                    BasicText(
                        text = "[ 风格 ]",
                        style = lineStyle,
                        maxLines = 1,
                        modifier = Modifier
                            .padding(start = 8.dp, end = 12.dp)
                            .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onStylePicker() }
                            .testTag(StyleEntryTag),
                    )
                } else {
                    BasicText(text = " ", style = lineStyle, maxLines = 1, modifier = Modifier.padding(end = 12.dp))
                }
            }
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
        modifier = modifier
            .testTag(TerminalScrollBarTag)
            // 纯装饰：滚动条是 `▓/░` 字符，读屏念出来毫无意义（"方块方块浅色方块…"）
            .clearAndSetSemantics { },
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

/**
 * 回显行：**带自增 id**。
 *
 * 用文本当 key 会崩：执行同一条命令两次会产生两条相同文本 -> LazyColumn 报
 * `Key "echo-# open 工具与 MCP" was already used`（真机 FATAL，直接崩溃重启）。
 */
internal data class EchoLine(val id: Long, val text: String)

/**
 * 追加一行回显，并把总量压在 [cap] 行以内（真终端不会无限堆输出）。
 *
 * 泛型化是为了既能装 EchoLine、也能在测试里直接用字符串验证这条规则本身。
 */
internal fun <T> appendCappedEcho(target: MutableList<T>, line: T, cap: Int = 3) {
    target.add(line)
    while (target.size > cap) target.removeAt(0)
}
/** ASCII 规则线的字符（`vim` / `less` 的 banner 分节）。 */
internal const val TerminalRule = "─"

/** 规则线的字符数：30 × ~14.4px ≈ 430px，正好铺满内容列。 */
internal const val TerminalRuleCells = 30
