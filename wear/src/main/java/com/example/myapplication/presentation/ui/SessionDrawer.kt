package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 谷歌 Wear OS Material Design 3 风格居中会话切换器
 * - 纯横条卡片排布：每一条会话均为一个横向 Squircle 小横条
 * - 屏幕曲率变形挤压：依托 ScalingLazyColumn，在圆屏上下边缘随物理边界平滑挤压缩放
 * - 右滑自然退出：从左滑出，向右滑动或轻触返回键平滑退回
 */
@Composable
fun SessionDrawer(
    isOpen: Boolean,
    sessions: List<ChatSession>,
    activeSessionId: String,
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onClose: () -> Unit
) {
    AnimatedVisibility(
        visible = isOpen,
        enter = fadeIn(tween(180)) + slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { -it },
        exit = fadeOut(tween(160)) + slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it }
    ) {
        val listState = rememberScalingLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000).copy(alpha = 0.92f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val startX = down.position.x
                        val startY = down.position.y

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                val deltaX = change.position.x - startX
                                val deltaY = change.position.y - startY
                                // 从左向右轻划（或向左轻划）：快速收回会话抽屉
                                if (abs(deltaX) > 24.dp.toPx() && abs(deltaX) > abs(deltaY) * 1.1f) {
                                    onClose()
                                }
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
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
                                text = "会话切换",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF8FAFC)
                            )
                            Text(
                                text = "右滑返回",
                                fontSize = 8.5.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                // 1. "+ 新开对话" 居中白色/银色小横条
                item(key = "action_new_session") {
                    Card(
                        onClick = onCreateSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF8FAFC),
                            contentColor = Color(0xFF0F172A)
                        ),
                        shape = RoundedCornerShape(21.dp)
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
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0F172A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "+",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "新开对话",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                        }
                    }
                }

                // 2. 一条一条平滑变形挤压的 M3 会话横条
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
                            text = "← 右滑或轻触返回",
                            fontSize = 9.sp,
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

            // 右侧独立删除键 (40x40 触摸保护)
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
