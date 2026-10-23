package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 纯暗微晶高级设置控制台 (纯黑白冷灰极简设计 · 级联弹射进场动效 · 彻底消除土味蓝 · 居中防圆屏裁切)
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    preferences: AgentPreferences,
    onBack: () -> Unit,
    onRestartOnboarding: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var gridSpacing by remember { mutableFloatStateOf(preferences.matrixSpacingDp) }
    var currentModel by remember { mutableStateOf(preferences.modelName) }
    var currentSecurityMode by remember { mutableStateOf(preferences.securityMode) }
    var currentRightAction by remember { mutableStateOf(preferences.rightButtonAction) }
    var ultraSaving by remember { mutableStateOf(preferences.ultraSaving) }

    // 级联入场动画状态
    var isContentVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(30)
        isContentVisible = true
    }

    // 全局右滑返回 (Swipe-to-Dismiss)
    val dragOffsetX = remember { Animatable(0f) }
    var screenWidthPx by remember { mutableFloatStateOf(480f) }
    val dismissThresholdPx = with(density) { 68.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000)) // 绝对纯黑 OLED 基底
            .onGloballyPositioned { coords ->
                screenWidthPx = coords.size.width.toFloat()
            }
            .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
            .alpha(1f - (dragOffsetX.value / (screenWidthPx * 1.4f)).coerceIn(0f, 0.6f))
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
                                        dragOffsetX.animateTo(screenWidthPx, tween(150))
                                        onBack()
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
                            if (deltaX > 16.dp.toPx() && deltaX > abs(deltaY) * 1.2f) {
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
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶部居中返回胶囊
            AnimatedVisibility(
                visible = isContentVisible,
                enter = fadeIn(tween(240)) + slideInVertically(tween(280, easing = FastOutSlowInEasing)) { -it / 2 }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF141820))
                            .border(0.8.dp, Color(0xFF333D4F), RoundedCornerShape(16.dp))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "←",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "右滑或轻触返回",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }

                    Text(
                        text = "智能体系统设置",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                }
            }

            // 1. 三级安全护栏模式 (小白[默认] / 普通人 / 大佬)
            StaggeredItem(visible = isContentVisible, delayMs = 40) {
                SettingSectionContainer(title = "AI 安全审查模式") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SecurityModeItem(
                            title = "新手小白模式 (默认)",
                            desc = "全命令审批 + 大白话说明 + 危险操作强制锁滑块 3 秒",
                            selected = currentSecurityMode == SecurityMode.NOVICE,
                            onClick = {
                                currentSecurityMode = SecurityMode.NOVICE
                                preferences.securityMode = SecurityMode.NOVICE
                            }
                        )
                        SecurityModeItem(
                            title = "普通人模式",
                            desc = "快速模型预审，无风险静默放行，有风险滑动审批",
                            selected = currentSecurityMode == SecurityMode.STANDARD,
                            onClick = {
                                currentSecurityMode = SecurityMode.STANDARD
                                preferences.securityMode = SecurityMode.STANDARD
                            }
                        )
                        SecurityModeItem(
                            title = "我是大佬模式",
                            desc = "除致命破坏指令硬拦截外，全部极速自主执行",
                            selected = currentSecurityMode == SecurityMode.EXPERT,
                            onClick = {
                                currentSecurityMode = SecurityMode.EXPERT
                                preferences.securityMode = SecurityMode.EXPERT
                            }
                        )
                    }
                }
            }

            // 2. 右滑副键快捷定义
            StaggeredItem(visible = isContentVisible, delayMs = 80) {
                SettingSectionContainer(title = "主界面两翼快捷键定义") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RightActionChip(
                                label = "切换模型",
                                selected = currentRightAction == RightButtonAction.MODEL_SWITCH,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentRightAction = RightButtonAction.MODEL_SWITCH
                                    preferences.rightButtonAction = RightButtonAction.MODEL_SWITCH
                                }
                            )
                            RightActionChip(
                                label = "打开设置",
                                selected = currentRightAction == RightButtonAction.OPEN_SETTINGS,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    currentRightAction = RightButtonAction.OPEN_SETTINGS
                                    preferences.rightButtonAction = RightButtonAction.OPEN_SETTINGS
                                }
                            )
                        }
                        RightActionChip(
                            label = "一键清空长记忆",
                            selected = currentRightAction == RightButtonAction.CLEAR_MEMORY,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                currentRightAction = RightButtonAction.CLEAR_MEMORY
                                preferences.rightButtonAction = RightButtonAction.CLEAR_MEMORY
                            }
                        )
                    }
                }
            }

            // 3. 模型端点切换 (DeepSeek Chat / Reasoner)
            StaggeredItem(visible = isContentVisible, delayMs = 120) {
                SettingSectionContainer(title = "当前运行大模型") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ModelChoiceChip(
                            name = "DeepSeek-V3",
                            sub = "通用全能推理",
                            selected = currentModel == "deepseek-chat",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentModel = "deepseek-chat"
                                preferences.modelName = "deepseek-chat"
                            }
                        )
                        ModelChoiceChip(
                            name = "DeepSeek-R1",
                            sub = "深度思维思考",
                            selected = currentModel == "deepseek-reasoner",
                            modifier = Modifier.weight(1f),
                            onClick = {
                                currentModel = "deepseek-reasoner"
                                preferences.modelName = "deepseek-reasoner"
                            }
                        )
                    }
                }
            }

            // 4. 同心点阵间距微调
            StaggeredItem(visible = isContentVisible, delayMs = 160) {
                SettingSectionContainer(title = "表盘微晶点阵密度") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("网格物理步长", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                            Text(
                                "${gridSpacing.roundToInt()} dp",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(12f, 16f, 20f).forEach { spacingVal ->
                                val isSel = abs(gridSpacing - spacingVal) < 1f
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSel) Color(0xFFF8FAFC) else Color(0xFF141720))
                                        .border(
                                            0.8.dp,
                                            if (isSel) Color.White else Color(0xFF262D3D),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            gridSpacing = spacingVal
                                            preferences.matrixSpacingDp = spacingVal
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (spacingVal) {
                                            12f -> "稠密"
                                            16f -> "标准"
                                            else -> "稀疏"
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. 极端省电模式
            StaggeredItem(visible = isContentVisible, delayMs = 200) {
                SettingSectionContainer(title = "极限长续航模式") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("纯黑微光静止", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE2E8F0))
                            Text("节流背景重绘与波动动画", fontSize = 9.sp, color = Color(0xFF64748B))
                        }
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (ultraSaving) Color.White else Color(0xFF1A1F2B))
                                .border(0.8.dp, if (ultraSaving) Color(0xFFE2E8F0) else Color(0xFF333D4F), RoundedCornerShape(11.dp))
                                .clickable {
                                    ultraSaving = !ultraSaving
                                    preferences.ultraSaving = ultraSaving
                                },
                            contentAlignment = if (ultraSaving) Alignment.CenterEnd else Alignment.CenterStart
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(2.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(if (ultraSaving) Color(0xFF0F172A) else Color(0xFF64748B))
                            )
                        }
                    }
                }
            }

            // 6. 重置新手引导
            StaggeredItem(visible = isContentVisible, delayMs = 240) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141720))
                        .border(0.8.dp, Color(0xFF262D3D), RoundedCornerShape(12.dp))
                        .clickable(onClick = onRestartOnboarding)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("观看手势操作新手引导", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                }
            }

            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun StaggeredItem(
    visible: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300, delayMillis = delayMs)) +
                slideInVertically(tween(360, delayMillis = delayMs, easing = FastOutSlowInEasing)) { it / 3 }
    ) {
        content()
    }
}

