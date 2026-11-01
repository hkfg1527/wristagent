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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.RadioButtonDefaults
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SliderDefaults
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBadge
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.launch
import kotlin.math.abs

/** 所有设置页共用的内容宽度上限：192dp 圆屏两侧各让出 12dp 安全边。 */
val SettingsContentWidth: Dp = SettingsLayoutMath.ContentWidthDp.dp

/** 单行 44dp 触控热区之间的最小留白。 */
val SettingsTouchGap: Dp = Wear.Space.Sm

/** 文本弹窗里输入框的最大高度：再高就会把按钮顶出圆屏。 */
private val DialogFieldMaxHeight = 120.dp

/** 表冠滚动触发震动的位移阈值。 */
private const val RotaryTickThreshold = 10f

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
    SECURITY_GUARDRAIL
}

/**
 * 统一子页骨架的行登记表：每调用一次就登记一行，登记顺序即逐行动画的序号。
 *
 * 之所以先登记再渲染，是因为退场要"从末行往前起跑"——只有先知道总行数才能算出反向延迟。
 */
class SettingsRows internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    /** 登记一行内容。 */
    fun row(content: @Composable () -> Unit) {
        items.add(content)
    }

    /** 登记一行分组标题。 */
    fun section(text: String) {
        items.add { SettingsSectionHeader(text = text) }
    }
}

/**
 * 所有子页共用的页面骨架。
 *
 * - 顶部：44dp 返回键 + 页面标题，整块落在 y=34dp 的曲面时钟带以下；
 * - 中部：ScalingLazyColumn，内容宽度上限 168dp，行间距 8dp；
 * - 底部：44dp「完成返回」行，滚动区底部预留 [Wear.Size.BottomBarReserve]，末行不会被主键压住；
 * - 入场沿用 [SettingsStaggeredRow]（逐行浮动 24dp→0 + 淡入，step 40ms，cap 360ms）。
 */
@Composable
fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    overlay: @Composable () -> Unit = {},
    rows: @Composable SettingsRows.() -> Unit
) {
    val context = LocalContext.current
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
                // 顺序即作用域：处理器必须在 focusable 之外，否则 rotary 事件冒不进来
                .onRotaryScrollEvent {
                    listState.dispatchRawDelta(it.verticalScrollPixels)
                    if (abs(it.verticalScrollPixels) > RotaryTickThreshold) {
                        context.vibrateTick()
                    }
                    true
                }
                .focusRequester(listFocusRequester)
                .focusable(),
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
                    SettingsPageHeader(
                        title = title,
                        onBack = { exit.begin(onBack) }
                    )
                }
            }

            items(
                count = content.size,
                key = { index -> "page-row-" + index }
            ) { index ->
                SettingsStaggeredRow(index = index + 1, rowCount = rowCount, stagger = stagger, exit = exit) {
                    Box(modifier = Modifier.widthIn(max = SettingsContentWidth)) {
                        content[index]()
                    }
                }
            }

            item(key = "page-footer") {
                SettingsStaggeredRow(index = rowCount - 1, rowCount = rowCount, stagger = stagger, exit = exit) {
                    SettingsActionButton(
                        label = stringResource(R.string.settings_done_back),
                        tone = Tone.ACCENT,
                        modifier = Modifier.widthIn(max = SettingsContentWidth),
                        onClick = { exit.begin(onBack) }
                    )
                }
            }
        }

        overlay()
    }
}

/** 子页头部：44dp 返回键 + 标题，整块压在曲面时钟带以下。 */
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
            style = Wear.TextTitle,
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
        border = BorderStroke(1.dp, Wear.Palette.Border)
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

