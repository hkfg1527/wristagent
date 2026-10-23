package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.ChatSession
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 谷歌 Wear OS Material 3 风格多会话抽屉
 * - 纯圆屏幕自适应：上下边缘条目自适应收缩内凹 (Curved Edge Adaptation)
 * - 圆形小胶囊 UI：一键切换历史会话、一键新开会话、一键删除会话
 * - 纯粹黑白冷灰质感：彻底告别土味蓝
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
        val listState = rememberLazyListState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF000000).copy(alpha = 0.88f))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startX = down.position.x
                        val startY = down.position.y

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                            if (!change.pressed) {
                                val deltaX = change.position.x - startX
                                val deltaY = change.position.y - startY
                                // 向左轻滑退出抽屉
                                if (deltaX < -32.dp.toPx() && abs(deltaX) > abs(deltaY)) {
                                    onClose()
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部标题与关闭 (内缩 28dp，防圆屏顶部月牙裁切)
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "会话切换",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )

                    // 极简关闭叉号
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E2430))
                            .border(0.6.dp, Color(0xFF333D4F), CircleShape)
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            fontSize = 9.sp,
                            color = Color(0xFFCBD5E1),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // M3 贴边自适应会话流列表
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 1. 顶部“+ 新建会话”圆形药丸
                    item(key = "create_new_session_btn") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.86f)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFF8FAFC))
                                .clickable(onClick = onCreateSession)
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                Text(
                                    text = "新开对话",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }

                    // 2. 历史会话小圆胶囊列表
                    itemsIndexed(sessions, key = { _, s -> s.id }) { index, session ->
                        val isSelected = session.id == activeSessionId

                        // 贴合圆形屏幕曲率的边缘宽度递减微调
                        val total = sessions.size
                        val widthFraction = when {
                            index == 0 && total > 2 -> 0.88f
                            index == total - 1 && total > 2 -> 0.84f
                            else -> 0.94f
                        }

                        SessionCapsuleItem(
                            session = session,
                            isSelected = isSelected,
                            widthFraction = widthFraction,
                            onClick = { onSelectSession(session.id) },
                            onDelete = { onDeleteSession(session.id) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 单个穿戴会话圆角小胶囊 (Google M3 规范 · 黑白高对比 · 独立删除)
 */
@Composable
private fun SessionCapsuleItem(
    session: ChatSession,
    isSelected: Boolean,
    widthFraction: Float,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val timeStr = remember(session.createdAt) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(session.createdAt))
    }

    val bgColor = if (isSelected) Color(0xFFF8FAFC) else Color(0xFF12151D)
    val borderColor = if (isSelected) Color.White else Color(0xFF262D3D)
    val titleColor = if (isSelected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val timeColor = if (isSelected) Color(0xFF64748B) else Color(0xFF94A3B8)

    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // 左侧状态点与标题
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 激活状态微晶实心小点
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color(0xFF0F172A) else Color(0xFF475569))
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = titleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = timeStr,
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = timeColor
                    )
                }
            }

            // 右侧删除小叉圆圈
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color(0xFFE2E8F0) else Color(0xFF1E2430))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    fontSize = 8.sp,
                    color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
