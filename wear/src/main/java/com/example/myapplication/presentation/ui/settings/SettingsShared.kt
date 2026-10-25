package com.example.myapplication.presentation.ui.settings

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// 调色板与材质规范
val SettingsCardBg = Color(0xFF161922)
val SettingsCardBorder = Color(0xFF2B3446)
val SettingsSubBg = Color(0xFF0F1219)
val SettingsSubBorder = Color(0xFF333F54)
val AccentCyan = Color(0xFF38BDF8)
val AccentCyanDark = Color(0xFF0284C7)
val TextPrimaryColor = Color(0xFFF8FAFC)
val TextSecondaryColor = Color(0xFF94A3B8)

fun Context.vibrateTick() {
    try {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(15L)
        }
    } catch (_: Exception) {}
}

/**
 * 设置子弹窗类型
 */
enum class SettingsSubDialog {
    MODEL_CONFIG,
    HYPER_PARAMS,
    TOOLS_MCP,
    PROMPT_VIEWER,
    MEMORY_MANAGER,
    WING_SHORTCUT,
    SECURITY_GUARDRAIL
}

/**
 * 现代 Wear OS 卡片封装 (大触控区域与高清晰度排版)
 */
@Composable
fun AgentConsoleCard(
    title: String,
    statusBadge: String,
    valueText: String,
    descText: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        onClick = {
            context.vibrateTick()
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = SettingsCardBg,
            contentColor = TextPrimaryColor
        ),
        border = BorderStroke(0.8.dp, SettingsCardBorder),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF222938))
                        .border(0.6.dp, Color(0xFF3A475E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = statusBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentCyan
                    )
                }
            }

            Text(
                text = valueText,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimaryColor
            )

            Text(
                text = descText,
                fontSize = 11.sp,
                color = TextSecondaryColor,
                lineHeight = 14.sp,
                maxLines = 2
            )
        }
    }
}

/**
 * 全屏二级配置遮罩面板 (支持手势右滑返回与 44dp 底部操作按钮)
 */
@Composable
fun SubSettingsModal(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val context = LocalContext.current

    val dragOffsetX = remember { Animatable(0f) }
    var screenWidthPx by remember { mutableFloatStateOf(480f) }
    val dismissThresholdPx = with(density) { 60.dp.toPx() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .onGloballyPositioned { coords ->
                screenWidthPx = coords.size.width.toFloat()
            }
            .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
            .pointerInput(screenWidthPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startX = down.position.x
                    val startY = down.position.y
                    var isSwipingRight = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (isSwipingRight) {
                                if (dragOffsetX.value >= dismissThresholdPx) {
                                    scope.launch {
                                        dragOffsetX.animateTo(screenWidthPx, tween(140))
                                        onDismiss()
                                    }
                                } else {
                                    scope.launch {
                                        dragOffsetX.animateTo(0f, spring(0.75f, 320f))
                                    }
                                }
                            }
                            break
                        }

                        val deltaX = change.position.x - startX
                        val deltaY = change.position.y - startY

                        if (!isSwipingRight) {
                            if (deltaX > 20.dp.toPx() && deltaX > abs(deltaY) * 1.2f) {
                                isSwipingRight = true
                            }
                        }

                        if (isSwipingRight) {
                            change.consume()
                            val clamped = deltaX.coerceAtLeast(0f)
                            scope.launch {
                                dragOffsetX.snapTo(clamped)
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.94f)
                .clip(RoundedCornerShape(20.dp))
                .background(SettingsSubBg)
                .border(0.8.dp, SettingsSubBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部小标题栏
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // 中间可滚动主体
            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                content()
            }

            // 统一底部大尺寸完成确认键 (高度 42dp，圆角 14dp，符合穿戴指触规范)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF263143))
                    .border(0.8.dp, Color(0xFF3F4F6B), RoundedCornerShape(14.dp))
                    .clickable {
                        context.vibrateTick()
                        onDismiss()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "完成返回",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
            }
        }
    }
}
