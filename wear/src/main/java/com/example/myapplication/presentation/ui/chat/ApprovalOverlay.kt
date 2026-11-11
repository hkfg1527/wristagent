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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * 内容带底边距屏幕底的总预留：底距 12 + 审批底栏块 64 + 间隙 12 = 88dp（见 [ApprovalBandMetrics]）。
 *
 * 旧实现只有一个 48dp 的"抬升"把文案整列居中，文案比文案带高时就直接画到滑轨上
 * （真机 docs/device/v10/71_approval2.png：最坏情况第二行整行压在粉色胶囊下面）。
 * 现在这一段由屏幕底部倒推得到：固定头从它里面再扣掉（头高见 [ApprovalBandMetrics.HEADER_HEIGHT_DP]），
 * 剩下的才是可滚动内容带，超出的内容只能在这一带里滚，不可能再压到滑轨。
 */
private val ApprovalContentBandReserve =
    ApprovalBandMetrics.contentBandReserveDp(ApprovalBlockHeight.value).dp

/**
 * 文案块间距：靠 10dp 间距分段，不用任何线框。
 *
 * 固定头与内容带之间也走这同一条间距，所以它同时是 [ApprovalBandMetrics.HEADER_HEIGHT_DP] 的最后一段。
 */
private val ApprovalTextBlockGap = ApprovalBandMetrics.BLOCK_GAP_DP.dp

/** 审批态只做极轻压暗（0.22）：点阵必须仍然看得见，也没有任何整屏绿/红蒙色层。 */
private const val ApprovalScrimAlpha = 0.22f

/** 文字底下径向暗晕：中心 0.82 的 Bg，150dp 处已经透明，于是没有边界也没有轮廓。 */
private const val ApprovalHaloCenterAlpha = 0.82f
private val ApprovalHaloRadius = 150.dp

/** 文案入场：每块从 +48dp 浮上来并淡入，四块依次错开 60ms（标题 → 调用 → 改动 → 最坏情况）。 */
private val ApprovalTextFloatDistance = 48.dp
private const val ApprovalTextStaggerMs = 60L

/** 危险条：3 段 32×8dp，按等级点亮。条高与 [ApprovalBandMetrics.HEADER_BARS_DP] 同源。 */
private val ApprovalBarWidth = 32.dp
private val ApprovalBarHeight = ApprovalBandMetrics.HEADER_BARS_DP.dp

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
 * 圆屏屏高 H 上自上而下是三段：
 * - 固定头 [0, HEADER_HEIGHT_DP)：等级标题 + 3 段危险条。它不在任何滚动容器里、也没有高度上限，
 *   所以"标题被滚动裁掉"与滚动位置无关，结构上不可能发生；
 * - 内容带 [HEADER_HEIGHT_DP, H − Reserve)：调用 / 命令 / 最坏情况，放不下就滚动并钉到底；
 * - 底栏带 [H − Reserve + Gap, H)：滑轨与「记住并放行」由 BottomInteractiveBlock 独占，
 *   它们的 y 区间只由屏高与块高决定，与本对象里的头高无关。
 * 内容带与滑轨之间隔着 [CONTENT_TO_TRACK_GAP_DP]，所以"文字被滑轨压住"在结构上不可能发生。
 *
 * 守恒：HEADER_HEIGHT_DP + contentBandHeightDp(H, Block) + 12 + 64 + 12 = H。
 * 头高是从内容带里扣掉的，不是叠在内容上 —— 头变高时内容带相应变矮，头只吃文字，不吃底栏。
 *
 * [BAR_BOTTOM_INSET_DP] 与 ChatScreen 的 private BottomBarBottomInset 取同一数值；
 * 审批块高不写死，由调用点传入 [ApprovalBlockHeight]（= BottomBarReserve 76 − Space.Md 12）。
 */
internal object ApprovalBandMetrics {
    /** 底栏底距。ChatScreen.BottomBarBottomInset 是 private，只能在这里取同值。 */
    const val BAR_BOTTOM_INSET_DP = 12f

    /**
     * 内容带与滑轨带之间的硬间隙，也是内容带底边到审批块顶边的距离。
     *
     * 底栏块顶边同时就是滑轨 44dp 触控热区的顶边，32dp 的粉色胶囊在热区里上下各缩 6dp，
     * 所以文字最后一个可绘制像素到胶囊的真实间距是 12 + 6 = 18dp。
     */
    const val CONTENT_TO_TRACK_GAP_DP = 12f

    /**
     * 固定头顶部的圆屏安全留白 8dp（= [Wear.Space.Sm]）。
     *
     * 固定头从屏高 0dp 开始，而 233dp 圆屏在 y<12dp 处的弦长比标题还窄：
     * y=5dp 处弦长只有 68dp、y=13dp 处 107dp，而「高风险操作」18sp 五个字宽 90dp。
     * 不留这 8dp，标题顶到 y≈5dp，两个字的上半截会被圆周直接切掉 —— 和"被滚动裁掉"是同一类毛病。
     */
    const val HEADER_TOP_PAD_DP = 8f

    /** 固定头里标题那一行的高：titleMedium 18/24sp 的行高，即 24dp（WearMaterialTheme 的显式覆写）。 */
    const val HEADER_TITLE_LINE_DP = 24f

    /** 标题与危险条之间的一段：[Wear.Space.Sm] 8dp。 */
    const val HEADER_TITLE_TO_BARS_DP = 8f

