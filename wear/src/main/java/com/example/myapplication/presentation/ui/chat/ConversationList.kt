package com.example.myapplication.presentation.ui.chat

import com.example.myapplication.presentation.ErrorMessage

import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ChatBodyGeometry
import com.example.myapplication.presentation.ui.AgentSquareBlurBox
import com.example.myapplication.presentation.ui.ChatVisualMetrics
import com.example.myapplication.presentation.ui.RenderableConversationItem
import com.example.myapplication.presentation.ui.SpringColoredToolPill
import com.example.myapplication.presentation.ui.SpringStreamingText
import com.example.myapplication.presentation.ui.ThoughtPill
import com.example.myapplication.presentation.ui.UserChatBubble
import com.example.myapplication.presentation.ui.chat.CapsuleExpansion
import com.example.myapplication.presentation.ui.chat.CapsuleKeys
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBanner
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 对话卡片宽度上限：184dp（见 [ChatVisualMetrics]）。
 *
 * 旧值 200dp = 屏宽 85.8%，最外墨迹半径 113.1dp / 116.5dp —— 只剩 3.4dp 余量，贴边。
 * 收到 184dp 后左右各留 24.5dp，长回复与用户气泡都不会碰圆周。
 */
private val ConversationCardMaxWidth = ChatVisualMetrics.BubbleMaxWidthDp.dp

/**
 * 顶部时钟带遮罩色：不是纯黑，留约 14% 透明让内容隐约透出（用户要求"别那么黑"）。
 * DstIn 混合下 alpha 决定"抹掉多少"，0.86 即保留 14%。
 */
private val MatMaskColor = Wear.Palette.Bg.copy(alpha = 0.86f)

/** 列表条目 key：用户气泡用 block id，助手回合用回合 key，保证滚动位置稳定。 */
internal fun itemKeyOf(item: RenderableConversationItem): String = when (item) {
    is RenderableConversationItem.UserPrompt -> item.block.id
    is RenderableConversationItem.AssistantTurn -> item.key
}

/**
 * 文字与卡片一体联动滚动流 (M3 圆屏标准：边缘缩小淡出，不做整块抹除)。
 * 全屏纵向滑动，正文左右内缩 Wear.Space.Lg；靠近上下边缘时按位置自动缩小淡出，正文可与时钟/主键重叠。
 * 表冠事件由外部注入，列表只负责渲染与手势透传。
 */
