package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.unit.sp
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.settings.motionSpec
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.abs
import kotlin.math.sqrt

/** 引导页的一页文案：标题与说明都必须单行放得下 176dp 正文列。 */
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

    /** 五页文案：标题 ≤8 字、说明 ≤20 字，且说明必须单行放得进 176dp 正文列。 */
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
 * 因此宽度必须和高度做交易：正文限宽 176dp，页脚压到 136dp。
 * 页脚曾经是「跳过 + 五个页码点 + 按钮」三件套同排；页码点搬到说明文字下方之后，
 * 页脚本体只剩左右两件，中段那 44dp（原点阵 36dp + 两道点距）留空，总宽一个像素没变。
 * "整块落在圆周内" = 矩形最远角点到圆心的距离不超过半径，判据见 [fitsInCircle]。
 */
internal object OnboardingGeometry {
    /** 圆屏直径（dp）：466px @2x。 */
    const val CircleDiameterDp = 233f

    /** 正文列宽度（dp）：标题与说明的最大宽度。 */
    const val ContentWidthDp = 176f

    /** 示意图形边长（dp）。 */
    const val IllustrationDp = 96f

    /** 标题行高（dp）：titleMedium 18sp/24sp。 */
    const val TitleLineDp = 24f

    /** 说明行高（dp）：bodyMedium 15sp/20sp。 */
    const val DescriptionLineDp = 20f

    val GapDp: Float = Wear.Space.Xs.value
    val TextGroupGapDp: Float = Wear.Space.Xs.value
    val FooterGapDp: Float = Wear.Space.Sm.value
    const val EntryShiftDp = 4f

    /** 96dp 画布中的公共外环半径，五页共用同一轮廓。 */
    const val FrameRadiusDp = 40f
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
    val BodyHeightDp: Float = IllustrationDp + TitleLineDp + DescriptionLineDp

    /** 内容总高：正文、点阵和页脚之间分别保留 4dp、8dp 的组间留白。 */
    val ContentHeightDp: Float =
        BodyHeightDp + TextGroupGapDp + DotsRowHeightDp + FooterGapDp + FooterHeightDp

    /** 内容盒上沿相对圆心的 y；96dp 方形与页脚最远角点均压在上限 116.5dp 以内。 */
    const val ContentTopDp = -106f

    /** Column 相对屏幕中心的竖直偏移。 */
    val ContentCenterOffsetDp: Float = ContentTopDp + ContentHeightDp / 2f

