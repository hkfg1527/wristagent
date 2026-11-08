package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.TextToggleButton
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.launch

/** 所有设置页共用的内容宽度上限：按圆周弦长推导，硬上限 168dp。 */
val SettingsContentWidth: Dp = SettingsLayoutMath.ContentWidthDp.dp

/**
 * 当前屏宽下的内容盒左右内缩（dp）：弦长推出来的值，真机 233dp 屏为 32.5dp。
 *
 * 用它替代固定的 12dp 内边距，窄屏（192dp）上的内容才不会画到圆周外。
 */
@Composable
fun settingsSideInset(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return SettingsLayoutMath.sideInsetDp(screenWidthDp.toFloat()).dp
}

/** 单行 44dp 触控热区之间的最小留白。 */
val SettingsTouchGap: Dp = Wear.Space.Sm

/** 行与行之间的唯一分隔物：1dp 细线，取代原来的"每行一张卡片"。 */
private val SettingsDividerThickness = SettingsSpacingMath.HairlineDp.dp

/** 相邻两行之间的垂直留白：把每行切成独立的一块，而不是连成一堵墙。 */
val SettingsItemGap: Dp = SettingsSpacingMath.ItemGapDp.dp

/** 组标题下方到本组首行之间的留白，也是"每组第一行上方"的那一段空气。 */
private val SettingsSectionHeaderBottomGap: Dp = SettingsSpacingMath.SectionHeaderBottomDp.dp

/** 行内：取值与行尾标记（箭头 / 状态色块）之间的留白。 */
private val SettingsValueTailGap: Dp = SettingsSpacingMath.ValueToTailDp.dp

/** 分区之间的间距：上一区末行与本区标题之间留 16dp。 */
private val SettingsSectionGap = Wear.Space.Lg

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
    itemGapDp = SettingsSpacingMath.ItemGapDp
).dp

/** 子页头部行内的顶部留白：子页列表顶部内边距是 4dp，行内补齐到 24dp 遮罩下沿。 */
val SubPageHeaderTopPadding: Dp = SettingsTopAnchorMath.titleTopPaddingDp(
    topInsetDp = SettingsLayoutMath.TopScrimDp,
    contentPaddingTopDp = Wear.Space.Xs.value,
    itemGapDp = SettingsSpacingMath.ItemGapDp
).dp

/** 文本弹窗里输入框的最大高度：再高就会把按钮顶出圆屏。 */
private val DialogFieldMaxHeight = 120.dp

fun Context.vibrateTick() {
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

    /** 关于中转页：快捷操作 / 重看引导 / 特权执行 / 网络体检的根页入口。 */
    ABOUT
}

/** 一条已登记的行：分区标题与内容行必须区分，因为只有内容行之间才画分隔线。 */
internal class SettingsRowEntry(
    val isSection: Boolean,
    val content: @Composable () -> Unit
)

/**
 * 统一子页骨架的行登记表：每调用一次就登记一行，登记顺序即逐行动画的序号。
 *
 * 之所以先登记再渲染，是因为退场要"从末行往前起跑"——只有先知道总行数才能算出反向延迟。
 */
class SettingsRows internal constructor() {
    internal val items = mutableListOf<SettingsRowEntry>()

    /** 登记一行内容。 */
    fun row(content: @Composable () -> Unit) {
        items.add(SettingsRowEntry(isSection = false, content = content))
    }

    /** 登记一行分组标题。 */
    fun section(text: String) {
        items.add(SettingsRowEntry(isSection = true, content = { SettingsSectionHeader(text = text) }))
    }
}

