package com.example.myapplication.presentation.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.contract.ConversationBlock

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        // 微型小胶囊条目 (带弹性点击反馈)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF161F2E).copy(alpha = 0.90f))
                .border(0.6.dp, Color(0xFF38BDF8).copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 8.dp, vertical = 3.5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                )
                Text(
                    text = "思考过程",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF93C5FD),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isExpanded) "收起" else "展开",
                    fontSize = 8.5.sp,
                    color = Color(0xFF64748B),
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "›",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF93C5FD),
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
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
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.95f))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                    .padding(7.dp)
            ) {
                Text(
                    text = thought.reasoning,
                    fontSize = 9.5.sp,
                    lineHeight = 13.5.sp,
                    color = Color(0xFFCBD5E1),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * 弹性彩色工具调用胶囊 (专有颜色 · 明确操作与生成状态 · 弹性展开与旋转)
 */
@Composable
fun SpringColoredToolPill(
    toolCall: ConversationBlock.ToolCall,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(toolCall.isExpanded) }
    val isExecuting = toolCall.isExecuting
    val meta = ToolVisualMeta.resolve(toolCall.toolName)

    val primaryColor = Color(meta.tagColorHex)
    val lightBgColor = Color(meta.lightBgColorHex)
    val borderColor = Color(meta.borderColorHex)

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
            .padding(vertical = 3.dp)
    ) {
        // 主极简彩色胶囊条 (简单干净 · 单色纯净底与细线)
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF131B2E).copy(alpha = 0.85f))
                .border(
                    width = 0.6.dp,
                    color = primaryColor.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { isExpanded = !isExpanded }
                .padding(horizontal = 8.dp, vertical = 3.5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // 工具专属图标或执行齿轮
                    if (isExecuting) {
                        Text(
                            text = "⚙",
                            fontSize = 9.sp,
                            color = primaryColor,
                            modifier = Modifier.rotate(spinnerAngle)
                        )
                    } else {
                        Text(
                            text = meta.iconSymbol,
                            fontSize = 9.sp
                        )
                    }

                    // 工具中文名
                    Text(
                        text = meta.displayName,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontFamily = FontFamily.Monospace
                    )

                    // 状态说明
                    val summaryLabel = if (isExecuting) {
                        "生成工具调用中..."
                    } else {
                        toolCall.resultSummary ?: "已执行"
                    }

                    Text(
                        text = "· $summaryLabel",
                        fontSize = 9.sp,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1
                    )
                }

                // 弹性箭头指示
                Text(
                    text = "›",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor.copy(alpha = 0.75f),
                    modifier = Modifier.rotate(arrowRotation)
                )
            }
        }

        // 弹性展开详情卡片 (极简纯暗底)
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(spring(dampingRatio = 0.65f, stiffness = 350f)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 0.75f, stiffness = 420f)) + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF090D1A).copy(alpha = 0.95f))
                    .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "参数: ${toolCall.argsJson}",
                        fontSize = 8.5.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 11.sp
                    )
                    if (toolCall.resultSummary != null) {
                        Text(
                            text = "结果: ${toolCall.resultSummary}",
                            fontSize = 8.5.sp,
                            color = primaryColor,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 弹性逐字流式文本渲染 (吐字弹簧回弹跳动与呼吸跳动光标)
 */
@Composable
fun SpringStreamingText(
    text: String,
    isStreaming: Boolean,
    modifier: Modifier = Modifier
) {
    val bounceScale = remember { Animatable(1f) }

    // 逐字生成时触发细微的弹簧微晶跃动
    LaunchedEffect(text.length) {
        if (isStreaming && text.isNotEmpty()) {
            bounceScale.snapTo(1.025f)
            bounceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.45f,
                    stiffness = 500f
                )
            )
        }
    }

    Box(
        modifier = modifier
            .scale(bounceScale.value)
    ) {
        Text(
            text = text + if (isStreaming) " ▋" else "",
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFFF1F5F9)
        )
    }
}

/**
 * 纯暗微晶容器卡片 (供教程页等基础页面复用)
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    contentPadding: Int = 10,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF131722).copy(alpha = 0.9f))
            .border(0.6.dp, Color(0xFF2E384D), RoundedCornerShape(12.dp))
            .padding(contentPadding.dp)
    ) {
        content()
    }
}

/**
 * 用户输入气泡 (极简暗蓝紧凑气泡 · 右对齐 · 纯净利落)
 */
@Composable
fun UserChatBubble(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val bubbleShape = RoundedCornerShape(
        topStart = 12.dp,
        topEnd = 12.dp,
        bottomStart = 12.dp,
        bottomEnd = 3.dp
    )
    Box(
        modifier = modifier
            .clip(bubbleShape)
            .background(Color(0xFF0F243A).copy(alpha = 0.80f))
            .border(
                width = 0.6.dp,
                color = Color(0xFF38BDF8).copy(alpha = 0.45f),
                shape = bubbleShape
            )
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        content()
    }
}

/**
 * 极简内部光波渲染层 (极其纯粹 · 仅包含一道随波浪自然掠过的淡淡柔光)
 * 外面的波浪是点阵，经过卡片的原生硬件模糊层后，在内部化为一层干干净净的淡淡光波
 */
