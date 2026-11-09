package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.settings.motionSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
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

/** 审批扁胶囊的视觉高度：44dp 主键收成 32dp 的扁横条，可点热区仍取 [ApprovalTrackTouchHeight]。 */
internal val ApprovalCapsuleHeight = 32.dp

/** 审批滑轨的触控热区高度：视觉只有 [ApprovalCapsuleHeight]，热区保持 44dp 令牌不缩水。 */
internal val ApprovalTrackTouchHeight = Wear.Size.Touch

/** 扁胶囊内拇指与胶囊边缘的留白：32dp 胶囊留 3dp 边距，拇指直径 26dp。 */
private val ApprovalThumbInset = 3.dp

/** 收起态小白条的视觉尺寸：56dp × 8dp、圆角 4dp，贴底同锚点。 */
internal val CollapsedStripWidth = 56.dp
internal val CollapsedStripHeight = 8.dp
internal val CollapsedStripCorner = 4.dp

/**
 * 非审批态底栏块高度：常驻 58dp（主键 52dp + 上下余量）。
 *
 * 提成常量是因为镜头也要用它 —— 按钮"占住屏幕底部多高"是镜头判断可读区的输入，
 * 两侧各写一个 58.dp 就会出现"按钮改了、镜头还在按旧高度对齐"。
 */
internal val IdleBlockHeight = 58.dp

/**
 * 审批态底栏高度：轨道贴顶、[Wear.Size.BottomBarReserve] 再收 [Wear.Space.Md]。
 * 480px/2.0 密度下屏高 240dp、圆心 120dp，底栏顶边落在 240−12−64=164dp，
 * 32dp 胶囊居中于 44dp 热区后占 170dp..202dp，弦长 2√(120²−82²)≈175dp；
 * 于是正文底边最高可到 164dp，磨砂审批卡片（中心 96dp）拿到 26dp..166dp 的 y 区间。
 */
