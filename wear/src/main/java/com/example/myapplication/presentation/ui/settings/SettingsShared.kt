package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
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
private val SettingsDividerThickness = 1.dp

/** 分区之间的间距：上一区末行与本区标题之间留 16dp。 */
private val SettingsSectionGap = Wear.Space.Lg

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
    HYPER_PARAMS,
    TOOLS_MCP,
    PROMPT_VIEWER,
    MEMORY_MANAGER,
    WING_SHORTCUT,
    PRIVILEGE,
    REMOTE_CONSOLE,
    SECURITY_GUARDRAIL
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
 * - 中部：ScalingLazyColumn，内容宽度上限 168dp，行间距 8dp，行与行之间只有一条 1dp 分隔线；
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

    // 表冠要焦点：等节点真正挂上树的那一帧再请求
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { listFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
            // 下滑退出：由内部列表经 nested scroll 报告"已到顶还剩下的向下位移"才接管
            .pullDownToDismissOnScroll(onDismiss = onBack)
    ) {
        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                // 官方 M3E 表冠：刻度→位移与松手惯性都由 behavior 提供（自带 focusTarget，
                // 焦点请求仍由 listFocusRequester 负责，不要再叠 focusable()）
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = listFocusRequester
                ),
            state = listState,
            rotaryScrollableBehavior = null,
            overscrollEffect = null,
            contentPadding = PaddingValues(
                start = Wear.Space.Md,
                end = Wear.Space.Md,
                top = Wear.Space.Xs,
                bottom = Wear.Size.BottomBarReserve
            ),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
            autoCentering = AutoCenteringParams(itemIndex = 0)
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
            .padding(top = Wear.Size.TopContentInset, bottom = Wear.Space.Xs),
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
 * 分组标题：Caption 级小标题。
 *
 * 顶部 16dp 就是**分区间距**的唯一来源；下方 4dp 只把标题和本区首行拉开一点。
 */
@Composable
fun SettingsSectionHeader(text: String) {
    BasicText(
        text = text,
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .padding(top = SettingsSectionGap, bottom = Wear.Space.Xs),
        style = Wear.TextCaption,
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
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
            BasicText(
                text = value,
                // 值收窄到 72dp，把宽度让给标题（原来 96dp 会把标题压到 50dp）
                modifier = Modifier.widthIn(max = 72.dp),
                style = SettingsRowValueStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
    ) {
        BasicText(
            text = label,
            modifier = Modifier.weight(1f),
            style = SettingsRowTitleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = if (value.isEmpty()) stringResource(R.string.settings_value_empty) else value,
            modifier = Modifier.widthIn(max = SettingsLayoutMath.SubtitleMinWidthDp.dp),
            style = SettingsRowValueStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

/**
 * 设置页"下滑退出"容器：与"主键上滑进入"对称的反向退出。
 * - 拖动一律先交给列表：[NestedScrollConnection.onPostScroll] 只会收到列表吃不下、还剩下的向下位移
 *   （`available.y > 0`，等价于"已到顶还继续下拽"）。列表还能滚时剩余位移恒为 0，此处一个像素都不吃，
 *   手指拖动的滚动与松手后的惯性衰减全部留在 ScalingLazyColumn 自己手里。
 * - 收位移的节点与做位移的节点分离：外层只挂 nestedScroll 与尺寸测量，位移只作用在内层内容，
 *   避免"节点自身平移又把局部坐标算回去"的正反馈闪屏。
 * - 松手（[NestedScrollConnection.onPreFling]）时位移超过 [PullDownDismissThreshold] 播退场并回调
 *   [onDismiss]，否则回弹复位。
 */
@Composable
fun PullDownDismissContainer(
    enabled: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    val pullPx = remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var pageHeightPx by remember { mutableFloatStateOf(1f) }
    val thresholdPx = with(density) { PullDownDismissThreshold.toPx() }
    val currentEnabled by rememberUpdatedState(enabled)
    val currentDismiss by rememberUpdatedState(onDismiss)
    // 手指是否按在屏幕上：官方 RotaryScrollLogic 的刻度也走 NestedScrollSource.UserInput
    // （字节码里是 getUserInput），只看位移分不清表冠和手指。不加这道闸，转到顶再接着转表冠
    // 会被当成"下拉退出"把整页滑走。这里只读观察，绝不 consume。
    var isPointerDown by remember { mutableStateOf(false) }

    // 关闭时（例如打开子页）立刻清零：不留半截位移，也不让残留累积量参与下一次手势
    LaunchedEffect(enabled) {
        if (!enabled) {
            pullPx.floatValue = 0f
            dragY.snapTo(0f)
        }
    }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 只有"列表已无法向上滚、手指还在继续向下拽"的剩余位移才归容器：onPostScroll 的
                // available 本来就是列表吃剩的部分，再看 source 只剩 UserInput —— 惯性/回弹(SideEffect)
                // 的尾巴不算下拉，否则甩到底那一帧会先被拉走再弹回，就是"手感诡异"的来源。
                if (!currentEnabled || isDismissing || !isPointerDown) return Offset.Zero
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pullPx.floatValue = (pullPx.floatValue + available.y).coerceAtLeast(0f)
                val target = pullPx.floatValue
                scope.launch { dragY.snapTo(target) }
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (isDismissing) return available
                val pull = pullPx.floatValue
                pullPx.floatValue = 0f
                if (pull > thresholdPx) {
                    isDismissing = true
                    dragY.animateTo(pageHeightPx, tween(140))
                    currentDismiss()
                    // 速度全部吃掉：退场时列表不该继续惯性滚动
                    return available
                }
                // 未达阈值：回弹交给容器自己的协程。绝不在这里 await —— onPreFling 是惯性派发前的
                // 挂起点，在这里挂 350ms 会把列表自己的 fling 一起拖住，真表上就是"松手先卡一下再滚"。
                if (pull > 0f) scope.launch { dragY.animateTo(0f, spring(0.75f, 350f)) }
                // Velocity.Zero = 本节点消费 0，剩余速度全部留给 ScalingLazyColumn 的惯性
                return Velocity.Zero
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> pageHeightPx = coords.size.height.toFloat().coerceAtLeast(1f) }
            // 只读观察按下状态（Initial 阶段不允许 consume：7d8dfb7 之前就是在这里吃掉了列表拖动）
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        isPointerDown = event.changes.any { it.pressed }
                    }
                }
            }
            // 手势节点：只有"列表吃不下"的剩余位移会到这里，且不携带任何位移
            .nestedScroll(connection)
    ) {
        // 内容节点：只做视觉位移与透明度衰减
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragY.value
                    alpha = (1f - dragY.value / (pageHeightPx * 1.5f)).coerceIn(0.25f, 1f)
                }
        ) {
            content()
        }
    }
}