/**
 * 所有子页共用的页面骨架。
 *
 * 版面契约（与 [SettingsLayoutMath] 一一对应）：
 * - 顶部：44dp 返回键 + 页面标题，整块落在 y=34dp 的曲面时钟带以下；
 * - 中部：ScalingLazyColumn，内容宽度上限 168dp，行间距 [SettingsItemGap]，行与行之间只有一条 1dp 分隔线；
 * - 分区间距 16dp，只由 [SettingsSectionHeader] 的顶部留白提供；
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

    val registry = SettingsRows()
    registry.rows()
    val content = registry.items

    val listState = rememberScalingLazyListState()
    val listFocusRequester = remember { FocusRequester() }

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
            // 曲率缩放关死：边缘行不再缩到 75% 并淡出，同一行在任何滚动位置尺寸一致 ——
            contentPadding = PaddingValues(
                start = settingsSideInset(),
                end = settingsSideInset(),
                top = Wear.Space.Xs,
                bottom = Wear.Size.SettingsBottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
            // 关掉 autoCentering：它会在首项之上插入
            // (viewportCenter − Σ前序高度) 的 spacer。头部行现在只有 32dp（不再为凑 spacer=0 而撑到
            // 86dp），算出来的 spacer 会是 26dp 级的空白 —— 那正是"顶部一大片黑再出现内容"的老问题。
            // 传 null 后顶部锚点就是 contentPadding.top，几何完全由常量决定（详见 SettingsTopAnchorMath）。
            autoCentering = null
        ) {
            val rowCount = content.size + 2

            item(key = "page-header") {
                SettingsStaggeredRow(index = 0, rowCount = rowCount, stagger = stagger, exit = exit) {
                    SettingsPageHeader(title = title, onBack = { exit.begin(onBack) })
                }
            }

            items(
                count = content.size,
                key = { index -> "page-row-" + index }
            ) { index ->
                val entry = content[index]
                val previous = content.getOrNull(index - 1)
                // 只有"上一条也是内容行"时才加分隔线：分区标题与紧随其后的首行之间不画。
                val needsDivider = !entry.isSection && previous != null && !previous.isSection
                SettingsStaggeredRow(index = index + 1, rowCount = rowCount, stagger = stagger, exit = exit) {
                    Column(modifier = Modifier.widthIn(max = SettingsContentWidth)) {
                        if (needsDivider) SettingsRowDivider()
                        entry.content()
                    }
                }
            }

            item(key = "page-footer") {
                SettingsStaggeredRow(index = rowCount - 1, rowCount = rowCount, stagger = stagger, exit = exit) {
                    SettingsActionButton(
                        label = stringResource(R.string.settings_done_back),
                        tone = Tone.MUTED,
                        modifier = Modifier.widthIn(max = SettingsContentWidth),
                        onClick = { exit.begin(onBack) }
                    )
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

/** 行间 1dp 分隔线：页面里唯一的"容器"，取代逐行卡片。 */
@Composable
fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsDividerThickness)
            // 分隔线内缩与行文字同一条左边界，不再用独立的 HairlineInset
            .padding(horizontal = SettingsRowInset)
            .background(Wear.Palette.Border)
    )
}

/**
 * 子页头部：44dp 返回键 + 页面标题，整块压在曲面时钟带以下。
 *
 * 标题用 titleLarge 级 + 主色，是本屏**唯一**的视觉重点；下面所有行一律降为 secondary。
 */
