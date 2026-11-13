package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import com.example.myapplication.agent.AgentPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.rememberOverscrollEffect
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonDefaults
import androidx.wear.compose.material3.EdgeButtonSize
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.TextToggleButton
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.accentRailOf
import kotlinx.coroutines.launch

/**
 * 所有设置页共用的固定内容宽度：176dp。
 *
 * 不再用"屏宽减边距"推出来的动态值：宽度固定 + 两侧等量内缩，整块在圆内居中，
 * 而行内左右两条槽线（标签起笔 / 取值收笔）在全页任何一行都落在同一个 x 上，误差 0。
 * 176dp 比 184dp 安全宽窄 8dp，圆边处留出呼吸。
 */
val SettingsContentWidth: Dp = SettingsDesignMath.ContentWidthDp.dp

/** 卡片外壳最大宽度：233dp 屏 216dp，192dp 屏退回 176dp。 */
@Composable
fun settingsCardWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return SettingsCurveMath.cardWidthDp(screenWidthDp.toFloat()).dp
}

/** 列表内容盒两侧到屏幕边缘的内缩，保证卡片外壳在屏上居中。 */
@Composable
fun settingsCardScreenInset(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return SettingsCurveMath.screenInsetDp(screenWidthDp.toFloat()).dp
}

/** 单行 44dp 触控热区之间的最小留白。 */

/** 全页唯一一种线的厚度：1dp。组内行与行之间的分隔线也是它。 */
private val SettingsDividerThickness = SettingsSpacingMath.HairlineDp.dp

/**
 * 列表项之间的垂直留白：**0**。
 *
 * 同一组内的行必须严丝合缝拼成一张卡，列表因此不在项之间插空；分组的呼吸全部落在
 * 组标题上方的 24dp（[SettingsSectionGap]）里 —— "组间 24dp / 组内一根 1px 线"才读得出来。
 */
val SettingsRowGap: Dp = SettingsSpacingMath.CardRowGapDp.dp

/** 组标题下方到本组首行之间的留白，也是"每组第一行上方"的那一段空气。 */
private val SettingsSectionHeaderBottomGap: Dp = SettingsSpacingMath.SectionHeaderBottomDp.dp

/** 组间留白：24dp。组内只隔一根 1px 线，比例因此一眼可辨（旧的 4dp 行距已被卡片取代）。 */
val SettingsSectionGap: Dp = SettingsDesignMath.SectionGapDp.dp

/**
 * 根页列表顶部内边距：等于顶部渐变遮罩高度。
 *
 * 本页没有曲面时钟，遮罩只需要挡住"内容滚到圆屏最上沿"的那 24dp；
 * 标题必须从遮罩下沿才开始，否则它自己就被遮罩按住变暗 —— 那会让全屏唯一焦点丢掉对比度。
 */
val RootContentTopPadding: Dp = SettingsLayoutMath.TopScrimDp.dp

/**
 * 根页标题行内的顶部留白：遮罩 24dp + 列表顶部内边距 24dp + 行间距 4dp 之后**不需要**再补。
 *
 * 旧值 14dp 是给 34dp 时钟带让位的（本页没有时钟），把它和 8dp 内边距、12dp 行距叠起来
 * 就是顶部 41dp 纯黑 = 屏高 17.6%。现在归零。
 */
val RootTitleTopPadding: Dp = SettingsTopAnchorMath.titleTopPaddingDp(
    topInsetDp = SettingsLayoutMath.TopScrimDp,
    contentPaddingTopDp = RootContentTopPadding.value,
    itemGapDp = SettingsSpacingMath.CardRowGapDp
).dp

/** 子页头部行内的顶部留白：子页列表顶部内边距是 4dp（项间距已归 0），行内补齐到 24dp 遮罩下沿。 */
val SubPageHeaderTopPadding: Dp = SettingsTopAnchorMath.titleTopPaddingDp(
    topInsetDp = SettingsLayoutMath.TopScrimDp,
    contentPaddingTopDp = Wear.Space.Xs.value,
    itemGapDp = SettingsSpacingMath.CardRowGapDp
).dp

/** 文本弹窗里输入框的最大高度：再高就会把按钮顶出圆屏。 */
private val DialogFieldMaxHeight = 120.dp

fun Context.vibrateTick() {
    if (!AgentPreferences(this).hapticsEnabled) return
    try {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15L)
        }
    } catch (_: Exception) {}
}

/** 破坏性操作与真实提交的唯一确认震动：一次、短促，与滚动刻度震动彻底分开。 */
fun Context.vibrateConfirm() {
    if (!AgentPreferences(this).hapticsEnabled) return
    try {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20L)
        }
    } catch (_: Exception) {}
}

/**
 * 设置子弹窗类型
 */
enum class SettingsSubDialog {
    MODEL_CONFIG,
    PROVIDER_PROFILES,
    HYPER_PARAMS,
    TOOLS_MCP,
    REMOTE_CONSOLE,
    SECURITY_GUARDRAIL,

    /** 语音与唤醒：显式入口收敛后的唯一设置面。 */
    VOICE_ENTRY,

    /** 外观与点阵：纯黑 / 主题模式只读回显，简洁模式与点阵间距可改。 */
    APPEARANCE,

    /** 提示词中转页：系统提示词 + 会话与上下文合并后的根页入口。 */
    PROMPT_CONTEXT,

    /** 关于中转页：手势与侧键 / 重看引导 / 特权执行 / 网络体检的根页入口。 */
    ABOUT,

    /** 配置导入导出：整份可移植设置的 JSON 快照，可复制也可粘贴回来。 */
    TRANSFER
}

/**
 * 一条已登记的行：分区标题与内容行必须区分，因为只有内容行才会被排进间距节奏。
 *
 * [isSubItem] 表示它是某个组下的子项：整页唯一的线画在子项组的**上沿**，
 * 于是"线数 == 子项组数"，而同级行之间一根线都没有。
 */
internal class SettingsRowEntry(
    val isSection: Boolean,
    val content: @Composable () -> Unit,
    val isSubItem: Boolean = false,
    val kind: SettingsRowEntryKind = SettingsRowEntryKind.CARD_ROW
)

/**
 * 统一子页骨架的行登记表：每调用一次就登记一行，登记顺序即逐行动画的序号。
 *
 * 之所以先登记再渲染，是因为退场要"从末行往前起跑"——只有先知道总行数才能算出反向延迟。
 */
class SettingsRows internal constructor(
    private val pageTitle: String = ""
) {
    internal val items = mutableListOf<SettingsRowEntry>()

    /** 登记一行内容；[subItem] = true 表示它是某个组下的子项，用更短的分隔线。 */
    fun row(subItem: Boolean = false, content: @Composable () -> Unit) {
        items.add(
            SettingsRowEntry(
                isSection = false,
                content = content,
                isSubItem = subItem,
                kind = SettingsRowEntryKind.CARD_ROW
            )
        )
    }

    /**
     * 登记一块卡外内容（动作按钮、正文块这类不并卡的整块）：它把所在卡截断，
     * 前后各行各自成组 —— 「完成返回」「清空记忆」不会粘到上一张卡上。
     */
    fun standaloneRow(content: @Composable () -> Unit) {
        items.add(
            SettingsRowEntry(
                isSection = false,
                content = content,
                kind = SettingsRowEntryKind.STANDALONE
            )
        )
    }

    /** 登记一行分组标题；首组紧跟页头，不再重复组间留白。 */
    fun section(text: String) {
        val isFirstSection = items.isEmpty()
        if (!SettingsTextLayout.shouldRenderSectionHeader(text, pageTitle, isFirstSection)) return
        items.add(
            SettingsRowEntry(
                isSection = true,
                content = { SettingsSectionHeader(text = text, isFirstSection = isFirstSection) },
                kind = SettingsRowEntryKind.SECTION
            )
        )
    }
}

/**
 * 所有子页共用的页面骨架。
 *
 * 版面契约（与 [SettingsDesignMath] 一一对应）：
 * - 顶部：44dp 返回键 + 页面标题，整块落在 y=34dp 的曲面时钟带以下；
 * - 中部：ScalingLazyColumn，内容基准 [SettingsContentWidth]（176dp），卡片外壳最大 216dp；项间距 [SettingsRowGap]（0）；
 *   同组行拼成一张连续曲面卡，卡内行与行之间一根 1px [SettingsGroupHairline]（子项组上沿左端另收 12dp）；
 * - 组间距 24dp，只由 [SettingsSectionHeader] 的顶部留白提供；
 * - 底部：44dp「完成返回」行，滚动区底部预留 [Wear.Size.BottomBarReserve]；
 * - 入场沿用 [SettingsStaggeredRow]（逐行浮动 24dp→0 + 淡入，step 40ms，cap 360ms）。
 */
