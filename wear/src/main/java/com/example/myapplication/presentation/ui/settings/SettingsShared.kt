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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
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

/** 所有设置页共用的内容宽度上限：192dp 圆屏两侧各让出 12dp 安全边。 */
val SettingsContentWidth: Dp = SettingsLayoutMath.ContentWidthDp.dp

/** 单行 44dp 触控热区之间的最小留白。 */
val SettingsTouchGap: Dp = Wear.Space.Sm

/** 行与行之间的唯一分隔物：1dp 细线，取代原来的"每行一张卡片"。 */
private val SettingsDividerThickness = SettingsSpacingMath.HairlineDp.dp

/** 相邻两行之间的垂直留白：把每行切成独立的一块，而不是连成一堵墙。 */
val SettingsItemGap: Dp = SettingsSpacingMath.ItemGapDp.dp

/** 组标题下方到本组首行之间的留白，也是"每组第一行上方"的那一段空气。 */
private val SettingsSectionHeaderBottomGap: Dp = SettingsSpacingMath.SectionHeaderBottomDp.dp

/** 行内：主标题与右侧取值之间的发丝级呼吸。 */
private val SettingsTitleValueGap: Dp = SettingsSpacingMath.TitleToValueDp.dp

/** 行内：取值与行尾标记（箭头 / 状态色块）之间的留白。 */
private val SettingsValueTailGap: Dp = SettingsSpacingMath.ValueToTailDp.dp

/** 分区之间的间距：上一区末行与本区标题之间留 16dp。 */
private val SettingsSectionGap = Wear.Space.Lg

/**
 * 根页标题行内的顶部留白：列表顶部内边距 8dp + 行间距 8dp + 本留白 = 34dp 时钟带下沿。
 *
 * 行顶边本身已经被列表内边距与行间距压在 16dp，行内只需要补齐差额，
 * 标题文字才会紧贴时钟带下沿而不是被重新顶到屏幕中部。
 */
val RootTitleTopPadding: Dp = SettingsTopAnchorMath.titleTopPaddingDp(
    topInsetDp = Wear.Size.TopContentInset.value,
    contentPaddingTopDp = Wear.Space.Sm.value,
    itemGapDp = SettingsSpacingMath.ItemGapDp
).dp

