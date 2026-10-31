package com.example.myapplication.presentation.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch

/**
 * 发送消息与回复卡片弹出物理动效容器 (微晶弹簧微动效)
 */
@Composable
internal fun AnimatedMessageCard(
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale = remember(key) { Animatable(0.94f) }
    val alpha = remember(key) { Animatable(0f) }

    LaunchedEffect(key) {
        launch {
            scale.animateTo(1f, spring(0.8f, 320f))
        }
        launch {
            alpha.animateTo(1f, tween(160))
        }
    }

    // 入场只做"轻微放大 + 淡入"，不做纵向位移：
    // 卡片自己沿 Y 移动的同时镜头也在滚，两层位移叠加就是肉眼看到的"卡来卡去"。
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            }
    ) {
        content()
    }
}