@Composable
fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    overlay: @Composable () -> Unit = {},
    rows: @Composable SettingsRows.() -> Unit
) {
    val stagger = rememberSettingsRowStaggerState()
    val exit = rememberSettingsExitController()

    val registry = SettingsRows(pageTitle = title)
    registry.rows()
    val content = registry.items
    // 组 = 一张卡：每行落在组里的哪个位置（组首 / 组中 / 组尾 / 单行）由行序列本身算出；
    // 组标题与卡外行（动作按钮）把卡截断。组内分隔线的左端缩进按"下一行是不是子项组首"算。
    val rowSlots = SettingsRowComposition.rowSlots(content.map { it.kind })

    val listState = rememberScalingLazyListState()
    val listFocusRequester = remember { FocusRequester() }

    // 子页切换时列表状态可能复用上一个页面的位置；标题变化先滚回页首。
    LaunchedEffect(title) { listState.scrollToItem(0) }

    SettingsTopAnchorProbe(listState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
            // 下滑退出：只有真实触摸、且列表已到顶时才累积；表冠不产生指针事件，天然进不来。
            // 到顶判据走 isAtScrollTop()，不能用 canScrollBackward（autoCentering 下恒为 true）。
            .pullDownToDismiss(canPullDown = { listState.isAtScrollTop() }, onDismiss = onBack)
    ) {
        // 表冠要焦点：等节点真正挂上树的那一帧再请求，首帧直接请求会落到未初始化的 requester 上。
        // 子页每次进入都是全新组合，按 Unit 触发即可，不需要外层再喂变化量。
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { listFocusRequester.requestFocus() }
        }

        ScreenScaffold(
            scrollState = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = settingsCardScreenInset(),
                end = settingsCardScreenInset(),
                top = Wear.Space.Xs
            ),
            timeText = null,
            scrollIndicator = null,
            edgeButton = {
                SettingsEdgeBackButton(
                    onClick = { exit.begin(onBack) },
                    exit = exit,
                    scrollState = listState
                )
            }
        ) { scaffoldPadding ->
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 顺序即作用域：处理器必须在 focusable 之外，否则 rotary 事件冒不进来
                .onRotaryScrollEvent {
                    // 表冠：同步吃位移，每格刻度都生效
                    listState.dispatchRawDelta(it.verticalScrollPixels)
                    true
                }
                .focusRequester(listFocusRequester)
                .focusable(),
            state = listState,
            // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
            flingBehavior = ScrollableDefaults.flingBehavior(),
            rotaryScrollableBehavior = null,
            overscrollEffect = null,
            // 关闭逐行等比缩放：曲面宽度与内容呼吸由卡片几何负责
            scalingParams = settingsScalingParams(),
            contentPadding = scaffoldPadding,
            // 项间距 0：同组的行必须严丝合缝拼成一张卡，分组的呼吸只由组标题上方的 24dp 承担
            verticalArrangement = Arrangement.spacedBy(SettingsRowGap),
            // 关掉 autoCentering：它会在首项之上插入
            // (viewportCenter − Σ前序高度) 的 spacer。头部行现在只有 32dp（不再为凑 spacer=0 而撑到
            // 86dp），算出来的 spacer 会是 26dp 级的空白 —— 那正是"顶部一大片黑再出现内容"的老问题。
            // 传 null 后顶部锚点就是 contentPadding.top，几何完全由常量决定（详见 SettingsTopAnchorMath）。
            autoCentering = null
        ) {
            val rowCount = content.size + 1

            item(key = "page-header") {
                SettingsStaggeredRow(index = 0, rowCount = rowCount, stagger = stagger, exit = exit) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        SettingsPageHeader(title = title)
                    }
                }
            }

            val chunks = SettingsPageChunking.chunks(content.map { it.kind })
            items(
                count = chunks.size,
                key = { chunkIndex -> "page-chunk-" + chunkIndex }
            ) { chunkIndex ->
                val chunk = chunks[chunkIndex]
                val firstIndex = chunk.indices.first()
                if (chunk.grouped) {
                    // 一组只画一张气球：所有行共享同一条闭合超椭圆，组内不再各自裁背景。
                    SettingsStaggeredRow(
                        index = firstIndex + 1,
                        rowCount = rowCount,
                        stagger = stagger,
                        exit = exit
                    ) {
                        SettingsBalloonGroup {
                            // 主气球上下各留一段纯曲面，首尾标题不会贴在收口尖上。
                            Spacer(Modifier.height(SettingsCurveGeometry.CapInsetDp.dp))
                            chunk.indices.forEach { index ->
                                val entry = content[index]
                                val next = content.getOrNull(index + 1)
                                val hairlineStart = SettingsRowComposition.groupHairlineStartIndentDp(
                                    isSubItem = next?.isSubItem ?: false,
                                    previousIsSubItem = entry.isSubItem
                                )
                                CompositionLocalProvider(
                                    LocalSettingsRowSlot provides rowSlots[index],
                                    LocalSettingsGroupHairlineStart provides hairlineStart.dp
                                ) {
                                    // 防止末端行的 hairline 判定跨出当前气球
                                    entry.content()
                                }
                            }
                            Spacer(Modifier.height(SettingsCurveGeometry.CapInsetDp.dp))
                        }
                    }
                } else {
                    val index = firstIndex
                    val entry = content[index]
                    val previous = content.getOrNull(index - 1)
                    val next = content.getOrNull(index + 1)
                    val hairlineStart = SettingsRowComposition.groupHairlineStartIndentDp(
                        isSubItem = next?.isSubItem ?: false,
                        previousIsSubItem = entry.isSubItem
                    )
                    val standaloneTopGap =
                        if (entry.kind == SettingsRowEntryKind.STANDALONE &&
                            previous != null && previous.kind == SettingsRowEntryKind.CARD_ROW
                        ) {
                            SettingsSectionGap
                        } else {
                            0.dp
                        }
                    SettingsStaggeredRow(
                        index = index + 1,
                        rowCount = rowCount,
                        stagger = stagger,
                        exit = exit
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            val entryWidth =
                                if (rowSlots[index] == null) SettingsContentWidth else settingsCardWidth()
                            CompositionLocalProvider(
                                LocalSettingsRowSlot provides rowSlots[index],
                                LocalSettingsGroupHairlineStart provides hairlineStart.dp
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(entryWidth)
                                        .padding(top = standaloneTopGap)
                                ) {
                                    entry.content()
                                }
                            }
                        }
                    }
                }
            }

        }
        }

        SettingsEdgeScrim()

        overlay()
    }
}

/**
 * 顶部/底部渐变遮罩：只在两端各压一条，内容滚到边缘时淡出而不是硬切。
 *
 * 纯绘制层，没有任何指针修改器，因此不会抢走列表的拖动、表冠或下拉退出手势。
 * 参数与 contentPadding 一一对应：遮罩高 [SettingsLayoutMath.TopScrimDp] /
 * [SettingsLayoutMath.BottomScrimDp]，底部可达高度 [Wear.Size.SettingsBottomInset] 大于后者，
 * 所以最后一行永远不会停在遮罩里。
 */
@Composable
fun SettingsEdgeScrim(modifier: Modifier = Modifier) {
    val topPx = SettingsLayoutMath.TopScrimDp.dp
    val bottomPx = SettingsLayoutMath.BottomScrimDp.dp
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithContent {
                val top = topPx.toPx()
                val bottom = bottomPx.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Wear.Palette.Bg, Color.Transparent),
                        startY = 0f,
                        endY = top
                    ),
                    topLeft = Offset.Zero,
                    size = Size(size.width, top)
                )
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Wear.Palette.Bg),
                        startY = size.height - bottom,
                        endY = size.height
                    ),
                    topLeft = Offset(0f, size.height - bottom),
                    size = Size(size.width, bottom)
                )
            }
    )
}

/**
 * 子页头部：只有页面标题，不重复一个左上角返回圆钮。
 *
 * 返回动作交给官方贴底 [EdgeButton]，标题仍是本屏**唯一**的视觉重点。
 */
