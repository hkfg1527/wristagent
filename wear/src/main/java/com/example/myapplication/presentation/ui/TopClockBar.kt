package com.example.myapplication.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedAlignment
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.material3.curvedText
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 弧心角度：0° 为 3 点钟方向、角度顺时针递增，270° 即屏幕正上方。 */
private const val CLOCK_ANCHOR_DEGREES = 270f

/** 时钟可用弧长上限：正上方 ±60°（合计 120°），保证文字绝不铺到圆屏两侧被裁。 */
private const val CLOCK_MAX_SWEEP_DEGREES = 120f

/** 时钟字号相对 TextDisplay(18sp) 的缩放：0.84 → 约 15sp。 */
private const val ClockFontScale = 0.84f

/**
 * 顶部时间：官方 Wear 曲面文字 (CurvedLayout + M3 curvedText)，沿屏幕圆形外缘贴在正上方。
 * 字号/字重/颜色全部取自设计令牌，不做任何硬编码。
 */
/**
 * 自给自足版本：时间状态由时钟自己持有。
 *
 * 之前时间状态放在 ChatScreen 里，每秒变化会让**整棵 ChatScreen 重组**（实测待机时那些 20ms+ 的重帧
 * 几乎都落在秒跳变的瞬间）。把状态下沉到这一个 composable 后，每秒只有时钟自己重组，
 * 列表/底栏/点阵都不会被牵连。
 */
@Composable
fun TopClockBar(modifier: Modifier = Modifier) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var timeText by remember { mutableStateOf(formatter.format(Date())) }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = formatter.format(Date())
            // 对齐到下一个整秒：固定 1000ms 会与系统秒边界漂移，偶发跳秒
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    TopClockBar(timeText = timeText, modifier = modifier)
}

@Composable
fun TopClockBar(
    timeText: String,
    modifier: Modifier = Modifier
) {
    CurvedLayout(
        modifier = modifier,
        anchor = CLOCK_ANCHOR_DEGREES,
        radialAlignment = CurvedAlignment.Radial.Outer
    ) {
        curvedText(
            text = timeText,
            modifier = CurvedModifier.padding(
                outer = Wear.Space.Sm,
                inner = 0.dp,
                before = 0.dp,
                after = 0.dp
            ),
            maxSweepAngle = CLOCK_MAX_SWEEP_DEGREES,
            // 默认角向（Normal）即为贴合顶缘的 ∩ 弧：两端下垂、中间最高，且字形正立
            color = Wear.Palette.TextPrimary,
            // 用户要求时间小一点：18sp 在 192dp 真表上偏抢眼，缩到约 15sp（仍 ≥12sp 下限）
            fontSize = Wear.TextDisplay.fontSize * ClockFontScale,
            fontWeight = Wear.TextDisplay.fontWeight
        )
    }
}