@Composable
private fun SettingSectionContainer(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF11141C))
            .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(14.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF94A3B8),
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        content()
    }
}

@Composable
private fun SecurityModeItem(
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color.White else Color(0xFF262D3D)
    val bgColor = if (selected) Color(0xFFF8FAFC) else Color(0xFF141720)
    val textColor = if (selected) Color(0xFF0F172A) else Color(0xFFE2E8F0)
    val descColor = if (selected) Color(0xFF475569) else Color(0xFF64748B)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (selected) {
                Text("✓", fontSize = 10.sp, color = textColor, fontWeight = FontWeight.Bold)
            }
        }
        Text(
            text = desc,
            fontSize = 9.sp,
            color = descColor,
            lineHeight = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun ModelChoiceChip(
    name: String,
    sub: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color.White else Color(0xFF262D3D)
    val bgColor = if (selected) Color(0xFFF8FAFC) else Color(0xFF141720)
    val textColor = if (selected) Color(0xFF0F172A) else Color.White
    val subColor = if (selected) Color(0xFF475569) else Color(0xFF64748B)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = textColor)
        Text(sub, fontSize = 8.sp, color = subColor, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun RightActionChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val borderColor = if (selected) Color.White else Color(0xFF262D3D)
    val bgColor = if (selected) Color(0xFFF8FAFC) else Color(0xFF141720)
    val textColor = if (selected) Color(0xFF0F172A) else Color(0xFF94A3B8)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(0.8.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor
        )
    }
}
