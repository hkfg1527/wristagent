package com.example.myapplication.presentation.ui

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.ChatSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 谷歌 Wear OS Material Design 3 风格居中会话切换器
 * - 纯横条卡片排布：每一条会话均为一个横向 Squircle 小横条
 * - 屏幕曲率变形挤压：依托 ScalingLazyColumn，在圆屏上下边缘随物理边界平滑挤压缩放
 * - 右滑跟手阻尼退出：手势向左划自右推入，向右轻划跟手推回退出
 * - 硬件物理表冠联动：旋转表冠顺滑滚动并伴随轻微齿轮触觉微震
 */
@Composable
fun SessionDrawer(
    isOpen: Boolean,
    sessions: List<ChatSession>,
    activeSessionId: String,
    currentModelName: String = "",
    onSelectModel: ((String) -> Unit)? = null,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { it },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it }
    ) {
        val listState = rememberScalingLazyListState()
        val drawerFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            drawerFocusRequester.requestFocus()
        }

        // 右滑跟手防抖动推回手势 (基于增量 delta 累加，杜绝局部坐标递归反向漂移闪烁)
        val dragOffsetX = remember { Animatable(0f) }
        var screenWidthPx by remember { mutableFloatStateOf(384f) }
        val dismissThresholdPx = with(density) { 50.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000).copy(alpha = 0.94f))
                .onGloballyPositioned { coords ->
                    screenWidthPx = coords.size.width.toFloat()
                }
                .graphicsLayer {
                    translationX = dragOffsetX.value
                    alpha = (1f - (dragOffsetX.value / (screenWidthPx * 1.5f))).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffsetX.value >= dismissThresholdPx) {
                                scope.launch {
                                    dragOffsetX.animateTo(screenWidthPx, tween(140))
                                    onClose()
                                }
                            } else {
                                scope.launch {
                                    dragOffsetX.animateTo(0f, spring(0.75f, 350f))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                dragOffsetX.animateTo(0f, spring(0.75f, 350f))
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            // 物理增量累加，手指停下时 dragAmount=0，位移保持不动，绝对无闪烁
                            if (dragAmount > 0f || dragOffsetX.value > 0f) {
                                change.consume()
                                val next = (dragOffsetX.value + dragAmount).coerceAtLeast(0f)
                                scope.launch {
                                    dragOffsetX.snapTo(next)
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(drawerFocusRequester)
                    .focusable()
                    .onRotaryScrollEvent {
                        scope.launch {
                            listState.scrollBy(it.verticalScrollPixels)
                            if (abs(it.verticalScrollPixels) > 8f) {
                                context.getSystemService(Vibrator::class.java)?.let { vib ->
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                    }
                                }
                            }
                        }
                        true
                    },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题横条
                item(key = "header_title") {
                    ListHeader(modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "模型与会话管理",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = "右滑退出 →",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // 快捷模型调试切换按钮行 (满足用户右滑快捷切换模型与调试需求)
                item(key = "model_switch_row") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF141824))
                            .border(0.8.dp, Color(0xFF2C3548), RoundedCornerShape(14.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = "当前模型调试",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF38BDF8)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                "gemini-2.5-flash" to "Flash",
                                "gemini-2.5-pro" to "Pro",
                                "deepseek-chat" to "DeepSeek"
                            ).forEach { (modelKey, label) ->
                                val isSel = currentModelName == modelKey
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) Color(0xFF0284C7) else Color(0xFF1E2536))
                                        .border(
                                            0.6.dp,
                                            if (isSel) Color(0xFF38BDF8) else Color(0xFF333E54),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            context.getSystemService(Vibrator::class.java)?.let { vib ->
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                                }
                                            }
                                            onSelectModel?.invoke(modelKey)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSel) Color.White else Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }

                // 1. "+ 新开对话" 居中白色/银色横条 (高度 44dp 规范)
                item(key = "action_new_session") {
                    Card(
                        onClick = onCreateSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "新开对话",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }

                // 2. 一条一条平滑变形挤压的 M3 会话横条 (参考微信 C100 / QQ Next 紧致风格)
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == activeSessionId
                    M3SessionStrip(
                        session = session,
                        isSelected = isSelected,
                        onClick = { onSelectSession(session.id) },
                        onDelete = { onDeleteSession(session.id) }
                    )
                }

                // 底部轻触返回药丸
                item(key = "action_dismiss") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141824))
                            .border(0.6.dp, Color(0xFF2B3446), RoundedCornerShape(16.dp))
                            .clickable(onClick = onClose)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "向右轻划退出",
                            fontSize = 8.5.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 遵循 Google Material Design 3 规范的单个会话扁平横条
 */
@Composable
private fun M3SessionStrip(
    session: ChatSession,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(session.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.createdAt))
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1E2433) else Color(0xFF11141C),
            contentColor = Color(0xFFF1F5F9)
        ),
        border = BorderStroke(
            0.6.dp,
            if (isSelected) Color(0xFF94A3B8) else Color(0xFF262D3D)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧状态点与标题
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else Color(0xFF475569))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeStr,
                        fontSize = 7.5.sp,
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 右侧独立删除键 (触摸防护)
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1C222E))
                    .border(0.5.dp, Color(0xFF333E50), CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 8.sp,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