@Composable
private fun SettingsPageHeader(title: String) {
    Row(
        modifier = Modifier
            .width(SettingsContentWidth)
            .heightIn(min = Wear.Size.Touch)
            .padding(top = SubPageHeaderTopPadding, bottom = Wear.Space.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            // 子页标题是本屏唯一的焦点：18sp / Medium，与根页的当前模型名同一档
            style = SettingsFocusStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 官方 M3E 贴底返回键：EdgeButton 的轮廓本身就是屏幕底部曲率的一部分，
 * 比左上角圆形按钮更符合圆屏与气球卡片的语言。
 */
@Composable
private fun BoxScope.SettingsEdgeBackButton(
    onClick: () -> Unit,
    exit: SettingsExitController,
    scrollState: ScalingLazyListState
) {
    if (WearAppearance.style == SettingsStyle.SQUARE) {
        SettingsSquareBackButton(onClick, exit, scrollState)
        return
    }
    val description = stringResource(R.string.settings_back)
    val density = LocalDensity.current
    val slidePx = with(density) { 56.dp.toPx() }
    EdgeButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .scrollable(
                state = scrollState,
                orientation = Orientation.Vertical,
                reverseDirection = true,
                overscrollEffect = rememberOverscrollEffect()
            )
            .graphicsLayer {
                val leave = exitFraction(exit.elapsedMs, 0, SettingsRowExitDurationMs)
                translationY = leave * slidePx
                alpha = 1f - leave
            }
            .semantics { contentDescription = description },
        buttonSize = EdgeButtonSize.ExtraSmall
    ) {
        Canvas(modifier = Modifier.size(EdgeButtonDefaults.ExtraSmallIconSize)) {
            val path = Path().apply {
                moveTo(size.width * 0.62f, size.height * 0.2f)
                lineTo(size.width * 0.34f, size.height * 0.5f)
                lineTo(size.width * 0.62f, size.height * 0.8f)
            }
            drawPath(
                path = path,
                color = Wear.Palette.Bg,
                style = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * 方角档的返回键：40dp 方框 + 1dp 描边 + 折角箭头。
 *
 * 与 [EdgeButton] 同一个底部槽位、同一套跟随列表滚动与淡出的动效，
 * 只是把"贴底圆弧"换成"直角方块"，与方角档的整页语言一致。
 */
@Composable
private fun BoxScope.SettingsSquareBackButton(
    onClick: () -> Unit,
    exit: SettingsExitController,
    scrollState: ScalingLazyListState
) {
    val description = stringResource(R.string.settings_back)
    val density = LocalDensity.current
    val slidePx = with(density) { 56.dp.toPx() }
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = Wear.Space.Sm)
            .size(40.dp)
            .scrollable(
                state = scrollState,
                orientation = Orientation.Vertical,
                reverseDirection = true,
                overscrollEffect = rememberOverscrollEffect()
            )
            .graphicsLayer {
                val leave = exitFraction(exit.elapsedMs, 0, SettingsRowExitDurationMs)
                translationY = leave * slidePx
                alpha = 1f - leave
            }
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(40.dp)) {
            val stroke = 1.dp.toPx()
            drawRect(
                color = Wear.Palette.Border,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
            val chevron = Path().apply {
                moveTo(size.width * 0.58f, size.height * 0.33f)
                lineTo(size.width * 0.42f, size.height * 0.5f)
                lineTo(size.width * 0.58f, size.height * 0.67f)
            }
            drawPath(
                path = chevron,
                color = Wear.Palette.TextPrimary,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }
    }
}

/**
 * 组标题：12sp / TextMuted / 字重 Medium / 字距 +0.5sp，左对齐到左槽线。
 *
 * - 中性色：强调色全页只留给"选中值 / 当前状态"这一处语义，组标题不属于它；
 * - 没有下方那条强调色细线，也没有下划线：一屏三根装饰线正是"AI 拼的"来源，
 *   组的边界改由 24dp 留白承担（组内只隔一根 1px 线，比例因此一眼可辨）；
 * - 上方 24dp 是**组间距**的唯一来源，下方 8dp 是标题与本组首行之间；
 * - 左内边距 14dp 与行标签同一条槽线：标题与行标签逐像素对齐。
 * - 左槽沟里一根 8dp × 2dp 的强调色短横是本页的组签名：它画在**沟里**、不占布局宽度，
 *   标题的起笔 x 因此一个像素都没动；8dp 与 14dp 槽线之间留 6dp，避免读成连字符。
 */
@Composable
fun SettingsSectionHeader(text: String, isFirstSection: Boolean = false) {
    val baseStyle = SettingsSectionStyle
    if (WearAppearance.style == SettingsStyle.SQUARE) {
        BasicText(
            text = text,
            modifier = Modifier
                .width(SettingsContentWidth)
                .padding(
                    top = SettingsDesignMath.sectionGapDp(isFirstSection).dp,
                    bottom = SettingsSectionHeaderBottomGap
                ),
            style = baseStyle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    val accent = Wear.Accent
    BasicText(
        text = text,
        modifier = Modifier.settingsSectionHeaderLayout(isFirstSection, accent),
        style = baseStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 短横画在 14dp 槽沟内，标题仍从同一条左槽线起笔；首组不重复页头后的留白。 */
private fun Modifier.settingsSectionHeaderLayout(isFirstSection: Boolean, accent: Color): Modifier = this
    .width(SettingsContentWidth)
    .padding(
        top = SettingsDesignMath.sectionGapDp(isFirstSection).dp,
        bottom = SettingsSectionHeaderBottomGap
    )
    .drawBehind { headerAccentMark(accent) }
    .padding(start = SettingsRowSidePadding)

private fun DrawScope.headerAccentMark(accent: Color) {
    val mark = SettingsSectionHeaderMark.mark(size.height / density)
    drawRect(
        color = accent,
        topLeft = Offset(mark.xDp.dp.toPx(), mark.yDp.dp.toPx()),
        size = Size(mark.widthDp.dp.toPx(), mark.heightDp.dp.toPx())
    )
}

/** 行内：左槽标签与右槽取值之间的最小呼吸。 */
private val SettingsLabelValueGap: Dp = SettingsSpacingMath.TitleToValueDp.dp

/** 行内：右槽取值与行尾控件之间的呼吸。 */
private val SettingsValueTailGap: Dp = SettingsSpacingMath.ValueToTailDp.dp

/** 右槽取值的宽度上限：再宽就把左槽标签压到折行。 */
private val SettingsValueMaxWidth: Dp = 92.dp

/** 行内两行文字之间的墨距：body 行盒自带上下内边距，这里只补一档最小呼吸。 */
private val SettingsRowLineGap: Dp = 2.dp

/** 副标题行上 6dp 语义色点与文字之间的呼吸：只占一档最小间距，宽度省给正文。 */
private val SettingsSubtitleStatusGap: Dp = Wear.Space.Xs

/** 行上下内边距：从 4dp 收到 2dp，把纵向预算还给内容。 */
private val RowVerticalPadding: Dp = 2.dp

/**
 * 行内左右内边距：14dp。
 *
 * 左端让标签起笔与组标题同一条槽线；右端把取值 / 圆点 / 状态点从卡片圆角上推回 14dp，
 * 旧版没有 end padding，取值是直接顶在圆角上的。
 */
val SettingsRowSidePadding: Dp = SettingsDesignMath.RowSidePaddingDp.dp

/**
 * 当前行在所属卡片里的位置，由列表构建侧（根页 / [SettingsSubPage]）按组提供。
 *
 * 放在 CompositionLocal 里而不是逐层传参：行的组合函数只关心"我是不是组首 / 组尾"，
 * 十几个调用点（子页各 section 文件）因此一行都不用改。null 表示这一行不在卡片里，
 * 行为退回旧形态（无卡片、无内边距、无线）。
 */
internal val LocalSettingsRowSlot = compositionLocalOf<SettingsRowSlot?> { null }

/**
 * 当前行卡尾那根组内分隔线的左端缩进，由列表构建侧按"下一行是不是子项组首"算好放入。
 * 默认取卡片左内边距（14dp）：行被用在列表之外时，卡与线都不存在，读不到它。
 */
internal val LocalSettingsGroupHairlineStart = compositionLocalOf { SettingsRowSidePadding }

/**
 * 行取值的可见门槛（屏宽 dp）：更窄的屏上不显示取值。
 *
 * 根页与所有子页共用 [SettingsNavRow]，这条规则因此只有一处实现。抽成纯函数是为了把
 * 200 / 201 的边界钉进单测 —— 写成 >= 在真机上就是 192dp 屏多出一条被截断的取值。
 */
internal const val SettingsRowValueMinScreenWidthDp = 200

/** 当前屏宽是否显示行取值：**严格大于** [SettingsRowValueMinScreenWidthDp] 才显示。 */
internal fun settingsRowShowsValue(screenWidthDp: Int): Boolean =
    screenWidthDp > SettingsRowValueMinScreenWidthDp

/** 组内分隔：一条 1 物理像素 Border 细线，右端保留 24dp 呼吸。 */
@Composable
private fun SettingsGroupHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsDividerThickness)
            .padding(
                start = LocalSettingsGroupHairlineStart.current,
                end = SettingsRowSidePadding
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val lineLength = SettingsGroupLineMath.lengthDp(size.width / density).dp.toPx()
            val centerY = size.height / 2f
            drawLine(
                color = Wear.Palette.Border,
                start = Offset(0f, centerY),
                end = Offset(lineLength, centerY),
                strokeWidth = SettingsGroupLineMath.StrokeWidthPx
            )
        }
    }
}

/**
 * 组卡片里的一行：曲面轮廓由 [LocalSettingsRowSlot] 与行在屏幕上的 y 共同决定。
 *
 * 三个问题在这一处一次解决：
 * 1. 每行一张方卡 → 每行取所在高度的圆弦半宽，组首/组尾加穹顶，组内拼成一张连续曲面；
 * 2. 取值贴死在圆角上 → 行内左右各 [SettingsRowSidePadding]（14dp），取值右端因此离卡片右缘 14dp；
 * 3. 不可点的纯展示行没有背景 → 背景不再与 onClick 绑定，展示行也是同一张卡的一部分，不留白洞。
 */
@Composable
internal fun SettingsCardRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    minHeight: Dp = SettingsSpacingMath.MinRowHeightDp.dp,
    content: @Composable () -> Unit
) {
    // 交互源只用来读"手指是否按住"：涟漪显式关掉，按下态由行底色承担（见 pressedRowBackground）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val clickModifier =
        if (onClick == null) {
            Modifier
        } else {
            Modifier.clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
        }
    val slot = LocalSettingsRowSlot.current
    val shape = SettingsRowComposition.shapeOf(slot)
    val rowBackground = pressedRowBackground(
        pressed = onClick != null && pressed,
        rest = if (shape == null) Color.Transparent else Wear.Palette.Surface
    )
    if (shape == null) {
        // 不在卡片里（行被用在列表之外）：退回旧形态，不擅自加内边距
        Box(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(rowBackground)
                .then(clickModifier)
        ) { content() }
        return
    }

    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightDp = configuration.screenHeightDp
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightPx = with(density) { screenHeightDp.dp.toPx() }
    val screenMarginPx = with(density) { SettingsCurveMath.ScreenEdgeMarginDp.dp.toPx() }
    val baseHalfPx = with(density) { SettingsCurveMath.MinCardWidthDp.dp.toPx() / 2f }
    val maxHalfPx = with(density) {
        (SettingsCurveMath.cardWidthDp(screenWidthDp.toFloat()).dp.toPx() / 2f)
    }
    val capHeightPx = with(density) { SettingsCurveGeometry.CapHeightDp.dp.toPx() }
    // y 只在绘制/图层阶段读；滚动时只失效绘制，不触发整行重组。
    val yInRootPx = remember { mutableStateOf(screenHeightPx / 2f) }
    val groupChrome = LocalSettingsGroupChrome.current
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.985f else 1f,
        animationSpec = Wear.Motion.micro(),
        label = "settingsRowPressScale"
    )

    if (WearAppearance.style == SettingsStyle.GOOGLE_CARD) {
        // 大卡片档：一行一张独立圆角卡，行与行之间靠 8dp 间隙分开，不再共用一张曲面。
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(SettingsContentWidth)
                    .heightIn(min = minHeight)
                    .clip(SettingsCardRowShape)
                    .background(SettingsCardSurface)
                    .then(clickModifier)
                    .padding(
                        start = SettingsRowSidePadding,
                        end = SettingsRowSidePadding,
                        top = RowVerticalPadding,
                        bottom = RowVerticalPadding
                    ),
                contentAlignment = Alignment.CenterStart
            ) { content() }
            Spacer(Modifier.height(SettingsCardStyle.Gap))
        }
        return
    }

    if (WearAppearance.style == SettingsStyle.SQUARE) {
        // 方角档：不画曲面、不做等比缩放，行与行之间只留一根发丝线。
        Column(
            modifier = modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .width(SettingsContentWidth)
                    .heightIn(min = minHeight)
                    .then(clickModifier)
                    .padding(
                        start = SettingsRowSidePadding,
                        end = SettingsRowSidePadding,
                        top = RowVerticalPadding,
                        bottom = RowVerticalPadding
                    ),
                contentAlignment = Alignment.CenterStart
            ) { content() }
            Box(
                modifier = Modifier
                    .width(SettingsContentWidth)
                    .height(1.dp)
                    .background(Wear.Palette.Border)
            )
        }
        return
    }

    if (groupChrome) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .onGloballyPositioned { coordinates ->
                    val newY = coordinates.positionInRoot().y
                    if (kotlin.math.abs(newY - yInRootPx.value) > 0.5f) yInRootPx.value = newY
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .width(SettingsContentWidth)
                    .graphicsLayer {
                        val rowCenterY = yInRootPx.value + size.height / 2f
                        val yFromCenterDp = (rowCenterY - screenHeightPx / 2f) / density.density
                        val halfDp = SettingsCurveMath.halfWidthDp(
                            yFromCenterDp = yFromCenterDp,
                            screenHeightDp = screenHeightDp.toFloat(),
                            maxHalfDp = maxHalfPx / density.density
                        )
                        // 只允许等比缩小；屏心 1.0，绝不做横向拉伸。
                        val scale = (halfDp / (SettingsCurveMath.MinCardWidthDp / 2f))
                            .coerceIn(0f, 1f) * pressScale
                        scaleX = scale
                        scaleY = scale
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = minHeight)
                        .then(clickModifier)
                        .padding(
                            start = SettingsRowSidePadding,
                            end = SettingsRowSidePadding,
                            top = RowVerticalPadding,
                            bottom = RowVerticalPadding
                        ),
                    contentAlignment = Alignment.CenterStart
                ) { content() }
                if (SettingsRowComposition.drawsGroupHairline(slot)) {
                    SettingsGroupHairline()
                }
            }
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                val newY = coordinates.positionInRoot().y
                if (kotlin.math.abs(newY - yInRootPx.value) > 0.5f) yInRootPx.value = newY
            }
            .drawWithContent {
                val path = SettingsCurveGeometry.cardPath(
                    size = size,
                    topInRootPx = yInRootPx.value,
                    screenHeightPx = screenHeightPx,
                    screenMarginPx = screenMarginPx,
                    capHeightPx = capHeightPx,
                    roundTop = shape.roundedTop,
                    roundBottom = shape.roundedBottom
                )
                drawPath(path = path, color = rowBackground)
                drawContent()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.width(SettingsContentWidth)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .then(clickModifier)
                    .padding(
                        start = SettingsRowSidePadding,
                        end = SettingsRowSidePadding,
                        top = RowVerticalPadding,
                        bottom = RowVerticalPadding
                    ),
                contentAlignment = Alignment.CenterStart
            ) { content() }
            if (SettingsRowComposition.drawsGroupHairline(slot)) {
                SettingsGroupHairline()
            }
        }
    }
}