/**
 * 子页"下滑退出"：子页列表由调用方创建，本组件拿不到它的滚动状态，因此用 nested scroll 判定是否到顶：
 * 列表先吃掉自己能滚的位移，只有"已到顶还剩下向下位移"（[NestedScrollConnection.onPostScroll] 的
 * available.y > 0）时才累积并吃掉这部分事件，列表还能向上滚时一个像素都不吃。
 */
@Composable
fun Modifier.pullDownToDismissOnScroll(
    onDismiss: () -> Unit,
): Modifier {
    val density = LocalDensity.current
    val dragY = remember { Animatable(0f) }
    var pullPx by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var pageHeightPx by remember { mutableFloatStateOf(1f) }
    // 同 PullDownDismissContainer：表冠刻度同样是 UserInput 来源，必须靠"手指按下"区分
    var isPointerDown by remember { mutableStateOf(false) }
    val thresholdPx = with(density) { PullDownDismissThreshold.toPx() }
    val currentDismiss by rememberUpdatedState(onDismiss)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 判据与 PullDownDismissContainer 一致：只收"已到顶 + 手指继续下拽"的剩余位移，
                // 惯性衰减的尾巴（SideEffect）一个像素都不收，否则甩到底会被当成一次下拉。
                if (isDismissing || !isPointerDown) return Offset.Zero
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                pullPx = (pullPx + available.y).coerceAtLeast(0f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pull = pullPx
                pullPx = 0f
                if (isDismissing) return available
                if (pull > thresholdPx) {
                    isDismissing = true
                    dragY.animateTo(pageHeightPx, tween(140))
                    currentDismiss()
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    return this
        .onGloballyPositioned { coords -> pageHeightPx = coords.size.height.toFloat().coerceAtLeast(1f) }
        // 只读观察按下状态，不消费（同 PullDownDismissContainer）
        .pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    isPointerDown = event.changes.any { it.pressed }
                }
            }
        }
        .graphicsLayer {
            translationY = dragY.value
            alpha = (1f - dragY.value / (pageHeightPx * 1.5f)).coerceIn(0.25f, 1f)
        }
        .nestedScroll(connection)
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
