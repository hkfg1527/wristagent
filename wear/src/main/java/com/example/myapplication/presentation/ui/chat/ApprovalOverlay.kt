package com.example.myapplication.presentation.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.settings.motionSpec
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.myapplication.R
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ApprovalTone
import com.example.myapplication.presentation.ui.ApprovalBlockHeight
import com.example.myapplication.presentation.ui.ApprovalTextRes
import com.example.myapplication.presentation.ui.ToolVisualMeta
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.sqrt
import kotlinx.coroutines.delay

/** 审批文案宽度：240dp 圆屏上固定 190dp，两侧各留 25dp，正文不会碰到圆周。 */
private val ApprovalTextWidth = 190.dp

/**
 * 文案带底边距屏幕底的总预留：底距 12 + 审批底栏块 64 + 间隙 12 = 88dp（见 [ApprovalBandMetrics]）。
 *
 * 旧实现只有一个 48dp 的"抬升"把文案整列居中，文案比文案带高时就直接画到滑轨上
 * （真机 docs/device/v10/71_approval2.png：最坏情况第二行整行压在粉色胶囊下面）。
 * 现在改成从屏幕底部倒推出来的固定带：超出的内容只能在这一带里滚动，不可能再压到滑轨。
 */
private val ApprovalContentBandReserve =
    ApprovalBandMetrics.contentBandReserveDp(ApprovalBlockHeight.value).dp

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
 * 审批浮层的纵向分带（不依赖 Compose 运行时，数值由 JVM 单测钉住）。
 *
 * 圆屏屏高 H 上只存在两条带：
 * - 文案带 [0, H − Reserve)：命令原文与最坏情况住在这里，放不下就滚动；
 * - 底栏带 [H − Reserve + Gap, H)：滑轨与「记住并放行」由 BottomInteractiveBlock 独占。
 * 两带之间隔着 [CONTENT_TO_TRACK_GAP_DP]，所以"文字被滑轨压住"在结构上不可能发生。
 *
 * [BAR_BOTTOM_INSET_DP] 与 ChatScreen 的 private BottomBarBottomInset 取同一数值；
 * 审批块高不写死，由调用点传入 [ApprovalBlockHeight]（= BottomBarReserve 76 − Space.Md 12）。
 */
internal object ApprovalBandMetrics {
    /** 底栏底距。ChatScreen.BottomBarBottomInset 是 private，只能在这里取同值。 */
    const val BAR_BOTTOM_INSET_DP = 12f

    /**
     * 文案带与滑轨带之间的硬间隙，也是文案带底边到审批块顶边的距离。
     *
     * 底栏块顶边同时就是滑轨 44dp 触控热区的顶边，32dp 的粉色胶囊在热区里上下各缩 6dp，
     * 所以文字最后一个可绘制像素到胶囊的真实间距是 12 + 6 = 18dp。
     */
    const val CONTENT_TO_TRACK_GAP_DP = 12f

    /** 文案带底边距屏幕底的总预留 = 底距 + 审批块高 + 间隙。 */
    fun contentBandReserveDp(blockHeightDp: Float): Float =
        BAR_BOTTOM_INSET_DP + blockHeightDp + CONTENT_TO_TRACK_GAP_DP

    /** 文案带可用高度：屏高不够时夹到 0，绝不返回负数去撑爆布局。 */
    fun contentBandHeightDp(screenHeightDp: Float, blockHeightDp: Float): Float =
        (screenHeightDp - contentBandReserveDp(blockHeightDp)).coerceAtLeast(0f)

    /**
     * 矩形元素最远角点到圆屏圆心的距离：半宽 [halfWidthDp] + 纵向偏移 [dyFromCenterDp]。
     * 圆屏半径 116.5dp（466px @2.0 密度），超过它的角点会被圆周切掉。
     */
    fun farthestCornerRadiusDp(halfWidthDp: Float, dyFromCenterDp: Float): Float =
        sqrt(halfWidthDp * halfWidthDp + dyFromCenterDp * dyFromCenterDp)
}

