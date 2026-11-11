package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.settings.motionSpec
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.abs
import kotlin.math.sqrt

/** 引导页的一页文案：标题与说明都必须单行放得下 148dp 正文列。 */
internal data class OnboardingPage(
    val title: String,
    val description: String
)

/**
 * 引导页的纯逻辑：第几页、按钮文案、页码点状态。
 *
 * 五页各对应一条**真实存在**的手势（不是想象出来的能力）：
 * 1. 长按主键 340ms 起录、松手发送 —— BottomInteractiveBlock 的长按计时器
 * 2. 轻触主键：恒为语音起录（文字输入改由左翼一步打开，主键不再有第二种含义）
 * 3. 主键上滑超过 38dp 进设置
 * 4. 主键横滑超过 22dp 弹开左右两翼（左翼直接打开文字输入框，右翼打开会话抽屉）
 * 5. 审批滑轨：单击拒绝，左滑到 85% 才放行
 */
internal object OnboardingPager {
    /** 页数：与页码点个数恒等，不允许各自漂移。 */
    const val PageCount = 5

    /** 五页文案：标题 ≤8 字、说明 ≤20 字，且说明必须单行放得进 148dp 正文列。 */
    val Pages: List<OnboardingPage> = listOf(
        OnboardingPage(
            title = "按住说话",
            description = "长按 0.34 秒，松手发送"
        ),
        OnboardingPage(
            title = "轻触主键",
            description = "单击恒为语音起录"
        ),
        OnboardingPage(
            title = "上滑设置",
            description = "上滑越过 38dp 进设置"
        ),
        OnboardingPage(
            title = "横滑两翼",
            description = "左翼打字，右翼开会话"
        ),
        OnboardingPage(
            title = "滑动放行",
            description = "左滑放行，点按拒绝"
        )
    )

    /** 跳过键文案。 */
    const val SkipLabel: String = "跳过"

    /** 翻页：末页不再前进。 */
    fun next(current: Int): Int = (current + 1).coerceIn(0, PageCount - 1)

    /** 是否已经是最后一页。 */
    fun isLast(index: Int): Boolean = index >= PageCount - 1

    /** 跳过入口是否出现：最后一页的按钮已经是"开始"，不再并排两个出口。 */
    fun canSkip(index: Int): Boolean = index in 0 until PageCount && !isLast(index)

    /** 页脚按钮文案：末页是"开始"，其余是"继续"。 */
    fun actionLabel(index: Int): String = if (isLast(index)) "开始" else "继续"

    /** 页码点是否点亮：只有当前页那一个点亮。 */
    fun isDotActive(dot: Int, page: Int): Boolean = dot == page
}

/**
 * 引导页几何：所有尺寸在这里算，UI 只引用结果。
 *
 * 圆屏 466px @2x = 233dp，半径 116.5dp。内容宽 184dp 时竖直方向只剩 ±71.5dp 可用，
 * 因此宽度必须和高度做交易：正文收窄到 148dp 换取上下空间，页脚压到 136dp。
 * 页脚曾经是「跳过 + 五个页码点 + 按钮」三件套同排；页码点搬到说明文字下方之后，
 * 页脚本体只剩左右两件，中段那 44dp（原点阵 36dp + 两道点距）留空，总宽一个像素没变。
 * "整块落在圆周内" = 矩形最远角点到圆心的距离不超过半径，判据见 [fitsInCircle]。
 */
internal object OnboardingGeometry {
    /** 圆屏直径（dp）：466px @2x。 */
    const val CircleDiameterDp = 233f

    /** 正文列宽度（dp）：标题与说明的最大宽度。 */
    const val ContentWidthDp = 148f

    /** 示意图形边长（dp）。 */
    const val IllustrationDp = 96f

    /** 标题行高（dp）：titleMedium 18sp/24sp。 */
    const val TitleLineDp = 24f

    /** 说明行高（dp）：bodySmall 12sp/16sp。 */
    const val DescriptionLineDp = 16f

    val GapDp: Float = Wear.Space.Xs.value
    val FooterHeightDp: Float = Wear.Size.Touch.value
    val SkipWidthDp: Float = Wear.Size.Touch.value
    val DotSizeDp: Float = Wear.Space.Xs.value
    const val ActionWidthDp = 48f

