package com.example.myapplication.presentation.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.settings.motionSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ApprovalTone
import com.example.myapplication.presentation.ui.ApprovalTextRes
import com.example.myapplication.presentation.ui.ToolVisualMeta
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay

/** 审批文案宽度：240dp 圆屏上固定 190dp，两侧各留 25dp，正文不会碰到圆周。 */
private val ApprovalTextWidth = 190.dp

/**
 * 文案块垂直抬升：屏幕高 240dp，取 48dp 抬升后文案中心落在 (240−48)/2 = 96dp，
 * 整列文案因此压在圆心上方，底边不会挤进 164dp 起的滑轨区。
 */
private val ApprovalTextCenterLift = 48.dp

/** 文案块间距：靠 10dp 间距分段，不用任何线框。 */
private val ApprovalTextBlockGap = 10.dp

/** 审批态只做极轻压暗（0.22）：点阵必须仍然看得见，也没有任何整屏绿/红蒙色层。 */
private const val ApprovalScrimAlpha = 0.22f

/** 文字底下径向暗晕：中心 0.82 的 Bg，150dp 处已经透明，于是没有边界也没有轮廓。 */
private const val ApprovalHaloCenterAlpha = 0.82f
private val ApprovalHaloRadius = 150.dp

/** 文案入场：每块从 +48dp 浮上来并淡入，四块依次错开 60ms（标题 → 调用 → 改动 → 最坏情况）。 */
private val ApprovalTextFloatDistance = 48.dp
private const val ApprovalTextStaggerMs = 60L

/** 危险条：3 段 32×8dp，按等级点亮。 */
private val ApprovalBarWidth = 32.dp
private val ApprovalBarHeight = 8.dp

/** 放行塌缩：卡片缩到工具胶囊尺度、下移淡出，动画结束后才真正退出审批态。 */
private const val ApprovalCollapseScale = 0.18f
private val ApprovalCollapseDrop = 60.dp

/** AI 风险等级 -> 危险条点亮段数：中低风险 1 段、高风险 2 段、致命 3 段。 */
private fun riskBarsLit(level: RiskLevel): Int = when (level) {
    RiskLevel.LOW, RiskLevel.MEDIUM -> 1
    RiskLevel.HIGH -> 2
    RiskLevel.CRITICAL -> 3
}

/**
 * 审批文案的行内排版：12sp [Wear.Palette.TextMuted] 标签做引子，正文接在同一段落里。
 * 竖排标签每块要多占一行（四块合计 64dp），而滑轨上方只有 164dp，
 * 行内引子把这行高度让给正文，四块信息一条不少。
 */
private fun approvalParagraph(
    label: String,
    body: String,
    bodyStyle: TextStyle,
    labelStyle: TextStyle
): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontSize = labelStyle.fontSize, color = Wear.Palette.TextMuted)) {
            append(label)
            append("  ")
        }
        withStyle(
            SpanStyle(
                fontSize = bodyStyle.fontSize,
                color = bodyStyle.color,
                fontWeight = bodyStyle.fontWeight
            )
        ) {
            append(body)
        }
    }

/**
 * 审批浮层：故意不带任何框 —— 没有 Surface 底、没有 1dp 描边、没有圆角、没有内边距盒，
 * 文字直接浮在屏幕中央。文案正下方铺一层 [ApprovalHaloRadius] 的径向暗晕
 * （中心 [ApprovalHaloCenterAlpha] 的 Bg → 半径处透明），暗晕没有边界也没有轮廓，
 * 于是文字是从背景里浮出来的，而不是被框住；暗晕之外的点阵依旧清晰可见。
 * 内容自上而下：等级标题 + 3 段危险条 / 调用 / 改动 / 最坏情况（空文案整块不占位），
 * 每块从 +48dp 浮上来并错开 60ms 启动；放行时整层按 [collapse] 塌缩成工具胶囊尺度。
 */