/**
 * 可点行的按下底色：按下立刻提到 SurfaceAlt，松开按 [Wear.Motion.quick]（140ms）回落。
 *
 * 只换底色 —— 不缩放整行（相邻行会跟着跳），不加涟漪、不加边框闪烁：这是交互反馈，不是装饰。
 * [rest] 是静态底色：卡片里是 Surface，卡外行是透明，两者都回落到同一个 SurfaceAlt。
 */
@Composable
private fun pressedRowBackground(pressed: Boolean, rest: Color): Color {
    val lift = remember { Animatable(0f) }
    LaunchedEffect(pressed) {
        if (pressed) lift.snapTo(1f) else lift.animateTo(0f, Wear.Motion.quick())
    }
    return lerp(rest, Wear.Palette.SurfaceAlt, lift.value)
}

/**
 * 行骨架：块居中、行内左右两槽。
 *
 * 整块（所有行共用的 [SettingsContentWidth]）在圆内水平居中，行内部**不居中**：
 * 标签左对齐到左槽线，取值 / 控件右对齐到右槽线。
 *
 * 为什么不是"每行都居中"：列表里每一行都居中时没有左边缘可循，取值也对不齐，
 * 用户无法竖向扫读和比较 —— 这是"看起来像 AI 拼的"的第一号成因。
 * 为什么也不是"全部左对齐"：圆屏上的重心会掉到左边。整块居中 + 行内两槽是唯一解。
 *
 * 左右各 [SettingsRowSidePadding]（14dp）：左端让标签起笔与组标题同一条槽线，右端把取值从卡片圆角上推回 14dp。
 *
 * [subtitle] 属于左槽（标签的从属信息，另起一行），[value] 属于右槽（当前取值）。
 * [subItem] 只把左槽线右移 [SettingsSubItemIndent]：从属关系靠位置表达，**字号不变**。
 *
 * 副标题的折行规则按**内容**分：中文说明可以整段折行（[subtitleMaxLines] 默认 2），
 * 而模型名 / endpoint 这类机器标识符必须传 [subtitleMaxLines] = 1 —— 从词中间劈成
 * "deepseek-reaso" + "ner" 在列表里读起来像拼错，截断加省略号才像正常排版。
 * 副标题右侧不保留任何固定宽度的槽位：整行宽度都归这段文字，只有它自己会被截断。
 * [subtitleStatus] 是副标题行上的 6dp 语义色点（如"密钥未填写"），颜色不落到文字上。
 *
 * [subtitleBelowRow] = true 时副标题从标签列里搬出来，落到"标签 + 行尾控件"那一行的**下方**，
 * 横向独占整行内宽（内容盒 176dp − 两侧 14dp 内边距 = 148dp）：模型名这类机器标识符因此能整串放下。
 * 默认 false —— 其它页的副标题是中文说明，随标签列折行本来就正常，不需要这 148dp。
 */