    /** 五个点 + 四个点距。 */
    val DotsWidthDp: Float =
        DotSizeDp * OnboardingPager.PageCount + GapDp * (OnboardingPager.PageCount - 1).coerceAtLeast(0)

    /** 页码点那一行的高度：一排点，没有别的元素。 */
    val DotsRowHeightDp: Float = DotSizeDp

    /** 页脚中段留白：页码点搬走之后空出来的那一档，宽度仍按原来「点阵 + 两道点距」的预算。 */
    val FooterMiddleGapDp: Float = GapDp + DotsWidthDp + GapDp

    /** 页脚总宽：跳过 + 中段留白 + 按钮 = 136dp，与三件套时代完全一致。 */
    val FooterWidthDp: Float = SkipWidthDp + FooterMiddleGapDp + ActionWidthDp

    /** 示意图 + 标题 + 说明 的总高，也就是翻页动画盒子的高度。 */
    val BodyHeightDp: Float = IllustrationDp + GapDp + TitleLineDp + GapDp + DescriptionLineDp

    /** 内容总高：正文 + 点距 + 页码点行 + 页脚（页码点行与页脚之间不再隔空档）。 */
    val ContentHeightDp: Float = BodyHeightDp + GapDp + DotsRowHeightDp + FooterHeightDp

    /**
     * 内容盒上沿相对圆心的 y（dp）。
     *
     * 上沿与下沿两个最远角点必须同时落在半径 116.5dp 内，解得可用窗口 [-106.15, -101.4]，
     * 取中点 -104：96dp 示意图的上沿角点 114.5dp、136dp 页脚的下沿角点 114.4dp，各留约 2dp。
     */
    const val ContentTopDp = -104f

    /** Column 相对屏幕中心的竖直偏移。 */
    val ContentCenterOffsetDp: Float = ContentTopDp + ContentHeightDp / 2f

    val IllustrationTopDp: Float = ContentTopDp
    val TitleTopDp: Float = IllustrationTopDp + IllustrationDp + GapDp
    val DescriptionTopDp: Float = TitleTopDp + TitleLineDp + GapDp
    val DotsTopDp: Float = DescriptionTopDp + DescriptionLineDp + GapDp
    val FooterTopDp: Float = DotsTopDp + DotsRowHeightDp

    /** 示意图形循环动效周期（ms）。 */
    const val LoopDurationMs = 1700

    /** 关闭动效时示意图形停留的静帧相位。 */
    const val StaticPhase = 0.55f

    /** 矩形最远角点到圆心的距离。 */
    fun farthestCornerRadiusDp(widthDp: Float, heightDp: Float, topDp: Float): Float {
        val halfWidth = widthDp / 2f
        val farY = maxOf(abs(topDp), abs(topDp + heightDp))
        return sqrt(halfWidth * halfWidth + farY * farY)
    }

    /** 整块是否落在圆内。 */
    fun fitsInCircle(
        widthDp: Float,
        heightDp: Float,
        topDp: Float,
        circleDiameterDp: Float = CircleDiameterDp
    ): Boolean = farthestCornerRadiusDp(widthDp, heightDp, topDp) <= circleDiameterDp / 2f
}