@Composable
internal fun ApprovalFloatingLayer(
    visible: Boolean,
    toolName: String,
    risk: RiskAssessment?,
    collapse: Float,
    modifier: Modifier = Modifier
) {
    // 动画开关：关闭后压暗层与文案浮起都直接落到终值，不给"审批流光"留任何进行中的时间线
    val animationsEnabled = LocalAnimationsEnabled.current

    // 只压暗 0.22：点阵保持可见，绿/红一律交给点阵自身的颜色插值，不再有整屏蒙色层
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) ApprovalScrimAlpha else 0f,
        animationSpec = motionSpec(animationsEnabled, tween(140)),
        label = "approvalScrimAlpha"
    )

    val level = risk?.aiRiskLevel ?: RiskLevel.MEDIUM
    val accent = ApprovalTone.accentOf(level)
    val toolMeta = ToolVisualMeta.resolve(toolName)
    val toolDisplayName = toolMeta.displayNameRes?.let { stringResource(it) } ?: toolMeta.toolName
    val whatItDoes = risk?.whatItDoes.orEmpty()
    val worstCase = risk?.worstCase.orEmpty()
    // 12sp / 16sp 行高：两行正文只占 32dp，四块文案加标题也压得住 190dp 宽
    val bodyStyle = Wear.TextBody.copy(
        fontSize = Wear.TextCaption.fontSize,
        lineHeight = Wear.TextCaption.lineHeight
    )
    val centeredBody = bodyStyle.copy(textAlign = TextAlign.Center)
    val worstCaseStyle = bodyStyle.copy(color = Wear.Palette.Danger)
    val centeredWorstCase = worstCaseStyle.copy(textAlign = TextAlign.Center)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motionSpec(animationsEnabled, tween(140))),
        exit = fadeOut(motionSpec(animationsEnabled, tween(120))),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 放行塌缩：压暗层与文案一起淡出
                .graphicsLayer { alpha = 1f - collapse }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Wear.Palette.Bg.copy(alpha = scrimAlpha))
            )
            // 文案居中偏上：底部留出 [ApprovalTextCenterLift]，文案中心才落在 96dp 而不是屏心
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ApprovalTextCenterLift),
                contentAlignment = Alignment.Center
            ) {
                // 径向暗晕：中心浓、150dp 处已透明，没有边界没有轮廓，文字像从背景里浮出来
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Wear.Palette.Bg.copy(alpha = ApprovalHaloCenterAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = ApprovalHaloRadius.toPx()
                        )
                    )
                }
                Column(
                    modifier = Modifier
                        .width(ApprovalTextWidth)
                        // 塌缩只作用于文案：缩到工具胶囊尺度并下移，动画走完才退出审批态
                        .graphicsLayer {
                            scaleX = 1f - (1f - ApprovalCollapseScale) * collapse
                            scaleY = 1f - (1f - ApprovalCollapseScale) * collapse
                            translationY = ApprovalCollapseDrop.toPx() * collapse
                        },
                    verticalArrangement = Arrangement.spacedBy(ApprovalTextBlockGap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 块一：等级标题 + 3 段危险条（标题定调，危险条按等级点亮几段）
                    FloatingApprovalText(slot = 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                        ) {
                            BasicText(
                                text = stringResource(ApprovalTextRes.headlineResOf(level)),
                                style = Wear.TextTitle.copy(color = accent),
                                maxLines = 1
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(3) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(ApprovalBarWidth, ApprovalBarHeight)
                                            .clip(RoundedCornerShape(Wear.Space.Xs))
                                            .background(
                                                if (index < riskBarsLit(level)) accent
                                                else Wear.Palette.Border.copy(alpha = 0.45f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // 块二：调用了什么（工具显示名，无 emoji）
                    FloatingApprovalText(slot = 1) {
                        BasicText(
                            text = approvalParagraph(
                                stringResource(R.string.wearchat_approval_label_call),
                                toolDisplayName,
                                centeredBody,
                                Wear.TextCaption
                            ),
                            style = centeredBody,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 块三：特权工具直接展示命令原文（审批的依据就是这条命令），其余工具展示"改动了什么"
                    val commandText = risk?.commandText.orEmpty()
                    val blockThreeLabelRes = if (commandText.isNotBlank()) {
                        R.string.wearchat_approval_label_command
                    } else {
                        R.string.wearchat_approval_label_change
                    }
                    val blockThreeText = if (commandText.isNotBlank()) commandText else whatItDoes
                    if (blockThreeText.isNotBlank()) {
                        FloatingApprovalText(slot = 2) {
                            BasicText(
                                text = approvalParagraph(
                                    stringResource(blockThreeLabelRes),
                                    blockThreeText,
                                    centeredBody,
                                    Wear.TextCaption
                                ),
                                style = centeredBody,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 块四：最坏情况（空文案整块不占位）
                    if (worstCase.isNotBlank()) {
                        FloatingApprovalText(slot = 3) {
                            BasicText(
                                text = approvalParagraph(
                                    stringResource(R.string.wearchat_approval_label_worst_case),
                                    worstCase,
                                    centeredWorstCase,
                                    Wear.TextCaption
                                ),
                                style = centeredWorstCase,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一块审批文案的入场：同一个 [Animatable] 同时驱动 translationY（+48dp → 0）与 alpha（0 → 1），
 * 走 spring(阻尼 0.82 / 刚度 260)；第 [slot] 块再延后 slot×60ms 启动，于是文字依次从底下浮上来排到上面。
 */
@Composable
private fun FloatingApprovalText(
    slot: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(enter, animationsEnabled) {
        // 关闭动画时既不排队也不弹簧：四块文案一次性、无位移地出现
        if (!animationsEnabled) {
            enter.snapTo(1f)
            return@LaunchedEffect
        }
        delay(ApprovalTextStaggerMs * slot)
        enter.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = enter.value
            translationY = ApprovalTextFloatDistance.toPx() * (1f - enter.value)
        }
    ) {
        content()
    }
}
