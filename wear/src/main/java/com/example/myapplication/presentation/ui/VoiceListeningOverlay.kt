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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
            .background(Color.Black.copy(alpha = 0.94f))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(72.dp),
                contentAlignment = Alignment.Center
            ) {
                // 外层呼吸光环
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color(0xFF38BDF8).copy(alpha = ringAlpha),
                        radius = (size.minDimension / 2f) * waveScale,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                }

                // 核心麦克风圆球
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF38BDF8))
                        .shadow(elevation = 6.dp, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    MinimalistSoundwaveIcon(
                        tint = Color(0xFF0F172A),
                        isRecording = true,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                text = "正在倾听...",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Text(
                text = "请对准手表说话",
                fontSize = 9.sp,
                color = Color(0xFF94A3B8)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E2430))
                    .border(0.6.dp, Color(0xFF333D4F), RoundedCornerShape(12.dp))
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "取消",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}
