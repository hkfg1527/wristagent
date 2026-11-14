package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * hero 卡右侧的环形指示器：**每张卡都有一个**，用来压住右边那块空白。
 *
 * 用户的原话是"我只是推荐每个卡片有一个来弥补重心，就算没有，你也得在旁边塞点什么东西"。
 * 所以这里的行为是：**永远画轨道**，有数据才画进度弧 —— 没有数据的卡也不会秃一块。
 *
 * 两条硬约束都体现在实现里：
 * 1. 只缩不放：进度恒在 0..1，弧长不会超过整圈；
 * 2. 圆环是**等比圆**，不随卡片缩放变形（画在卡片自己的图层里，外层缩放是等比的）。
 */
@Composable
internal fun SettingsHeroRing(
    progress: Float?,
    accent: Color = Wear.CardModel,
    modifier: Modifier = Modifier
) {
    // 取到局部再进绘制 lambda：@Composable getter 不能在绘制的非组合上下文里调用
    val arcColor = accent
    val diameter = RingDiameter
    val stroke = RingStroke
    Canvas(modifier = modifier.size(diameter)) {
        val inset = stroke.toPx() / 2f
        val arcSize = Size(size.width - stroke.toPx(), size.height - stroke.toPx())
        val topLeft = Offset(inset, inset)
        drawArc(
            color = RingTrackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = stroke.toPx())
        )
        val clamped = progress?.coerceIn(0f, 1f)
        if (clamped != null && clamped > 0f) {
            drawArc(
                color = arcColor,
                startAngle = -90f,
                sweepAngle = 360f * clamped,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/** 直径 28dp：比 Google Health 实测的 50dp 小一档，因为卡片只有 176dp 宽，环再大就挤掉模型名。 */
private val RingDiameter = 28.dp

/** 描边 3dp。 */
private val RingStroke = 3.dp

/** 轨道色取自实测的 #48556A。 */
private val RingTrackColor = androidx.compose.ui.graphics.Color(0xFF48556A)