/** 分组标题：Caption 级小标题，上下留 Xs。 */
@Composable
fun SettingsSectionHeader(text: String) {
    BasicText(
        text = text,
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .padding(top = Wear.Space.Sm, bottom = Wear.Space.Xs),
        style = Wear.TextCaption,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/**
 * 根页可进入行：官方 FilledTonal 风格 Button 承载整行，标题独占首行（右侧只有 chevron），
 * 副标题与徽章同行 —— 徽章宽度封顶 [SettingsLayoutMath.BadgeMaxWidthDp]，
 * 副标题因此恒有 >= 96dp 可用宽度，不会被省略号截断。
 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    badge: Pair<String, Tone>? = null,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Wear.Radius.Chip),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Wear.Palette.Surface,
            contentColor = Wear.Palette.TextPrimary
        ),
        contentPadding = PaddingValues(
            horizontal = Wear.Space.Md,
            vertical = Wear.Space.Sm
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
            ) {
                BasicText(
                    text = title,
                    modifier = Modifier.weight(1f),
                    style = Wear.TextBody,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ChevronMark()
            }
            if (value != null || badge != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                ) {
                    if (value != null) {
                        BasicText(
                            text = value,
                            modifier = Modifier.weight(1f),
                            style = Wear.TextCaption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (badge != null) {
                        WearBadge(
                            text = badge.first,
                            tone = badge.second,
                            modifier = Modifier.widthIn(max = SettingsLayoutMath.BadgeMaxWidthDp.dp),
                            horizontalPadding = Wear.Space.Xs
                        )
                    }
                }
            }
        }
    }
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
        shape = RoundedCornerShape(Wear.Radius.Chip),
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
 * 数值行：官方 Slider（带两端图标的多台阶滑轨），细粒度取值由 [SettingsValueMath] 量化，
 * 不再用 ± 按钮拼装。
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
                style = Wear.TextBody,
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
            shape = RoundedCornerShape(Wear.Radius.Card),
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
        shape = RoundedCornerShape(Wear.Radius.Chip),
        colors = RadioButtonDefaults.radioButtonColors(
            selectedContainerColor = Wear.Palette.AccentDim,
            selectedContentColor = Wear.Palette.Accent,
            selectedSecondaryContentColor = Wear.Palette.Accent,
            selectedControlColor = Wear.Palette.Accent,
            unselectedContainerColor = Wear.Palette.Surface,
            unselectedContentColor = Wear.Palette.TextPrimary,
            unselectedSecondaryContentColor = Wear.Palette.TextSecondary,
            unselectedControlColor = Wear.Palette.TextSecondary
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

/** 只读说明行：没有点击语义，只用令牌配色区分层级。 */
@Composable
fun SettingsInfoRow(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            .clip(RoundedCornerShape(Wear.Radius.Chip))
            .background(Wear.Palette.Surface)
            .border(1.dp, Wear.Palette.Border, RoundedCornerShape(Wear.Radius.Chip))
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        BasicText(
            text = title,
            style = Wear.TextBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = value,
            style = Wear.TextCaption,
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
        shape = RoundedCornerShape(Wear.Radius.Chip),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Wear.Palette.SurfaceAlt,
            disabledContentColor = Wear.Palette.TextSecondary
        ),
        border = BorderStroke(1.dp, if (enabled) stroke else Wear.Palette.Border),
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
 * 文本输入入口：列表里只留一行 Button，点开才是真正的输入对话框。
 * 列表里塞 TextField 会被表冠滚动与软键盘互相抢焦点，这一行不承载输入本身。
 */
@Composable
fun SettingsTextEntryRow(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Wear.Radius.Chip),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Wear.Palette.Surface,
            contentColor = Wear.Palette.TextPrimary
        ),
        contentPadding = PaddingValues(
            horizontal = Wear.Space.Md,
            vertical = Wear.Space.Sm
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            BasicText(
                text = label,
                modifier = Modifier.weight(1f),
                style = Wear.TextBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = if (value.isEmpty()) stringResource(R.string.settings_value_empty) else value,
                modifier = Modifier.widthIn(max = SettingsLayoutMath.SubtitleMinWidthDp.dp),
                style = Wear.TextCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ChevronMark()
        }
    }
}

/**
 * 设置页文本输入对话框：遮罩 + 令牌化卡片 + 官方 Button。
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

    val shape = RoundedCornerShape(Wear.Radius.Card)
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
                .border(1.dp, Wear.Palette.Border, shape)
                .padding(Wear.Space.Md),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            BasicText(
                text = title,
                style = Wear.TextTitle,
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
                    .clip(RoundedCornerShape(Wear.Radius.Chip))
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
 * - 收手势的节点（外层）自己永不位移，位移只作用在内容节点（内层）。同一个节点既收指针又被
 *   translationY 平移时，会出现"手指动 1px → 节点下移 1px → 指针局部坐标再变 1px"的正反馈，
 *   表现为原点与拖动位置之间高频闪屏。
 * - 位移量由 [positionChangeIgnoreConsumed] 的增量累加得到，既不依赖起始点，也不受任何自身平移影响。
 * - 只有列表已在顶部（[atTop] 为 true）且继续向下拖动超过触摸阈值时才在 Initial 阶段接管并 consume；
 *   未到顶时一个事件都不消费，纵向滚动照常交给列表。
 */
@Composable
fun PullDownDismissContainer(
    enabled: Boolean,
    atTop: () -> Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dragY = remember { Animatable(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    var pageHeightPx by remember { mutableFloatStateOf(1f) }
    val thresholdPx = with(density) { PullDownDismissThreshold.toPx() }
    val currentAtTop by rememberUpdatedState(atTop)
    val currentDismiss by rememberUpdatedState(onDismiss)

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords -> pageHeightPx = coords.size.height.toFloat().coerceAtLeast(1f) }
            // 手势节点：不携带任何位移
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slopPx = viewConfiguration.touchSlop
                    var capturing = false
                    var accumulatedX = 0f
                    var accumulatedY = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (capturing) {
                                if (dragY.value > thresholdPx) {
                                    if (!isDismissing) {
                                        isDismissing = true
                                        scope.launch {
                                            dragY.animateTo(pageHeightPx, tween(140))
                                            currentDismiss()
                                        }
                                    }
                                } else {
                                    scope.launch { dragY.animateTo(0f, spring(0.75f, 350f)) }
                                }
                            }
                            break
                        }

                        val delta = change.positionChangeIgnoreConsumed()
                        accumulatedX += delta.x
                        accumulatedY += delta.y
                        // 只有"已在顶部 + 明确向下 + 超过触摸阈值"才接管，其余全部让给列表自己滚动
                        if (!capturing && !isDismissing && currentAtTop() &&
                            accumulatedY > slopPx && accumulatedY > abs(accumulatedX)
                        ) {
                            capturing = true
                        }
                        if (capturing) {
                            change.consume()
                            val clamped = accumulatedY.coerceAtLeast(0f)
                            scope.launch { dragY.snapTo(clamped) }
                        }
                    }
                }
            }
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
    val thresholdPx = with(density) { PullDownDismissThreshold.toPx() }
    val currentDismiss by rememberUpdatedState(onDismiss)

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (isDismissing || available.y <= 0f) return Offset.Zero
                pullPx = (pullPx + available.y).coerceAtLeast(0f)
                return Offset(0f, available.y)
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val pull = pullPx
                pullPx = 0f
                if (!isDismissing && pull > thresholdPx) {
                    isDismissing = true
                    dragY.animateTo(pageHeightPx, tween(140))
                    currentDismiss()
                }
                return Velocity.Zero
            }
        }
    }

    return this
        .onGloballyPositioned { coords -> pageHeightPx = coords.size.height.toFloat().coerceAtLeast(1f) }
        .graphicsLayer {
            translationY = dragY.value
            alpha = (1f - dragY.value / (pageHeightPx * 1.5f)).coerceIn(0.25f, 1f)
        }
        .nestedScroll(connection)
}

/** 设置页状态徽章的资源映射：状态 -> 文案资源，纯函数便于单测。 */
internal object SettingsStatusTextRes {
    /** 模型与服务卡片的配置徽章：已配置走强调色，未配置走警示色。 */
    fun modelServiceBadge(configured: Boolean): Pair<Int, Tone> =
        if (configured) {
            R.string.settings_badge_configured to Tone.ACCENT
        } else {
            R.string.settings_badge_unconfigured to Tone.DANGER
        }
}
