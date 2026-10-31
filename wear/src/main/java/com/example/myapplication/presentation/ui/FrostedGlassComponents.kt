package com.example.myapplication.presentation.ui

import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * 深度思考微型小胶囊 (带弹性展开动画)
 */
@Composable
fun ThoughtPill(
    thought: ConversationBlock.Thought,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(thought.isExpanded) }
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = 0.58f,
            stiffness = 380f
        ),
        label = "thoughtArrowSpring"
    )
    val shape = RoundedCornerShape(Wear.Radius.Chip)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Wear.Space.Xs)
    ) {
        // 思考胶囊条目：实底 + 描边，整条可点，点击区不小于 44dp
        Row(
            modifier = Modifier
                .clip(shape)
                .background(Wear.Palette.SurfaceAlt)
                .border(1.dp, Wear.Palette.Border, shape)
                .heightIn(min = Wear.Size.Touch)
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = Wear.Space.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            BasicText(
                text = "思考过程",
                style = Wear.TextCaption,
                maxLines = 1
            )
            BasicText(
                text = if (isExpanded) "收起" else "展开",
                style = Wear.TextCaption.copy(color = Wear.Palette.TextMuted),
                maxLines = 1
            )
            BasicText(
                text = "›",
                modifier = Modifier.rotate(arrowRotation),
                style = Wear.TextCaption.copy(color = Wear.Palette.Accent),
                maxLines = 1
            )
        }

        // 展开后的完整思考链 (弹性收展)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(spring(dampingRatio = 0.65f, stiffness = 350f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Wear.Space.Xs)
                    .clip(shape)
                    .background(Wear.Palette.SurfaceAlt)
                    .border(1.dp, Wear.Palette.Border, shape)
                    .padding(Wear.Space.Md)
            ) {
                BasicText(
                    text = thought.reasoning,
                    style = Wear.TextMono
                )
            }
        }
    }
}

/** 工具调用无独立错误字段，按结果摘要判定失败态，用于状态取色。 */
private val toolFailureKeywords = listOf("失败", "错误", "异常", "error", "failed", "exception")

/**
 * 小胶囊里只放一条最关键的信息：摘要以 "·" 分段时只取第一段，其余细节留给展开态。
 * 首段为空（摘要以 "·" 开头）时回退到整条摘要，避免胶囊里只剩一个点。
 */
private fun primarySummary(summary: String?): String? {
    val first = summary?.substringBefore('·')?.trim()
    return first?.takeIf { it.isNotEmpty() } ?: summary?.trim()?.takeIf { it.isNotEmpty() }
}

private fun toolStatusColor(toolCall: ConversationBlock.ToolCall): Color {
    val summary = toolCall.resultSummary
    return when {
        toolCall.isExecuting -> Wear.Palette.Accent
        summary == null -> Wear.Palette.Accent
        toolFailureKeywords.any { summary.contains(it, ignoreCase = true) } -> Wear.Palette.Danger
        else -> Wear.Palette.Success
    }
}

/**
 * 弹性工具调用胶囊 (令牌配色 · 明确操作与生成状态 · 弹性展开与旋转)
 */
