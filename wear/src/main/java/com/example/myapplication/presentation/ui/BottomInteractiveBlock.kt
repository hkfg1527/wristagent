package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 底部多功能胶囊交互模式
 */
enum class BottomBlockMode {
    IDLE,               // 闲置单体主按键 (手势滑动可临时呼出副键)
    RECORDING,          // 录音中：按键微放大回弹
    THINKING_STOP,      // 正在思考：拉长为小巧胶囊显示“停止”
    ACTION_ALLOW        // 审批拦截：拉长为滑块横条，向左滑动放行
}

/**
 * 主按键模式：语音优先 vs 键盘优先
 */
enum class MainInputType {
    VOICE,
    KEYBOARD
}

/**
 * 单体形态演变底栏控制器 (左右滑动弹性弹射两翼 · 点击弹性收回 · 绝无 Emoji)
 */
@Composable
fun BottomInteractiveBlock(
    mode: BottomBlockMode,
    modifier: Modifier = Modifier,
    mainInputType: MainInputType = MainInputType.VOICE,
    onMainInputTypeChange: (MainInputType) -> Unit = {},
    cooldownSeconds: Int = 0,               // 严格确认模式下的风险锁定倒计时
    riskExplanation: String = "",           // 审批横条上方的最坏后果说明
    onVoiceStartRecord: () -> Unit,
    onVoiceStopAndTrigger: () -> Unit,
    onLeftKeyboardClick: () -> Unit,
    onRightActionClick: () -> Unit,
    onStopClick: () -> Unit,
    onAllow: () -> Unit,
    onReject: () -> Unit,
    onSwipeUpSettings: () -> Unit           // 阻尼跟手上滑进设置
) {
    val scope = rememberCoroutineScope()
    val isApproval = mode == BottomBlockMode.ACTION_ALLOW
    val isBusy = mode in listOf(BottomBlockMode.RECORDING, BottomBlockMode.THINKING_STOP, BottomBlockMode.ACTION_ALLOW)

    // 分裂展开状态与两翼弹性动量
    var isSplitExpanded by remember { mutableStateOf(false) }
    val splitProgress = remember { Animatable(0f) }

    // 录音按压缩放
    val orbScaleAnim by animateFloatAsState(
        targetValue = if (mode == BottomBlockMode.RECORDING) 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 280f),
        label = "orbRecordScale"
    )

    // 胶囊宽度物理演变：待机 44dp -> 思考 88dp -> 审批 180dp
    val targetWidth = when (mode) {
        BottomBlockMode.ACTION_ALLOW -> 180.dp
        BottomBlockMode.THINKING_STOP -> 88.dp
        else -> Wear.Size.Orb
    }

    val capsuleWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "capsuleStretchWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            // 主键之外起手的横向拖动同样弹出两翼，并独占指针流，避免被系统"右滑返回"抢走。
            // 两翼已展开时同一条手势改为反向收回，使"横滑打开"有对称的"横滑收起"。
            .pointerInput(isBusy, mode, isSplitExpanded) {
                if (isBusy || mode != BottomBlockMode.IDLE) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    val orbHalfPx = Wear.Size.Orb.toPx() / 2f
                    // 主键内部的手势交给主键自己处理，这里只补主键外缘的空白区
                    if (abs(startX - size.width / 2f) <= orbHalfPx) return@awaitEachGesture
                    var isHorizontalDrag = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            if (isHorizontalDrag) {
                                scope.launch {
                                    if (isSplitExpanded) {
                                        // 反向横滑：收回两翼（方向不限）
                                        isSplitExpanded = false
                                        splitProgress.animateTo(0f, spring(0.72f, 280f))
                                    } else {
                                        isSplitExpanded = true
                                        splitProgress.animateTo(1f, spring(0.65f, 260f))
                                    }
                                }
                            }
                            break
                        }
                        val deltaX = change.position.x - startX
                        val deltaY = change.position.y - startY
                        // 只消费横向位移：纵向仍按原样交给上层，避免系统把横向拖动判成"右滑返回"
                        if (abs(deltaX) >= abs(deltaY)) change.consume()
                        if (abs(deltaX) > 22.dp.toPx() && abs(deltaX) > abs(deltaY) * 1.4f) {
                            isHorizontalDrag = true
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isApproval && mode != BottomBlockMode.THINKING_STOP) {
            // ================= 左右副键：左滑/右滑以弹性弹射出 (Spring Overshoot) =================
            if (!isBusy) {
                val wingScale = (0.4f + 0.6f * splitProgress.value).coerceIn(0f, 1f)
                val wingAlpha = splitProgress.value.coerceIn(0f, 1f)

                // 左副键：键盘文字录入 (TXT) 或切回语音 (MIC)
                if (wingAlpha > 0.02f) {
                    val leftOffsetDp = (-50.dp * splitProgress.value)
                    Box(
                        modifier = Modifier
                            .offset(x = leftOffsetDp)
                            .scale(wingScale)
                            .alpha(wingAlpha)
                            .size(Wear.Size.Touch)
                            .clip(CircleShape)
                            .background(Wear.Palette.SurfaceAlt)
                            .border(1.dp, Wear.Palette.Border, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    scope.launch {
                                        isSplitExpanded = false
                                        splitProgress.animateTo(0f, spring(0.72f, 280f))
                                    }
                                    // 左副键只负责切换输入方式；"打开输入框"统一归主键，避免一按就被 IME 盖住提示
                                    if (mainInputType == MainInputType.VOICE) {
                                        onMainInputTypeChange(MainInputType.KEYBOARD)
                                    } else {
                                        onMainInputTypeChange(MainInputType.VOICE)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mainInputType == MainInputType.VOICE) {
                            MinimalistKeyboardIcon(tint = Wear.Palette.TextSecondary)
                        } else {
                            MinimalistSoundwaveIcon(tint = Wear.Palette.TextSecondary, isRecording = false)
                        }
                    }
                }

                // 右副键：固定打开会话抽屉 (横向手势已被系统占用，不再按偏好渲染三种图标)
                if (wingAlpha > 0.02f) {
                    val rightOffsetDp = (50.dp * splitProgress.value)
                    Box(
                        modifier = Modifier
                            .offset(x = rightOffsetDp)
                            .scale(wingScale)
                            .alpha(wingAlpha)
                            .size(Wear.Size.Touch)
                            .clip(CircleShape)
                            .background(Wear.Palette.SurfaceAlt)
                            .border(1.dp, Wear.Palette.Border, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    scope.launch {
                                        isSplitExpanded = false
                                        splitProgress.animateTo(0f, spring(0.72f, 280f))
                                    }
                                    onRightActionClick()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MinimalistLayersIcon(tint = Wear.Palette.Accent)
                    }
                }
            }

            // ================= 核心单体主按键 (手势感应、弹性伸缩、长按录音) =================
            Box(
                modifier = Modifier
                    .scale(orbScaleAnim)
                    .size(capsuleWidth, Wear.Size.Orb)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    // 冷银白主键底色是品牌识别，允许保留此唯一色值字面量
                    .background(Color(0xFFE8EDF5))
                    .pointerInput(isSplitExpanded) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var isLongPressActive = false
                            var isDraggingX = false
                            var isDraggingY = false

                            val longPressJob = scope.launch {
                                delay(340)
                                if (!isDraggingX && !isDraggingY && !isSplitExpanded) {
                                    isLongPressActive = true
                                    onVoiceStartRecord()
                                }
                            }

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                if (!change.pressed) {
                                    longPressJob.cancel()
                                    if (isLongPressActive) {
                                        // 释放长按：松手回弹 + 发射水波纹 + 开始思考
                                        onVoiceStopAndTrigger()
                                    } else if (isDraggingY) {
                                        val totalDeltaY = change.position.y - startY
                                        if (totalDeltaY < -38.dp.toPx()) {
                                            onSwipeUpSettings() // 触发跟手上滑进入设置
                                        }
                                    } else if (isDraggingX) {
                                        // 水平滑动：弹性弹射出两翼按钮
                                        scope.launch {
                                            isSplitExpanded = true
                                            splitProgress.animateTo(
                                                targetValue = 1f,
                                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 260f)
                                            )
                                        }
                                    } else {
                                        // 轻触单击：
                                        if (isSplitExpanded) {
                                            // 已展开状态下轻触一次弹性收起
                                            scope.launch {
                                                isSplitExpanded = false
                                                splitProgress.animateTo(
                                                    targetValue = 0f,
                                                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 280f)
                                                )
                                            }
                                        } else if (mode == BottomBlockMode.RECORDING) {
                                            // 正在录音时单触：结束录音并触发
                                            onVoiceStopAndTrigger()
                                        } else if (mainInputType == MainInputType.KEYBOARD) {
                                            // 键盘主键模式下单触：直接弹出文字输入对话框
                                            onLeftKeyboardClick()
                                        } else {
                                            // 语音模式下单触：启动录音 (设备若无录音引擎则弹出快捷输入)
                                            onVoiceStartRecord()
                                        }
                                    }
                                    break
                                }

                                val deltaX = change.position.x - startX
                                val deltaY = change.position.y - startY

                                // 拖动期间独占指针流：不消费则 Wear 系统的"右滑返回"会把横向拖动抢成返回手势
                                change.consume()

                                if (!isDraggingX && !isDraggingY && !isLongPressActive) {
                                    if (abs(deltaX) > 22.dp.toPx() && abs(deltaX) > abs(deltaY) * 1.4f) {
                                        isDraggingX = true
                                        longPressJob.cancel()
                                    } else if (deltaY < -32.dp.toPx() && abs(deltaY) > abs(deltaX) * 1.4f) {
                                        isDraggingY = true
                                        longPressJob.cancel()
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (mainInputType == MainInputType.KEYBOARD && mode != BottomBlockMode.RECORDING) {
                    MinimalistKeyboardIcon(
                        tint = Wear.Palette.Bg,
                        modifier = Modifier.size(16.dp, 12.dp)
                    )
                } else {
                    MinimalistSoundwaveIcon(
                        tint = if (mode == BottomBlockMode.RECORDING) Wear.Palette.Danger else Wear.Palette.Bg,
                        isRecording = mode == BottomBlockMode.RECORDING,
                        modifier = Modifier.size(16.dp, 16.dp)
                    )
                }
            }
        } else if (isApproval) {
            // ================= 审批拦截：滑块向左滑放行 + 同排 44dp 拒绝键，整行收进圆周内 =================
            Box(
                modifier = Modifier.fillMaxSize(),
                // 顶对齐能把整行抬到弦长更大的位置，横向可用宽度随之上限更高
                contentAlignment = Alignment.TopCenter
            ) {
                ApprovalBar(
                    riskExplanation = riskExplanation,
                    cooldownSeconds = cooldownSeconds,
                    onAllow = onAllow,
                    onReject = onReject
                )
            }
        } else {
            // ================= 思考中：微型停止按钮 =================
            val stopShape = RoundedCornerShape(Wear.Radius.Chip)
            Box(
                modifier = Modifier
                    .size(capsuleWidth, Wear.Size.Touch)
                    .clip(stopShape)
                    .background(Wear.Palette.SurfaceAlt)
                    .border(1.dp, Wear.Palette.Border, stopShape)
                    .clickable { onStopClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Wear.Palette.Danger, CircleShape))
                    BasicText(
                        text = "停止",
                        style = Wear.TextCaption.copy(color = Wear.Palette.TextPrimary),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 审批行：滑块 + 44dp 拒绝键同排。
 * 圆屏几何：容器按圆处理，圆心在正中、半径 R = min(w,h)/2；本行高 44dp，取它离圆心更远的那条边 farEdge，
 * 该高度上可用弦长的一半为 sqrt(R² - farEdge²)，两侧各留 [Wear.Space.Sm] 余量，得到整行宽度上限
 * 2*sqrt(R² - farEdge²) - 2*Sm，保证四角都在圆周内（与设置子页 circleSafeWidth 同一套算法）。
 */
@Composable
private fun ApprovalBar(
    riskExplanation: String,
    cooldownSeconds: Int,
    onAllow: () -> Unit,
    onReject: () -> Unit
) {
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var rowTopPx by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(density) { Wear.Size.Touch.toPx() }
    val marginPx = with(density) { Wear.Space.Sm.toPx() }
    // 拒绝键 44dp + 间距 8dp + 滑块至少一个 44dp 触控区
    val minRowWidth = Wear.Size.Touch * 2 + Wear.Space.Sm

    val rowWidth = remember(rowTopPx, windowSize, rowHeightPx) {
        val radiusPx = (min(windowSize.width, windowSize.height) / 2f)
        if (radiusPx < rowHeightPx) {
            minRowWidth
        } else {
            val centerY = windowSize.height / 2f
            val farEdge = max(abs(rowTopPx - centerY), abs(rowTopPx + rowHeightPx - centerY))
            val halfChord = sqrt((radiusPx * radiusPx - farEdge * farEdge).coerceAtLeast(1f))
            with(density) { (2f * halfChord - 2f * marginPx).toDp() }
                .coerceIn(minRowWidth, Wear.Size.Orb * 4)
        }
    }

    Row(
        modifier = Modifier
            .onGloballyPositioned { coords -> rowTopPx = coords.positionInRoot().y }
            .widthIn(max = rowWidth)
            .fillMaxWidth()
            .height(Wear.Size.Touch),
        horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ApprovalLeftSlider(
            modifier = Modifier.weight(1f),
            riskExplanation = riskExplanation,
            cooldownSeconds = cooldownSeconds,
            onAllow = onAllow
        )
        RejectButton(onClick = onReject)
    }
}

/** 审批态 44dp 显式拒绝键：Danger 描边 + Danger 文字，点击即 [onReject]。 */
@Composable
private fun RejectButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(Wear.Radius.Chip)
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(shape)
            .background(Wear.Palette.DangerSurface)
            .border(1.dp, Wear.Palette.Danger, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = "拒绝",
            style = Wear.TextBody.copy(color = Wear.Palette.Danger),
            maxLines = 1
        )
    }
}

/**
 * 审批态滑块：向左滑动放行 (Swipe Left to Allow)。
 * 拒绝改为右侧独立的 44dp 显式按钮，滑块自身不再暗含"点空白即拒绝"的隐藏路径：
 * 未拖动到位的点击一律视为无效操作并回弹，避免误触直接否决。
 */
@Composable
private fun ApprovalLeftSlider(
    modifier: Modifier = Modifier,
    cooldownSeconds: Int,
    riskExplanation: String,
    onAllow: () -> Unit
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    // 拇指是纯视觉件（触控区是整条滑块），宽度随滑块变窄自适应，给文案让出位置
    val thumbWidth = with(density) { (containerWidthPx * 0.32f).toDp() }.coerceIn(22.dp, Wear.Size.Touch)
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val maxDragLeftPx = remember(containerWidthPx, thumbWidthPx) {
        (containerWidthPx - thumbWidthPx - with(density) { 6.dp.toPx() }).coerceAtLeast(1f)
    }
    val labelSpace = with(density) {
        (containerWidthPx - thumbWidthPx - 12.dp.toPx()).toDp()
    }

    val dragOffsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    var remainingCooldown by remember(cooldownSeconds) { mutableIntStateOf(cooldownSeconds) }
    LaunchedEffect(cooldownSeconds) {
        remainingCooldown = cooldownSeconds
        while (remainingCooldown > 0) {
            delay(1000)
            remainingCooldown--
        }
    }

    val sliderShape = RoundedCornerShape(Wear.Radius.Chip)
    Box(
        modifier = modifier
            .height(Wear.Size.Touch)
            .clip(sliderShape)
            .background(Wear.Palette.DangerSurface)
            .border(1.dp, Wear.Palette.Danger, sliderShape)
            .onGloballyPositioned { coords ->
                containerWidthPx = coords.size.width.toFloat()
            }
            .pointerInput(remainingCooldown, maxDragLeftPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    var hasDragged = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (hasDragged) {
                                val current = abs(dragOffsetX.value)
                                if (current > maxDragLeftPx * 0.45f && remainingCooldown == 0) {
                                    scope.launch {
                                        dragOffsetX.animateTo(-maxDragLeftPx, tween(100))
                                        onAllow()
                                    }
                                } else {
                                    scope.launch { dragOffsetX.animateTo(0f, spring(0.72f, 280f)) }
                                }
                            } else {
                                // 未拖动到位的点击不构成任何决定，直接回弹
                                scope.launch { dragOffsetX.animateTo(0f, spring(0.72f, 280f)) }
                            }
                            break
                        }

                        val deltaX = change.position.x - startX
                        // 仅允许向左滑动 (deltaX < 0)
                        if (deltaX < -10.dp.toPx()) {
                            hasDragged = true
                            if (remainingCooldown == 0) {
                                change.consume()
                                val clamped = deltaX.coerceIn(-maxDragLeftPx, 0f)
                                scope.launch { dragOffsetX.snapTo(clamped) }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.CenterEnd
    ) {
        // 提示文本：只保留"如何放行"这一条真实存在的操作；宽度不够时宁可不显示也不显示半截字
        if (labelSpace >= 34.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = thumbWidth + 4.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicText(
                    text = if (remainingCooldown > 0) "锁定" else "← 放行",
                    style = Wear.TextCaption.copy(
                        color = if (remainingCooldown > 0) Wear.Palette.Accent else Wear.Palette.TextPrimary
                    ),
                    maxLines = 1
                )
            }
        }

        // 滑块手柄 (初始在最右侧，向左滑动)
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                .padding(end = 4.dp)
                .size(width = thumbWidth, height = 34.dp)
                .clip(RoundedCornerShape(Wear.Radius.Chip))
                .background(if (remainingCooldown > 0) Wear.Palette.TextMuted else Wear.Palette.Danger),
            contentAlignment = Alignment.Center
        ) {
            if (remainingCooldown > 0) {
                BasicText(
                    text = "${remainingCooldown}",
                    style = Wear.TextCaption.copy(color = Wear.Palette.TextPrimary),
                    maxLines = 1
                )
            } else {
                Canvas(modifier = Modifier.size(7.dp, 9.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height / 2f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, color = Wear.Palette.TextPrimary)
                }
            }
        }
    }
}

/**
 * 极简微晶键盘图标
 */
@Composable
fun MinimalistKeyboardIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(15.dp, 11.dp)) {
        val strokeW = 1.2.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(strokeW / 2, strokeW / 2),
            size = Size(size.width - strokeW, size.height - strokeW),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = strokeW)
        )
        val dotRadius = 0.75.dp.toPx()
        drawCircle(color = tint, radius = dotRadius, center = Offset(size.width * 0.28f, size.height * 0.35f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(size.width * 0.50f, size.height * 0.35f))
        drawCircle(color = tint, radius = dotRadius, center = Offset(size.width * 0.72f, size.height * 0.35f))
        drawLine(
            color = tint,
            start = Offset(size.width * 0.30f, size.height * 0.72f),
            end = Offset(size.width * 0.70f, size.height * 0.72f),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

/**
 * 极简声波/麦克风图标 (录音中动态起伏波形)
 */
@Composable
fun MinimalistSoundwaveIcon(
    tint: Color,
    isRecording: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
        label = "w1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.9f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "w2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
        label = "w3"
    )

    Canvas(modifier = modifier.size(13.dp, 13.dp)) {
        val barWidth = 1.8.dp.toPx()
        val spacing = size.width / 4f
        val centerY = size.height / 2f

        val h1 = if (isRecording) size.height * (0.35f + 0.65f * wave1) else size.height * 0.45f
        val h2 = if (isRecording) size.height * (0.45f + 0.55f * wave2) else size.height * 0.90f
        val h3 = if (isRecording) size.height * (0.35f + 0.65f * wave3) else size.height * 0.45f

        drawLine(
            color = tint,
            start = Offset(spacing * 1, centerY - h1 / 2),
            end = Offset(spacing * 1, centerY + h1 / 2),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(spacing * 2, centerY - h2 / 2),
            end = Offset(spacing * 2, centerY + h2 / 2),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(spacing * 3, centerY - h3 / 2),
            end = Offset(spacing * 3, centerY + h3 / 2),
            strokeWidth = barWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * 极简调节滑块图标 (对应设置)
 */
@Composable
fun MinimalistSlidersIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(13.dp, 11.dp)) {
        val strokeW = 1.2.dp.toPx()
        val dotR = 1.3.dp.toPx()
        // Line 1
        drawLine(
            color = tint,
            start = Offset(0f, size.height * 0.28f),
            end = Offset(size.width, size.height * 0.28f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawCircle(color = tint, radius = dotR, center = Offset(size.width * 0.35f, size.height * 0.28f))

        // Line 2
        drawLine(
            color = tint,
            start = Offset(0f, size.height * 0.72f),
            end = Offset(size.width, size.height * 0.72f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawCircle(color = tint, radius = dotR, center = Offset(size.width * 0.68f, size.height * 0.72f))
    }
}

/**
 * 极简模型分层图标 (对应模型切换)
 */
@Composable
fun MinimalistLayersIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(13.dp, 11.dp)) {
        val strokeW = 1.2.dp.toPx()
        drawLine(
            color = tint,
            start = Offset(size.width * 0.15f, size.height * 0.2f),
            end = Offset(size.width * 0.85f, size.height * 0.2f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.25f, size.height * 0.5f),
            end = Offset(size.width * 0.75f, size.height * 0.5f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.15f, size.height * 0.8f),
            end = Offset(size.width * 0.85f, size.height * 0.8f),
            strokeWidth = strokeW,
            cap = StrokeCap.Round
        )
    }
}