/** 子页头部行内的顶部留白：子页列表顶部内边距是 4dp，行内相应多占 4dp。 */
val SubPageHeaderTopPadding: Dp = SettingsTopAnchorMath.titleTopPaddingDp(
    topInsetDp = Wear.Size.TopContentInset.value,
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
    PROMPT_VIEWER,
    MEMORY_MANAGER,
    WING_SHORTCUT,
    PRIVILEGE,
    REMOTE_CONSOLE,
    SECURITY_GUARDRAIL,
    NETWORK_DIAGNOSTICS
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
 * 表冠用的取焦效果：库只把表冠事件交给**当前持有焦点**的那个 rotary 节点，
 * 所以每次进入/回到本页都必须显式 requestFocus 一次。
 *
 * 为什么不是 requestFocusOnHierarchyActive()：它只对修饰符链上排在它**之后**的那个可聚焦
 * 元素生效（会把焦点转交给下家）；挂在 rotaryScrollable 之后就是没有下家，等于空操作。
 *
 * 为什么不是 onAttach 时序：根页在二级页开关过程中一直留在树里，onAttach 只跑一次，
 * 从子页返回后不会重跑 —— 用 [refocusKey]（根页喂 activeDialog，子页喂 Unit）驱动重跑，
 * 时序与"是不是刚挂上树"解耦。首帧节点可能还没上树，因此最多重试 [RotaryFocusRetryFrames]
 * 帧，而不是把异常吞掉后就地放弃。
 *
 * @param refocusKey 变化即重新取焦；子页每次进入都是新组合，传 Unit 即可。
 * @param enabled 为 false 时不抢焦点（二级页盖在根页上时，根页必须让出焦点）。
 */
@Composable
fun RotaryFocusEffect(focusRequester: FocusRequester, refocusKey: Any?, enabled: Boolean) {
    LaunchedEffect(refocusKey, enabled) {
        if (!enabled) return@LaunchedEffect
        repeat(RotaryFocusRetryFrames) {
            withFrameNanos { }
            if (runCatching { focusRequester.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
}

/** 取焦重试帧数：首帧节点尚未上树时 requestFocus 会抛，重试几帧足以覆盖挂载抖动。 */
private const val RotaryFocusRetryFrames = 3

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
        // 表冠只把事件交给当前持有焦点的节点：rotaryScrollable 把 listFocusRequester 链在自带的
        // focusTarget 上之后，必须显式 requestFocus() 一次才算接管。子页每次进入都是全新一次
        // 组合，因此按 Unit 触发即可，不需要外层再喂变化量。
        RotaryFocusEffect(focusRequester = listFocusRequester, refocusKey = Unit, enabled = true)

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 官方 M3E 表冠：刻度→位移与松手惯性都由 behavior 提供。
                // 不要再叠 focusable()，也不要叠 requestFocusOnHierarchyActive()：
                // 后者只对"修饰符链上排在它之后"的那个可聚焦元素生效，挂在 rotaryScrollable
                // 之后是一个没有下家的空修饰符，rotary 节点永远拿不到焦点。
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = listFocusRequester
                ),
            state = listState,
            // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
            flingBehavior = ScrollableDefaults.flingBehavior(),
            rotaryScrollableBehavior = null,
            overscrollEffect = null,
            contentPadding = PaddingValues(
                start = Wear.Space.Md,
                end = Wear.Space.Md,
                top = Wear.Space.Xs,
                bottom = Wear.Size.BottomBarReserve
            ),
            verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
            // itemIndex = 1：自动居中项是第一条内容行，头部行不再被居中 ——
            // 于是滚到最顶时头部贴在上方内容区顶边，而不是停在屏幕正中、上面留半屏空白。
            // 居中只影响两端边界，滚动过程一像素都不变（详见 SettingsTopAnchorMath）。
            autoCentering = AutoCenteringParams(itemIndex = 1)
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

        overlay()
    }
}

/** 行间 1dp 分隔线：页面里唯一的"容器"，取代逐行卡片。 */
@Composable
fun SettingsRowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SettingsDividerThickness)
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
 */
@Composable
fun SettingsSectionHeader(text: String) {
    BasicText(
        text = text,
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .padding(top = SettingsSectionGap, bottom = SettingsSectionHeaderBottomGap),
        style = Wear.TextCaption.copy(color = Wear.Palette.Accent),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 可进入行：**没有容器**——只有标题 / 值 / 行尾标记三个元素，行与行之间由 1dp 分隔线切分。
 *
 * 行尾标记二选一：有状态时是一块 [Wear.Size.StatusBlock] 的色块（状态文案转成无障碍描述，
 * 不再和大段文字抢注意力），没有状态时是右向箭头。
 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    badge: Pair<String, Tone>? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Wear.Size.Touch)
            .padding(horizontal = Wear.Space.Sm, vertical = Wear.Space.Sm),
        verticalAlignment = Alignment.CenterVertically
        // 不用 Arrangement.spacedBy：三段的间距本来就不该一样 —— 统一间距正是"一堵墙"的成因。
        // 标题↔取值只留一条发丝呼吸，取值↔行尾标记留得更开，行尾因此看起来是独立挂上去的。
    ) {
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = SettingsRowTitleStyle,
            // 标题允许换行而不是省略：168dp 内容宽下，"端侧工具与 MCP + 6/6 已启用 + 箭头"
            // 三段挤一行会把标题裁成"端侧工…"，宁可多占一行也要让用户读全。
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (value != null) {
            Spacer(Modifier.width(SettingsTitleValueGap))
            BasicText(
                text = value,
                // 值收窄到 72dp，把宽度让给标题（原来 96dp 会把标题压到 50dp）
                modifier = Modifier.widthIn(max = 72.dp),
                style = SettingsRowValueStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(SettingsValueTailGap))
        if (badge != null) {
            SettingsStatusBlock(description = badge.first, tone = badge.second)
        } else {
            ChevronMark()
        }
    }
}

/** 行标题统一样式：secondary 色，把视线全部让给页面标题。 */
private val SettingsRowTitleStyle: TextStyle
    @Composable get() = Wear.TextBody.copy(color = Wear.Palette.TextSecondary)

/** 行值统一样式：muted 色，只做参照，不抢注意力。 */
private val SettingsRowValueStyle: TextStyle
    @Composable get() = Wear.TextCaption.copy(color = Wear.Palette.TextMuted)

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
    Tone.ACCENT -> Wear.Palette.Accent
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
        colors = SwitchButtonDefaults.switchButtonColors(
            checkedContainerColor = Wear.Palette.AccentDim,
            checkedContentColor = Wear.Palette.Accent,
            checkedSecondaryContentColor = Wear.Palette.Accent,
            checkedThumbColor = Wear.Palette.Orb,
            checkedTrackColor = Wear.Palette.Accent,
            checkedTrackBorderColor = Wear.Palette.Accent,
            uncheckedContainerColor = Wear.Palette.Surface,
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
            colors = SliderDefaults.sliderColors(
                containerColor = Wear.Palette.Surface,
                buttonIconColor = Wear.Palette.TextPrimary,
                selectedBarColor = Wear.Palette.Accent,
                unselectedBarColor = Wear.Palette.SurfaceAlt,
                selectedBarSeparatorColor = Wear.Palette.Bg,
                unselectedBarSeparatorColor = Wear.Palette.Bg
            )
        )
    }
}