internal val ApprovalBlockHeight = Wear.Size.BottomBarReserve - Wear.Space.Md

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
    riskExplanation: String = "",           // 最坏后果说明：审批改为滑块形态后不再单独成行，保留参数以兼容调用方
    onVoiceStartRecord: () -> Unit,
    onVoiceStopAndTrigger: () -> Unit,
    onLeftKeyboardClick: () -> Unit,
    onRightActionClick: () -> Unit,
    onStopClick: () -> Unit,
    onAllow: () -> Unit,
    onReject: () -> Unit,
    onSwipeUpSettings: () -> Unit,           // 阻尼跟手上滑进设置
    onApprovalProgress: (Float) -> Unit = {}, // 审批滑块当前进度（0 拒绝红 → 1 放行绿），退出审批时回调 0f
    approvalTone: Color = Wear.Palette.Danger, // 审批强调色：随 AI 风险等级变化（调用方传入等级色）
    collapsed: Boolean = false,              // 用户已经滚动过：主键收起成小白条，点它才恢复
    // 收展进度 1=完整按钮 0=收起。由 ChatScreen 统一持有，保证"按钮缩小"与"弧条成形"是同一条时间线，
    // 不会出现"下面突然冒出、上面自己收、两边各弹一下"的割裂感。
    collapseProgress: Float = 1f,
    onCollapsedStripTap: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    // 动画开关：关掉后所有形变/弹射/滑块回弹都走 snap，直接落到目标值，不残留中间态
    val animationsEnabled = LocalAnimationsEnabled.current
    val isApproval = mode == BottomBlockMode.ACTION_ALLOW
    val isBusy = mode in listOf(BottomBlockMode.RECORDING, BottomBlockMode.THINKING_STOP, BottomBlockMode.ACTION_ALLOW)

    // 分裂展开状态与两翼弹性动量
    var isSplitExpanded by remember { mutableStateOf(false) }
    val splitProgress = remember { Animatable(0f) }

    // 收起时两翼必须先收干净，否则会从翼间掉下来；主键本身的尺寸/圆角跟随外部传入的
    // [collapseProgress]（与 ChatScreen 的弧条共用同一时间线）。
    LaunchedEffect(collapsed) {
        if (collapsed && (isSplitExpanded || splitProgress.value > 0f)) {
            isSplitExpanded = false
            splitProgress.animateTo(0f, motionSpec(animationsEnabled, Wear.Motion.micro()))
        }
    }

    // 录音按压缩放
    val orbScaleAnim by animateFloatAsState(
        targetValue = if (mode == BottomBlockMode.RECORDING) 1.08f else 1.0f,
        // 弹性手感：阻尼 0.62 保留一次轻微过冲，抬手时是"弹回来"而不是生硬复位；
        // 旧值 1.25 倍在 52dp 键上等于放大 13dp，视觉上像整颗键"炸开"，改成 1.08 只做呼吸式起伏。
        animationSpec = motionSpec(animationsEnabled, spring(dampingRatio = 0.62f, stiffness = 420f)),
        label = "orbRecordScale"
    )

    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    var blockTopPx by remember { mutableFloatStateOf(0f) }

    // 审批滑块是整行唯一控件，宽度直接取满该 y 处的圆弦长：行顶贴齐底栏顶边，圆心取屏幕正中，
    // R = min(w,h)/2，弦长一半 = sqrt(R² - farEdge²)，于是滑块四角到圆心必然 ≤ R。
    // 480px/2.0 密度下弦长约 185dp。
    val approvalCapsuleWidth = remember(blockTopPx, windowSize, density) {
        val radiusPx = min(windowSize.width, windowSize.height) / 2f
        val centerY = windowSize.height / 2f
        val rowHeightPx = with(density) { Wear.Size.Touch.toPx() }
        if (blockTopPx <= 0f || radiusPx <= 0f) {
            Wear.Size.Orb
        } else {
            val farEdge = max(abs(blockTopPx - centerY), abs(blockTopPx + rowHeightPx - centerY))
            if (farEdge >= radiusPx) {
                Wear.Size.Orb
            } else {
                val halfChord = sqrt(radiusPx * radiusPx - farEdge * farEdge)
                with(density) { (2f * halfChord).toDp() }.coerceAtLeast(Wear.Size.Orb)
            }
        }
    }

    // 胶囊形变：待机 52dp 圆 -> 思考 88dp -> 审批态拉长到弦安全宽度
    val targetWidth = when (mode) {
        BottomBlockMode.ACTION_ALLOW -> approvalCapsuleWidth
        BottomBlockMode.THINKING_STOP -> 88.dp
        else -> Wear.Size.Orb
    }

    // 审批态按设计稿用 Wear.Motion.micro() 变形；其余态保留原有弹簧参数
    val morphSpring: AnimationSpec<Dp> = motionSpec(
        animationsEnabled,
        if (isApproval) {
            Wear.Motion.micro()
        } else {
            Wear.Motion.micro()
        }
    )

    val capsuleWidth by animateDpAsState(
        targetValue = targetWidth,
        animationSpec = morphSpring,
        label = "capsuleStretchWidth"
    )

    // 主键高度 52dp -> 36dp：变形成横向扁胶囊，比触控热区更扁
    val capsuleHeight by animateDpAsState(
        targetValue = if (isApproval) ApprovalCapsuleHeight else Wear.Size.Orb,
        animationSpec = motionSpec(animationsEnabled, Wear.Motion.micro()),
        label = "capsuleStretchHeight"
    )

    // 底栏高度：常驻 [IdleBlockHeight]；审批态取 [ApprovalBlockHeight]，让滑块落在圆心下方约 48dp 处
    val blockHeight by animateDpAsState(
        targetValue = if (isApproval) ApprovalBlockHeight else IdleBlockHeight,
        animationSpec = motionSpec(animationsEnabled, Wear.Motion.micro()),
        label = "bottomBlockHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(blockHeight)
            .onGloballyPositioned { coords -> blockTopPx = coords.positionInRoot().y }
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
                                        splitProgress.animateTo(0f, motionSpec(animationsEnabled, Wear.Motion.micro()))
                                    } else {
                                        isSplitExpanded = true
                                        splitProgress.animateTo(1f, motionSpec(animationsEnabled, Wear.Motion.micro()))
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
                                        splitProgress.animateTo(0f, motionSpec(animationsEnabled, Wear.Motion.micro()))
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
                                        splitProgress.animateTo(0f, motionSpec(animationsEnabled, Wear.Motion.micro()))
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
            // 收起进度 → 尺寸与圆角：56dp×8dp/4dp 的白色小条 <-> 52dp 圆形主键
            // 平方缓动：让主键在收起的**前半段**就迅速退到最小尺寸，
            // 避免中间态停在一块"宽圆角矩形"上（那会和随后淡入的弧条同时出现，看起来像被拆成两个东西）。
            val collapseProgressRaw = collapseProgress.coerceIn(0f, 1f)
            val collapseFraction = collapseProgressRaw * collapseProgressRaw
            // 收起态（< 0.5）时触摸归贴底白条：主键只剩 56×8dp 的视觉条，
            // 但它的手势节点原本常驻，会和白条热区同时命中 —— 于是"展开底栏"和"开始录音"一起发生。
            val isCollapsed = collapseFraction < 0.5f
            val keyWidth = lerpDp(CollapsedStripWidth, capsuleWidth, collapseFraction)
            val keyHeight = lerpDp(CollapsedStripHeight, Wear.Size.Orb, collapseFraction)
            val keyCorner = lerpDp(CollapsedStripCorner, Wear.Size.Orb / 2, collapseFraction)
            Box(
                modifier = Modifier
                    .scale(orbScaleAnim)
                    .size(keyWidth, keyHeight)
                    // 收起态（collapseFraction→0）由 ChatScreen 画贴底弧线，这里的实心块淡出，只保留热区
                    .shadow(elevation = 8.dp * collapseFraction, shape = RoundedCornerShape(keyCorner))
                    .clip(RoundedCornerShape(keyCorner))
                    // 静息用 Orb（暗于正文），只有「此刻在录」才提亮到正文白：亮 = 现在可以动作
                    .background(
                        (if (mode == BottomBlockMode.RECORDING) Wear.Palette.TextPrimary else Wear.Palette.Orb)
                            .copy(alpha = collapseFraction)
                    )
                    .pointerInput(isSplitExpanded, isCollapsed) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val startX = down.position.x
                            val startY = down.position.y
                            var isLongPressActive = false
                            var isDraggingX = false
                            var isDraggingY = false

                            // 语音起录只能由用户此刻的动作触发（这里按住 340ms，或收起态单触）。
                            // 亮屏 / 锁屏 / 摇腕 / 常驻监听等自动唤起已因续航整体删除，见 VoiceEntryPolicy：
                            // 常驻麦克风会在每次抬腕时唤醒音频链路，是手表端唯一不可接受的功耗形态。
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
                                                animationSpec = motionSpec(
                                                    animationsEnabled,
                                                    Wear.Motion.micro()
                                                )
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
                                                    animationSpec = motionSpec(
                                                        animationsEnabled,
                                                        Wear.Motion.micro()
                                                    )
                                                )
                                            }
                                        } else if (isCollapsed) {
                                            // 收起态的轻触交给贴底白条，主键不抢
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

                                // 上滑与长按在同一次触摸里竞争：慢速上滑时，长按 340ms 会先到期，
                                // 于是整个手势被判成"录音"，设置页打不开（实测连续 3 次失败）。
                                // 只要手指上移超过 8dp 就立刻取消长按计时，把这次触摸让给上滑判定。
                                if (!isLongPressActive && deltaY < -8.dp.toPx()) {
                                    longPressJob.cancel()
                                }
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
                // 图标随收起进度淡出并缩到 0.6：小条上不残留任何图形
                Box(
                    modifier = Modifier
                        .alpha(collapseFraction)
                        .scale(0.6f + 0.4f * collapseFraction)
                ) {
                    if (mainInputType == MainInputType.KEYBOARD && mode != BottomBlockMode.RECORDING) {
                        // 键盘主键：输入框轮廓按同一比例放大到 20×14dp（旧值 16×12dp）
                        MinimalistKeyboardIcon(
                            tint = Wear.Palette.Bg,
                            modifier = Modifier.size(20.dp, 14.dp)
                        )
                    } else if (mode == BottomBlockMode.RECORDING) {
                        // 录音中保留起伏波形：动态反馈比静态图标更能说明"正在收音"
                        MinimalistSoundwaveIcon(
                            tint = Wear.Palette.Danger,
                            isRecording = true,
                            modifier = Modifier.size(22.dp, 22.dp)
                        )
                    } else {
                        // 待机主键：22dp 麦克风（旧值是 16×11.5dp 的输入框轮廓）
                        MinimalistMicIcon(tint = Wear.Palette.Bg)
                    }
                }
            }

            // 注意：收起态的可点热区**不在这里**。
            // 收起后视觉已经是贴在底缘的弧条，热区必须跟着弧条走（由 ChatScreen 的贴底弧条承担）：
            // 之前热区留在按钮原位，用户点看得见的弧条没反应，等于把入口藏了起来。
        } else if (isApproval) {
            // ================= 审批拦截：整行只剩一条滑块，单击=拒绝 / 左滑=放行 =================
            ApprovalTrack(
                modifier = Modifier.align(Alignment.TopCenter),
                trackWidth = capsuleWidth,
                trackHeight = capsuleHeight,
                cooldownSeconds = cooldownSeconds,
                approvalTone = approvalTone,
                onAllow = onAllow,
                onReject = onReject,
                onApprovalProgress = onApprovalProgress
            )
        } else {
            // ================= 思考中：白色停止按钮 =================
            // 与主键同色（Orb 冷银白），文字用 Bg 深色保证对比；不再加边框，白底在深色点阵上已经足够分离。
            val stopShape = RoundedCornerShape(Wear.Radius.Control)
            Box(
                modifier = Modifier
                    .size(capsuleWidth, Wear.Size.Touch)
                    .clip(stopShape)
                    .background(Wear.Palette.Orb)
                    .clickable { onStopClick() },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
                ) {
                    Box(modifier = Modifier.size(8.dp).background(Wear.Palette.Danger, CircleShape))
                    BasicText(
                        text = stringResource(R.string.ui_bottom_stop),
                        style = Wear.TextCaption.copy(color = Wear.Palette.Bg),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * 审批滑块：整行唯一控件，白色主键底条拉长成占满弦长的扁胶囊（视觉 36dp，热区仍是 44dp），
 * 30dp 拇指自右端向左拖动。胶囊内"点击拒绝"与"放行"两个 label 叠在同一位置随进度交叉淡化。
 * 底座保持主键冷银白不变，只给"已滑过区域"上色：progress 0 是当前等级的强调色（[approvalTone]）、
 * 1 恒为放行绿，中间由 [ApprovalTrackTone.colorOf] 连续插值，于是越往左越绿、滑回去退回等级色。
 * 未滑过区域保留 0.34 的等级色调，已滑过区域取 0.72 的插值调；
 * 交互只有两种结局：位移都在 [ApprovalSliderLogic.TAP_SLOP_PX] 内是单击（拒绝），
 * 左滑松手达到 [ApprovalSliderLogic.ALLOW_THRESHOLD] 才放行，滑不够则弹簧回弹且什么都不发生。
 */
@Composable
private fun ApprovalTrack(
    modifier: Modifier = Modifier,
    trackWidth: Dp,
    trackHeight: Dp,
    cooldownSeconds: Int,
    approvalTone: Color,
    onAllow: () -> Unit,
    onReject: () -> Unit,
    onApprovalProgress: (Float) -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAnimationsEnabled.current
    val dragProgress = remember { Animatable(0f) }
    var trackPx by remember { mutableFloatStateOf(0f) }

    // 严格确认模式：倒计时结束前拇指锁定在右端，不可拖动
    var remainingCooldown by remember(cooldownSeconds) { mutableIntStateOf(cooldownSeconds) }
    val isLocked = remainingCooldown > 0
    LaunchedEffect(cooldownSeconds) {
        remainingCooldown = cooldownSeconds
        while (remainingCooldown > 0) {
            delay(1000)
            remainingCooldown--
        }
    }
    // 进入审批态（ApprovalTrack 重新组合）时进度必须归零：否则上一轮残留会让文案一进审批就显示"松开即放行"
    LaunchedEffect(Unit) {
        dragProgress.snapTo(0f)
    }
    LaunchedEffect(isLocked) {
        if (isLocked) dragProgress.snapTo(0f)
    }

    // 拖动进度逐帧回传（snapshotFlow 天然按帧合并），退出审批时归零，主界面可跟着变绿
    LaunchedEffect(dragProgress) {
        snapshotFlow { dragProgress.value }
            .distinctUntilChanged()
            .collect { onApprovalProgress(it) }
    }
    DisposableEffect(Unit) {
        onDispose { onApprovalProgress(0f) }
    }

    val insetPx = with(density) { ApprovalThumbInset.toPx() }
    val thumbSlotPx = with(density) { trackHeight.toPx() }
    val thumbDiameter = (trackHeight - ApprovalThumbInset * 2).coerceAtLeast(20.dp)
    val travelPx = (trackPx - thumbSlotPx).coerceAtLeast(1f)
    // 单击容差：设计值 22dp，按本机密度换算成 px 后交给纯逻辑判定
    val tapSlopPx = with(density) { ApprovalSliderLogic.TAP_SLOP_PX.dp.toPx() }

    // 44dp 触控热区包住 36dp 扁胶囊：热区不缩水，外观更扁；指针坐标与轨道同宽，进度换算语义不变
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(ApprovalTrackTouchHeight)
            .pointerInput(isLocked, trackPx, thumbSlotPx, tapSlopPx) {
                // 锁定倒计时内单击与滑动都无效
                if (isLocked) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // 松手定性：位移都落在 touchSlop 内 = 单击 → 拒绝；
                            // 否则是滑动，左滑完成度达标才放行，滑不够就回弹（回弹不算拒绝）
                            val totalDxPx = change.position.x - startX
                            val totalDyPx = change.position.y - startY
                            if (ApprovalSliderLogic.isRejectTap(totalDxPx, totalDyPx, tapSlopPx)) {
                                onReject()
                            } else {
                                val releasedProgress = ApprovalSliderLogic.progress(
                                    startX = startX,
                                    currentX = change.position.x,
                                    trackWidthPx = trackPx,
                                    thumbWidthPx = thumbSlotPx
                                )
                                if (ApprovalSliderLogic.shouldAllow(releasedProgress)) {
                                    scope.launch { dragProgress.snapTo(1f) }
                                    onAllow()
                                } else {
                                    scope.launch {
                                        dragProgress.animateTo(
                                            0f,
                                            motionSpec(
                                                animationsEnabled,
                                                Wear.Motion.micro()
                                            )
                                        )
                                    }
                                }
                            }
                            break
                        }
                        // 独占指针流：不消费则 Wear 系统的"右滑返回"会把横向拖动抢成返回手势
                        change.consume()
                        val nextProgress = ApprovalSliderLogic.progress(
                            startX = startX,
                            currentX = change.position.x,
                            trackWidthPx = trackPx,
                            thumbWidthPx = thumbSlotPx
                        )
                        scope.launch { dragProgress.snapTo(nextProgress) }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                // 审批态底条沿用主键冷银白（同一颗键的延续），红/绿由着色层叠加
                .background(Wear.Palette.Orb)
                .onGloballyPositioned { coords -> trackPx = coords.size.width.toFloat() },
            contentAlignment = Alignment.Center
        ) {
            // 着色层：未滑过区该等级的淡调 + 已滑过区 等级色 → 放行绿 的连续插值
            Canvas(modifier = Modifier.fillMaxSize()) {
                val p = dragProgress.value
                val thumbLeftPx = (size.width - thumbSlotPx + insetPx - p * travelPx)
                    .coerceIn(0f, size.width)
                drawRect(
                    color = approvalTone.copy(alpha = ApprovalTrackUnswipedAlpha),
                    size = Size(thumbLeftPx, size.height)
                )
                drawRect(
                    color = ApprovalTrackTone.colorOf(approvalTone, p)
                        .copy(alpha = ApprovalTrackSwipedAlpha),
                    topLeft = Offset(thumbLeftPx, 0f),
                    size = Size(size.width - thumbLeftPx, size.height)
                )
            }

            // 文案：锁定倒计时优先；解锁后两个 label 叠在同一位置，随进度交叉淡化
            if (isLocked) {
                BasicText(
                    text = stringResource(ApprovalTextRes.sliderLabelResOf(dragProgress.value, isLocked = true)),
                    style = Wear.TextCaption.copy(color = Wear.Palette.Bg),
                    maxLines = 1
                )
            } else {
                Box(contentAlignment = Alignment.Center) {
                    // 只放一个文案，在 50% 处换词：两个 label 叠放交叉淡化会在中途糊成"点放行绝"。
                    // 颜色渐变由图层的红→绿着色表达，文字保持深色以保证对比度。
                    BasicText(
                        text = stringResource(ApprovalTextRes.actionLabelResOf(dragProgress.value)),
                        style = Wear.TextCaption.copy(color = Wear.Palette.Bg),
                        maxLines = 1
                    )
                }
            }

            // 拇指：白色圆，1.5dp 白描边 + 3dp 阴影把它从着色的滑轨上托起来；锁定时停在右端并显示剩余秒数
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset { IntOffset(-(dragProgress.value * travelPx).roundToInt(), 0) }
                    .padding(end = ApprovalThumbInset)
                    .size(thumbDiameter)
                    .shadow(elevation = 3.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(Wear.Palette.Orb)
                    .border(1.5.dp, Wear.Palette.TextPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (isLocked) {
                    BasicText(
                        text = "${remainingCooldown}",
                        style = Wear.TextCaption.copy(color = Wear.Palette.Bg),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** 滑轨未滑过区的着色不透明度：等级色的淡调，足够看出等级又不抢拇指。 */
private const val ApprovalTrackUnswipedAlpha = 0.34f

/** 滑轨已滑过区的着色不透明度：拖动痕迹要压过未滑过区。 */
private const val ApprovalTrackSwipedAlpha = 0.72f

/**
 * 审批滑轨的着色逻辑：已滑过区从当前等级色连续插值到放行绿，progress 到底恒为绿。
 * 纯函数，无 Compose 运行时依赖，JVM 单测可直接覆盖。
 */
internal object ApprovalTrackTone {
    fun colorOf(tone: Color, progress: Float): Color =
        lerp(tone, Wear.Palette.Success, progress.coerceIn(0f, 1f))
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
 * 极简麦克风图标：胶囊收音头 + 半圆托架 + 短杆，2.5dp 圆头描边，22dp 图形盒。
 *
 * 取代原来的"输入框轮廓"（16×11.5dp，只占 52dp 主键圆的 31%，语义也不是"说话"）。
 * 22 / 52 = 42%，主键圆里第一次有了一个读得出动作的语义图形。
 */
@Composable
fun MinimalistMicIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(ChatVisualMetrics.OrbGlyphDp.dp)) {
        val stroke = 2.5.dp.toPx()
        val centerX = size.width / 2f
        val headWidth = size.width * 0.38f
        val headHeight = size.height * 0.50f
        val headTop = 0f
        drawRoundRect(
            color = tint,
            topLeft = Offset(centerX - headWidth / 2f, headTop),
            size = Size(headWidth, headHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(headWidth / 2f, headWidth / 2f),
            style = Stroke(width = stroke)
        )
        val cradleRadius = size.width * 0.31f
        val cradleCenterY = headTop + headHeight * 0.68f
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - cradleRadius, cradleCenterY - cradleRadius),
            size = Size(cradleRadius * 2f, cradleRadius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = Offset(centerX, cradleCenterY + cradleRadius),
            end = Offset(centerX, size.height),
            strokeWidth = stroke,
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
    val animationsEnabled = LocalAnimationsEnabled.current
    // 只有真在录音且动效开启时才挂无限动画，并把相位以 State 形式交给绘制阶段读取：
    // 空闲时既不创建动画，也不因为读相位而重组
    val wave1: State<Float>
    val wave2: State<Float>
    val wave3: State<Float>
    if (isRecording && animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "waveAnim")
        wave1 = infiniteTransition.animateFloat(
            initialValue = 0.3f, targetValue = 1.0f,
            animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
            label = "w1"
        )
        wave2 = infiniteTransition.animateFloat(
            initialValue = 0.9f, targetValue = 0.4f,
            animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
            label = "w2"
        )
        wave3 = infiniteTransition.animateFloat(
            initialValue = 0.4f, targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse),
            label = "w3"
        )
    } else {
        // 关闭动效 / 未录音：三条波形钉在中位，形态与动画开启时一致，不会"卡在某一帧"
        val flat = remember { mutableStateOf(0.65f) }
        wave1 = flat
        wave2 = flat
        wave3 = flat
    }

    Canvas(modifier = modifier.size(13.dp, 13.dp)) {
        val barWidth = 1.8.dp.toPx()
        val spacing = size.width / 4f
        val centerY = size.height / 2f

        val h1 = if (isRecording) size.height * (0.35f + 0.65f * wave1.value) else size.height * 0.45f
        val h2 = if (isRecording) size.height * (0.45f + 0.55f * wave2.value) else size.height * 0.90f
        val h3 = if (isRecording) size.height * (0.35f + 0.65f * wave3.value) else size.height * 0.45f

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

/**
 * 审批文案的资源映射：等级标题、滑块提示与操作词都以资源 id 返回，
 * 让 [ApprovalTone]/[ApprovalSliderLogic] 里原本写死的中文随系统语言切换。
 */
internal object ApprovalTextRes {
    /** 风险等级 -> 审批页标题：中风险只提示确认，高与致命共用高风险标题。 */
    @StringRes
    fun headlineResOf(level: RiskLevel): Int = when (level) {
        RiskLevel.LOW -> R.string.ui_approval_headline_low
        RiskLevel.MEDIUM -> R.string.ui_approval_headline_confirm
        RiskLevel.HIGH, RiskLevel.CRITICAL -> R.string.ui_approval_headline_high
    }

    /** 胶囊内左侧提示：锁定倒计时优先，其次在"点拒绝 · 左滑放行 / 松开即放行"之间切换。 */
    @StringRes
    fun sliderLabelResOf(progress: Float, isLocked: Boolean): Int = when {
        isLocked -> R.string.ui_approval_locked
        progress < ApprovalSliderLogic.LABEL_SWITCH_PROGRESS -> R.string.ui_approval_slider_hint
        else -> R.string.ui_approval_release_to_allow
    }

    /** 正中操作词：过半前是"点击拒绝"，过半后是"放行"。 */
    @StringRes
    fun actionLabelResOf(progress: Float): Int =
        if (progress < ApprovalSliderLogic.LABEL_SWITCH_PROGRESS) R.string.ui_approval_reject_tap
        else R.string.ui_approval_allow
}
