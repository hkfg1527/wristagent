package com.example.myapplication.presentation.ui.chat

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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.presentation.ui.AgentSquareBlurBox
import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.RenderableConversationItem
import com.example.myapplication.presentation.ui.SpringColoredToolPill
import com.example.myapplication.presentation.ui.SpringStreamingText
import com.example.myapplication.presentation.ui.ThoughtPill
import com.example.myapplication.presentation.ui.UserChatBubble
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBanner
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 对话卡片宽度上限：240dp 圆屏减去两侧 [Wear.Space.Edge] 后四周仍留 20dp，
 * 长回复不再贴着圆屏边缘被裁；用户气泡同样收在此宽度内保持右对齐。
 */
private val ConversationCardMaxWidth = 200.dp

/** 列表条目 key：用户气泡用 block id，助手回合用回合 key，保证滚动位置稳定。 */
internal fun itemKeyOf(item: RenderableConversationItem): String = when (item) {
    is RenderableConversationItem.UserPrompt -> item.block.id
    is RenderableConversationItem.AssistantTurn -> item.key
}

/**
 * 对齐剩余距离（px，带符号）：[targetIndex] 这一项的**顶边**到"可读区顶部"的差。
 * 正值 = 还要向前滚（把内容往上推），负值 = 目标已经跑到上方，需要反向滚回来。
 *
 * 目标项不在可视区时不给"猜的距离"，而是按它相对可见窗口的方向返回搜寻距离，
 * 让限速模型先找过去（不瞬移），进可视区后再按实际偏移收敛。
 */
internal fun ScalingLazyListState.focusRemainingPx(targetIndex: Int, targetTopPx: Float): Float {
    val info = layoutInfo
    val viewportHeightPx = info.viewportSize.height.toFloat()
    val target = info.visibleItemsInfo.firstOrNull { it.index == targetIndex }
    if (target != null) {
        return CameraCurve.focusRemainingPx(
            itemCenterOffsetPx = target.offset.toFloat(),
            itemSizePx = target.size.toFloat(),
            viewportHeightPx = viewportHeightPx,
            targetTopPx = targetTopPx
        )
    }
    val lastVisible = info.visibleItemsInfo.lastOrNull()
        ?: return CameraCurve.seekRemainingPx(viewportHeightPx, targetBelow = true)
    return CameraCurve.seekRemainingPx(viewportHeightPx, targetBelow = targetIndex > lastVisible.index)
}

/**
 * 文字与卡片一体联动滚动流 (M3 圆屏标准：边缘缩小淡出，不做整块抹除)。
 * 全屏纵向滑动，正文左右内缩 Wear.Space.Edge；靠近上下边缘时按位置自动缩小淡出，正文可与时钟/主键重叠。
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
    onRotaryScroll: (Float) -> Boolean,
    modifier: Modifier = Modifier
) {
    ScalingLazyColumn(
        state = listState,
        // autoCentering = null：默认 AutoCenteringParams 会为 itemIndex 预留顶部空间，
        // 本项目曾写死 itemIndex = 0，于是"第 0 项能滚到圆心"变成滚动锚点 —— 每轮新消息到达后
        // 视图被拉回列表开头，新内容落在视口外。关掉后位置控制完全交给"最后一条居中"逻辑。
        autoCentering = null,
        // 库自带的 rotary（ScalingLazyColumn 默认装 requestFocusOnHierarchyActive + rotaryScrollable）
        // 会自己抢焦点并把刻度全部吃掉，本文件的处理器就永远收不到事件 —— 关掉它，表冠只由下方
        // onRotaryScrollEvent 独家接管；同理关掉系统拉伸越界，越界手感统一走 nestedScroll + graphicsLayer。
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
            .drawWithContent {
                drawContent()
                val clockBandPx = Wear.Size.TopContentInset.toPx()   // 34dp：曲面时钟占的高度
                val rampPx = 8.dp.toPx()                              // 收口用得很短，避免"半透明半可见"
                val bottomEdgePx = 14.dp.toPx()
                val heightPx = size.height
                if (heightPx > clockBandPx + bottomEdgePx) {
                    // 关键：不是从 0 线性拉到 34dp（那样这段里只是半透明、字仍可见），
                    // 而是"先整段抹干净，最后 8dp 快速收口"，这样正文绝不会压在时钟上。
                    val solidUntilPx = (clockBandPx - rampPx).coerceAtLeast(0f)
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            (solidUntilPx / heightPx).coerceIn(0f, 1f) to Color.Transparent,
                            (clockBandPx / heightPx).coerceIn(0f, 1f) to Wear.Palette.Bg,
                            ((heightPx - bottomEdgePx) / heightPx).coerceIn(0f, 1f) to Wear.Palette.Bg,
                            1f to Color.Transparent
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
            }
            .padding(horizontal = Wear.Space.Edge)
            // 顺序即作用域：rotary 事件从聚焦节点向外冒泡，处理器必须挂在 focusable 之外，
            // 写成 focusable().onRotaryScrollEvent{} 会让处理器落在焦点节点内侧，永远收不到事件
            // （wear 自己的 Modifier.rotaryScrollable 也是 rotaryHandler(...).focusRequester(...).focusable 这个顺序）。
            .onRotaryScrollEvent { event -> onRotaryScroll(event.verticalScrollPixels) }
            .focusRequester(focusRequester)
            .focusable(),
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
                            UserChatBubble(modifier = Modifier.widthIn(max = ConversationCardMaxWidth)) {
                                BasicText(
                                    text = item.block.content,
                                    style = Wear.TextBody
                                )
                            }
                        }
                    }

                    is RenderableConversationItem.AssistantTurn -> {
                        // Agent 回复：绝不用聊天气泡，Surface 实底卡片 + 1dp 描边
                        // 思考胶囊、工具调用与逐字流式文本 100% 完整包含在卡片内部
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
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
                                    verticalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    item.blocks.forEach { block ->
                                        when (block) {
                                            is ConversationBlock.Thought -> {
                                                // 思考微型小胶囊 (带弹性收展动画，内嵌在方框内)
                                                ThoughtPill(thought = block)
                                            }

                                            is ConversationBlock.ToolCall -> {
                                                // 彩色工具调用小胶囊 (专属颜色、状态干了什么、弹性展开，内嵌在方框内)
                                                SpringColoredToolPill(toolCall = block)
                                            }

                                            is ConversationBlock.Text -> {
                                                // 弹性逐字生成流式文本 (带弹簧微晶跃动与呼吸跳动光标)
                                                SpringStreamingText(
                                                    text = block.content,
                                                    isStreaming = block.isStreaming,
                                                    // 单次刷新吐出十几字以上就是"过快"：不做逐字动画，只直接刷新文本
                                                    animateCursor = !isStreamingBurst
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
            item { WearBanner(text = "[ALERT] $errorMessage", tone = Tone.DANGER) }
        }
    }
}