@Composable
private fun SettingsSlotRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    subtitleStatus: SettingsStatusTone? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(subtitle),
    value: String? = null,
    onClick: (() -> Unit)? = null,
    titleMaxLines: Int = 2,
    valueMaxLines: Int = 1,
    subItem: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val valueBelowRow = SettingsTextLayout.shouldPlaceValueBelowRow(title, value)
    SettingsCardRow(modifier = modifier, onClick = onClick) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (subItem) Modifier.padding(start = SettingsSubItemIndent) else Modifier
                        )
                ) {
                    BasicText(
                        text = title,
                        style = SettingsRowLabelStyle,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null && !subtitleBelowRow) {
                        Spacer(Modifier.height(SettingsRowLineGap))
                        // 与标签同列时副标题只拿得到标签列剩下的宽度：右边没有固定槽位，超长才省略。
                        SettingsSubtitleLine(
                            subtitle = subtitle,
                            subtitleMaxLines = subtitleMaxLines,
                            subtitleStatus = subtitleStatus,
                            belowRow = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                // 右槽：取值靠右，右端落在行内边距以内；有行尾控件时控件才是最后一个元素
                if (value != null && !valueBelowRow) {
                    Spacer(Modifier.width(SettingsLabelValueGap))
                    BasicText(
                        text = value,
                        modifier = Modifier.widthIn(max = SettingsValueMaxWidth),
                        style = SettingsRowValueStyle,
                        maxLines = valueMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (trailing != null) {
                    Spacer(Modifier.width(SettingsValueTailGap))
                    trailing()
                }
            }
            // 标题 → 说明 → 值：长说明与长值分别独占整行，不再被右槽或单选点挤压。
            if (subtitle != null && subtitleBelowRow) {
                SettingsSubtitleLine(
                    subtitle = subtitle,
                    subtitleMaxLines = subtitleMaxLines,
                    subtitleStatus = subtitleStatus,
                    belowRow = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (subItem) SettingsSubItemIndent else 0.dp,
                            top = SettingsRowLineGap
                        )
                )
            }
            if (value != null && valueBelowRow) {
                BasicText(
                    text = value,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (subItem) SettingsSubItemIndent else 0.dp,
                            top = SettingsRowLineGap
                        ),
                    style = SettingsRowValueStyle.copy(textAlign = TextAlign.Start),
                    maxLines = Int.MAX_VALUE,
                    overflow = TextOverflow.Visible
                )
            }
        }
    }
}

/**
 * 副标题行：可选 6dp 语义色点 + 文字，撑满交给它的那块宽度。
 *
 * 抽成一处是因为副标题有两个落点（标签列内 / 第一行之下），而两条规则必须逐字一致：
 * 语义色只染点不染文字，超长一律尾部省略。
 */
@Composable
private fun SettingsSubtitleLine(
    subtitle: String,
    subtitleMaxLines: Int,
    subtitleStatus: SettingsStatusTone?,
    belowRow: Boolean,
    modifier: Modifier = Modifier
) {
    val wrapsFully = belowRow && subtitleMaxLines > 1
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (subtitleStatus != null) {
            SettingsStatusDot(tone = subtitleStatus, description = subtitle)
            Spacer(Modifier.width(SettingsSubtitleStatusGap))
        }
        BasicText(
            text = subtitle,
            modifier = Modifier.weight(1f, fill = false),
            style = SettingsRowValueStyle.copy(textAlign = TextAlign.Start),
            maxLines = if (wrapsFully) Int.MAX_VALUE else subtitleMaxLines,
            overflow = if (wrapsFully) TextOverflow.Visible else TextOverflow.Ellipsis
        )
    }
}

/**
 * 可进入行：标签左槽 + 取值右槽，没有容器也没有分隔线。
 *
 * 取值只在宽屏显示（见 [settingsRowShowsValue]），窄屏上整行只剩一行标签 ——
 * 根页与子页是同一份实现，左右槽线因此全站一致。
 *
 * 行尾标记二选一：有状态时是一块 [Wear.Size.StatusBlock] 的色块（状态文案转成无障碍描述，
 * 不再和大段文字抢注意力），没有状态时什么都不放。
 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    badge: Pair<String, Tone>? = null,
    modifier: Modifier = Modifier
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    SettingsSlotRow(
        title = title,
        value = if (value != null && settingsRowShowsValue(screenWidthDp)) value else null,
        modifier = modifier,
        onClick = onClick,
        trailing = if (badge == null) {
            null
        } else {
            { SettingsStatusBlock(description = badge.first, tone = badge.second) }
        }
    )
}

/**
 * 全页字号只许三档：组标题 12sp / 行文本 15sp / 唯一焦点 18sp。
 *
 * 层级靠**字重与位置**，不靠继续堆字号：行标签与行取值同为 15sp，
 * 二者的差别是颜色（主色 vs 次色）与槽位（左 vs 右），不是大小。
 */
/** 组标题档：12sp / TextMuted / Medium / 字距 +0.5sp。 */
internal val SettingsSectionStyle: TextStyle
    @Composable get() = Wear.TextCaption.copy(
        color = Wear.Palette.TextMuted,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp
    )

/** 行标签档：15sp / TextPrimary / Normal。 */
internal val SettingsRowLabelStyle: TextStyle
    @Composable get() = Wear.TextBody.copy(fontWeight = FontWeight.Normal)

/** 行取值档：15sp / TextSecondary / Normal，右槽对齐写在样式里（BasicText 没有 textAlign 参数）。 */
internal val SettingsRowValueStyle: TextStyle
    @Composable get() = Wear.TextRowValue.copy(fontWeight = FontWeight.Normal, textAlign = TextAlign.End)

/** 焦点档：18sp / TextPrimary / Medium，全页只给当前模型名和子页标题。 */
internal val SettingsFocusStyle: TextStyle
    @Composable get() = Wear.TextRowTitle.copy(fontWeight = FontWeight.Medium)

/** 子项行的左槽线右移量：字号不变，只把标签推后 12dp，值槽线不动。 */
val SettingsSubItemIndent: Dp = SettingsDesignMath.SubItemIndentDp.dp

/** 行尾状态色块：一块纯色就够表达状态，文案走无障碍描述。 */
@Composable
private fun SettingsStatusBlock(description: String, tone: Tone) {
    Box(
        modifier = Modifier
            .size(Wear.Size.StatusBlock)
            .clip(CircleShape)
            .background(tone.statusBlockColor(Wear.Accent))
            .semantics { contentDescription = description }
    )
}

/**
 * [Tone] 到令牌色的唯一映射，避免状态色散落在各处。
 *
 * 强调色那一档读不了投影 —— [Wear.AccentRail] 是 @Composable 的，这里是零 Compose 依赖的纯映射，
 * 所以由调用方在组合作用域把当前强调色传进来，轨道位系数仍只由 [accentRailOf] 定义。
 */
private fun Tone.statusBlockColor(accent: Color): Color = when (this) {
    // 静态属性不该是焦点：主色压到 60% 的轨道位（黑底 5.28:1），不再满饱和抢页面标题
    Tone.ACCENT -> accentRailOf(accent)
    Tone.DANGER -> Wear.Palette.Danger
    Tone.MUTED -> Wear.Palette.TextMuted
}

/** 开关行：左槽标签 + 右槽 44×26dp 胶囊开关；不再使用会吞掉整行宽度的 SwitchButton。 */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(secondary)
) {
    SettingsSlotRow(
        title = label,
        subtitle = secondary,
        subtitleMaxLines = Int.MAX_VALUE,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = { SettingsCompactSwitch(checked = checked) }
    )
}