/**
 * 审批文案的行内排版：12sp [Wear.Palette.TextMuted] 标签做引子，正文接在同一段落里。
 * 竖排标签每块要多占一行（四块合计 64dp），而文案带只有 145dp（233dp 真表），
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
 *
 * 纵向只分两条带：文案带 [审批块顶边 − 12dp] 往上，滑轨与「记住并放行」归 BottomInteractiveBlock。
 * 文案带内的内容超过带高时**滚动**而不是截断（钉在底部，最坏情况始终可见），
 * 滚动容器同时把内容夹在带内，于是任何文字都不可能再画到滑轨上。
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
        animationSpec = motionSpec(animationsEnabled, Wear.Motion.quick()),
        label = "approvalScrimAlpha"
    )

    val level = risk?.aiRiskLevel ?: RiskLevel.MEDIUM
    val accent = ApprovalTone.accentOf(level)
    val toolMeta = ToolVisualMeta.resolve(toolName)
    val toolDisplayName = toolMeta.displayNameRes?.let { stringResource(it) } ?: toolMeta.toolName
    val whatItDoes = risk?.whatItDoes.orEmpty()
    val worstCase = risk?.worstCase.orEmpty()
    // 12sp 字号 + 说明档行高：190dp 宽下正文一行 14dp，四块文案的总高交给文案带滚动兜底
    val bodyStyle = Wear.TextBody.copy(
        fontSize = Wear.TextCaption.fontSize,
        lineHeight = Wear.TextCaption.lineHeight
    )
    val centeredBody = bodyStyle.copy(textAlign = TextAlign.Center)
    val worstCaseStyle = bodyStyle.copy(color = Wear.Palette.Danger)
    val centeredWorstCase = worstCaseStyle.copy(textAlign = TextAlign.Center)

    // 整层不透明度：与压暗层同一条曲线，常驻组合（= 提前预合成）靠它显隐。
    // 原来用 AnimatedVisibility：visible 变 true 的那一帧才开始建这棵子树，
    // "组合 + 布局 + 建径向渐变画刷"三笔成本全压在首帧上，真表上就是一次轻微闪切。
    // 现在整层一直挂在这棵树里，切换那一帧不再有组合与布局开销；不可见时 alpha 为 0，绘制整体跳过。
    val layerAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = motionSpec(animationsEnabled, Wear.Motion.quick()),
        label = "approvalLayerAlpha"
    )

    // 文案带的滚动位置：内容比带子高时钉在底部 —— 「最坏情况」永远紧贴滑轨上方那 12dp 间隙可见，
    // 用户不必先猜"要不要滚一下才能看到最坏后果"。内容变短（maxValue 变化）时自动重新钉一次。
    val contentScroll = rememberScrollState()
    LaunchedEffect(contentScroll.maxValue) {
        contentScroll.scrollTo(contentScroll.maxValue)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = layerAlpha }
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
            // 文案带：底边钉在滑轨上方 [ApprovalBandMetrics.CONTENT_TO_TRACK_GAP_DP]，文案只在这一带里布局与滚动
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ApprovalContentBandReserve),
                contentAlignment = Alignment.Center
            ) {
                // 径向暗晕：中心浓、150dp 处已透明，没有边界没有轮廓，文字像从背景里浮出来。
                // 画刷交给 drawWithCache：只在尺寸变化时重建，渐显那几帧不再每帧新建一个 Brush。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val halo = Brush.radialGradient(
                                colors = listOf(
                                    Wear.Palette.Bg.copy(alpha = ApprovalHaloCenterAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(size.width / 2f, size.height / 2f),
                                radius = ApprovalHaloRadius.toPx()
                            )
                            onDrawBehind { drawRect(brush = halo) }
                        }
                )
                Column(
                    modifier = Modifier
                        .width(ApprovalTextWidth)
                        // 塌缩只作用于文案：缩到工具胶囊尺度并下移，动画走完才退出审批态
                        .graphicsLayer {
                            scaleX = 1f - (1f - ApprovalCollapseScale) * collapse
                            scaleY = 1f - (1f - ApprovalCollapseScale) * collapse
                            translationY = ApprovalCollapseDrop.toPx() * collapse
                        }
                        // 放不下就滚动，不裁字：滚动容器把内容夹在文案带里，越过底边的东西也画不出来
                        .verticalScroll(contentScroll),
                    verticalArrangement = Arrangement.spacedBy(ApprovalTextBlockGap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 块一：等级标题 + 3 段危险条（标题定调，危险条按等级点亮几段）
                    FloatingApprovalText(slot = 0, visible = visible) {
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
                    FloatingApprovalText(slot = 1, visible = visible) {
                        BasicText(
                            text = approvalParagraph(
                                stringResource(R.string.wearchat_approval_label_call),
                                toolDisplayName,
                                centeredBody,
                                Wear.TextCaption
                            ),
                            style = centeredBody
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
                        FloatingApprovalText(slot = 2, visible = visible) {
                            BasicText(
                                text = approvalParagraph(
                                    stringResource(blockThreeLabelRes),
                                    blockThreeText,
                                    centeredBody,
                                    Wear.TextCaption
                                ),
                                style = centeredBody
                            )
                        }
                    }

                    // 块四：最坏情况（空文案整块不占位）
                    if (worstCase.isNotBlank()) {
                        FloatingApprovalText(slot = 3, visible = visible) {
                            BasicText(
                                text = approvalParagraph(
                                    stringResource(R.string.wearchat_approval_label_worst_case),
                                    worstCase,
                                    centeredWorstCase,
                                    Wear.TextCaption
                                ),
                                style = centeredWorstCase
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
 * 走 [Wear.Motion.micro]；第 [slot] 块再延后 slot×60ms 启动，于是文字依次从底下浮上来排到上面。
 * 浮层常驻组合后，入场必须由 [visible] 重新起跑，否则这套错峰只在页面首帧隐身着跑一次。
 */
@Composable
private fun FloatingApprovalText(
    slot: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(enter, animationsEnabled, visible) {
        // 浮层现在常驻组合（见 ApprovalFloatingLayer 的预合成说明），入场动画必须靠 visible 重新起跑，
        // 否则四块文案只在页面首帧隐身着跑完一次，真审批弹出时是"啪"地直接出现。
        if (!visible) return@LaunchedEffect
        // 关闭动画时既不排队也不弹簧：四块文案一次性、无位移地出现
        if (!animationsEnabled) {
            enter.snapTo(1f)
            return@LaunchedEffect
        }
        enter.snapTo(0f)
        delay(ApprovalTextStaggerMs * slot)
        enter.animateTo(1f, Wear.Motion.micro())
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
