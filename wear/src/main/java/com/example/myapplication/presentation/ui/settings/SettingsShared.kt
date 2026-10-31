package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
 * 全屏二级配置面板：顶部固定标题，中部滚动内容，底部固定"完成返回"键（支持右滑返回与入场淡入）。
 */
@Composable
fun SubSettingsModal(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    val dragOffsetX = remember { Animatable(0f) }
    val entryAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        entryAlpha.animateTo(1f, tween(160))
    }

    var screenWidthPx by remember { mutableFloatStateOf(480f) }
    val dismissThresholdPx = with(density) { 50.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
            .onGloballyPositioned { coords ->
                screenWidthPx = coords.size.width.toFloat()
            }
            .graphicsLayer {
                translationX = dragOffsetX.value
                alpha = (1f - (dragOffsetX.value / (screenWidthPx * 1.5f))).coerceIn(0f, 1f) * entryAlpha.value
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragOffsetX.value >= dismissThresholdPx) {
                            scope.launch {
                                dragOffsetX.animateTo(screenWidthPx, tween(140))
                                onDismiss()
                            }
                        } else {
                            scope.launch {
                                dragOffsetX.animateTo(0f, spring(0.75f, 320f))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffsetX.animateTo(0f, spring(0.75f, 320f))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // 增量驱动，手指停止滑动时 dragAmount=0，位移保持不变，彻底杜绝闪烁抽搐
                        if (dragAmount > 0f || dragOffsetX.value > 0f) {
                            change.consume()
                            val next = (dragOffsetX.value + dragAmount).coerceAtLeast(0f)
                            scope.launch {
                                dragOffsetX.snapTo(next)
                            }
                        }
                    }
                )
            }
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

            // 标题行（标题 + 44dp 叉号）整块落在圆周内：宽度取该行上沿处的弦长
            val headerTop = Wear.Size.TopContentInset + Wear.Space.Xs
            val headerWidth = circleSafeWidth(headerTop.value, Wear.Size.Touch.value)
                .coerceAtLeast(Wear.Size.Touch * 2)

            CompositionLocalProvider(LocalSubPageDismiss provides onDismiss) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Wear.Space.Edge),
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .widthIn(max = headerWidth)
                            .padding(top = headerTop, bottom = Wear.Space.Xs)
                            .height(Wear.Size.Touch),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Xs),
                    ) {
                        BasicText(
                            text = title,
                            modifier = Modifier.weight(1f),
                            style = Wear.TextTitle.copy(textAlign = TextAlign.Center),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SubPageCloseButton(onClick = onDismiss)
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

/** 44dp Canvas 叉号关闭键，与设置首页一致。 */
@Composable
private fun SubPageCloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(Wear.Space.Md)) {
            val inset = size.minDimension * 0.25f
            val stroke = Wear.Size.BadgeHeight.toPx() * 0.1f
            drawLine(
                color = Wear.Palette.TextSecondary,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Wear.Palette.TextSecondary,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