@Composable
fun SimpleLightWaveBacking(
    modifier: Modifier = Modifier,
    shockwaveProgress: Float = -1f,
    thinkingIntensity: Float = 0f
) {
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // 1. 水波冲击时的淡淡光波 (Shockwave)
        if (shockwaveProgress in 0f..1.2f) {
            val waveCenterY = h * (1.1f - shockwaveProgress * 1.2f)
            val waveBand = h * 0.45f
            val waveAlpha = (0.28f * (1f - shockwaveProgress * 0.35f)).coerceIn(0f, 0.35f)
            val waveBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFBAE6FD).copy(alpha = waveAlpha),
                    Color(0xFF38BDF8).copy(alpha = waveAlpha * 0.8f),
                    Color.Transparent
                ),
                startY = waveCenterY - waveBand / 2f,
                endY = waveCenterY + waveBand / 2f
            )
            drawRect(brush = waveBrush)
        }

        // 2. 思考时的淡淡同心流光 (Thinking Wave)
        if (thinkingIntensity > 0.01f) {
            val thinkBrush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF38BDF8).copy(alpha = 0.22f * thinkingIntensity),
                    Color.Transparent
                ),
                center = androidx.compose.ui.geometry.Offset(w / 2f, h / 2f),
                radius = maxOf(w, h) * 0.7f
            )
            drawRect(brush = thinkBrush)
        }
    }
}

/**
 * Agent 回复极简偏正方形磨砂卡片 (大圆角微晶 Squircle · 双重镜像漫射真正毛玻璃)
 * 外面的波浪是点阵，经过卡片的原生硬件高斯模糊层后，在内部化为柔美微晶光晕与一层淡淡的光波。
 */
@Composable
fun AgentSquareBlurBox(
    modifier: Modifier = Modifier,
    borderColor: Color = Color(0xFF334155),
    approvalVisualState: ApprovalVisualState = ApprovalVisualState.NONE,
    isTaskCompletedTrigger: Boolean = false,
    thinkingIntensity: Float = 0f,
    isRecordingVoice: Boolean = false,
    shockwaveProgress: Float = -1f,
    gridSpacingDp: Float = 16f,
    content: @Composable () -> Unit
) {
    // 大圆角 Squircle 现代微晶超椭圆造型
    val squareShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 145.dp)
            .clip(squareShape)
            .border(
                width = 0.8.dp,
                color = borderColor.copy(alpha = 0.55f),
                shape = squareShape
            )
    ) {
        // 0. 卡片底座不透明深黑板 (彻底阻隔下层屏幕的未模糊锐利点阵，防止生硬透光)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF050811))
        )

        // 1. 安卓原生硬件高斯模糊底板 (RenderEffect 22f 真实物理 GPU 漫射底层点阵与柔光)
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    renderEffect = RenderEffect.createBlurEffect(
                        22f,
                        22f,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
        ) {
            // 真实点阵图层：经由 RenderEffect 物理漫射后，化作柔和朦胧的微晶星云光晕
            UnifiedMatrixCanvas(
                modifier = Modifier.fillMaxSize(),
                approvalVisualState = approvalVisualState,
                isTaskCompletedTrigger = isTaskCompletedTrigger,
                thinkingIntensity = thinkingIntensity,
                isRecordingVoice = isRecordingVoice,
                shockwaveProgress = shockwaveProgress,
                gridSpacingDp = gridSpacingDp
            )

            // 内部波浪：经由 RenderEffect 物理漫射后，化作一道纯净的淡淡光波
            SimpleLightWaveBacking(
                modifier = Modifier.fillMaxSize(),
                shockwaveProgress = shockwaveProgress,
                thinkingIntensity = thinkingIntensity
            )
        }

        // 2. 半透防眩光深黑曜石微晶层 (维持高对比度，让模糊后的点阵发光晕染若隐若现，并保证前景文字绝对清晰)
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color(0xFF090E1A).copy(alpha = 0.68f))
        )

        // 3. 内部正文内容 (100% 清晰锐利排布，纯净无多余英文伪表头)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp)
        ) {
            content()
        }
    }
}

/**
 * 原生硬件高斯模糊文字卡片 (兼容保留)
 */
@Composable
fun FrostedPhysicalMaskCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    borderColor: Color = Color(0xFF38BDF8),
    contentPaddingHorizontal: Int = 10,
    contentPaddingVertical: Int = 8,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .border(
                width = 0.8.dp,
                color = borderColor.copy(alpha = 0.40f),
                shape = shape
            )
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    renderEffect = RenderEffect.createBlurEffect(
                        18f,
                        18f,
                        Shader.TileMode.CLAMP
                    ).asComposeRenderEffect()
                }
                .background(Color(0xFF0F172A).copy(alpha = 0.45f))
        )
        Box(
            modifier = Modifier.padding(
                horizontal = contentPaddingHorizontal.dp,
                vertical = contentPaddingVertical.dp
            )
        ) {
            content()
        }
    }
}

/**
 * 旗舰级硬件高斯模糊文字容器 (兼容保留)
 */
@Composable
fun FrostedTextMaskContainer(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    FrostedPhysicalMaskCard(modifier = modifier, content = content)
}