@Composable
private fun SettingsCompactSwitch(checked: Boolean) {
    val stateLabel = stringResource(
        if (checked) R.string.settings_switch_on else R.string.settings_switch_off
    )
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = Wear.Motion.micro(),
        label = "settingsSwitchThumb"
    )
    val shape = CircleShape
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .clip(shape)
            .background(if (checked) Wear.Accent.copy(alpha = 0.82f) else Wear.Palette.SurfaceAlt)
            .border(1.dp, if (checked) Wear.Accent else Wear.Palette.Border, shape)
            .semantics {
                role = Role.Switch
                stateDescription = stateLabel
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (4f + 18f * progress).dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Wear.Palette.Orb else Wear.Palette.TextSecondary)
        )
    }
}

/**
 * 二值枚举行：官方 M3E [TextToggleButton]。
 *
 * Wear M3 1.7.1 里没有 Chip / SplitToggleChip（那是 material2 的组件族），
 * 分段开关在 M3E 由 TextToggleButton 承担，因此枚举切换走这里。
 */
@Composable
fun SettingsSplitToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(secondary)
) {
    SettingsSlotRow(
        title = label,
        subtitle = secondary,
        subtitleMaxLines = 2,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier,
        onClick = { onCheckedChange(!checked) },
        trailing = { SettingsCompactSwitch(checked = checked) }
    )
}

/** 细滑轨的几何常量（轨道 / 滑块 / 外圈）钉在 [SettingsSliderMath]，测试直接钉死防回漂。 */
private val SliderTrackHeight: Dp = SettingsSliderMath.TrackHeightDp.dp

private val SliderThumbSize: Dp = SettingsSliderMath.ThumbSizeDp.dp

private val SliderThumbRing: Dp = SettingsSliderMath.ThumbRingDp.dp

/** 滑轨的触控带高度：轨道只有 6dp，手指需要更高的一块命中区。 */
private val SliderTouchHeight: Dp = 24.dp

/**
 * 数值行：一条 6dp 细轨 + 18dp 圆形滑块，细粒度取值由 [SettingsValueMath] 量化。
 *
 * 标签 / 数值 / 滑轨三件，整体是组卡片里的一行：圆角与 14dp 内边距由 [SettingsCardRow] 统一给。
 */
@Composable
fun SettingsSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueText: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsCardRow(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = SettingsRowLabelStyle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(SettingsLabelValueGap))
                // 数值进右槽：与所有行的取值落在同一条右槽线上，不再用 titleMedium 抢焦点
                BasicText(
                    text = valueText,
                    modifier = Modifier.widthIn(max = SettingsValueMaxWidth),
                    style = SettingsRowValueStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            SettingsThinSlider(
                value = value,
                valueRange = valueRange,
                steps = steps,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 细滑轨 + 圆形滑块：已选段走运行时强调色，未选段走 SurfaceAlt；
 * 滑块 18dp 填充 TextPrimary，外圈 2dp 用轨道色，视觉上"压在轨道上"而不是融进去。
 *
 * 为什么不用官方 Slider：Wear M3 的 Slider 是"52dp 分段胶囊 + 两端 ± 图标"，整块铺满强调色，
 * 眯眼看是页面上最大的一块实色 —— 强调色在这里是重音，不该铺面积。轨道因此自己画、拖拽自己接。
 */
@Composable
private fun SettingsThinSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedBarColor = Wear.Accent
    val unselectedBarColor = Wear.Palette.SurfaceAlt
    val thumbColor = Wear.Palette.TextPrimary
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f
    val fraction = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(SliderTouchHeight)
            .pointerInput(valueRange, steps) {
                detectTapGestures { offset ->
                    onValueChange(
                        settingsSliderValueAt(
                            xPx = offset.x,
                            widthPx = size.width.toFloat(),
                            thumbRadiusPx = SliderThumbSize.toPx() / 2f,
                            valueRange = valueRange,
                            steps = steps
                        )
                    )
                }
            }
            .pointerInput(valueRange, steps) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange(
                        settingsSliderValueAt(
                            xPx = change.position.x,
                            widthPx = size.width.toFloat(),
                            thumbRadiusPx = SliderThumbSize.toPx() / 2f,
                            valueRange = valueRange,
                            steps = steps
                        )
                    )
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val trackHeightPx = SliderTrackHeight.toPx()
            val thumbRadiusPx = SliderThumbSize.toPx() / 2f
            val centerY = size.height / 2f
            val usableWidth = (size.width - thumbRadiusPx * 2f).coerceAtLeast(1f)
            val thumbCenterX = thumbRadiusPx + usableWidth * fraction
            val trackTop = centerY - trackHeightPx / 2f
            drawRoundRect(
                color = unselectedBarColor,
                topLeft = Offset(0f, trackTop),
                size = Size(size.width, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )
            drawRoundRect(
                color = selectedBarColor,
                topLeft = Offset(0f, trackTop),
                size = Size(thumbCenterX, trackHeightPx),
                cornerRadius = CornerRadius(trackHeightPx / 2f)
            )
            drawCircle(
                color = thumbColor,
                radius = thumbRadiusPx,
                center = Offset(thumbCenterX, centerY)
            )
            drawCircle(
                color = unselectedBarColor,
                radius = thumbRadiusPx - SliderThumbRing.toPx() / 2f,
                center = Offset(thumbCenterX, centerY),
                style = Stroke(width = SliderThumbRing.toPx())
            )
        }
    }
}

/**
 * 单选行：标签左槽 + 行尾圆点，不用官方 RadioButton（它自带另一套内边距与左对齐）。
 *
 * [dangerSelected] 只染**选中态那个圆点**：删数据 / 全权限 / 从不确认这类选项，
 * 危险语义落在控件本身上，不涂整行文字。
 *
 * 副标题带机器标识符（模型名 / endpoint）时传 [subtitleMaxLines] = 1：
 * 单行 + 尾部省略号，绝不把标识符从词中间劈成两行。
 *
 * [subtitleBelowRow] = true 时副标题改画在"标签 + 圆点"那一行的下方，横向独占整行内宽；
 * 行尾圆点因此不再从副标题嘴里分走 18dp + 10dp。默认 false，别的页一行都不用改。
 */
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    subtitleStatus: SettingsStatusTone? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(subtitle),
    dangerSelected: Boolean = false,
    dotColor: Color? = null
) {
    SettingsSlotRow(
        title = label,
        subtitle = subtitle,
        subtitleMaxLines = subtitleMaxLines,
        subtitleStatus = subtitleStatus,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier,
        onClick = onSelect,
        trailing = {
            SettingsRadioDot(selected = selected, danger = dangerSelected, dotColor = dotColor)
        }
    )
}

/**
 * 单选圆点：空心环 + 选中实心点，只表达状态，不做容器。
 *
 * 全页强调色的唯一落点就在"选中"这一件事上：未选中是中性 TextMuted，选中是运行时强调色
 * （危险选项为 Danger，且只染这一点）。[dotColor] 只给强调色档用：未选中时留一颗该档颜色
 * 的小点做预览，环仍是中性色 —— "选中"和"这个颜色长什么样"因此不会互相冒充。
 */
@Composable
private fun SettingsRadioDot(selected: Boolean, danger: Boolean = false, dotColor: Color? = null) {
    val selectedColor = dotColor ?: (if (danger) Wear.Palette.Danger else Wear.Accent)
    val ring = if (selected) selectedColor else Wear.Palette.TextMuted
    Box(
        modifier = Modifier
            .size(SettingsRadioDotSize)
            .clip(CircleShape)
            .border(SettingsRadioStroke, ring, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(SettingsRadioDotSize / 2)
                    .clip(CircleShape)
                    .background(selectedColor)
            )
        } else if (dotColor != null) {
            // 未选中的预设色只留一颗小色点做预览：环仍是中性色，"选中"和"这个颜色长什么样"不互相冒充
            Box(
                modifier = Modifier
                    .size(SettingsRadioDotSize / 3)
                    .clip(CircleShape)
                    .background(dotColor.copy(alpha = SettingsPreviewDotAlpha))
            )
        }
    }
}

private val SettingsRadioDotSize = 18.dp
private val SettingsRadioStroke = 2.dp

/** 未选中预设色点的预览透明度：够看清颜色，又不至于被误读成"已选中"。 */
private const val SettingsPreviewDotAlpha = 0.75f

/** 子项单选行：与 [SettingsRadioRow] 同一套圆点，只把左槽线右移 12dp（字号与右槽线不变）。 */
@Composable
fun SettingsSubRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleMaxLines: Int = 2,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(subtitle),
    dangerSelected: Boolean = false,
    dotColor: Color? = null
) {
    SettingsSlotRow(
        title = label,
        subtitle = subtitle,
        subtitleMaxLines = subtitleMaxLines,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier,
        onClick = onSelect,
        subItem = true,
        trailing = {
            SettingsRadioDot(selected = selected, danger = dangerSelected, dotColor = dotColor)
        }
    )
}

