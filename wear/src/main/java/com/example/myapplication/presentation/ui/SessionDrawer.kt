package com.example.myapplication.presentation.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearDivider
import com.example.myapplication.presentation.ui.components.WearRow
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 会话抽屉：不透明实底，无重影
 * - 模型切换为 WearChip 横向滚动，会话条目为 WearRow 风格双行信息 + 44dp 删除键
 * - 屏幕曲率变形挤压：依托 ScalingLazyColumn，在圆屏上下边缘随物理边界平滑挤压缩放
 * - 右滑跟手阻尼退出，硬件表冠联动滚动并伴随轻微触觉微震
 */
@Composable
fun SessionDrawer(
    isOpen: Boolean,
    sessions: List<ChatSession>,
    activeSessionId: String,
    currentModelName: String = "",
    availableModels: List<String> = emptyList(),
    onSelectModel: ((String) -> Unit)? = null,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(Wear.Motion.base()) + slideInHorizontally(Wear.Motion.page()) { it },
        exit = fadeOut(Wear.Motion.base()) + slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it }
    ) {
        val listState = rememberScalingLazyListState()
        val drawerFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            drawerFocusRequester.requestFocus()
        }

        // 横滑跟手退出 (左右两向对称，基于增量 delta 累加，杜绝局部坐标递归反向漂移闪烁)
        val dragOffsetX = remember { Animatable(0f) }
        var screenWidthPx by remember { mutableFloatStateOf(384f) }
        val dismissThresholdPx = with(density) { 50.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Wear.Palette.Bg)
                .onGloballyPositioned { coords ->
                    screenWidthPx = coords.size.width.toFloat()
                }
                // 手势节点不携带位移：位移放在内层，否则"局部坐标 + 自身平移"会互相放大
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val offset = dragOffsetX.value
                            if (abs(offset) >= dismissThresholdPx) {
                                // 退场朝手指实际拖拽的方向滑出，而不是固定向右
                                val direction = if (offset >= 0f) 1f else -1f
                                scope.launch {
                                    dragOffsetX.animateTo(direction * screenWidthPx, Wear.Motion.quick())
                                    onClose()
                                }
                            } else {
                                scope.launch {
                                    dragOffsetX.animateTo(0f, Wear.Motion.micro())
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragOffsetX.animateTo(0f, Wear.Motion.micro())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // 物理增量累加，手指停下时 dragAmount=0，位移保持不动，绝对无闪烁
                            change.consume()
                            val next = (dragOffsetX.value + dragAmount)
                                .coerceIn(-screenWidthPx, screenWidthPx)
                            scope.launch {
                                dragOffsetX.snapTo(next)
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dragOffsetX.value
                        // 位移可正可负，衰减与判定一律按绝对值，左右两向关闭观感一致
                        alpha = (1f - abs(dragOffsetX.value) / (screenWidthPx * 1.5f)).coerceIn(0f, 1f)
                    }
            ) {
                ScalingLazyColumn(
                    state = listState,
                    // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    modifier = Modifier
                        .fillMaxSize()
                        // 官方 M3E 表冠：刻度→位移、松手惯性、平台触感（ScrollTick / ScrollLimit）全在
                        // behavior 里，不再自造 8f 震动门限；自带 focusTarget，不要再叠 focusable()
                        .rotaryScrollable(
                            behavior = RotaryScrollableDefaults.behavior(listState),
                            focusRequester = drawerFocusRequester
                        ),
                    contentPadding = PaddingValues(horizontal = Wear.Space.Lg, vertical = Wear.Space.Lg),
                    verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 顶部标题与关闭键
                    item(key = "header_title") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BasicText(
                                text = stringResource(R.string.ui_drawer_title),
                                modifier = Modifier.weight(1f),
                                style = Wear.TextTitle,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            CloseButton(onClick = onClose)
                        }
                    }

                    // 快捷模型切换行 (保留横向滚动与 availableModels 入参)
                    item(key = "model_switch_row") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
                        ) {
                            WearSectionHeader(text = stringResource(R.string.ui_drawer_current_model))
                            if (ModelLabels.isEmpty(availableModels)) {
                                BasicText(
                                    text = stringResource(R.string.ui_drawer_no_models),
                                    style = Wear.TextCaption,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                                ) {
                                    availableModels.forEach { modelKey ->
                                        WearChip(
                                            label = ModelLabels.label(modelKey),
                                            selected = currentModelName == modelKey,
                                            onClick = {
                                                context.getSystemService(Vibrator::class.java)?.let { vib ->
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                                    }
                                                }
                                                onSelectModel?.invoke(modelKey)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 新开对话：单行列表项
                    item(key = "action_new_session") {
                        WearRow(
                            title = stringResource(R.string.ui_drawer_new_chat),
                            onClick = onCreateSession,
                            showChevron = false
                        )
                    }

                    // 会话列表：标题 14sp + 次行 12sp + 44dp 删除键
                    items(sessions, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            isSelected = session.id == activeSessionId,
                            onClick = { onSelectSession(session.id) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }

                    // 系统设置入口：抽屉底部的稳定入口，不再依赖"主键上滑且必须够快"这一易被判成长按的手势
                    item(key = "action_settings") {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
                        ) {
                            WearDivider()
                            WearRow(
                                title = stringResource(R.string.ui_drawer_settings),
                                showChevron = true,
                                onClick = onOpenSettings
                            )
                        }
                    }

                    // 圆屏底部预留：保证滚到底时最后一行会话完整可见（不被圆形裁切）
                    item(key = "bottom_reserve") {
                        Spacer(modifier = Modifier.height(Wear.Size.BottomBarReserve))
                    }
                }
            }
        }
    }
}

/**
 * 单个会话条目：状态点 + 标题 + 时间 + 独立删除键
 * 删除为二次确认：点 ✕ 只把本行切到确认态（删除 / 取消 两个 44dp 键），3 秒无操作自动复原，
 * 确认态只挂在 session.id 上，不会误删其它会话。
 */
@Composable
private fun SessionRow(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(session.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.createdAt))
    }
    val shape = RoundedCornerShape(Wear.Radius.Control)

    var confirmingDelete by remember(session.id) { mutableStateOf(false) }
    LaunchedEffect(session.id, confirmingDelete) {
        if (confirmingDelete) {
            delay(3000)
            confirmingDelete = false
        }
    }

    val containerColor = when {
        confirmingDelete -> Wear.Palette.DangerSurface
        isSelected -> Wear.Palette.SurfaceAlt
        else -> Wear.Palette.Surface
    }
    val strokeColor = when {
        confirmingDelete -> Wear.Palette.Danger
        isSelected -> Wear.Palette.Accent
        else -> Wear.Palette.Border
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, strokeColor, shape)
            // 确认态下整行不再可点，避免"确认为删除"的窗口里误切会话
            .clickable(enabled = !confirmingDelete, role = Role.Button, onClick = onClick)
            .padding(start = Wear.Space.Md, end = Wear.Space.Xs, top = Wear.Space.Xs, bottom = Wear.Space.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
    ) {
        if (confirmingDelete) {
            BasicText(
                text = stringResource(R.string.ui_drawer_delete_confirm),
                modifier = Modifier.weight(1f),
                style = Wear.TextCaption.copy(color = Wear.Palette.Danger),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            ConfirmDeleteButton(
                label = stringResource(R.string.ui_common_delete),
                color = Wear.Palette.Danger,
                onClick = {
                    confirmingDelete = false
                    onDelete()
                }
            )
            ConfirmDeleteButton(
                label = stringResource(R.string.ui_common_cancel),
                color = Wear.Palette.TextSecondary,
                onClick = { confirmingDelete = false }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Wear.Space.Sm)
                    .clip(CircleShape)
                    .background(if (isSelected) Wear.Palette.Accent else Wear.Palette.TextMuted)
            )

            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = session.title,
                    style = Wear.TextBody.copy(
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) Wear.Palette.TextPrimary else Wear.Palette.TextSecondary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                BasicText(
                    text = timeStr,
                    style = Wear.TextMono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            DeleteButton(onClick = { confirmingDelete = true })
        }
    }
}

/** 44dp Canvas 叉号删除键：只进入确认态，不直接删数据。 */
@Composable
private fun DeleteButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CrossMark(color = Wear.Palette.Danger)
    }
}

/** 44dp 确认态按钮：删除用 Danger，取消用次要文字色。 */
@Composable
private fun ConfirmDeleteButton(label: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Wear.Radius.Control)
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(shape)
            .background(Wear.Palette.Surface)
            .border(1.dp, color, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = Wear.TextCaption.copy(color = color),
            maxLines = 1
        )
    }
}

/** 44dp Canvas 叉号关闭键。 */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CrossMark(color = Wear.Palette.TextSecondary)
    }
}

@Composable
private fun CrossMark(color: androidx.compose.ui.graphics.Color) {
    Canvas(modifier = Modifier.size(Wear.Space.Md)) {
        val inset = size.minDimension * 0.25f
        val stroke = Wear.Size.BadgeHeight.toPx() * 0.1f
        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}