    /** 危险条本身的高：3 段 32×8dp 里的 8dp。 */
    const val HEADER_BARS_DP = 8f

    /** 文案块间距 10dp：块二/三/四之间用它，固定头到内容带也用同一条。 */
    const val BLOCK_GAP_DP = 10f

    /**
     * 固定头总高 = 圆屏留白 8 + 标题行 24 + 标题到条 8 + 条 8 + 到内容带的间距 10 = 58dp。
     *
     * 除了那 8dp 圆屏留白，没有一 dp 余量：233dp 真表上内容带只剩 87dp，三块正文约 113dp，
     * 多留一 dp 就多一 dp 要上滑。标题行按 titleMedium 的 24dp 算，不另设字号；
     * 字号档放大时布局取标题的内在行高会自然变高（见 ApprovalFloatingLayer 的固定头），
     * 内容带用 weight(1f) 相应变矮，守恒关系照样成立。
     */
    const val HEADER_HEIGHT_DP =
        HEADER_TOP_PAD_DP + HEADER_TITLE_LINE_DP + HEADER_TITLE_TO_BARS_DP +
            HEADER_BARS_DP + BLOCK_GAP_DP

    /** 内容带底边距屏幕底的总预留 = 底距 + 审批块高 + 间隙；固定头不在其中，它从内容带里扣。 */
    fun contentBandReserveDp(blockHeightDp: Float): Float =
        BAR_BOTTOM_INSET_DP + blockHeightDp + CONTENT_TO_TRACK_GAP_DP

    /** 可滚动内容带的可用高度 = 屏高 − 预留 − 固定头；屏高不够时夹到 0，绝不返回负数去撑爆布局。 */
    fun contentBandHeightDp(screenHeightDp: Float, blockHeightDp: Float): Float =
        (screenHeightDp - contentBandReserveDp(blockHeightDp) - HEADER_HEIGHT_DP).coerceAtLeast(0f)

    /** 整叠高度：固定头 + 内容带 + 间隙 + 审批块 + 底距，恒等于屏高（守恒式本身，供单测断言）。 */
    fun stackHeightDp(screenHeightDp: Float, blockHeightDp: Float): Float =
        HEADER_HEIGHT_DP + contentBandHeightDp(screenHeightDp, blockHeightDp) +
            contentBandReserveDp(blockHeightDp)

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
 * 纵向分三段：固定头（等级标题 + 危险条）、可滚动内容带、底栏带（滑轨与「记住并放行」归
 * BottomInteractiveBlock）。固定头与内容带是兄弟节点，头**不在滚动子树里**，所以标题永远完整可见，
 * 与滚动位置无关；只有内容带会滚，滚动位置钉在底部（最坏情况始终可见），
 * 滚动容器同时把内容夹在内容带内，于是任何文字都不可能再画到滑轨上。
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

    // 内容带的滚动位置：三块正文比带子高时钉在底部 —— 「最坏情况」永远紧贴滑轨上方那 12dp 间隙可见，
    // 用户不必先猜"要不要滚一下才能看到最坏后果"。固定头不在这条滚动线上，钉底也裁不到标题。
    // 内容变短（maxValue 变化）时自动重新钉一次。
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
            // 分带容器：底边钉在滑轨上方 [ApprovalBandMetrics.CONTENT_TO_TRACK_GAP_DP]。
            // 里面只有两条兄弟带：固定头 + 可滚动内容带；头高从内容带里扣（见 ApprovalBandMetrics 的守恒式）。
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
                // 分带列：撑满文案带，但自己**不滚动** —— 滚动只发生在下面的内容带里。
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(ApprovalTextWidth)
                        // 塌缩只作用于文案：缩到工具胶囊尺度并下移，动画走完才退出审批态
                        .graphicsLayer {
                            scaleX = 1f - (1f - ApprovalCollapseScale) * collapse
                            scaleY = 1f - (1f - ApprovalCollapseScale) * collapse
                            translationY = ApprovalCollapseDrop.toPx() * collapse
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 固定头：等级标题 + 3 段危险条（标题定调，危险条按等级点亮几段）。
                    // 它是滚动容器的**兄弟节点**，不在任何可滚动子树里，也没有高度上限：
                    // "标题被滚动裁掉"因此在结构上不可能发生，与滚动位置、内容多少都无关。
                    FloatingApprovalText(slot = 0, visible = visible) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                // 上 8dp 圆屏留白（y<12dp 处弦长比标题窄），下 10dp 是到内容带的间距：
                                // 两段都算进 [ApprovalBandMetrics.HEADER_HEIGHT_DP]，内容带高度已扣掉
                                .padding(
                                    top = ApprovalBandMetrics.HEADER_TOP_PAD_DP.dp,
                                    bottom = ApprovalTextBlockGap
                                ),
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

                    // 内容带：固定头之外剩下的全部高度都归它（weight(1f)），超出的内容滚在这里。
                    // 滚动位置钉在底部：「最坏情况」紧贴滑轨上方那 12dp 间隙、无需手势；
                    // 命令原文往上滑才完整 —— 判断风险优先于看完整命令。
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // 放不下就滚动，不裁字：滚动容器把内容夹在内容带里，越过底边的东西也画不出来
                            .verticalScroll(contentScroll),
                        verticalArrangement = Arrangement.spacedBy(ApprovalTextBlockGap),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