/**
 * 状态点的语义：设置页正文里唯一允许出现语义色的地方。
 *
 * 60-30-10 里的那 10 就是它：一页最多一个状态点。
 * 把 Success / Warning 涂在取值文字上的用法（大面积高饱和）已经全部撤掉。
 */
enum class SettingsStatusTone { OK, WARN }

/** 状态点：6dp 圆点，颜色只走语义色，文案走无障碍描述。 */
@Composable
fun SettingsStatusDot(tone: SettingsStatusTone, description: String) {
    val color = when (tone) {
        SettingsStatusTone.OK -> Wear.Palette.Success
        SettingsStatusTone.WARN -> Wear.Palette.Warning
    }
    Box(
        modifier = Modifier
            .size(Wear.Size.StatusBlock)
            .clip(CircleShape)
            .background(color)
            .semantics { contentDescription = description }
    )
}

/**
 * 状态行：标签左槽 + 取值右槽，状态的"好坏"只落在右端 6dp 状态点上。
 *
 * 取值回到中性的 TextSecondary：颜色退回点，文字回到灰阶，重点才立得住。
 */
@Composable
fun SettingsStatusRow(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    statusTone: SettingsStatusTone? = null
) {
    SettingsSlotRow(
        title = title,
        value = value,
        modifier = modifier,
        trailing = if (statusTone == null) {
            null
        } else {
            { SettingsStatusDot(tone = statusTone, description = title) }
        }
    )
}

/** 只读说明行：标签左槽 + 取值右槽，与可点行共用同两条槽线。 */
@Composable
fun SettingsInfoRow(
    title: String,
    value: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleBelowRow: Boolean = SettingsTextLayout.shouldPlaceSubtitleBelowRow(subtitle)
) {
    SettingsSlotRow(
        title = title,
        value = value,
        subtitle = subtitle,
        subtitleBelowRow = subtitleBelowRow,
        modifier = modifier.widthIn(max = SettingsContentWidth)
    )
}

/**
 * 状态标签/说明文本：默认 2 行上限 + 省略号，长文本不会顶出圆屏。
 *
 * 行数由内容定（[maxLines]）：URL 这类能在 "/" 处折行的是默认 2 行；
 * 探针明细、含原始模型 id 的提示这类机器串传 1 —— 折行会把它们从词中间劈开。
 *
 * 它也是一个卡片行（minHeight = 0，不撑 48dp 触控高度）：夹在两张行之间时若没有背景，
 * 卡片正中就会破出一个黑洞 —— 说明行同样是这一组的一部分。
 */
@Composable
fun SettingsCaptionRow(
    text: String,
    modifier: Modifier = Modifier,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE
) {
    SettingsCardRow(modifier = modifier, minHeight = 0.dp) {
        BasicText(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            style = if (mono) Wear.TextMono else Wear.TextCaption,
            maxLines = maxLines,
            overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Visible else TextOverflow.Ellipsis
        )
    }
}

