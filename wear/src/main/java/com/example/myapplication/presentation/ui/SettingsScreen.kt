package com.example.myapplication.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.settings.AgentConsoleCard
import com.example.myapplication.presentation.ui.settings.HyperParamsContent
import com.example.myapplication.presentation.ui.settings.MemoryManagerContent
import com.example.myapplication.presentation.ui.settings.ModelConfigContent
import com.example.myapplication.presentation.ui.settings.PromptViewerContent
import com.example.myapplication.presentation.ui.settings.SecurityGuardrailContent
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.SubSettingsModal
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import com.example.myapplication.presentation.ui.settings.WingShortcutContent
import com.example.myapplication.presentation.ui.settings.vibrateTick
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * 谷歌 Wear OS 现代 Jetpack Compose 端侧 AI 设置中心
 * - 纯黑 OLED 沉浸基底，搭配微光超椭圆卡片
 * - ScalingLazyColumn 圆屏上下自适应贴合与物理表冠联动
 * - 全触控区域严格符合 40dp+ 穿戴规范，全按钮化直观调试
 * - 物理防抖水平手势，滑动停驻绝对零闪烁
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
    val context = LocalContext.current

    var currentModel by remember { mutableStateOf(preferences.modelName) }
    var currentBaseUrl by remember { mutableStateOf(preferences.baseUrl) }
    var currentSecurityMode by remember { mutableStateOf(preferences.securityMode) }
    var currentRightAction by remember { mutableStateOf(preferences.rightButtonAction) }
    var mcpEndpoint by remember { mutableStateOf(preferences.mcpEndpoint) }
    var isMcpEnabled by remember { mutableStateOf(preferences.isMcpEnabled) }
    var customPrompt by remember { mutableStateOf(preferences.customSystemPrompt) }
    var currentTemp by remember { mutableFloatStateOf(preferences.temperature) }
    var currentMaxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var currentStreamMode by remember { mutableStateOf(preferences.streamMode) }

    // 二级弹窗状态
    var activeDialog by remember { mutableStateOf<SettingsSubDialog?>(null) }

    // 右滑返回手势 (基于增量物理位移防抖，停止滑动绝对无闪烁)
    val dragOffsetX = remember { Animatable(0f) }
    var screenWidthPx by remember { mutableFloatStateOf(480f) }
    val dismissThresholdPx = with(density) { 50.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
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
                                onBack()
                            }
                        } else {
                            scope.launch {
                                dragOffsetX.animateTo(0f, spring(0.75f, 320f))
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            dragOffsetX.animateTo(0f, spring(0.75f, 320f))
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (dragAmount > 0f || dragOffsetX.value > 0f) {
                            change.consume()
                            val next = (dragOffsetX.value + dragAmount).coerceAtLeast(0f)
                            scope.launch {
                                dragOffsetX.snapTo(next)
                            }
                        }
                    }
                )
            }
    ) {
        val listState = rememberScalingLazyListState()
        val settingsFocusRequester = remember { FocusRequester() }

        LaunchedEffect(Unit) {
            settingsFocusRequester.requestFocus()
        }

        ScalingLazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(settingsFocusRequester)
                .focusable()
                .onRotaryScrollEvent {
                    scope.launch {
                        listState.scrollBy(it.verticalScrollPixels)
                        if (abs(it.verticalScrollPixels) > 10f) {
                            context.vibrateTick()
                        }
                    }
                    true
                },
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 顶部返回与标题
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(21.dp))
                            .background(Color(0xFF1E2533))
                            .border(0.8.dp, Color(0xFF3B4860), RoundedCornerShape(21.dp))
                            .clickable {
                                context.vibrateTick()
                                onBack()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "←",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "返回主界面",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFE2E8F0)
                            )
                        }
                    }

                    ListHeader(modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            text = "系统设置",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // 1. 模型与服务
            item {
                AgentConsoleCard(
                    title = "模型与服务",
                    statusBadge = "已配置",
                    valueText = currentModel,
                    descText = currentBaseUrl,
                    onClick = { activeDialog = SettingsSubDialog.MODEL_CONFIG }
                )
            }

            // 2. 模型参数
            item {
                AgentConsoleCard(
                    title = "模型参数",
                    statusBadge = "Temp %.1f".format(currentTemp),
                    valueText = "最大输出 $currentMaxTokens Token",
                    descText = if (currentStreamMode) "真实 SSE 流式传输已开启" else "非流式输出",
                    onClick = { activeDialog = SettingsSubDialog.HYPER_PARAMS }
                )
            }

            // 3. 内置工具与 MCP
            item {
                val activeCount = listOf(
                    preferences.toolDeviceStatusEnabled,
                    preferences.toolVibrateEnabled,
                    preferences.toolMatrixDensityEnabled,
                    preferences.toolPowerModeEnabled,
                    preferences.toolWeatherEnabled,
                    preferences.toolWebSearchEnabled
                ).count { it }
                AgentConsoleCard(
                    title = "端侧工具与 MCP",
                    statusBadge = "$activeCount/6 已启用",
                    valueText = if (activeCount == 6) "全部 6 项工具已就绪" else "已启用 $activeCount 项工具",
                    descText = "电量感知 • 振动 • 点阵 • 省电 • 天气 • 真实搜索",
                    onClick = { activeDialog = SettingsSubDialog.TOOLS_MCP }
                )
            }

            // 4. 系统提示词
            item {
                AgentConsoleCard(
                    title = "系统提示词",
                    statusBadge = if (customPrompt.isBlank()) "预置默认" else "自定义",
                    valueText = "穿戴端侧 AI 助手",
                    descText = "指令偏好与工具调用基准设定",
                    onClick = { activeDialog = SettingsSubDialog.PROMPT_VIEWER }
                )
            }

            // 5. 会话与上下文
            item {
                AgentConsoleCard(
                    title = "会话记忆管理",
                    statusBadge = "${preferences.maxContextTurns} 轮",
                    valueText = "单会话保留 ${preferences.maxContextTurns} 轮历史",
                    descText = "超出后自动滑窗淘汰早期对话",
                    onClick = { activeDialog = SettingsSubDialog.MEMORY_MANAGER }
                )
            }

            // 6. 快捷操作
            item {
                AgentConsoleCard(
                    title = "底栏副键绑定",
                    statusBadge = "右侧副键",
                    valueText = when (currentRightAction) {
                        RightButtonAction.MODEL_SWITCH -> "切换大模型"
                        RightButtonAction.OPEN_SETTINGS -> "打开系统设置"
                        RightButtonAction.CLEAR_MEMORY -> "一键清空长记忆"
                    },
                    descText = "长滑展开底栏时右侧按钮的功能",
                    onClick = { activeDialog = SettingsSubDialog.WING_SHORTCUT }
                )
            }

            // 7. 操作安全防线
            item {
                AgentConsoleCard(
                    title = "操作安全策略",
                    statusBadge = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "严格确认"
                        SecurityMode.STANDARD -> "标准提示"
                        SecurityMode.EXPERT -> "自动执行"
                    },
                    valueText = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "严格确认 (倒计时防误触)"
                        SecurityMode.STANDARD -> "标准提示 (高危需确认)"
                        SecurityMode.EXPERT -> "自动执行 (跳过确认)"
                    },
                    descText = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "所有敏感与系统操作均弹出确认详情"
                        SecurityMode.STANDARD -> "只读查询自动放行，修改系统需确认"
                        SecurityMode.EXPERT -> "除致命危险指令外直接执行"
                    },
                    onClick = { activeDialog = SettingsSubDialog.SECURITY_GUARDRAIL }
                )
            }

            // 8. 重新观看新手引导
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E2533))
                        .border(0.8.dp, Color(0xFF3B4860), RoundedCornerShape(14.dp))
                        .clickable {
                            context.vibrateTick()
                            onRestartOnboarding()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重新观看操作引导",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        // ================= 二级配置对话弹窗 (展开动效与纯按钮调试) =================
        activeDialog?.let { dialog ->
            when (dialog) {
                SettingsSubDialog.MODEL_CONFIG -> {
                    SubSettingsModal(
                        title = "模型与端点设置",
                        onDismiss = { activeDialog = null }
                    ) {
                        ModelConfigContent(
                            preferences = preferences,
                            currentModel = currentModel,
                            currentBaseUrl = currentBaseUrl,
                            onSelect = { model, base ->
                                currentModel = model
                                currentBaseUrl = base
                                preferences.modelName = model
                                preferences.baseUrl = base
                            }
                        )
                    }
                }
                SettingsSubDialog.HYPER_PARAMS -> {
                    SubSettingsModal(
                        title = "模型推理参数",
                        onDismiss = { activeDialog = null }
                    ) {
                        HyperParamsContent(
                            preferences = preferences,
                            onParamsChanged = { temp, tokens, stream ->
                                currentTemp = temp
                                currentMaxTokens = tokens
                                currentStreamMode = stream
                            }
                        )
                    }
                }
                SettingsSubDialog.TOOLS_MCP -> {
                    SubSettingsModal(
                        title = "端侧工具与 MCP",
                        onDismiss = { activeDialog = null }
                    ) {
                        ToolsMcpContent(
                            preferences = preferences,
                            isMcpEnabled = isMcpEnabled,
                            mcpEndpoint = mcpEndpoint,
                            onToggleMcp = { enabled, ep ->
                                isMcpEnabled = enabled
                                mcpEndpoint = ep
                                preferences.isMcpEnabled = enabled
                                preferences.mcpEndpoint = ep
                            }
                        )
                    }
                }
                SettingsSubDialog.PROMPT_VIEWER -> {
                    SubSettingsModal(
                        title = "系统提示词",
                        onDismiss = { activeDialog = null }
                    ) {
                        PromptViewerContent(
                            customPrompt = customPrompt,
                            onSavePrompt = { prompt ->
                                customPrompt = prompt
                                preferences.customSystemPrompt = prompt
                            }
                        )
                    }
                }
                SettingsSubDialog.MEMORY_MANAGER -> {
                    SubSettingsModal(
                        title = "会话记忆管理",
                        onDismiss = { activeDialog = null }
                    ) {
                        MemoryManagerContent(preferences = preferences)
                    }
                }
                SettingsSubDialog.WING_SHORTCUT -> {
                    SubSettingsModal(
                        title = "快捷操作绑定",
                        onDismiss = { activeDialog = null }
                    ) {
                        WingShortcutContent(
                            currentAction = currentRightAction,
                            onSelect = { action ->
                                currentRightAction = action
                                preferences.rightButtonAction = action
                            }
                        )
                    }
                }
                SettingsSubDialog.SECURITY_GUARDRAIL -> {
                    SubSettingsModal(
                        title = "操作安全策略",
                        onDismiss = { activeDialog = null }
                    ) {
                        SecurityGuardrailContent(
                            currentMode = currentSecurityMode,
                            onSelect = { mode ->
                                currentSecurityMode = mode
                                preferences.securityMode = mode
                            }
                        )
                    }
                }
            }
        }
    }
}
