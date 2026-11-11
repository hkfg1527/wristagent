package com.example.myapplication.presentation.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.presentation.ui.components.WearCard
import com.example.myapplication.presentation.ui.theme.Wear

/**
 * 语音倾听悬浮层 (圆屏自适应，居中动态声波波浪)
 */
@Composable
fun VoiceListeningOverlay(
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voicePulse")
    val waveScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "waveScale"
    )
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ringAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg.copy(alpha = 0.94f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Md)
        ) {
            val accent = Wear.Accent
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // 外层呼吸光环
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = accent.copy(alpha = ringAlpha),
                        radius = (size.minDimension / 2f) * waveScale,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 核心麦克风圆球
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .shadow(elevation = 6.dp, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    MinimalistSoundwaveIcon(
                        tint = Wear.Palette.Bg,
                        isRecording = true,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            BasicText(
                text = stringResource(R.string.ui_listening),
                style = Wear.TextBody.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )

            BasicText(
                text = stringResource(R.string.ui_listening_hint),
                style = Wear.TextCaption,
                maxLines = 1
            )

            // 取消目标：整层可点 + 卡片本体 ≥ 44dp
            WearCard(
                onClick = onCancel,
                modifier = Modifier.padding(top = Wear.Space.Xs)
            ) {
                BasicText(
                    text = stringResource(R.string.ui_common_cancel),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = Wear.TextCaption.copy(color = Wear.Palette.Danger),
                    maxLines = 1
                )
            }
        }
    }
}
