package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * hero 卡右侧的环形指示器 —— **官方 M3 组件**，不是自绘。
 *
 * 用户的原话："我要的指示器就是要谷歌还要那样原生的指示器"。所以这里用
 * `androidx.wear.compose.material3.CircularProgressIndicator`（Wear M3 1.7.1 自带），
 * 轨道色取 Pixel Watch 2 上 Google Health 实测的 #48556A，指示色取该卡自己的强调色。
 *
 * 两条硬约束：
 * 1. 只缩不放：进度恒在 0..1，官方组件也只在 0..1 之间画弧；
 * 2. 环是等比圆，不接受任何横向形变。
 *
 * 没有数据的卡也画（`progress = null` → 传 0f，只留轨道），因为用户要求"每张卡都得有"。
 */
@Composable
internal fun SettingsHeroRing(
    progress: Float?,
    accent: Color = Wear.CardModel,
    modifier: Modifier = Modifier
) {
    val target = (progress ?: 0f).coerceIn(0f, 1f)
    CircularProgressIndicator(
        progress = { target },
        modifier = modifier.size(RingDiameter),
        colors = ProgressIndicatorDefaults.colors(
            indicatorColor = accent,
            trackColor = RingTrackColor
        ),
        strokeWidth = RingStroke
    )
}

/** 直径 28dp：卡片只有 176dp 宽，环再大就会挤掉模型名。 */
private val RingDiameter = 28.dp

/** 描边 3dp。 */
private val RingStroke = 3.dp

/** 轨道色：Pixel Watch 2 上 Google Health 的实测值。 */
private val RingTrackColor = Color(0xFF48556A)