@Composable
fun SpringColoredToolPill(
    toolCall: ConversationBlock.ToolCall,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(toolCall.isExpanded) }
    val isExecuting = toolCall.isExecuting
    val meta = ToolVisualMeta.resolve(toolCall.toolName)
    val statusColor = toolStatusColor(toolCall)
    // 工具专属染色：底色 14%、描边 60% 都由该工具的 tagColor 推导，和正文卡片的 Surface/Border 明确区分
    val tagColor = Color(meta.tagColorHex)
    val pillBackground = tagColor.copy(alpha = 0.14f)
    val pillBorder = tagColor.copy(alpha = 0.6f)
    val shape = RoundedCornerShape(Wear.Radius.Chip)

    // 弹性展开箭头旋转
    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 400f
        ),
        label = "toolArrowSpring"
    )

    // 执行中：圆点呼吸 (替代原来的旋转齿轮字形，保持无 emoji)
    val infiniteTransition = rememberInfiniteTransition(label = "executingPulse")
    val dotScale by if (isExecuting) {
        infiniteTransition.animateFloat(
            initialValue = 0.55f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(620, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotPulse"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 44dp 触控热区包住 26dp 视觉胶囊：一眼是"一粒小标签"，不是"一张卡片"
        Box(
            modifier = Modifier
                .heightIn(min = Wear.Size.Touch)
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .height(Wear.Size.BadgeHeight + 6.dp)
                    .clip(shape)
                    .background(pillBackground)
                    .border(1.dp, pillBorder, shape)
                    .padding(horizontal = Wear.Space.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
            ) {
                // 图标位：6dp 纯色几何圆点 (tagColor)，执行中呼吸
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(color = tagColor, radius = size.minDimension / 2f * dotScale)
                }

                // 工具中文名
                BasicText(
                    text = meta.displayName,
                    style = Wear.TextCaption.copy(color = Wear.Palette.TextPrimary),
                    maxLines = 1
                )

                // 状态说明 (running/success/failed 只点缀在文字颜色上)
                // 摘要只留第一段，宽度不足时缩略摘要而不是工具名，完整信息在展开态
                val summaryLabel = if (isExecuting) {
                    "生成中..."
                } else {
                    primarySummary(toolCall.resultSummary) ?: "已执行"
                }
                BasicText(
                    text = "· $summaryLabel",
                    modifier = Modifier.weight(1f),
                    style = Wear.TextCaption.copy(color = statusColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 弹性箭头指示
                BasicText(
                    text = "›",
                    modifier = Modifier.rotate(arrowRotation),
                    style = Wear.TextCaption.copy(color = tagColor),
                    maxLines = 1
                )
            }
        }

        // 弹性展开详情卡片
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(spring(dampingRatio = 0.65f, stiffness = 350f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Wear.Space.Xs)
                    .clip(shape)
                    .background(Wear.Palette.SurfaceAlt)
                    .border(1.dp, tagColor.copy(alpha = 0.3f), shape)
                    .padding(Wear.Space.Md)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)) {
                    BasicText(
                        text = "参数: ${toolCall.argsJson}",
                        style = Wear.TextMono,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (toolCall.resultSummary != null) {
                        BasicText(
                            text = "结果: ${toolCall.resultSummary}",
                            style = Wear.TextMono.copy(color = statusColor),
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * 逐字流式文本渲染 (呼吸跳动光标)
 *
 * [animateCursor] = false 用于"模型一次吐出十几字以上"的突发刷新：光标留在原位不动、不呼吸，
 * 只把文本直接换掉。突发时整段文字本来就是一跳一跳地长，再叠一层光标动画只会让手表更花。
 */
@Composable
fun SpringStreamingText(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    animateCursor: Boolean = true
) {
    // 不需要呼吸时不创建无限动画：静止光标不占帧回调
    val cursorAlpha by if (isStreaming && animateCursor) {
        val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(450, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "cursorAlpha"
        )
    } else {
        remember { mutableStateOf(1f) }
    }

    BasicText(
        modifier = modifier,
        text = buildAnnotatedString {
            // 渲染前做最小 Markdown 清洗：成对强调标记与反引号不外露，流式未闭合标记不闪烁
            append(stripEmphasisMarkers(text, hideUnclosedTail = isStreaming))
            if (isStreaming) {
                withStyle(SpanStyle(color = Wear.Palette.Accent.copy(alpha = cursorAlpha))) {
                    append(" ▌")
                }
            }
        },
        style = Wear.TextBody
    )
}

/**
 * 用户输入气泡 (右对齐 · AccentDim 实底 · 最宽 200dp)
 */
@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .widthIn(max = 200.dp)
            .clip(RoundedCornerShape(Wear.Radius.Card))
            .background(Wear.Palette.AccentDim)
            .padding(Wear.Space.Md)
    ) {
        content()
    }
}

/**
 * Agent 回复卡片：Surface 实底 + 1dp 描边 + Card 圆角，无 RenderEffect 模糊层。
 */
@Composable
fun AgentSquareBlurBox(
    modifier: Modifier = Modifier,
    borderColor: Color = Wear.Palette.Border,
    approvalVisualState: ApprovalVisualState = ApprovalVisualState.NONE,
    isTaskCompletedTrigger: Boolean = false,
    thinkingIntensity: Float = 0f,
    isRecordingVoice: Boolean = false,
    shockwaveProgress: Float = -1f,
    gridSpacingDp: Float = 16f,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(Wear.Radius.Card)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Wear.Palette.Surface)
            .border(1.dp, borderColor, shape)
            .padding(Wear.Space.Md)
    ) {
        content()
    }
}

// ===================== 列表越界手感（主界面底部 / 设置页顶部共用同一套判定） =====================

/**
 * 越界手感常量：阈值、拉开量、回弹力度、震动强度都集中在这里，调手感只改这里。
 */
internal object ListOverscrollSpec {
    /** 累积越界位移达到该距离才切页（主界面底部 → 设置页；设置页顶部 → 主界面）。 */
    val SwitchThreshold = 48.dp

    /** 越界期间列表最多拉开这么多，继续滑也不会真的滚出边界。 */
    val MaxPull = 24.dp

    /** 拉开量 = 越界量 × 该比例（24dp 越界 × 0.4 = 9.6dp）。 */
    const val PullRatio = 0.4f

    /** 表冠停转多久算"松手"：到时回弹归零并重置累计。 */
    const val RotarySettleMs = 220L

    /** 松手回弹的 spring：阻尼偏小，收尾带一点弹性。 */
    const val ReleaseDamping = 0.62f
    const val ReleaseStiffness = 460f

    /** 首次触达边界的提示震动 / 越过阈值的确认震动。 */
    const val TickEffect = VibrationEffect.EFFECT_TICK
    const val SwitchEffect = VibrationEffect.EFFECT_CLICK
}

/**
 * 列表越界状态机：到边界后继续滑 = 累计剩余位移 → 首次触达打一记 [ListOverscrollSpec.TickEffect]、
 * 累计过 [ListOverscrollSpec.SwitchThreshold] 打更强的 [ListOverscrollSpec.SwitchEffect] 并回调切页；
 * 越界期间给列表一层不超过 [ListOverscrollSpec.MaxPull] 的弹性位移，松手 spring 回弹。
 *
 * 触摸拖拽与表冠共用 [push]；触摸的"松手"由 [connection] 的 onPostFling 结算，
 * 表冠没有松手事件，靠停转 [ListOverscrollSpec.RotarySettleMs] 判定。
 */
@Stable
internal class EdgeOverscroll(
    private val scope: CoroutineScope,
    private val density: Density,
    private val overflowSign: Float,
    private val atEdge: () -> Boolean,
    private val onCross: () -> Unit,
    private val vibrate: (Int) -> Unit
) {
    private val pull = Animatable(0f)

    /** 弹性位移（px，已夹到 [ListOverscrollSpec.MaxPull] 并乘过 [ListOverscrollSpec.PullRatio]），直接读进 graphicsLayer。 */
    val pullOffsetPx: Float get() = pull.value

    private var accumulatedPx = 0f
    private var ticked = false
    private var crossed = false
    private var settleJob: Job? = null

    /** 越界入口：[overflowPx] 是列表没吃下的剩余位移（符号由内部取绝对值）。 */
    fun push(overflowPx: Float, rotary: Boolean = false) {
        if (crossed) return
        accumulatedPx += abs(overflowPx)
        if (!ticked) {
            ticked = true
            vibrate(ListOverscrollSpec.TickEffect)
        }
        val maxPullPx = with(density) { ListOverscrollSpec.MaxPull.toPx() }
        val target = min(accumulatedPx, maxPullPx) * ListOverscrollSpec.PullRatio * overflowSign
        scope.launch { pull.snapTo(target) }
        if (rotary) {
            settleJob?.cancel()
            settleJob = scope.launch {
                delay(ListOverscrollSpec.RotarySettleMs)
                release()
            }
        }
        if (accumulatedPx >= with(density) { ListOverscrollSpec.SwitchThreshold.toPx() }) {
            crossed = true
            vibrate(ListOverscrollSpec.SwitchEffect)
            onCross()
        }
    }

    /** 松手：回弹到原位并重置累计，下一次越界重新计。 */
    fun release() {
        settleJob?.cancel()
        settleJob = null
        accumulatedPx = 0f
        ticked = false
        crossed = false
        scope.launch {
            pull.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    ListOverscrollSpec.ReleaseDamping,
                    ListOverscrollSpec.ReleaseStiffness
                )
            )
        }
    }

    /** 列表没吃下的位移就是越界：吃掉它（返回 consumed），列表本身不滚出边界。 */
    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            // 只认手指真拖出来的越界：惯性甩到底后剩下的位移不该触发震动与切页
            if (source != NestedScrollSource.UserInput) return Offset.Zero
            val leftover = available.y
            if (leftover != 0f && leftover * overflowSign > 0f && atEdge()) {
                push(leftover)
                return Offset(0f, leftover)
            }
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            release()
            return Velocity.Zero
        }
    }
}

/**
 * 给列表接上越界手感（[EdgeOverscroll.connection] 交给 Modifier.nestedScroll，
 * [EdgeOverscroll.push] 交给表冠处理器）。
 *
 * [overflowSign]：越界方向剩余位移的符号 —— 主界面"到底继续上滑"是负值（-1f），
 * 设置页"到顶继续下滑"是正值（+1f）。
 */
@Composable
internal fun rememberEdgeOverscroll(
    overflowSign: Float,
    atEdge: () -> Boolean,
    onCross: () -> Unit
): EdgeOverscroll {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current
    val currentAtEdge by rememberUpdatedState(atEdge)
    val currentOnCross by rememberUpdatedState(onCross)
    return remember(overflowSign, density) {
        EdgeOverscroll(
            scope = scope,
            density = density,
            overflowSign = overflowSign,
            atEdge = { currentAtEdge() },
            onCross = { currentOnCross() },
            vibrate = { effect ->
                context.getSystemService(Vibrator::class.java)
                    ?.vibrate(VibrationEffect.createPredefined(effect))
            }
        )
    }
}