@Composable
internal fun ConversationList(
    items: List<RenderableConversationItem>,
    listState: ScalingLazyListState,
    focusRequester: FocusRequester,
    listAlpha: Float,
    isApprovalFocus: Boolean,
    listBlurEffect: BlurEffect,
    errorMessage: String?,
    isStreamingBurst: Boolean,
    approvalVisualState: ApprovalVisualState,
    isTaskCompleted: Boolean,
    thinkingIntensity: Float,
    isRecordingVoice: Boolean,
    shockwaveProgress: Float,
    gridSpacingDp: Float,
    /** 已展开的胶囊键（ViewModel 持有）：滚动与重组都不会把它折回去。 */
    expandedCapsules: Set<String>,
    onToggleCapsule: (String) -> Unit,
    /** 简洁模式（ChatUiState.compactMode）：只收紧正文行高与块间距，见 [ChatBodyGeometry]。 */
    compactMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val body = ChatBodyGeometry.of(compactMode)

    ScalingLazyColumn(
        state = listState,
        // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
        flingBehavior = ScrollableDefaults.flingBehavior(),
        // autoCentering = null：默认 AutoCenteringParams 会为 itemIndex 预留顶部空间，
        // 本项目曾写死 itemIndex = 0，于是"第 0 项能滚到圆心"变成滚动锚点 —— 每轮新消息到达后
        // 视图被拉回列表开头，新内容落在视口外。关掉后位置控制完全交给"最后一条居中"逻辑。
        autoCentering = null,
        // 关掉库内建的 rotary：表冠由下方 Modifier.rotaryScrollable 显式接管，焦点请求器留在调用方手里
        // （库内建那条走 requestFocusOnHierarchyActive，与"由调用方 requester 抢焦点"不能混用）；
        // 系统拉伸越界同样关掉，越界手感统一走 graphicsLayer。
        rotaryScrollableBehavior = null,
        overscrollEffect = null,
        // top 必须让开顶部曲面时钟：14dp 时首条卡片会直接压到时钟上（真表 192dp 屏上尤其明显）；
        // 取 TopContentInset(34dp) + Space.Md(12dp) = 46dp，既在时钟下方、又只让掉一点点高度。
        // bottom 让最后一项能滚到圆屏中心（见 Wear.Size.ListCenterReach）
        contentPadding = PaddingValues(
            top = Wear.Size.TopContentInset + Wear.Space.Md,
            bottom = Wear.Size.ListCenterReach
        ),
        modifier = modifier
            .fillMaxSize()
            // 审批沉浸：整列正文退到 35% 并轻度模糊（只糊列表图层，点阵画布保持清晰）；
            // 图层被缓存，模糊只在列表内容真正变化时重算，不是每帧全屏后处理
            .graphicsLayer {
                alpha = listAlpha
                renderEffect = if (isApprovalFocus) listBlurEffect else null
            }
            // 边缘淡出：DstIn 必须落在离屏图层里，否则会把下方已画的点阵一起抹掉。
            // 顶部渐隐必须盖住整个曲面时钟带（TopContentInset=34dp），
            // 否则长回复向上滚动时正文会从时钟底下穿出来、和 "22:57" 叠字（真表 192dp 屏上尤其明显）；
            // 底部只需让开一点点，给底栏留白的边缘收口。
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            // drawWithCache 只在**尺寸变化**时重建渐变，滚动时直接复用同一个 Brush。
            // 原来放在 drawWithContent 里，等于每次重绘都 new 一个 verticalGradient（滚动时每帧一次）。
            .drawWithCache {
                val clockBandPx = Wear.Size.TopContentInset.toPx()   // 34dp：曲面时钟占的高度
                val rampPx = 8.dp.toPx()                              // 收口用得很短，避免"半透明半可见"
                val bottomEdgePx = 14.dp.toPx()
                val heightPx = size.height
                val brush = if (heightPx > clockBandPx + bottomEdgePx) {
                    // 不是从 0 线性拉到 34dp（那样这段里只是半透明、字仍可见），
                    // 而是"先整段抹干净，最后 8dp 快速收口"，这样正文绝不会压在时钟上。
                    val solidUntilPx = (clockBandPx - rampPx).coerceAtLeast(0f)
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        (solidUntilPx / heightPx).coerceIn(0f, 1f) to Color.Transparent,
                        // 不再 100% 抹黑：留约 14% 透出，让时钟带里能隐约看到内容而不是一块死黑
                        (clockBandPx / heightPx).coerceIn(0f, 1f) to MatMaskColor,
                        ((heightPx - bottomEdgePx) / heightPx).coerceIn(0f, 1f) to MatMaskColor,
                        1f to Color.Transparent
                    )
                } else {
                    null
                }
                onDrawWithContent {
                    drawContent()
                    if (brush != null) {
                        drawRect(brush = brush, blendMode = BlendMode.DstIn)
                    }
                }
            }
            .padding(horizontal = Wear.Space.Lg)
            // 官方 M3E 表冠：刻度→位移、松手惯性(RotaryFlingHandler + RotaryVelocityTracker)、
            // 平台触感(RotaryHapticsType.ScrollTick)全部由 behavior 提供，不再自造增益与震动阈值。
            // 展开后是 rotaryHandler(...).focusRequester(fr).focusTargetWithSemantics()，
            // 焦点目标已经由它自己装好，后面不能再叠 focusable()（会多出一个收不到 rotary 的焦点节点）。
            .rotaryScrollable(
                behavior = RotaryScrollableDefaults.behavior(listState),
                focusRequester = focusRequester
            ),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
    ) {
        // 顶部不再保留大留白：正文允许与弧形时钟重叠，只由列表自身的边缘缩放淡出收敛
        // 依次渲染对话结构流 (用户右对齐气泡 vs Assistant 纯正方形框，工具调用完整内嵌)
        items(
            count = items.size,
            key = { index -> itemKeyOf(items[index]) }
        ) { index ->
            val item = items[index]
            val itemKey = itemKeyOf(item)
            AnimatedMessageCard(key = itemKey) {
                when (item) {
                    is RenderableConversationItem.UserPrompt -> {
                        // 用户指令：允许右对齐聊天气泡样式，随手势一体滚动
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            UserChatBubble(
                                modifier = Modifier.widthIn(
                                    max = ChatVisualMetrics.userBubbleWidthDp().dp
                                )
                            ) {
                                BasicText(
                                    text = item.block.content,
                                    // CJK 行距 1.43×：旧值继承 bodyMedium 的 1.18 倍，
                                    // 中文行间墨距只剩 3.5dp，整段读成一堵灰墙；简洁模式再收 2sp
                                    style = Wear.TextBody.copy(lineHeight = body.lineHeightSp.sp)
                                )
                            }
                        }
                    }

                    is RenderableConversationItem.AssistantTurn -> {
                        // Agent 回复：绝不用聊天气泡，Surface 实底卡片 + 1dp 描边
                        // 思考胶囊、工具调用与逐字流式文本 100% 完整包含在卡片内部
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            AgentSquareBlurBox(
                                modifier = Modifier.widthIn(max = ConversationCardMaxWidth),
                                approvalVisualState = approvalVisualState,
                                isTaskCompletedTrigger = isTaskCompleted,
                                thinkingIntensity = thinkingIntensity,
                                isRecordingVoice = isRecordingVoice,
                                shockwaveProgress = shockwaveProgress,
                                gridSpacingDp = gridSpacingDp
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    // 段间距随简洁模式收紧：常规 5dp，简洁 1dp
                                    verticalArrangement = Arrangement.spacedBy(body.blockSpacingDp.dp)
                                ) {
                                    item.blocks.forEach { block ->
                                        when (block) {
                                            is ConversationBlock.Thought -> {
                                                // 思考小胶囊：标题随流式进度翻牌，展开态由 ViewModel 记住
                                                val key = CapsuleKeys.of(block)
                                                ThoughtPill(
                                                    thought = block,
                                                    isExpanded = CapsuleExpansion.isExpanded(expandedCapsules, key),
                                                    onToggle = { onToggleCapsule(key) }
                                                )
                                            }

                                            is ConversationBlock.ToolCall -> {
                                                // 彩色工具调用小胶囊 (专属颜色、状态干了什么、弹性展开，内嵌在方框内)
                                                val key = CapsuleKeys.of(block)
                                                SpringColoredToolPill(
                                                    toolCall = block,
                                                    isExpanded = CapsuleExpansion.isExpanded(expandedCapsules, key),
                                                    onToggle = { onToggleCapsule(key) }
                                                )
                                            }

                                            is ConversationBlock.Text -> {
                                                // 弹性逐字生成流式文本 (带弹簧微晶跃动与呼吸跳动光标)
                                                SpringStreamingText(
                                                    text = block.content,
                                                    isStreaming = block.isStreaming,
                                                    // 单次刷新吐出十几字以上就是"过快"：不做逐字动画，只直接刷新文本
                                            
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 错误提示 (随列表一体滚动)
        if (!errorMessage.isNullOrBlank()) {
            // 只说人话：原始 provider JSON 不进气泡（映射规则见 ErrorMessage）
            item { WearBanner(text = ErrorMessage.display(errorMessage), tone = Tone.DANGER) }
        }
    }
}
