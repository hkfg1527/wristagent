package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear

/** 大卡片档的圆角与间距：比 [Wear.Radius.Card] 更宽，才撑得住卡里的 30sp 主数值。 */
internal object SettingsCardStyle {
    /** 卡片圆角：沿用卡片令牌，不新增第三档圆角。 */
    val Radius = Wear.Radius.Card

    /** 卡间距。 */
    val Gap = Wear.Space.Sm

    /** 环直径：Google Health 实测 100px @2x = 50dp。 */
    val RingSize = 50.dp

    /** 环轨道宽度。 */
    val RingTrack = 4.dp

    /** 环进度宽度。 */
    val RingProgress = 6.dp
}

/**
 * 谷歌大卡片：左上标签 → 大数值 → 可选药丸 chip，右侧一枚圆环指示器。
 *
 * 圆环表达"这一项内部的状态"（配置完成度、已调用比例、就绪度），
 * 卡片里因此不需要"已配置 / 未启用"这类整句文字。
 */
@Composable
internal fun SettingsHeroCard(
    label: String,
    value: String,
    accent: Color,
    fraction: Float,
    chip: String? = null,
    ringIcon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // 卡片宽度是固定内容轴：文字列的账在这里算一次，字号决策与布局共用同一个数
    val cardWidthDp = SettingsContentWidth.value
    Row(
        modifier = modifier
            .width(SettingsContentWidth)
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(SettingsCardStyle.Radius))
            .background(accent.copy(alpha = 0.10f).compositeOver(SettingsCardSurface))
            .padding(horizontal = Wear.Space.Lg, vertical = Wear.Space.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
        ) {
            BasicText(
                text = label,
                style = Wear.TextCaption.copy(
                    color = accent.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            BasicText(
                text = value,
                style = if (SettingsCardTypography.usesDisplayStyle(value, cardWidthDp)) {
                    Wear.TextDisplay
                } else {
                    Wear.TextDisplayTitle
                },
                maxLines = SettingsCardTypography.maxLinesFor(value, cardWidthDp),
                overflow = TextOverflow.Ellipsis
            )
            if (chip != null) {
                BasicText(
                    text = chip,
                    style = Wear.TextCaption.copy(
                        color = Wear.Palette.Bg,
                        fontWeight = FontWeight.Medium
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(accent.copy(alpha = 0.85f))
                        .padding(horizontal = Wear.Space.Sm, vertical = 2.dp),
                    maxLines = 1
                )
            }
        }
        Spacer(Modifier.width(Wear.Space.Md))
        SettingsRing(fraction = fraction, accent = accent, modifier = Modifier.size(SettingsCardStyle.RingSize)) {
            ringIcon?.invoke()
        }
    }
}

/**
 * 圆环指示器：轨道全程可见，进度从 12 点方向顺时针铺开。
 *
 * 只画两层圆弧，不用 third-party 进度组件 —— 表端每多一层 composition 都要算在滚动成本里。
 */
@Composable
private fun SettingsRing(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val clamped = fraction.coerceIn(0f, 1f)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(SettingsCardStyle.RingSize)) {
            val track = SettingsCardStyle.RingTrack.toPx()
            val progress = SettingsCardStyle.RingProgress.toPx()
            val inset = progress / 2f
            val arcSize = Size(size.width - progress, size.height - progress)
            drawArc(
                color = accent.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = track, cap = StrokeCap.Round)
            )
            if (clamped > 0.001f) {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * clamped,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = progress, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** 环内图标位：22dp 的槽，图或字都在这里落。 */
internal val SettingsRingIconSize: Dp = 22.dp

/** 环内用一个字形兜底：没有位图时也不留空环。 */
@Composable
internal fun SettingsRingGlyph(glyph: String, tint: Color) {
    BasicText(
        text = glyph,
        style = Wear.TextCaption.copy(color = tint, fontWeight = FontWeight.Medium),
        modifier = Modifier.size(SettingsRingIconSize),
        maxLines = 1
    )
}

/** 环内麦克风：胶囊 + 底弧 + 支架，与谷歌度量卡同一套 2dp 描边语言。 */
@Composable
internal fun SettingsMicGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(SettingsRingIconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.36f, h * 0.08f),
            size = Size(w * 0.28f, h * 0.46f),
            cornerRadius = CornerRadius(w * 0.14f),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.3f),
            size = Size(w * 0.56f, h * 0.38f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.5f, h * 0.68f),
            end = Offset(w * 0.5f, h * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** 环内终端提示符：一个折角箭头加一条底线，代表系统与提示词。 */
@Composable
internal fun SettingsTerminalGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(SettingsRingIconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val chevron = Path().apply {
            moveTo(w * 0.2f, h * 0.3f)
            lineTo(w * 0.42f, h * 0.5f)
            lineTo(w * 0.2f, h * 0.7f)
        }
        drawPath(chevron, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(
            color = tint,
            start = Offset(w * 0.52f, h * 0.7f),
            end = Offset(w * 0.82f, h * 0.7f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

/** 环内滑轨：三条带旋钮的横线，代表工具与参数。 */
@Composable
internal fun SettingsSlidersGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(SettingsRingIconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()
        val rows = listOf(Triple(0.26f, 0.64f, 0.7f), Triple(0.5f, 0.36f, 0.55f), Triple(0.74f, 0.58f, 0.62f))
        rows.forEach { row ->
            val y = h * row.first
            drawLine(
                color = tint,
                start = Offset(w * 0.1f, y),
                end = Offset(w * 0.9f, y),
                strokeWidth = stroke * 0.7f,
                cap = StrokeCap.Round
            )
            drawCircle(color = tint, radius = stroke * row.third, center = Offset(w * row.second, y))
        }
    }
}

/**
 * 大卡片档的卡片底色：Pixel Watch 2 上 Google Health 步数卡实测值 #2A313C。
 *
 * 刻意不进 Palette：调色板上限 14 个 token，这枚颜色只服务这一套风格，
 * 与 VoxelMatrixEffect 里的像素色同属"局部材质色"。
 */
internal val SettingsCardSurface = Color(0xFF2A313C)

/** 大卡片档下的普通行：不再画曲面，改成一张独立的圆角卡。 */
internal val SettingsCardRowShape = RoundedCornerShape(Wear.Radius.Card)