/** 单选行：官方 RadioButton，选中态取 Accent（primary 色）。 */
@Composable
fun SettingsRadioRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    RadioButton(
        selected = selected,
        onSelect = onSelect,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = RadioButtonDefaults.radioButtonColors(
            selectedContainerColor = Wear.Palette.AccentDim,
            selectedContentColor = Wear.Palette.Accent,
            selectedSecondaryContentColor = Wear.Palette.Accent,
            selectedControlColor = Wear.Palette.Accent,
            unselectedContainerColor = Wear.Palette.Surface,
            unselectedContentColor = Wear.Palette.TextSecondary,
            unselectedSecondaryContentColor = Wear.Palette.TextMuted,
            unselectedControlColor = Wear.Palette.TextMuted
        ),
        contentPadding = PaddingValues(
            horizontal = Wear.Space.Md,
            vertical = Wear.Space.Sm
        ),
        secondaryLabel = if (subtitle == null) {
            null
        } else {
            { SettingsSecondaryLabel(text = subtitle, maxLines = 2) }
        },
        label = { SettingsPrimaryLabel(text = label) }
    )
}

/** 只读说明行：同样没有容器，只有标题与值两个元素。 */
@Composable
fun SettingsInfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .padding(horizontal = Wear.Space.Sm, vertical = Wear.Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
    ) {
        BasicText(
            text = title,
            modifier = Modifier.weight(1f),
            style = SettingsRowTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            modifier = Modifier.widthIn(max = SettingsLayoutMath.SubtitleMinWidthDp.dp),
            style = SettingsRowValueStyle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = Wear.Size.Touch)
            .padding(horizontal = Wear.Space.Sm, vertical = Wear.Space.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = SettingsRowTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(SettingsTitleValueGap))
        BasicText(
            text = if (value.isEmpty()) stringResource(R.string.settings_value_empty) else value,
            modifier = Modifier.widthIn(max = SettingsLayoutMath.SubtitleMinWidthDp.dp),
            style = SettingsRowValueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(SettingsValueTailGap))
        ChevronMark()
    }
}

/**
 * 设置页文本输入对话框：遮罩 + 卡片档圆角 + 官方 Button。
 * 逐字符写回由调用方通过 [onValueChange] 完成，取消时回滚到 [initial]。
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
    var draft by remember { mutableStateOf(initial) }

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
                value = draft,
                onValueChange = {
                    draft = it
                    onValueChange(it)
                },
                textStyle = Wear.TextBody,
                cursorBrush = SolidColor(Wear.Palette.Accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Wear.Size.Touch, max = DialogFieldMaxHeight)
                    .clip(MaterialTheme.shapes.small)
                    .background(Wear.Palette.SurfaceAlt)
                    .padding(Wear.Space.Md),
                decorationBox = { innerTextField ->
                    if (draft.isEmpty()) {
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
                    onClick = {
                        onValueChange(initial)
                        onDismiss()
                    }
                )
                SettingsActionButton(
                    label = stringResource(R.string.settings_dialog_confirm),
                    tone = Tone.ACCENT,
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss
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

/** 右向折线箭头，与列表行一致。 */
@Composable
fun ChevronMark() {
    Canvas(modifier = Modifier.size(Wear.Space.Md)) {
        val path = Path().apply {
            moveTo(size.width * 0.3f, size.height * 0.2f)
            lineTo(size.width * 0.7f, size.height * 0.5f)
            lineTo(size.width * 0.3f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = Wear.Palette.TextMuted,
            style = Stroke(
                width = 2.dp.toPx(),
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
