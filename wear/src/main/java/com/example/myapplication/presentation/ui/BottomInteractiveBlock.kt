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
import androidx.compose.ui.graphics.Brush
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
import com.example.myapplication.presentation.ui.theme.accentRailOf
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
 * 「记住并放行」条带高度：正好吃掉审批块里滑轨热区之下那段空档（64 − 44 = 20dp）。
 *
 * 不新增高度、不向上借位：审批文案底边与滑轨热区都不动，184dp 宽的圆屏上也不需要第二列按钮。
 */
internal val ApprovalRememberBandHeight = ApprovalBlockHeight - ApprovalTrackTouchHeight

/**
 * 单体形态演变底栏控制器 (左右滑动弹性弹射两翼 · 点击弹性收回 · 绝无 Emoji)
 */
@Composable
fun BottomInteractiveBlock(
    mode: BottomBlockMode,
    modifier: Modifier = Modifier,
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
    // 记住并放行：只有风险声明"可记住"（非破坏性 + 有命令原文）时才出现，见 ApprovalRememberPolicy
    rememberable: Boolean = false,
    onRememberAndAllow: () -> Unit = {},
    collapsed: Boolean = false,              // 用户已经滚动过：主键收起成小白条，点它才恢复
    // 收展进度 1=完整按钮 0=收起。由 ChatScreen 统一持有，保证"按钮缩小"与"弧条成形"是同一条时间线，
    // 不会出现"下面突然冒出、上面自己收、两边各弹一下"的割裂感。
    collapseProgress: Float = 1f,
    onCollapsedStripTap: () -> Unit = {},
    // 主键按下/抬起：只上报布尔状态的变化（不逐帧回调），用于让点阵跟手被顶开
    onOrbPressChange: (Boolean) -> Unit = {}
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

    // 主键按压缩放：录音时微放大做呼吸，按下时收 0.94 给出"按进去了"的即时反馈
    var isOrbPressed by remember { mutableStateOf(false) }

    // 按下/抬起只是一个布尔量的两次翻转，用 key 变化触发一次性外发：
    // 不订阅每一帧的手势事件，也就不存在"每帧回调"这种浪费
    LaunchedEffect(isOrbPressed) { onOrbPressChange(isOrbPressed) }

    // 发送闪亮：抬手把这一句发出去的那一帧点满，走 quick() 140ms 衰减回静息（见 OrbEdgeHighlight）
    val sendFlash = remember { Animatable(0f) }

    val orbScaleAnim by animateFloatAsState(
        targetValue = when {
            isOrbPressed -> OrbEdgeHighlight.PressedScale
            mode == BottomBlockMode.RECORDING -> 1.08f
            else -> 1.0f
        },
        // 弹性手感：阻尼 0.62 保留一次轻微过冲，抬手时是"弹回来"而不是生硬复位；
        // 旧值 1.25 倍在 52dp 键上等于放大 13dp，视觉上像整颗键"炸开"，改成 1.08 只做呼吸式起伏。
        animationSpec = motionSpec(animationsEnabled, spring(dampingRatio = 0.62f, stiffness = 420f)),
        label = "orbRecordScale"
    )

    // 发送时刻闪亮一次：抬手把这一句发出去的那一帧开始衰减，不给静息留残光
    val flashOnSend: () -> Unit = {
        if (animationsEnabled) {
            scope.launch {
                sendFlash.snapTo(1f)
                sendFlash.animateTo(0f, Wear.Motion.quick())
            }
        }
    }

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

                // 左副键：打开文字输入对话框。输入方式的二选一状态已删除 ——
                // 左翼本身就是"改用键盘"那条路，不必先切模式再回主键上点第二下。
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
                                    onLeftKeyboardClick()
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        MinimalistKeyboardIcon(tint = Wear.Palette.TextSecondary)
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
                        MinimalistLayersIcon(tint = Wear.Accent)
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
                            isOrbPressed = true
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
                                    isOrbPressed = false
                                    longPressJob.cancel()
                                    if (isLongPressActive) {
                                        // 释放长按：松手回弹 + 发射水波纹 + 开始思考
                                        flashOnSend()
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
                                            flashOnSend()
                                            onVoiceStopAndTrigger()
                                        } else {
                                            // 单触固定走语音起录 (设备若无录音引擎则弹出快捷输入)：
                                            // 文字输入由左翼直接打开，主键不再随某个持久化模式换行为
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
                    if (mode == BottomBlockMode.RECORDING) {
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

                // 边缘高光：1dp 细光贴着圆周走，只在右侧那一段亮、其余收成透明 ——
                // 是"薄薄一层反光"，不是描边也不是一圈粗边框。强度唯一来源是 [OrbEdgeHighlight]：
                // 静息 0.22 / 录音随呼吸脉动 / 按下 1.0 / 发送后 140ms 内衰减回静息。
                if (collapseFraction > 0.01f) {
                    // 呼吸只在录音时挂无限时间线：静息态不为"可能发生的动效"常开一条动画
                    val pulseState: State<Float>? = if (mode == BottomBlockMode.RECORDING) {
                        rememberInfiniteTransition(label = "orbEdgePulse").animateFloat(
                            initialValue = 0.72f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(durationMillis = 900),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "orbEdgePulseValue"
                        )
                    } else {
                        null
                    }
                    // 高光色要在组合里取一次：运行时投影是 @Composable 的，Canvas 的绘制 lambda 读不到。
                    val edgeTone = OrbEdgeHighlight.toneOf(mode, isOrbPressed, Wear.Accent)
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val level = OrbEdgeHighlight.intensityOf(
                            mode = mode,
                            isPressed = isOrbPressed,
                            sendFlash = sendFlash.value,
                            pulse = pulseState?.value ?: 1f
                        ) * collapseFraction
                        if (level <= 0.01f) return@Canvas
                        val strokePx = OrbEdgeHighlight.StrokeWidthDp.dp.toPx()
                        drawArc(
                            brush = Brush.sweepGradient(
                                0f to Color.Transparent,
                                0.10f to edgeTone.copy(alpha = level),
                                0.34f to Color.Transparent,
                                1f to Color.Transparent,
                                center = center
                            ),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(strokePx / 2f, strokePx / 2f),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            // 注意：收起态的可点热区**不在这里**。
            // 收起后视觉已经是贴在底缘的弧条，热区必须跟着弧条走（由 ChatScreen 的贴底弧条承担）：
            // 之前热区留在按钮原位，用户点看得见的弧条没反应，等于把入口藏了起来。
        } else if (isApproval) {
            // ================= 审批拦截：主机一行滑块，单击=拒绝 / 左滑=放行 =================
            // 记住入口排在滑轨正下方那 20dp 空档：不抬高底栏、不压缩滑轨，也不碰上方审批文案。
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(capsuleWidth),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ApprovalTrack(
                    modifier = Modifier.fillMaxWidth(),
                    trackWidth = capsuleWidth,
                    trackHeight = capsuleHeight,
                    cooldownSeconds = cooldownSeconds,
                    approvalTone = approvalTone,
                    onAllow = onAllow,
                    onReject = onReject,
                    onApprovalProgress = onApprovalProgress
                )
                if (rememberable) {
                    RememberAndAllowBand(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(ApprovalRememberBandHeight),
                        onRememberAndAllow = onRememberAndAllow
                    )
                }
            }
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

/** 长按完成时间：500ms。"记住"是一次永久的放行决定，单击不够，按住不放才生效。 */
private const val RememberHoldMs = 500

/** 「记住并放行」标签：与该命令一起进 allowlist，下次同一条命令不再弹这一页。 */
private const val RememberAndAllowLabel = "记住并放行"

/**
 * 「记住并放行」条带：滑轨之下 20dp 空档里的一条细字，长按 [RememberHoldMs] 才生效。
 *
 * 单击不生效的理由是安全而非手感：记忆一旦落盘就没有第二次确认，误触等于永久放行一条特权命令；
 * 按住期间底部那条 1dp 细线从中间长到满宽，进度可见，松手早于完成就撤回、什么也不发生。
 */
@Composable
private fun RememberAndAllowBand(
    modifier: Modifier = Modifier,
    onRememberAndAllow: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val animationsEnabled = LocalAnimationsEnabled.current
    val hold = remember { Animatable(0f) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val holdJob = scope.launch {
                    hold.snapTo(0f)
                    hold.animateTo(1f, tween(durationMillis = RememberHoldMs, easing = LinearEasing))
                    onRememberAndAllow()
                }
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                }
                // 抬手早于完成：撤回到 0，这一次触摸什么也不发生。
                // awaitEachGesture 的 lambda 是 AwaitPointerEventScope（受限挂起作用域），
                // 不能就地 await 动画，必须丢回组合作用域里跑。
                if (holdJob.isActive) {
                    holdJob.cancel()
                    scope.launch {
                        hold.animateTo(0f, motionSpec(animationsEnabled, Wear.Motion.quick()))
                    }
                }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        val accent = Wear.Accent
        // 进度细线：1dp、只占条带底缘，从中间向两侧长满 —— 与主键高光同一种"细而透"的语言
        Canvas(modifier = Modifier.matchParentSize()) {
            val progress = hold.value
            if (progress <= 0.01f) return@Canvas
            val linePx = 1.dp.toPx()
            val lineWidth = size.width * progress
            drawRect(
                color = accent.copy(alpha = 0.85f),
                topLeft = Offset((size.width - lineWidth) / 2f, size.height - linePx),
                size = Size(lineWidth, linePx)
            )
        }
        BasicText(
            text = RememberAndAllowLabel,
            style = Wear.TextCaption.copy(
                color = lerp(Wear.Palette.TextSecondary, accent, hold.value)
            ),
            maxLines = 1
        )
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
 * 主键边缘高光的唯一强度/色调来源（纯函数，无 Compose 运行时依赖，JVM 单测直接覆盖）。
 *
 * 主键底色永远是 [Wear.Palette.Orb]（冷银白 #CDD3DC）—— 高光只叠在**边缘那 1dp** 上，
 * 绝不拿底色去换明暗：用户明确要求过"按钮要亮的，不要灰色"。
 * 四个时刻各一档：静息最淡、录音随呼吸脉动、按下最亮、发送后点满再衰减回静息。
 */
internal object OrbEdgeHighlight {
    /** 细光宽度：1dp。再粗就是一圈边框，那不是高光。 */
    const val StrokeWidthDp = 1f

    /** 按下时主键缩放：收 0.94，给出"按进去了"的即时反馈。 */
    const val PressedScale = 0.94f

    /** 静息档：只是让边缘有一层反光，不参与注意力竞争。 */
    const val IdleLevel = 0.22f

    /** 录音档：一眼能看出"正在听"，但不抢中间的波形图标。 */
    const val RecordingLevel = 0.62f

    /** 发送档：抬手发出去那一刻点到这里，随后衰减回静息。 */
    const val SendLevel = 0.86f

    /** 按下档：细光最亮的一档。 */
    const val PressedLevel = 1f

    /** 录音呼吸的下限：再低就会和静息档分不出来。 */
    const val PulseFloor = 0.6f

    fun intensityOf(
        mode: BottomBlockMode,
        isPressed: Boolean,
        sendFlash: Float = 0f,
        pulse: Float = 1f
    ): Float {
        if (isPressed) return PressedLevel
        val base = when (mode) {
            BottomBlockMode.RECORDING -> RecordingLevel * pulse.coerceIn(PulseFloor, 1f)
            else -> IdleLevel
        }
        val flash = sendFlash.coerceIn(0f, 1f)
        return (base + (SendLevel - base) * flash).coerceIn(0f, 1f)
    }

    /**
     * 高光只允许这三种色：静息取强调色的轨道位，录音取强调色本身，按下取 TextPrimary。
     *
     * 强调色由调用方传入而不是在这里读 Wear.Accent：本对象是零 Compose 依赖的纯函数（JVM 单测
     * 直接覆盖），而运行时投影是 @Composable 的。静息档复用页面同一份轨道位算法（[accentRailOf]），
     * 不另记一个系数 —— 换色时边缘高光跟着变，而不是停在默认蓝上。
     */
    fun toneOf(mode: BottomBlockMode, isPressed: Boolean, accent: Color): Color = when {
        isPressed -> Wear.Palette.TextPrimary
        mode == BottomBlockMode.RECORDING -> accent
        else -> accentRailOf(accent)
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