    val IllustrationTopDp: Float = ContentTopDp
    val TitleTopDp: Float = IllustrationTopDp + IllustrationDp
    val DescriptionTopDp: Float = TitleTopDp + TitleLineDp
    val DotsTopDp: Float = DescriptionTopDp + DescriptionLineDp + TextGroupGapDp
    val FooterTopDp: Float = DotsTopDp + DotsRowHeightDp + FooterGapDp

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
 * 背景沿用应用统一点阵；换页正文只走 [Wear.Motion.page] 的 4dp 上推与淡入淡出。
 * 图形保持静态，动画开关通过 [motionSpec] 统一归零。
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
            // 4dp 轻推只作用于正文；页码与页脚保持空间锚点，切换途中按钮始终可点
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OnboardingGeometry.BodyHeightDp.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        (
                            fadeIn(motionSpec(animationsEnabled, Wear.Motion.page())) +
                                slideInVertically(
                                    animationSpec = motionSpec(animationsEnabled, Wear.Motion.page()),
                                    initialOffsetY = { fullHeight ->
                                        (fullHeight * OnboardingGeometry.EntryShiftDp /
                                            OnboardingGeometry.BodyHeightDp).toInt()
                                    }
                                )
                            ) togetherWith fadeOut(motionSpec(animationsEnabled, Wear.Motion.page()))
                    },
                    label = "onboardingPage"
                ) { current ->
                    PageBody(current)
                }
            }

            Spacer(Modifier.height(OnboardingGeometry.TextGroupGapDp.dp))

            // 页码点是「读到第几页」的辅助信息，跟着正文走：贴在说明文字正下方，
            // 不再和跳过 / 继续挤在同一条页脚基线上
            PageDots(page)

            Spacer(Modifier.height(OnboardingGeometry.FooterGapDp.dp))

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
private fun PageBody(page: Int) {
    val copy = OnboardingPager.Pages[page]
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        GestureIllustration(page = page)
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
            style = Wear.TextBody.copy(
                fontSize = 15.sp,
                color = Wear.Palette.TextSecondary,
                textAlign = TextAlign.Center
            ),
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
 * 底部弦长预算没有变 —— 跳过 44 + 中段留白 44 + 按钮 48 = 136dp。
 * 点阵与页脚之间保留 8dp 独立间隔；页脚最远角点半径约 114.40dp。
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
 * 五页共用 96dp 画布、40dp 外环和四个方位点；图形保持静态，状态由弧段、轨迹与触点表达。
 */
@Composable
private fun GestureIllustration(page: Int) {
    val accent = Wear.Accent
    val accentSurface = Wear.AccentSurface
    Canvas(
        modifier = Modifier
            .size(OnboardingGeometry.IllustrationDp.dp)
            .semantics { contentDescription = OnboardingPager.Pages[page].title }
    ) {
        val unit = size.minDimension / OnboardingGeometry.IllustrationDp
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun px(value: Float): Float = value * unit
        val center = Offset(cx, cy)
        val frameRadius = px(OnboardingGeometry.FrameRadiusDp)

        // 外环统一五页轮廓；四点共用页码点阵的语言，方位点标记当前手势方向。
        drawCircle(
            color = Wear.Palette.Border,
            radius = frameRadius,
            center = center,
            style = Stroke(width = px(1.5f))
        )
        val anchors = listOf(
            Offset(cx, cy - frameRadius),
            Offset(cx + frameRadius, cy),
            Offset(cx, cy + frameRadius),
            Offset(cx - frameRadius, cy)
        )
        anchors.forEachIndexed { index, anchor ->
            drawCircle(
                color = if (index == page % anchors.size) accent else Wear.Palette.TextMuted,
                radius = px(if (index == page % anchors.size) 2.5f else 1.75f),
                center = anchor
            )
        }

        when (page) {
            // 持续按压：闭合外环 + 长进度弧表示保持，键内三段波形对应收音状态。
            0 -> {
                val orbRadius = px(22f)
                val progressRadius = px(33f)
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 285f,
                    useCenter = false,
                    topLeft = Offset(cx - progressRadius, cy - progressRadius),
                    size = Size(progressRadius * 2f, progressRadius * 2f),
                    style = Stroke(width = px(2.5f), cap = StrokeCap.Round)
                )
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = center)
                val heights = listOf(9f, 19f, 13f)
                heights.forEachIndexed { index, height ->
                    val x = cx + (index - 1) * px(7f)
                    val half = px(height) / 2f
                    drawLine(
                        color = Wear.Palette.Bg,
                        start = Offset(x, cy - half),
                        end = Offset(x, cy + half),
                        strokeWidth = px(3f),
                        cap = StrokeCap.Round
                    )
                }
            }

            // 单击：单段短弧与上方触点表示一次落下，键内保留同一收音波形。
            1 -> {
                val orbRadius = px(22f)
                val responseRadius = px(33f)
                drawArc(
                    color = accent,
                    startAngle = -55f,
                    sweepAngle = 110f,
                    useCenter = false,
                    topLeft = Offset(cx - responseRadius, cy - responseRadius),
                    size = Size(responseRadius * 2f, responseRadius * 2f),
                    style = Stroke(width = px(2.5f), cap = StrokeCap.Round)
                )
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = center)
                drawLine(
                    color = Wear.Palette.Bg,
                    start = Offset(cx, cy - px(9f)),
                    end = Offset(cx, cy + px(9f)),
                    strokeWidth = px(3f),
                    cap = StrokeCap.Round
                )
                drawCircle(color = Wear.Palette.Bg, radius = px(1.5f), center = Offset(cx - px(7f), cy))
                drawCircle(color = Wear.Palette.Bg, radius = px(1.5f), center = Offset(cx + px(7f), cy))
                drawCircle(color = accent, radius = px(4f), center = Offset(cx, cy - px(23f)))
            }

            // 上滑：主键留在下端，三枚轨迹点收束到向上的箭头，运动方向不依赖动画。
            2 -> {
                val orbCenter = Offset(cx, cy + px(18f))
                drawLine(
                    color = Wear.Palette.Border,
                    start = Offset(cx, cy - px(28f)),
                    end = Offset(cx, orbCenter.y - px(15f)),
                    strokeWidth = px(2f),
                    cap = StrokeCap.Round
                )
                listOf(8f, 15f, 22f).forEach { y ->
                    drawCircle(color = Wear.Palette.TextMuted, radius = px(1.75f), center = Offset(cx, cy - px(y)))
                }
                drawCircle(color = Wear.Palette.Orb, radius = px(15f), center = orbCenter)
                val tip = Offset(cx, cy - px(31f))
                drawLine(
                    color = accent,
                    start = Offset(cx - px(8f), tip.y + px(8f)),
                    end = tip,
                    strokeWidth = px(3f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = accent,
                    start = Offset(cx + px(8f), tip.y + px(8f)),
                    end = tip,
                    strokeWidth = px(3f),
                    cap = StrokeCap.Round
                )
            }

            // 横滑：主键向左右两条等长支路延展，左键刻键盘、右键刻会话层叠，和真实两翼一一对应。
            3 -> {
                val orbRadius = px(14f)
                val wingRadius = px(11f)
                val leftWing = Offset(cx - px(32f), cy)
                val rightWing = Offset(cx + px(32f), cy)
                drawLine(
                    color = Wear.Palette.Border,
                    start = Offset(leftWing.x + wingRadius, cy),
                    end = Offset(rightWing.x - wingRadius, cy),
                    strokeWidth = px(1.5f)
                )
                drawCircle(color = Wear.Palette.SurfaceAlt, radius = wingRadius, center = leftWing)
                drawCircle(
                    color = Wear.Palette.Border,
                    radius = wingRadius,
                    center = leftWing,
                    style = Stroke(width = px(1.5f))
                )
                listOf(-4f, 0f, 4f).forEach { row ->
                    listOf(-4f, 0f, 4f).forEach { column ->
                        drawCircle(
                            color = Wear.Palette.TextSecondary,
                            radius = px(1f),
                            center = Offset(leftWing.x + px(column), leftWing.y + px(row))
                        )
                    }
                }
                drawCircle(color = accentSurface, radius = wingRadius, center = rightWing)
                drawCircle(
                    color = accent,
                    radius = wingRadius,
                    center = rightWing,
                    style = Stroke(width = px(1.5f))
                )
                listOf(-4f, 0f, 4f).forEach { y ->
                    drawLine(
                        color = accent,
                        start = Offset(rightWing.x - px(5f), rightWing.y + px(y)),
                        end = Offset(rightWing.x + px(5f), rightWing.y + px(y)),
                        strokeWidth = px(1.5f),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color = Wear.Palette.Orb, radius = orbRadius, center = center)
            }

            // 审批：滑轨保持单色语言，左侧强调面表示已扫过的放行区，拇指刻左向箭头。
            else -> {
                val trackWidth = px(72f)
                val trackHeight = px(24f)
                val thumbRadius = px(10f)
                val left = cx - trackWidth / 2f
                val top = cy - trackHeight / 2f
                val right = left + trackWidth
                val capsule = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(left, top, right, top + trackHeight),
                            cornerRadius = CornerRadius(px(12f), px(12f))
                        )
                    )
                }
                val travel = trackWidth - px(6f) - thumbRadius * 2f
                val thumbCenterX = right - px(3f) - thumbRadius - travel * 0.85f
                val sweptEdge = thumbCenterX + thumbRadius
                drawPath(path = capsule, color = Wear.Palette.SurfaceAlt)
                clipPath(capsule) {
                    drawRect(
                        color = accentSurface,
                        topLeft = Offset(left, top),
                        size = Size((sweptEdge - left).coerceAtLeast(0f), trackHeight)
                    )
                }
                drawLine(
                    color = accent,
                    start = Offset(sweptEdge, top + px(4f)),
                    end = Offset(sweptEdge, top + trackHeight - px(4f)),
                    strokeWidth = px(1.5f),
                    cap = StrokeCap.Round
                )
                val thumbCenter = Offset(thumbCenterX, cy)
                drawCircle(color = Wear.Palette.Orb, radius = thumbRadius, center = thumbCenter)
                drawCircle(
                    color = Wear.Palette.TextPrimary,
                    radius = thumbRadius,
                    center = thumbCenter,
                    style = Stroke(width = px(1.5f))
                )
                val tip = Offset(thumbCenterX - px(4f), cy)
                drawLine(
                    color = Wear.Palette.Bg,
                    start = Offset(tip.x + px(4f), cy - px(4f)),
                    end = tip,
                    strokeWidth = px(1.75f),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = Wear.Palette.Bg,
                    start = Offset(tip.x + px(4f), cy + px(4f)),
                    end = tip,
                    strokeWidth = px(1.75f),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