/** 44dp 实底动作按钮：官方 Button 承载，色调取自 [Tone]；DANGER 即错误配色。 */
@Composable
fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Tone = Tone.MUTED
) {
    val container = when (tone) {
        Tone.ACCENT -> Wear.AccentSurface
        Tone.DANGER -> Wear.Palette.DangerSurface
        Tone.MUTED -> Wear.Palette.Surface
    }
    // 强调色按钮不再染文字与描边：全页强调色只留给"选中态"（圆点 / 滑轨）。
    // 主键身份由强调色的配套面实底（AccentSurface）+ 轨道位发丝线（AccentRail）承担，换色时一起跟随。
    val content = when (tone) {
        Tone.ACCENT -> Wear.Palette.TextPrimary
        Tone.DANGER -> Wear.Palette.Danger
        Tone.MUTED -> Wear.Palette.TextPrimary
    }
    val stroke = when (tone) {
        Tone.ACCENT -> Wear.AccentRail
        Tone.DANGER -> Wear.Palette.Danger
        Tone.MUTED -> Wear.Palette.Border
    }

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Wear.Palette.SurfaceAlt,
            disabledContentColor = Wear.Palette.TextSecondary
        ),
        border = BorderStroke(SettingsDividerThickness, if (enabled) stroke else Wear.Palette.Border),
        contentPadding = PaddingValues(
            horizontal = Wear.Space.Md,
            vertical = Wear.Space.Sm
        )
    ) {
        BasicText(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            style = Wear.TextBody.copy(
                color = content,
                textAlign = TextAlign.Center
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 文本输入入口：列表里只留一行可点区域，点开才是真正的输入对话框。
 * 列表里塞 TextField 会被表冠滚动与软键盘互相抢焦点，这一行不承载输入本身。
 */
@Composable
fun SettingsTextEntryRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SettingsSlotRow(
        title = label,
        value = if (value.isEmpty()) stringResource(R.string.settings_value_empty) else value,
        modifier = modifier,
        onClick = onClick
    )
}

/**
 * 文本对话框草稿：不可变，[edit] 返回新草稿，确认才产出要落盘的值，取消丢掉新草稿即可。
 *
 * 纯逻辑、不依赖 Compose，因此"编辑 / 确认 / 取消"三条路径可以直接单测。
 */
internal class SettingsDialogDraft(val openedValue: String, val text: String = openedValue) {
    /** 草稿是否偏离了打开时的值。 */
    val isDirty: Boolean get() = text != openedValue

    /** 编辑：返回新草稿，原草稿不被修改（取消就是把它丢掉）。 */
    fun edit(next: String): SettingsDialogDraft = SettingsDialogDraft(openedValue, next)

    /** 确认：返回要落盘的值；null 表示与初值一致、不必写。空串是合法提交，不能当成"没改"。 */
    fun commitValue(): String? = if (isDirty) text else null
}

/**
 * 设置页文本输入对话框：遮罩 + 卡片档圆角 + 官方 Button。
 *
 * **草稿模式**：编辑期间只改本地 [SettingsDialogDraft]，确认才通过 [onValueChange] 落盘，
 * 取消把草稿整个丢掉、一个字符都不写。旧实现逐字符实时落盘、取消时再写回 [initial] ——
 * 那是"回滚"不是"取消"：中途写坏的值早就落过盘，回滚只覆盖最后一次。
 */
@Composable
fun SettingsTextDialog(
    title: String,
    initial: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler { onDismiss() }

    val shape = MaterialTheme.shapes.large
    // 草稿只活在本地：打开时的值变了（父级换了目标）就重新开一份
    var draft by remember(initial) { mutableStateOf(SettingsDialogDraft(initial)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg.copy(alpha = 0.92f))
            .imePadding()
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = SettingsContentWidth)
                .fillMaxWidth()
                .clip(shape)
                .background(Wear.Palette.Surface)
                .border(SettingsDividerThickness, Wear.Palette.Border, shape)
                .padding(Wear.Space.Md),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            BasicText(
                text = title,
                // 对话框标题也落在焦点档（18sp / Medium）：全站字号只有三档
                style = SettingsFocusStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicTextField(
                value = draft.text,
                // 编辑只改草稿：确认之前一个字符都不外泄
                onValueChange = { draft = draft.edit(it) },
                textStyle = Wear.TextBody,
                cursorBrush = SolidColor(Wear.Palette.TextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Wear.Size.Touch, max = DialogFieldMaxHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(Wear.Palette.SurfaceAlt)
                    .padding(Wear.Space.Md),
                decorationBox = { innerTextField ->
                    if (draft.text.isEmpty()) {
                        BasicText(
                            text = placeholder,
                            style = Wear.TextCaption,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
            ) {
                SettingsActionButton(
                    label = stringResource(R.string.settings_dialog_cancel),
                    tone = Tone.MUTED,
                    modifier = Modifier.weight(1f),
                    // 取消：不碰 onValueChange，草稿随本组合一起销毁
                    onClick = onDismiss
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_dialog_confirm),
                    tone = Tone.ACCENT,
                    modifier = Modifier.weight(1f),
                    // 确认：草稿与初值不同才写；先通知再收尾（部分调用方在 onDismiss 里落库）
                    onClick = {
                        draft.commitValue()?.let(onValueChange)
                        onDismiss()
                    }
                )
            }
        }
    }
}

/** 主标签：1 行 + 省略号，官方组件内部的行内容统一走这里。 */
@Composable
private fun SettingsPrimaryLabel(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = Wear.TextBody,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 次标签：Caption 级，最多 2 行。 */
@Composable
private fun SettingsSecondaryLabel(text: String, maxLines: Int = 1) {
    BasicText(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        style = Wear.TextCaption,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}

/** 下滑退出手势阈值：松手时位移超过该值即退场，否则回弹复位。 */
val PullDownDismissThreshold = 50.dp

/** 临时诊断开关：真机确认手势路径后置 false 即彻底消音（release 包也带日志，便于直接装 release 验证）。 */
// 生产默认关闭：打开后每次下拉动作都会打坐标日志。留着是为了下次排查手势问题时能一行改回来。
private const val PullDownDismissDebug = false

private const val PullDownDismissLogTag = "PullDownDismiss"

/**
 * 列表是否停在顶部（= 已无法再向上滚）。
 *
 * 不能用 [ScalingLazyListState.canScrollBackward]：它直通底层 LazyListState 的
 * `firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > 0`，而 ScalingLazyColumn 在
 * autoCentering 开启时会额外插入一个头部 Spacer 作为 lazy index 0，并把 scaling index N 映射成
 * lazy index N+1（ScalingLazyListState 内部 `targetIndex + if (autoCentering != null) 1 else 0`）。
 * 因此停在本页视觉顶部时 firstVisibleItemIndex 恒为 1 —— canScrollBackward 永远为 true，
 * 一旦拿它当"到顶"判据，下拉量永远进不了累计：页面不跟手、松手也不退场。
 *
 * 改用 scaling 空间的可见项判断：visibleItemsInfo 已剔除两侧 Spacer，首项 index == 0 即视觉顶部。
 * 列表只要滚离顶部，首项 index 立刻 > 0，中部拖动一个像素都不会被吃。
 * （设置页现在传 autoCentering = null，这条判据在新配置下依然成立，因此不必再分两种情形。）
 */
internal fun ScalingLazyListState.isAtScrollTop(): Boolean {
    val first = layoutInfo.visibleItemsInfo.firstOrNull() ?: return true
    return first.index == 0
}

/** 顶部对齐诊断开关：真机确认"滚到最顶不留空白"后置 false 消音（release 包也带日志，便于直接装 release 验证）。 */
// 生产默认关闭：打开后 SettingsTopAnchorProbe 会在**每次布局变化**时 snapshotFlow 收集一次
// 列表几何（含 isAtScrollTop()）并打日志——这是调试探针，不是常驻功能。
private const val SettingsTopAnchorDebug = false

private const val SettingsTopAnchorLogTag = "SettingsTopAnchor"

/**
 * 顶部对齐的真机探针：把首项在视口里的几何打出来。
 *
 * 判据（配合 [SettingsTopAnchorMath]）：停在顶部时 firstIndex == 0、topItemTopPx 应等于
 * "列表顶部内边距 + 行间距"（根页 ≈ 16dp、子页 ≈ 12dp 的像素值），而不是半屏；
 * scale / alpha 应接近 1（说明标题没被当作边缘项缩小淡出）。
 */
@Composable
internal fun SettingsTopAnchorProbe(state: ScalingLazyListState) {
    if (!SettingsTopAnchorDebug) return
    LaunchedEffect(state) {
        snapshotFlow {
            val info = state.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            listOf(
                first?.index ?: -1,
                info.viewportSize.height,
                if (first == null) -1 else info.viewportSize.height / 2 + first.offset - first.size / 2,
                first?.scale ?: 0f,
                first?.alpha ?: 0f,
                if (state.isAtScrollTop()) 1 else 0
            )
        }.collect { values ->
            Log.d(
                SettingsTopAnchorLogTag,
                "firstIndex=" + values[0] + " viewportPx=" + values[1].toInt() +
                    " topItemTopPx=" + values[2].toInt() +
                    " scale=" + values[3] + " alpha=" + values[4] +
                    " atScrollTop=" + values[5].toInt()
            )
        }
    }
}

/**
 * 设置页"下滑退出"的共享手势实现（根页与子页共用同一套判定）。
 *
 * - 判定只看指针事件流：按下（[awaitFirstDown]）→ 逐帧位移 → 抬起。表冠/滚轮不产生指针事件，
 *   因此无论转多少格都进不到这条路径，"转到顶就退页"不可能发生。
 * - 观察者挂在 [PointerEventPass.Initial] 且永不 consume，也不接 nestedScroll：
 *   列表的拖动与松手后的惯性一个像素都不经过这里。
 * - 到顶判据逐帧求值（[canPullDown]）并在一次手势内锁存"曾到顶"：位移发生在到顶那一帧之后时
 *   仍然计入下拉量，不会因为差一帧读数就整段作废。
 * - 松手累计超过 [PullDownDismissThreshold] 播退场并回调 [onDismiss]，否则回弹复位。
 * - 修饰符顺序有意义：pointerInput 在 graphicsLayer 之前，手势读到的是未平移的坐标，
 *   不会出现"自身平移又把局部坐标算回去"的正反馈闪屏。
 */
@Composable
private fun Modifier.pullDownDismissGesture(
    canPullDown: () -> Boolean,
    onDismiss: () -> Unit,
): Modifier {
    val thresholdPx = with(LocalDensity.current) { PullDownDismissThreshold.toPx() }
    val scope = rememberCoroutineScope()
    var offsetPx by remember { mutableFloatStateOf(0f) }
    var pageHeightPx by remember { mutableFloatStateOf(1f) }
    var isDismissing by remember { mutableStateOf(false) }
    val currentCanPullDown by rememberUpdatedState(canPullDown)
    val currentDismiss by rememberUpdatedState(onDismiss)

    return this
        .onGloballyPositioned { coords ->
            pageHeightPx = coords.size.height.toFloat().coerceAtLeast(1f)
        }
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    if (isDismissing) continue
                    var pull = 0f
                    var lastY = down.position.y
                    var reachedTop = false
                    var moves = 0
                    var aborted = false
                    var released = false
                    if (PullDownDismissDebug) {
                        Log.d(
                            PullDownDismissLogTag,
                            "down y=${down.position.y} canPullDown=${currentCanPullDown()}"
                        )
                    }
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            aborted = true
                            break
                        }
                        val dy = change.position.y - lastY
                        lastY = change.position.y
                        moves++
                        // 到顶判据逐帧求值 + 本次手势内锁存：列表到顶那一帧之后的下拽都要算数
                        val atTop = currentCanPullDown()
                        if (atTop) reachedTop = true
                        if (dy > 0f && (atTop || reachedTop)) {
                            if (PullDownDismissDebug && pull == 0f) {
                                Log.d(
                                    PullDownDismissLogTag,
                                    "firstPull dy=$dy atTop=$atTop reachedTop=$reachedTop"
                                )
                            }
                            pull += dy
                            // 受限挂起作用域（awaitPointerEventScope）里不能调 Animatable，
                            // 直接写状态：零延迟跟随手指，也不占用帧回调
                            offsetPx = pull
                        }
                        if (!change.pressed) {
                            released = true
                            break
                        }
                    }
                    if (PullDownDismissDebug) {
                        Log.d(
                            PullDownDismissLogTag,
                            "up pullPx=$pull thresholdPx=$thresholdPx moves=$moves " +
                                "atTopSeen=$reachedTop released=$released aborted=$aborted " +
                                "verdict=${if (released && pull > thresholdPx) "dismiss" else "bounce"}"
                        )
                    }
                    when {
                        aborted -> offsetPx = 0f
                        released && pull > thresholdPx -> {
                            isDismissing = true
                            scope.launch {
                                animate(offsetPx, pageHeightPx, animationSpec = tween(140)) { value, _ ->
                                    offsetPx = value
                                }
                                currentDismiss()
                            }
                        }
                        pull > 0f -> scope.launch {
                            animate(pull, 0f, animationSpec = Wear.Motion.micro()) { value, _ ->
                                offsetPx = value
                            }
                        }
                    }
                }
            }
        }
        .graphicsLayer {
            translationY = offsetPx
            alpha = (1f - offsetPx / (pageHeightPx * 1.5f)).coerceIn(0.25f, 1f)
        }
}

/** 子页入口：与 [PullDownDismissContainer] 共用同一套手势判定。 */
@Composable
fun Modifier.pullDownToDismiss(
    canPullDown: () -> Boolean,
    onDismiss: () -> Unit,
): Modifier = pullDownDismissGesture(canPullDown = canPullDown, onDismiss = onDismiss)

/** 设置页"下滑退出"容器：与"主键上滑进入"对称的反向退出。 */
@Composable
fun PullDownDismissContainer(
    canPullDown: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pullDownDismissGesture(canPullDown = canPullDown, onDismiss = onDismiss)
    ) {
        content()
    }
}

/** 设置页状态徽章的资源映射：状态 -> 文案资源，纯函数便于单测。 */
internal object SettingsStatusTextRes {
    /** 模型与服务卡片的配置状态：已配置走强调色，未配置走警示色。 */
    fun modelServiceBadge(configured: Boolean): Pair<Int, Tone> =
        if (configured) {
            R.string.settings_badge_configured to Tone.ACCENT
        } else {
            R.string.settings_badge_unconfigured to Tone.DANGER
        }
}
