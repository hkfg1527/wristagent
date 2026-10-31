package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/** 子页滚动区底部预留高度：底部固定"完成返回"键不遮挡滚动内容。 */
val SubPageBottomReserve = 72.dp

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
    SECURITY_GUARDRAIL
}

/**
 * 单行可选条目：标题 + 可选副行说明 + 可选右侧状态值，最小 44dp 可点。
 */
@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    value: String? = null,
    subtitle: String? = null,
) {
    val shape = RoundedCornerShape(Wear.Radius.Chip)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .clip(shape)
            .background(if (selected) Wear.Palette.AccentDim else Wear.Palette.Surface)
            .border(1.dp, if (selected) Wear.Palette.Accent else Wear.Palette.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs),
        ) {
            BasicText(
                text = title,
                style = Wear.TextBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                BasicText(
                    text = subtitle,
                    style = Wear.TextCaption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (value != null) {
            BasicText(
                text = value,
                style = Wear.TextCaption.copy(
                    textAlign = TextAlign.End,
                    color = if (selected) Wear.Palette.Accent else Wear.Palette.TextSecondary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 44dp 实底动作按钮，色调取自 [Tone]。
 */
@Composable
fun SettingsActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Tone = Tone.MUTED,
) {
    val shape = RoundedCornerShape(Wear.Radius.Chip)
    val container = when (tone) {
        Tone.ACCENT -> Wear.Palette.AccentDim
        Tone.DANGER -> Wear.Palette.DangerSurface
        Tone.MUTED -> Wear.Palette.SurfaceAlt
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .clip(shape)
            .background(container)
            .border(1.dp, stroke, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = label,
            style = Wear.TextBody.copy(textAlign = TextAlign.Center, color = content),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 单行文本输入框：最小 44dp 高度，令牌配色与光标色。
 */
@Composable
fun SettingsInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val shape = RoundedCornerShape(Wear.Radius.Chip)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = Wear.TextBody.copy(color = Wear.Palette.TextPrimary),
        cursorBrush = SolidColor(Wear.Palette.Accent),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .clip(shape)
            .background(Wear.Palette.Surface)
            .border(1.dp, Wear.Palette.Border, shape)
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Md),
        decorationBox = { innerTextField ->
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                BasicText(
                    text = placeholder,
                    style = Wear.TextCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            innerTextField()
        },
    )
}

/**
 * 全屏二级配置面板：顶部居中标题，中部滚动内容，底部滚动流里的"完成返回"键（入场淡入）。
 * 退出三条路径：下滑、系统 BACK（经外层 BackHandler）、末尾"完成返回"，均不依赖横向手势。
 */
@Composable
fun SubSettingsModal(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val entryAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entryAlpha.animateTo(1f, tween(160))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
            .graphicsLayer { alpha = entryAlpha.value }
            // 下滑退出：由内部列表经 nested scroll 报告"已到顶还剩下的向下位移"才接管，
            // 列表还能向上滚时一个像素都不吃，滚动照常交还列表。
            .pullDownToDismissOnScroll(onDismiss = onDismiss)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val radiusDp = min(maxWidth.value, maxHeight.value) / 2f
            val centerYDp = maxHeight.value / 2f
            // 圆内可用宽度：取矩形离圆心更远的那条边所在的弦长，再各留 Wear.Space.Sm 安全余量
            fun circleSafeWidth(topY: Float, boxHeight: Float): Dp {
                val farEdge = maxOf(abs(topY - centerYDp), abs(topY + boxHeight - centerYDp))
                val chordHalf = sqrt((radiusDp * radiusDp - farEdge * farEdge).coerceAtLeast(0f))
                return ((chordHalf - Wear.Space.Sm.value).coerceAtLeast(0f) * 2f).dp
            }

            // 标题整块落在圆周内：宽度取该行上沿处的弦长（圆屏最外圈不放任何控件）
            val headerTop = Wear.Size.TopContentInset + Wear.Space.Xs
            val headerWidth = circleSafeWidth(headerTop.value, Wear.Size.Touch.value)
                .coerceAtLeast(Wear.Size.Touch * 2)

            CompositionLocalProvider(LocalSubPageDismiss provides onDismiss) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Wear.Space.Edge),
                ) {
                    // 标题水平居中；圆屏最外圈不再放叉号。子页退出 = 下滑 / 系统 BACK / 末尾完成返回。
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = headerWidth)
                            .padding(top = headerTop, bottom = Wear.Space.Xs)
                            .height(Wear.Size.Touch),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            text = title,
                            style = Wear.TextTitle.copy(color = Wear.Palette.TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        content()
                    }
                }
            }
        }
    }
}

/** 子页内容末尾的"完成返回"：关闭回调由 [SubSettingsModal] 注入，避免改动各子页签名。 */
private val LocalSubPageDismiss = staticCompositionLocalOf<(() -> Unit)?> { null }

/** 子页滚动内容的最后一项：44dp"完成返回"键。 */
@Composable
fun SubPageDoneRow() {
    val dismiss = LocalSubPageDismiss.current ?: return
    val context = LocalContext.current
    SettingsActionButton(
        label = "完成返回",
        tone = Tone.ACCENT,
        onClick = {
            context.vibrateTick()
            dismiss()
        },
    )
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
 * - 松手位移超过 [PullDownDismissThreshold] 以 tween(140) 退场并回调 [onDismiss]（isDismissing 保证只回调一次），
 *   否则以 spring(0.75f, 350f) 弹回原位。
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
 * 拖动期间不改位移——位移会反过来改变子列表的局部坐标，同样会自反馈——累积量只在松手
 * （[NestedScrollConnection.onPreFling]）时结算：超过 [PullDownDismissThreshold] 以 tween(140) 退场。
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
