package com.example.myapplication.presentation.ui.settings.stylekit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay

/**
 * 终端档的加载行：`> 正在思考…█`，方块光标按 [TerminalText.CURSOR_PERIOD_MS] 闪。
 *
 * 为什么单独做成组件、而不是直接写进聊天页：这样它能被单独渲染与断言（判据不依赖聊天页的整套状态），
 * 接线时只需要在聊天页里"终端档换成它"，不必再动一份时序逻辑。
 *
 * 相位只需要两态，所以用 600ms 的 `delay` 推进，不逐帧刷新 —— 手表上省电。
 */
@Composable
fun TerminalLoadingLine(
    label: String,
    modifier: Modifier = Modifier,
    periodMs: Long = TerminalText.CURSOR_PERIOD_MS
) {
    var phaseMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(periodMs) {
        val period = if (periodMs <= 0L) TerminalText.CURSOR_PERIOD_MS else periodMs
        while (true) {
            delay(period)
            phaseMs += period
        }
    }
    Text(
        text = TerminalText.loadingLine(label, phaseMs, periodMs),
        modifier = modifier,
        color = TerminalPalette.Foreground,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp,
        maxLines = 1
    )
}