/**
 * 引导页：五页，每页一条真实手势。
 *
 * 背景沿用应用统一点阵；翻页只走 [Wear.Motion.page] 的淡入淡出；
 * 示意图形有克制的循环动效，但受 [LocalAnimationsEnabled] 总开关管辖（关掉即停在静帧）。
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    preferences: AgentPreferences,
    onFinish: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val animationsEnabled = LocalAnimationsEnabled.current

    // 两条出口共用同一个收尾：先写完成标记再交给上层，保证"跳过"与"开始"都不会再回到引导页
    val finishOnboarding: () -> Unit = {
        preferences.hasCompletedOnboarding = true
        onFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
    ) {
        UnifiedMatrixCanvas(
            modifier = Modifier.fillMaxSize(),
            thinkingIntensity = 0.15f,
            isRecordingVoice = false,
            gridSpacingDp = preferences.matrixSpacingDp
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = OnboardingGeometry.ContentCenterOffsetDp.dp)
                .width(OnboardingGeometry.ContentWidthDp.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 翻页只淡入淡出正文块：页脚不跟着闪，按钮在切换途中始终可点
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OnboardingGeometry.BodyHeightDp.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        fadeIn(motionSpec(animationsEnabled, Wear.Motion.page())) togetherWith
                            fadeOut(motionSpec(animationsEnabled, Wear.Motion.page()))
                    },
                    label = "onboardingPage"
                ) { current ->
                    PageBody(current, animationsEnabled)
                }
            }

            Spacer(Modifier.height(OnboardingGeometry.GapDp.dp))

            // 页码点是「读到第几页」的辅助信息，跟着正文走：贴在说明文字正下方，
            // 不再和跳过 / 继续挤在同一条页脚基线上
            PageDots(page)

            OnboardingFooter(
                page = page,
                onSkip = finishOnboarding,
                onAction = {
                    if (OnboardingPager.isLast(page)) {
                        finishOnboarding()
                    } else {
                        page = OnboardingPager.next(page)
                    }
                }
            )
        }
    }
}

/** 单页正文：示意图形 + 标题 + 说明。 */
@Composable
private fun PageBody(page: Int, animationsEnabled: Boolean) {
    val copy = OnboardingPager.Pages[page]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OnboardingGeometry.GapDp.dp)
    ) {
        GestureIllustration(page = page, animationsEnabled = animationsEnabled)
        // 宽高都钉死成几何表里的数值：排版实测高矮与"落在圆周内"的判据是同一组数字
        BasicText(
            text = copy.title,
            modifier = Modifier
                .width(OnboardingGeometry.ContentWidthDp.dp)
                .height(OnboardingGeometry.TitleLineDp.dp),
            style = Wear.TextTitle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        BasicText(
            text = copy.description,
            modifier = Modifier
                .width(OnboardingGeometry.ContentWidthDp.dp)
                .height(OnboardingGeometry.DescriptionLineDp.dp),
            style = Wear.TextCaption.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * 页脚：[跳过] …… [继续/开始]，左右分置、中段留空。
 *
 * 页码点已经搬到说明文字下方（见 [PageDots]）：三类完全不同的元素挤在同一条基线上时，
 * 第 5 页去掉「跳过」之后页码点会紧贴按钮，看起来像按钮属于点阵的一部分。
 * 底部弦长预算没有变 —— 跳过 44 + 中段留白 44 + 按钮 48 = 136dp，仍塞进 143dp 弦长，
 * 页脚盒与三件套时代同宽同位，最远角点半径 114.4dp。
 * 跳过槽位在最后一页留空占位，保证按钮不因文案切换而左右跳动。
 */
@Composable
private fun OnboardingFooter(page: Int, onSkip: () -> Unit, onAction: () -> Unit) {
    Row(
        modifier = Modifier
            .width(OnboardingGeometry.FooterWidthDp.dp)
            .height(OnboardingGeometry.FooterHeightDp.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        if (OnboardingPager.canSkip(page)) {
            val skipLabel = OnboardingPager.SkipLabel
            Box(
                modifier = Modifier
                    .width(OnboardingGeometry.SkipWidthDp.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .clickable(onClickLabel = skipLabel, role = Role.Button, onClick = onSkip)
                    .semantics { contentDescription = skipLabel },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = skipLabel,
                    style = Wear.TextCaption,
                    maxLines = 1
                )
            }
        } else {
            Spacer(Modifier.width(OnboardingGeometry.SkipWidthDp.dp))
        }

        val actionLabel = OnboardingPager.actionLabel(page)
        Box(
            modifier = Modifier
                .width(OnboardingGeometry.ActionWidthDp.dp)
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable(onClickLabel = actionLabel, role = Role.Button, onClick = onAction)
                .semantics { contentDescription = actionLabel },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(OnboardingGeometry.ActionWidthDp.dp)
                    .height(Wear.Size.ChipHeight)
                    .clip(RoundedCornerShape(Wear.Radius.Pill))
                    // 与主键同一套语言：Orb 冷银白实底配 Bg 深色字（黑底 14.6:1）
                    .background(Wear.Palette.Orb),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = actionLabel,
                    style = Wear.TextCaption.copy(color = Wear.Palette.Bg),
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 五个页码点：只有当前页点亮，未点亮的一档仍取最暗可读色。
 *
 * 位置在说明文字正下方、页脚之上（几何见 [OnboardingGeometry.DotsTopDp]）：
 * 这一行只占一排点的宽高（36dp × 4dp），不参与页脚的两端分置。
 * 整行带「第 N 页，共 5 页」的无障碍描述，换页时读屏仍能报出位置。
 */
@Composable
private fun PageDots(page: Int) {
    val accent = Wear.Accent
    Row(
        modifier = Modifier
            .width(OnboardingGeometry.DotsWidthDp.dp)
            .height(OnboardingGeometry.DotsRowHeightDp.dp)
            .semantics {
                contentDescription = "第 ${page + 1} 页，共 ${OnboardingPager.PageCount} 页"
            },
        horizontalArrangement = Arrangement.spacedBy(OnboardingGeometry.GapDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(OnboardingPager.PageCount) { index ->
            Box(
                modifier = Modifier
                    .size(OnboardingGeometry.DotSizeDp.dp)
                    .clip(CircleShape)
                    .background(
                        if (OnboardingPager.isDotActive(index, page)) {
                            accent
                        } else {
                            Wear.Palette.TextMuted
                        }
                    )
            )
        }
    }
}

/**
 * 手势示意图：五个手势各画一张，全部用 Canvas 画，不引图片资源、不用 emoji。
 *
 * 循环动效只改绘制相位、不动布局；[LocalAnimationsEnabled] 关掉时相位钉在 [OnboardingGeometry.StaticPhase]，
 * 连无限动画都不创建。
 */
@Composable
private fun GestureIllustration(page: Int, animationsEnabled: Boolean) {
    val phase: Float = if (animationsEnabled) {
        val transition = rememberInfiniteTransition(label = "onboardingGestureLoop")
        val animated: Float by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(OnboardingGeometry.LoopDurationMs, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "gesturePhase"
        )
        animated
    } else {
        OnboardingGeometry.StaticPhase
    }

    val accent = Wear.Accent
    val accentSurface = Wear.AccentSurface
    Canvas(modifier = Modifier.size(OnboardingGeometry.IllustrationDp.dp)) {
        // 96dp 设计稿 → 像素的比例：所有长度都按设计稿数值写
        val unit = size.minDimension / 96f
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun px(value: Float): Float = value * unit

        when (page) {
            // 1. 长按主键：外圈进度弧扫一圈 = 按住不放满 340ms，主键内是起伏的收音波形
            0 -> {
                val orbRadius = px(22f)
                drawCircle(
                    color = accent.copy(alpha = 0.45f),
                    radius = orbRadius + px(8f),
                    center = Offset(cx, cy),
                    style = Stroke(width = px(2f), cap = StrokeCap.Round)
                )
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * phase,
                    useCenter = false,
                    topLeft = Offset(cx - orbRadius - px(8f), cy - orbRadius - px(8f)),
                    size = Size((orbRadius + px(8f)) * 2f, (orbRadius + px(8f)) * 2f),
                    style = Stroke(width = px(2f), cap = StrokeCap.Round)
                )
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = Offset(cx, cy))
                val barGap = px(7f)
                val heights = floatArrayOf(
                    orbRadius * (0.45f + 0.85f * phase),
                    orbRadius * (1.15f - 0.5f * phase),
                    orbRadius * (0.45f + 0.85f * (1f - phase))
                )
                for (index in 0..2) {
                    val barX = cx + (index - 1) * barGap
                    val half = heights[index].coerceAtMost(orbRadius * 0.8f) / 2f
                    drawLine(
                        color = Wear.Palette.Bg,
                        start = Offset(barX, cy - half),
                        end = Offset(barX, cy + half),
                        strokeWidth = px(3f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. 轻触主键：两道扩散涟漪 + 主键里的输入光标 + 落在键上缘的触点
            1 -> {
                val orbRadius = px(22f)
                repeat(2) { index ->
                    val ripple = (phase + index * 0.5f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - ripple) * 0.55f),
                        radius = orbRadius + px(6f) + px(16f) * ripple,
                        center = Offset(cx, cy),
                        style = Stroke(width = px(1.5f))
                    )
                }
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = Offset(cx, cy))
                drawLine(
                    color = Wear.Palette.Bg,
                    start = Offset(cx, cy - px(9f)),
                    end = Offset(cx, cy + px(9f)),
                    strokeWidth = px(3f),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = accent,
                    radius = px(4f),
                    center = Offset(cx + orbRadius * 0.62f, cy - orbRadius * 0.66f)
                )
            }

            // 3. 上滑主键：主键压在轨道底端，箭头随相位向上跑
            2 -> {
                val orbRadius = px(15f)
                val orbCenterY = cy + px(24f)
                drawLine(
                    color = Wear.Palette.Border,
                    start = Offset(cx, orbCenterY - orbRadius),
                    end = Offset(cx, cy - px(32f)),
                    strokeWidth = px(2f),
                    cap = StrokeCap.Round
                )
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = Offset(cx, orbCenterY))
                val arrowY = orbCenterY - orbRadius - px(4f) - px(30f) * phase
                val arrowHalfWidth = px(9f)
                drawLine(
                    color = accent,
                    start = Offset(cx - arrowHalfWidth, arrowY + px(7f)),
                    end = Offset(cx, arrowY),
                    strokeWidth = px(3f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(cx + arrowHalfWidth, arrowY + px(7f)),
                    end = Offset(cx, arrowY),
                    strokeWidth = px(3f),
                    cap = StrokeCap.Round
                )
            }

            // 4. 横滑主键：左右两翼从主键两侧弹出去
            3 -> {
                val orbRadius = px(14f)
                val wingRadius = px(11f)
                val spread = px(20f) + px(12f) * phase
                drawLine(
                    color = Wear.Palette.Border,
                    start = Offset(cx - orbRadius, cy),
                    end = Offset(cx - spread + wingRadius, cy),
                    strokeWidth = px(1.5f)
                )
                drawLine(
                    color = Wear.Palette.Border,
                    start = Offset(cx + orbRadius, cy),
                    end = Offset(cx + spread - wingRadius, cy),
                    strokeWidth = px(1.5f)
                )
                // 左翼：直接打开文字输入框，中性色
                drawCircle(color = Wear.Palette.SurfaceAlt, radius = wingRadius, center = Offset(cx - spread, cy))
                drawCircle(
                    color = Wear.Palette.Border,
                    radius = wingRadius,
                    center = Offset(cx - spread, cy),
                    style = Stroke(width = px(1.5f))
                )
                // 右翼：会话抽屉，强调色
                drawCircle(color = accentSurface, radius = wingRadius, center = Offset(cx + spread, cy))
                drawCircle(
                    color = accent,
                    radius = wingRadius,
                    center = Offset(cx + spread, cy),
                    style = Stroke(width = px(1.5f))
                )
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = Offset(cx, cy))
            }

            // 5. 审批滑轨：拇指自右端向左，未滑过区是等级色、已滑过区是放行绿
            else -> {
                val trackWidth = px(76f)
                val trackHeight = px(26f)
                val thumbRadius = px(10f)
                val left = cx - trackWidth / 2f
                val top = cy - trackHeight / 2f
                val right = left + trackWidth
                val corner = px(13f)
                val capsule = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, right, top + trackHeight),
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    )
                }
                val travel = trackWidth - px(6f) - thumbRadius * 2f
                val thumbCenterX = right - px(3f) - thumbRadius - travel * phase
                val sweptEdge = (thumbCenterX + thumbRadius).coerceIn(left, right)

                drawPath(path = capsule, color = Wear.Palette.Orb)
                clipPath(capsule) {
                    drawRect(
                        color = Wear.Palette.Danger.copy(alpha = 0.34f),
                        topLeft = Offset(sweptEdge, top),
                        size = Size((right - sweptEdge).coerceAtLeast(0f), trackHeight)
                    )
                    drawRect(
                        color = Wear.Palette.Success.copy(alpha = 0.72f),
                        topLeft = Offset(left, top),
                        size = Size((sweptEdge - left).coerceAtLeast(0f), trackHeight)
                    )
                }
                drawCircle(
                    color = Wear.Palette.Orb,
                    radius = thumbRadius,
                    center = Offset(thumbCenterX, cy)
                )
                drawCircle(
                    color = Wear.Palette.TextPrimary,
                    radius = thumbRadius,
                    center = Offset(thumbCenterX, cy),
                    style = Stroke(width = px(1.5f))
                )
            }
        }
    }
}
