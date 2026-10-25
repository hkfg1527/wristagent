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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.contract.RightButtonAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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
    rightButtonAction: RightButtonAction = RightButtonAction.MODEL_SWITCH,
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
        else -> 44.dp
    }

    val capsuleWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 260f),
        label = "capsuleStretchWidth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
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
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141722))
                            .border(1.dp, Color(0xFF333D56), CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    scope.launch {
                                        isSplitExpanded = false
                                        splitProgress.animateTo(0f, spring(0.72f, 280f))
                                    }
                                    if (mainInputType == MainInputType.VOICE) {
                                        onMainInputTypeChange(MainInputType.KEYBOARD)
                                        onLeftKeyboardClick()
                                    } else {
                                        onMainInputTypeChange(MainInputType.VOICE)
                                    }
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (mainInputType == MainInputType.VOICE) {
                            MinimalistKeyboardIcon(tint = Color(0xFF94A3B8))
                        } else {
                            MinimalistSoundwaveIcon(tint = Color(0xFF94A3B8), isRecording = false)
                        }
                    }
                }

                // 右副键：模型/设置/清空 (MDL/SET/CLR)
                if (wingAlpha > 0.02f) {
                    val rightOffsetDp = (50.dp * splitProgress.value)
                    Box(
                        modifier = Modifier
                            .offset(x = rightOffsetDp)
                            .scale(wingScale)
                            .alpha(wingAlpha)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141722))
                            .border(1.dp, Color(0xFF333D56), CircleShape)
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
                        when (rightButtonAction) {
                            RightButtonAction.MODEL_SWITCH -> MinimalistLayersIcon(tint = Color(0xFF38BDF8))
                            RightButtonAction.OPEN_SETTINGS -> MinimalistSlidersIcon(tint = Color(0xFF38BDF8))
                            RightButtonAction.CLEAR_MEMORY -> Text(
                                text = "CLR",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                        }
                    }
                }
            }

            // ================= 核心单体主按键 (手势感应、弹性伸缩、长按录音) =================
            Box(
                modifier = Modifier
                    .scale(orbScaleAnim)
                    .size(capsuleWidth, 44.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Color(0xFFE2E8F0)) // 高对比冷银白质感
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
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(15.dp, 11.dp)
                    )
                } else {
                    MinimalistSoundwaveIcon(
                        tint = if (mode == BottomBlockMode.RECORDING) Color(0xFFEF4444) else Color(0xFF0F172A),
                        isRecording = mode == BottomBlockMode.RECORDING,
                        modifier = Modifier.size(13.dp, 13.dp)
                    )
                }
            }
        } else if (isApproval) {
            // ================= 审批拦截：向左滑动放行 (小白倒计时锁定 + 纯红滑块) =================
            ApprovalLeftSlider(
                capsuleWidth = capsuleWidth,
                cooldownSeconds = cooldownSeconds,
                riskExplanation = riskExplanation,
                onAllow = onAllow,
                onReject = onReject
            )
        } else {
            // ================= 思考中：微型停止按钮 =================
            Box(
                modifier = Modifier
                    .size(capsuleWidth, 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF1E2433))
                    .border(0.8.dp, Color(0xFF475569), RoundedCornerShape(20.dp))
                    .clickable { onStopClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(7.dp).background(Color(0xFFEF4444), RoundedCornerShape(1.5.dp)))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "停止",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE2E8F0)
                    )
                }
            }
        }
    }
}

/**
 * 审批态滑块：向左滑动放行 (Swipe Left to Allow)，点击左侧空白拒绝
 */
@Composable
private fun ApprovalLeftSlider(
    capsuleWidth: androidx.compose.ui.unit.Dp,
    cooldownSeconds: Int,
    riskExplanation: String,
    onAllow: () -> Unit,
    onReject: () -> Unit
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(1f) }
    val thumbWidthPx = with(density) { 48.dp.toPx() }
    val maxDragLeftPx = remember(containerWidthPx, thumbWidthPx) {
        (containerWidthPx - thumbWidthPx - with(density) { 6.dp.toPx() }).coerceAtLeast(1f)
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

    Box(
        modifier = Modifier
            .width(capsuleWidth)
            .height(42.dp)
            .clip(RoundedCornerShape(21.dp))
            .background(Color(0xFF1E1418))
            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.85f), RoundedCornerShape(21.dp))
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
                                // 点击任意空白区域拒绝
                                onReject()
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
        // 提示文本居中
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "点击拒绝",
                fontSize = 9.sp,
                color = Color(0xFFEF4444).copy(alpha = 0.8f)
            )
            Text(
                text = if (remainingCooldown > 0) "锁定 (${remainingCooldown}s)" else "向左滑放行 <",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (remainingCooldown > 0) Color(0xFFFBBF24) else Color(0xFFE2E8F0)
            )
        }

        // 滑块手柄 (初始在最右侧，向左滑动)
        Box(
            modifier = Modifier
                .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
                .padding(end = 4.dp)
                .size(width = 44.dp, height = 34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (remainingCooldown > 0) Color(0xFF475569) else Color(0xFFEF4444)),
            contentAlignment = Alignment.Center
        ) {
            if (remainingCooldown > 0) {
                Text(
                    text = "${remainingCooldown}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Canvas(modifier = Modifier.size(7.dp, 9.dp)) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width, 0f)
                        lineTo(0f, size.height / 2f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, color = Color.White)
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
