package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ui.theme.Wear

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

    // 旋转齿轮动画 (仅在执行中生效)
    val infiniteTransition = rememberInfiniteTransition(label = "executingPulse")
    val spinnerAngle by if (isExecuting) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "spin"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Wear.Space.Xs)
    ) {
        // 工具主胶囊：SurfaceAlt 实底 + Border 描边，状态色只取令牌
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
            // 工具专属图标或执行齿轮
            BasicText(
                text = if (isExecuting) "⚙" else meta.iconSymbol,
                modifier = if (isExecuting) Modifier.rotate(spinnerAngle) else Modifier,
                style = Wear.TextCaption.copy(color = statusColor),
                maxLines = 1
            )

            // 工具中文名
            BasicText(
                text = meta.displayName,
                style = Wear.TextCaption.copy(color = statusColor),
                maxLines = 1
            )

            // 状态说明
            val summaryLabel = if (isExecuting) {
                "生成工具调用中..."
            } else {
                toolCall.resultSummary ?: "已执行"
            }
            BasicText(
                text = "· $summaryLabel",
                modifier = Modifier.weight(1f, fill = false),
                style = Wear.TextCaption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // 弹性箭头指示
            BasicText(
                text = "›",
                modifier = Modifier.rotate(arrowRotation),
                style = Wear.TextCaption.copy(color = statusColor),
                maxLines = 1
            )
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
                    .border(1.dp, Wear.Palette.Border, shape)
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
 */
@Composable
fun SpringStreamingText(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursorBlink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    BasicText(
        modifier = modifier,
        text = buildAnnotatedString {
            append(text)
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