@Composable
private fun SettingsPageHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .padding(top = SubPageHeaderTopPadding, bottom = Wear.Space.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
    ) {
        SettingsBackButton(onClick = onBack)
        BasicText(
            text = title,
            modifier = Modifier
                .widthIn(min = SettingsLayoutMath.SubtitleMinWidthDp.dp)
                .weight(1f),
            style = Wear.TextDisplayTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 44dp 返回键：官方 IconButton + 令牌配色，图标用 Canvas 画折线，不引入图标依赖。 */
@Composable
fun SettingsBackButton(onClick: () -> Unit) {
    val description = stringResource(R.string.settings_back)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(Wear.Size.Touch)
            .semantics { contentDescription = description },
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Wear.Palette.Surface,
            contentColor = Wear.Palette.TextPrimary
        ),
        border = BorderStroke(SettingsDividerThickness, Wear.Palette.Border)
    ) {
        Canvas(modifier = Modifier.size(Wear.Space.Lg)) {
            val path = Path().apply {
                moveTo(size.width * 0.62f, size.height * 0.2f)
                lineTo(size.width * 0.34f, size.height * 0.5f)
                lineTo(size.width * 0.62f, size.height * 0.8f)
            }
            drawPath(
                path = path,
                color = Wear.Palette.TextPrimary,
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
 * 分组标题：Caption 级小标题，主色。
 *
 * 顶部 16dp 是**分区间距**的唯一来源；下方 12dp 是"每组第一行上方"的那段空气，
 * 比行内留白更硬，让标题明显属于它下面那一组，而不是悬在两组之间。
 * 用主色而不是正文色：标题本身是结构信息，行内容已经全部降为 secondary/muted。
 *
 * [center] 默认 true：整页的文字轴已经居中，组标题留在左边会成为唯一的例外。
 * 需要左对齐时显式传 false，既有调用点一个都不用改。
 */
@Composable
fun SettingsSectionHeader(text: String, center: Boolean = false) {
    if (center) {
        BasicText(
            text = text,
            modifier = Modifier
                .widthIn(max = SettingsContentWidth)
                .fillMaxWidth()
                .padding(top = SettingsSectionGap, bottom = SettingsSectionHeaderBottomGap),
            style = Wear.TextCaption.copy(color = Wear.Palette.TextSecondary, textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        return
    }
    // 不再画前置短线：那 12dp 横线 + 8dp 空隙把标题文字推到 20dp，
    // 而行文字在 8dp，同一屏就出现两条左轴（实测偏差 12dp，肉眼可见）。
    // 分组现在只靠上方 16dp 留白区分（亲密性原则：组间 > 组内），标题与行共用同一条左边界。
    BasicText(
        text = text,
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .padding(start = SettingsRowInset)
            .padding(top = SettingsSectionGap, bottom = SettingsSectionHeaderBottomGap),
        style = Wear.TextCaption.copy(color = Wear.Palette.TextSecondary),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** 行的左右内缩：组标题、行文字、分隔线共用这一条左边界。 */
private val SettingsRowInset = Wear.Space.Sm

/** 行尾安全区：箭头图形盒 + 取值↔尾标的呼吸。行内文字左右各让出同样的宽度。 */
private val SettingsRowTailZone: Dp =
    (SettingsLayoutMath.ChevronGlyphDp + SettingsSpacingMath.ValueToTailDp).dp

/** 行内两行文字之间的墨距：body 行盒自带上下内边距，这里只补一档最小呼吸。 */
private val SettingsRowLineGap: Dp = 2.dp

/** 行上下内边距：从 4dp 收到 2dp，把纵向预算还给内容。 */
private val RowVerticalPadding: Dp = 2.dp

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

/**
 * 行骨架：标题 +（可选）取值 + 行尾标记，三段全部相对**整行**居中。
 *
 * 取值不和标题挤同一行，是几何上的硬约束：内容盒 168dp 扣掉行内左右各 8dp 只剩 152dp，
 * 行尾标记还要占掉右端 20dp。同行摆放时标题的盒子只有 87dp（见
 * [SettingsLayoutMath.titleBudgetDp]），它的中心离整行中心差 32.5dp —— 这正是"加了
 * textAlign = Center 仍然看着偏左"的成因：textAlign 只在**自己的盒子**里居中。
 * 取值另起一行后，两行文字落在同一个对称盒子里，视觉中心才与屏幕中心重合。
 *
 * 行高：单行行 = 44dp（[Wear.Size.Touch] 兜底），双行行 ≈ 49dp（4+20+4+17+4），
 * 与 [SettingsSpacingMath.RowStepDp] 的 49dp 一档对得上，不额外吃掉每屏行数 ——
 * 纵向内边距因此收到 [Wear.Space.Xs]，多出来的一行由这一档消化。
 */
@Composable
private fun SettingsCenteredRow(
    title: String,
    value: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    titleMaxLines: Int = 2,
    valueMaxLines: Int = 2,
    trailing: (@Composable () -> Unit)? = null
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(
                if (onClick == null) {
                    Modifier
                } else {
                    Modifier.clickable(role = Role.Button, onClick = onClick)
                }
            )
            .heightIn(min = SettingsSpacingMath.MinRowHeightDp.dp)
            // 字号不动，只收 chrome：上下各 2dp 已经够把两行文字包住
            .padding(horizontal = Wear.Space.Sm, vertical = RowVerticalPadding)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // 左对齐体系：只有右侧给行尾控件让位，左边界与组标题、分隔线共用同一条线
                .padding(end = if (trailing == null) 0.dp else SettingsRowTailZone),
            horizontalAlignment = Alignment.Start
        ) {
            BasicText(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                style = SettingsRowTitleStyle,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (value != null) {
                Spacer(Modifier.height(SettingsRowLineGap))
                BasicText(
                    text = value,
                    modifier = Modifier.fillMaxWidth(),
                    style = SettingsRowValueStyle,
                    maxLines = valueMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Box(modifier = Modifier.align(Alignment.CenterEnd)) { trailing() }
        }
    }
}

/**
 * 可进入行：**没有容器**——只有标题 / 取值 / 行尾标记三个元素，行与行之间由 1dp 分隔线切分。
 *
 * 行尾标记二选一：有状态时是一块 [Wear.Size.StatusBlock] 的色块（状态文案转成无障碍描述，
 * 不再和大段文字抢注意力），没有状态时是右向箭头。
 *
 * 版面走 [SettingsCenteredRow]：标题居中，取值居中于下一行；取值只在宽屏显示
 * （见 [settingsRowShowsValue]），窄屏上整行只剩一行标题 —— 根页与子页是同一份实现。
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
    SettingsCenteredRow(
        title = title,
        value = if (value != null && settingsRowShowsValue(screenWidthDp)) value else null,
        modifier = modifier,
        onClick = onClick,
        // 居中的版式里，右侧箭头是孤立元素：没有状态块就什么都不放，文字也不必让出尾区
        trailing = if (badge == null) {
            null
        } else {
            { SettingsStatusBlock(description = badge.first, tone = badge.second) }
        }
    )
}

/** 行标题档：官方 bodyMedium + 主色（19.57:1）。层级由字号与亮度双轴承担，不再靠"全体压暗"。 */
private val SettingsRowTitleStyle: TextStyle
    @Composable get() = Wear.TextRowTitle.copy(
        textAlign = TextAlign.Start,
        fontWeight = FontWeight.Medium
    )

/** 行取值档：官方 bodySmall + 次色（6.94:1）。旧 muted 色只有 3.87:1，12sp 小字读不清。 */
private val SettingsRowValueStyle: TextStyle
    @Composable get() = Wear.TextRowValue.copy(textAlign = TextAlign.Start)

/** 行尾状态色块：一块纯色就够表达状态，文案走无障碍描述。 */
@Composable
private fun SettingsStatusBlock(description: String, tone: Tone) {
    Box(
        modifier = Modifier
            .size(Wear.Size.StatusBlock)
            .clip(CircleShape)
            .background(tone.statusBlockColor())
            .semantics { contentDescription = description }
    )
}

/** [Tone] 到令牌色的唯一映射，避免状态色散落在各处。 */
private fun Tone.statusBlockColor(): Color = when (this) {
    // 静态属性不该是焦点：主色压到 60%（= AccentTrack，黑底 5.29:1），不再满饱和抢页面标题
    Tone.ACCENT -> Wear.Palette.AccentTrack
    Tone.DANGER -> Wear.Palette.Danger
    Tone.MUTED -> Wear.Palette.TextMuted
}

/** 开关行：官方 SwitchButton（label + onCheckedChange），不再手绘开关。 */
@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    secondary: String? = null
) {
    SwitchButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        // 容器一律透明：行本身就是"裸行 + 发丝线"，控件再自带一块实底就成了第二层卡片。
        // 旧值：ON 态 #0E3A4E 整块实色（质量 ≈3–4 条 30dp 胶囊）、OFF 态 #10141A 边界 1.14:1 看不见。
        colors = SwitchButtonDefaults.switchButtonColors(
            checkedContainerColor = Color.Transparent,
            checkedContentColor = Wear.Palette.TextPrimary,
            checkedSecondaryContentColor = Wear.Palette.TextSecondary,
            checkedThumbColor = Wear.Palette.Orb,
            checkedTrackColor = Wear.Palette.AccentTrack,
            checkedTrackBorderColor = Wear.Palette.AccentTrack,
            uncheckedContainerColor = Color.Transparent,
            uncheckedContentColor = Wear.Palette.TextPrimary,
            uncheckedSecondaryContentColor = Wear.Palette.TextSecondary,
            uncheckedThumbColor = Wear.Palette.TextSecondary,
            uncheckedTrackColor = Wear.Palette.SurfaceAlt,
            uncheckedTrackBorderColor = Wear.Palette.Border
        ),
        contentPadding = PaddingValues(
            horizontal = Wear.Space.Md,
            vertical = Wear.Space.Sm
        ),
        secondaryLabel = if (secondary == null) {
            null
        } else {
            { SettingsSecondaryLabel(text = secondary) }
        },
        label = { SettingsPrimaryLabel(text = label) }
    )
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
    modifier: Modifier = Modifier
) {
    TextToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.fillMaxWidth(),
        content = { SettingsPrimaryLabel(text = label) }
    )
}

/**
 * 数值行：官方 Slider（带两端图标的多台阶滑轨），细粒度取值由 [SettingsValueMath] 量化，
 * 不再用 ± 按钮拼装。标签 / 数值 / 滑轨三件，不再额外套一层卡片。
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
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            BasicText(
                text = label,
                modifier = Modifier.weight(1f),
                style = SettingsRowTitleStyle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = valueText,
                style = Wear.TextTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            valueRange = valueRange,
            shape = MaterialTheme.shapes.medium,
            // 容器透明：滑轨自己就是控件，不需要再垫一块 Surface
            colors = SliderDefaults.sliderColors(
                containerColor = Color.Transparent,
                buttonIconColor = Wear.Palette.TextPrimary,
                selectedBarColor = Wear.Palette.Accent,
                unselectedBarColor = Wear.Palette.SurfaceAlt,
                selectedBarSeparatorColor = Wear.Palette.Bg,
                unselectedBarSeparatorColor = Wear.Palette.Bg
            )
        )
    }
}

/**
 * 单选行：并进居中版式，不再用官方 RadioButton。
 *
 * 官方 RadioButton 自带 12dp/8dp 内边距与左对齐文字，和全站其余居中的文字行混在一起，
 * 同一屏出现两套对齐与两套行高。这里改成同一行组件 + 行尾圆点，
 * 行高、内边距、文字对齐全部跟随 [SettingsCenteredRow]。
 */
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    SettingsCenteredRow(
        title = label,
        value = subtitle,
        modifier = modifier.widthIn(max = SettingsContentWidth),
        onClick = onSelect,
        valueMaxLines = 2,
        trailing = { SettingsRadioDot(selected) }
    )
}

/** 单选圆点：空心环 + 选中实心点，只表达状态，不做容器。 */
@Composable
private fun SettingsRadioDot(selected: Boolean) {
    val ring = if (selected) Wear.Palette.Accent else Wear.Palette.TextMuted
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
                    .background(Wear.Palette.Accent)
            )
        }
    }
}

private val SettingsRadioDotSize = 18.dp
private val SettingsRadioStroke = 2.dp

/** 只读说明行：同样没有容器，标题与取值各自居中成一行。 */
@Composable
fun SettingsInfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    SettingsCenteredRow(
        title = title,
        value = value,
        modifier = modifier.widthIn(max = SettingsContentWidth),
        valueMaxLines = 2
    )
}

/** 状态标签/说明文本：统一 2 行上限 + 省略号，长文本不会顶出圆屏。 */
@Composable
fun SettingsCaptionRow(text: String, modifier: Modifier = Modifier, mono: Boolean = false) {
    BasicText(
        text = text,
        modifier = modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth(),
        style = if (mono) Wear.TextMono else Wear.TextCaption,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
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
        Tone.ACCENT -> Wear.Palette.AccentDim
        Tone.DANGER -> Wear.Palette.DangerSurface
        Tone.MUTED -> Wear.Palette.Surface
    }
    val content = when (tone) {
        Tone.ACCENT -> Wear.Palette.Accent
        Tone.DANGER -> Wear.Palette.Danger
        Tone.MUTED -> Wear.Palette.TextPrimary
    }
    val stroke = when (tone) {
        Tone.ACCENT -> Wear.Palette.Accent
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
    SettingsCenteredRow(
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
                style = Wear.TextDisplayTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicTextField(
                value = draft.text,
                // 编辑只改草稿：确认之前一个字符都不外泄
                onValueChange = { draft = draft.edit(it) },
                textStyle = Wear.TextBody,
                cursorBrush = SolidColor(Wear.Palette.Accent),
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

/**
 * 右向折线箭头，与列表行一致。
 *
 * 几何：10dp 图形盒 + 1.5dp 描边（12dp/2dp 在 12sp 取值旁边偏重），
 * 并整体下移 1dp 补齐"墨心比同行文字高 1.0dp"的光学偏差。
 */
@Composable
fun ChevronMark() {
    Canvas(
        modifier = Modifier
            .offset(y = 1.dp)
            .size(Wear.Size.ChevronGlyph)
    ) {
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.2f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.3f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = Wear.Palette.TextMuted,
            style = Stroke(
                width = 1.5.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

/** 下滑退出手势阈值：松手时位移超过该值即退场，否则回弹复位。 */
val PullDownDismissThreshold = 50.dp

/** 临时诊断开关：真机确认手势路径后置 false 即彻底消音（release 包也带日志，便于直接装 release 验证）。 */
private const val PullDownDismissDebug = true

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
private const val SettingsTopAnchorDebug = true

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
                            animate(pull, 0f, animationSpec = spring(0.75f, 350f)) { value, _ ->
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
