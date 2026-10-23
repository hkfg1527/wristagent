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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 谷歌 Wear OS Material 3 智能体系统设置控制台
 * - 纯黑 OLED 沉浸基底，消除白闪
 * - 遵循 ScalingLazyColumn 圆屏上下自适应贴合
 * - 深度集成 Agent 大模型端点、工具集状态、MCP 扩展、长记忆与安全护栏
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

    var currentModel by remember { mutableStateOf(preferences.modelName) }
    var currentBaseUrl by remember { mutableStateOf(preferences.baseUrl) }
    var currentSecurityMode by remember { mutableStateOf(preferences.securityMode) }
    var currentRightAction by remember { mutableStateOf(preferences.rightButtonAction) }
    var mcpEndpoint by remember { mutableStateOf(preferences.mcpEndpoint) }
    var isMcpEnabled by remember { mutableStateOf(preferences.isMcpEnabled) }
    var customPrompt by remember { mutableStateOf(preferences.customSystemPrompt) }

    // 二级弹窗状态
    var activeDialog by remember { mutableStateOf<SettingsSubDialog?>(null) }

    // 右滑返回手势 (Swipe-to-Dismiss)
    val dragOffsetX = remember { Animatable(0f) }
    var screenWidthPx by remember { mutableFloatStateOf(480f) }
    val dismissThresholdPx = with(density) { 60.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
            .onGloballyPositioned { coords ->
                screenWidthPx = coords.size.width.toFloat()
            }
            .offset { IntOffset(dragOffsetX.value.roundToInt(), 0) }
            .alpha(1f - (dragOffsetX.value / (screenWidthPx * 1.5f)).coerceIn(0f, 0.5f))
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
                            if (deltaX > 18.dp.toPx() && deltaX > abs(deltaY) * 1.2f) {
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
        val listState = rememberScalingLazyListState()
        val settingsFocusRequester = remember { FocusRequester() }
        val context = LocalContext.current

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
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部返回与标题
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF141720))
                            .border(0.6.dp, Color(0xFF333D4F), RoundedCornerShape(12.dp))
                            .clickable(onClick = onBack)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "←",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White
                            )
                            Text(
                                text = "右滑或轻触返回",
                                fontSize = 9.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }

                    ListHeader(modifier = Modifier.padding(top = 2.dp)) {
                        Text(
                            text = "智能体控制台",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF8FAFC)
                        )
                    }
                }
            }

            // 1. 当前运行大模型服务 (动态读取与端点配置)
            item {
                AgentConsoleCard(
                    title = "模型与端点",
                    statusBadge = "实时生效",
                    valueText = currentModel,
                    descText = currentBaseUrl,
                    onClick = { activeDialog = SettingsSubDialog.MODEL_CONFIG }
                )
            }

            // 2. 推理超参数调优 (Temperature / MaxTokens / Stream)
            item {
                AgentConsoleCard(
                    title = "推理超参数",
                    statusBadge = "创客模式",
                    valueText = "Temp: ${preferences.temperature} | Max: ${preferences.maxOutputTokens}",
                    descText = if (preferences.streamMode) "流式输出开启 • 精准采样" else "非流式输出 • 稳定整包",
                    onClick = { activeDialog = SettingsSubDialog.HYPER_PARAMS }
                )
            }

            // 3. 原生工具集细粒度权限 (6 项穿戴工具独立开关)
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
                    title = "工具集细粒度权限",
                    statusBadge = "$activeCount/6 启用",
                    valueText = if (activeCount == 6) "全部原生工具已就绪" else "已按需定制放行",
                    descText = "电量感知 • 触觉反馈 • 点阵谐振 • 功耗调度 • 气象环境 • 广域搜索",
                    onClick = { activeDialog = SettingsSubDialog.TOOLS_MCP }
                )
            }

            // 4. 伴生系统提示词与角色设定 (System Prompt / Persona)
            item {
                AgentConsoleCard(
                    title = "伴生系统提示词",
                    statusBadge = if (customPrompt.isBlank()) "原生默认" else "自定义",
                    valueText = "Wear OS 全自主伴生体",
                    descText = "开放反思 • ReAct 工具编排 • 随身参谋",
                    onClick = { activeDialog = SettingsSubDialog.PROMPT_VIEWER }
                )
            }

            // 5. 上下文与长记忆管理
            item {
                AgentConsoleCard(
                    title = "上下文与长记忆",
                    statusBadge = "${preferences.maxContextTurns} 轮",
                    valueText = "${preferences.maxContextTurns} 轮动态滚动窗口",
                    descText = "超出自动向前滑窗裁剪，顶层持久锁定用户长期偏好",
                    onClick = { activeDialog = SettingsSubDialog.MEMORY_MANAGER }
                )
            }

            // 6. 两翼快捷键定义 (卡片化二级交互)
            item {
                AgentConsoleCard(
                    title = "主界面两翼快捷键",
                    statusBadge = "可自定义",
                    valueText = when (currentRightAction) {
                        RightButtonAction.MODEL_SWITCH -> "切换模型"
                        RightButtonAction.OPEN_SETTINGS -> "打开设置"
                        RightButtonAction.CLEAR_MEMORY -> "清空长记忆"
                    },
                    descText = "底栏向右滑动轻触右翼按键触发的功能",
                    onClick = { activeDialog = SettingsSubDialog.WING_SHORTCUT }
                )
            }

            // 7. AI 安全护栏审查模式
            item {
                AgentConsoleCard(
                    title = "AI 安全审查护栏",
                    statusBadge = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "小白模式"
                        SecurityMode.STANDARD -> "普通模式"
                        SecurityMode.EXPERT -> "大佬模式"
                    },
                    valueText = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "新手小白 (3秒强制锁)"
                        SecurityMode.STANDARD -> "普通人 (智能预审)"
                        SecurityMode.EXPERT -> "我是大佬 (极速自主)"
                    },
                    descText = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "全操作大白话通报，高风险锁定3秒"
                        SecurityMode.STANDARD -> "无风险静默放行，有风险滑动确认"
                        SecurityMode.EXPERT -> "除破坏性指令外，极速全自主执行"
                    },
                    onClick = { activeDialog = SettingsSubDialog.SECURITY_GUARDRAIL }
                )
            }

            // 7. 新手引导
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF141720))
                        .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(12.dp))
                        .clickable(onClick = onRestartOnboarding)
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "重新观看操作引导",
                        fontSize = 10.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // ================= 二级配置对话弹窗 =================
        activeDialog?.let { dialog ->
            SubSettingsModal(
                dialog = dialog,
                preferences = preferences,
                currentModel = currentModel,
                currentBaseUrl = currentBaseUrl,
                currentSecurityMode = currentSecurityMode,
                currentRightAction = currentRightAction,
                mcpEndpoint = mcpEndpoint,
                isMcpEnabled = isMcpEnabled,
                customPrompt = customPrompt,
                onModelChange = { model, base ->
                    currentModel = model
                    currentBaseUrl = base
                    preferences.modelName = model
                    preferences.baseUrl = base
                },
                onMcpChange = { enabled, ep ->
                    isMcpEnabled = enabled
                    mcpEndpoint = ep
                    preferences.isMcpEnabled = enabled
                    preferences.mcpEndpoint = ep
                },
                onSecurityModeChange = { mode ->
                    currentSecurityMode = mode
                    preferences.securityMode = mode
                },
                onRightActionChange = { action ->
                    currentRightAction = action
                    preferences.rightButtonAction = action
                },
                onPromptChange = { prompt ->
                    customPrompt = prompt
                    preferences.customSystemPrompt = prompt
                },
                onDismiss = { activeDialog = null }
            )
        }
    }
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
 * 谷歌 Wear OS M3 卡片封装
 */
@Composable
private fun AgentConsoleCard(
    title: String,
    statusBadge: String,
    valueText: String,
    descText: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF11141C),
            contentColor = Color(0xFFF1F5F9)
        ),
        border = BorderStroke(0.6.dp, Color(0xFF262D3D)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                    color = Color(0xFFF8FAFC)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1E2430))
                        .border(0.5.dp, Color(0xFF333D4F), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = statusBadge,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            Text(
                text = valueText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Text(
                text = descText,
                fontSize = 8.5.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 11.5.sp,
                maxLines = 2
            )
        }
    }
}

/**
 * 全功能二级配置遮罩面板
 */
@Composable
private fun SubSettingsModal(
    dialog: SettingsSubDialog,
    preferences: AgentPreferences,
    currentModel: String,
    currentBaseUrl: String,
    currentSecurityMode: SecurityMode,
    currentRightAction: RightButtonAction,
    mcpEndpoint: String,
    isMcpEnabled: Boolean,
    customPrompt: String,
    onModelChange: (model: String, baseUrl: String) -> Unit,
    onMcpChange: (enabled: Boolean, endpoint: String) -> Unit,
    onSecurityModeChange: (SecurityMode) -> Unit,
    onRightActionChange: (RightButtonAction) -> Unit,
    onPromptChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000000).copy(alpha = 0.94f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(0.92f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF0F1219))
                .border(0.8.dp, Color(0xFF2B3446), RoundedCornerShape(16.dp))
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
                                // 二级子弹窗任意区域向右轻划，顺畅关闭返回
                                if (deltaX > 24.dp.toPx() && deltaX > abs(deltaY) * 1.2f) {
                                    onDismiss()
                                }
                                break
                            }
                        }
                    }
                }
                .clickable(enabled = false) {}
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            when (dialog) {
                SettingsSubDialog.MODEL_CONFIG -> {
                    ModelConfigContent(
                        preferences = preferences,
                        currentModel = currentModel,
                        currentBaseUrl = currentBaseUrl,
                        onSelect = onModelChange
                    )
                }
                SettingsSubDialog.HYPER_PARAMS -> {
                    HyperParamsContent(
                        preferences = preferences
                    )
                }
                SettingsSubDialog.TOOLS_MCP -> {
                    ToolsMcpContent(
                        preferences = preferences,
                        isMcpEnabled = isMcpEnabled,
                        mcpEndpoint = mcpEndpoint,
                        onToggleMcp = onMcpChange
                    )
                }
                SettingsSubDialog.PROMPT_VIEWER -> {
                    PromptViewerContent(
                        customPrompt = customPrompt,
                        onSavePrompt = onPromptChange
                    )
                }
                SettingsSubDialog.MEMORY_MANAGER -> {
                    MemoryManagerContent(
                        preferences = preferences
                    )
                }
                SettingsSubDialog.WING_SHORTCUT -> {
                    WingShortcutContent(
                        currentAction = currentRightAction,
                        onSelect = onRightActionChange
                    )
                }
                SettingsSubDialog.SECURITY_GUARDRAIL -> {
                    SecurityGuardrailContent(
                        currentMode = currentSecurityMode,
                        onSelect = onSecurityModeChange
                    )
                }
            }

            // 统一底部关闭确认键
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E2430))
                    .border(0.6.dp, Color(0xFF333D4F), RoundedCornerShape(8.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 18.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "完成返回",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC)
                )
            }
        }
    }
}

/**
 * 1. 模型与端点配置内容 (支持实时拉取端点与测速)
 */
@Composable
private fun ModelConfigContent(
    preferences: AgentPreferences,
    currentModel: String,
    currentBaseUrl: String,
    onSelect: (model: String, baseUrl: String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var inputBaseUrl by remember { mutableStateOf(currentBaseUrl) }
    var inputApiKey by remember { mutableStateOf(preferences.apiKey) }
    var isEditingUrl by remember { mutableStateOf(false) }
    var isEditingKey by remember { mutableStateOf(false) }

    var isFetching by remember { mutableStateOf(false) }
    var fetchResultText by remember { mutableStateOf<String?>(null) }
    var fetchedModels by remember {
        mutableStateOf(
            preferences.cachedRemoteModels.split(",").filter { it.isNotBlank() }
        )
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("大模型端点与在线探测", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // Base URL 配置行
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .clickable { isEditingUrl = !isEditingUrl }
                .padding(6.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("API Base URL", fontSize = 8.sp, color = Color(0xFF94A3B8))
                    Text(if (isEditingUrl) "点击收起" else "点击编辑", fontSize = 7.5.sp, color = Color(0xFFCBD5E1))
                }
                if (isEditingUrl) {
                    BasicTextField(
                        value = inputBaseUrl,
                        onValueChange = {
                            inputBaseUrl = it
                            preferences.baseUrl = it
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 8.5.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                } else {
                    Text(inputBaseUrl, fontSize = 8.5.sp, color = Color.White, maxLines = 1)
                }
            }
        }

        // API Key 状态行
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .clickable { isEditingKey = !isEditingKey }
                .padding(6.dp)
        ) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("API Key", fontSize = 8.sp, color = Color(0xFF94A3B8))
                    Text(if (isEditingKey) "点击收起" else "点击编辑", fontSize = 7.5.sp, color = Color(0xFFCBD5E1))
                }
                if (isEditingKey) {
                    BasicTextField(
                        value = inputApiKey,
                        onValueChange = {
                            inputApiKey = it
                            preferences.apiKey = it
                        },
                        textStyle = TextStyle(color = Color.White, fontSize = 8.5.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                    )
                } else {
                    val masked = if (inputApiKey.length > 8) {
                        "${inputApiKey.take(4)}...${inputApiKey.takeLast(4)}"
                    } else if (inputApiKey.isNotBlank()) {
                        "********"
                    } else {
                        "未配置 API Key"
                    }
                    Text(masked, fontSize = 8.5.sp, color = Color.White)
                }
            }
        }

        // 测速并拉取模型按钮
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFetching) Color(0xFF1E2430) else Color(0xFF233045))
                .border(0.6.dp, Color(0xFF475569), RoundedCornerShape(8.dp))
                .clickable(enabled = !isFetching) {
                    isFetching = true
                    fetchResultText = "探测端点中..."
                    scope.launch {
                        val (timeMs, models) = AgentDispatcher.fetchRemoteModels(inputBaseUrl, inputApiKey)
                        isFetching = false
                        if (timeMs >= 0 && models.isNotEmpty()) {
                            fetchResultText = "探测成功 • 延迟 ${timeMs}ms"
                            fetchedModels = models
                            preferences.cachedRemoteModels = models.joinToString(",")
                        } else {
                            fetchResultText = "端点握手失败，已加载预置"
                        }
                    }
                }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isFetching) "正在连接 /v1/models ..." else "测速并实时拉取模型列表",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        fetchResultText?.let {
            Text(it, fontSize = 8.sp, color = Color(0xFF38BDF8))
        }

        Text("可用模型 (点击即选生效)：", fontSize = 8.5.sp, color = Color(0xFF94A3B8), modifier = Modifier.align(Alignment.Start))

        val displayList = if (fetchedModels.isNotEmpty()) {
            fetchedModels
        } else {
            listOf("deepseek-chat", "deepseek-reasoner", "gemini-2.5-flash", "claude-3-5-sonnet")
        }

        displayList.forEach { modelId ->
            val isSelected = currentModel == modelId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) Color.White else Color(0xFF141720))
                    .border(0.5.dp, if (isSelected) Color.White else Color(0xFF262D3D), RoundedCornerShape(6.dp))
                    .clickable { onSelect(modelId, inputBaseUrl) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = modelId,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0F172A) else Color.White
                    )
                    if (isSelected) {
                        Text("当前生效", fontSize = 7.5.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 2. 推理超参数调优控制台 (Temperature / MaxTokens / Stream)
 */
@Composable
private fun HyperParamsContent(
    preferences: AgentPreferences
) {
    var temp by remember { mutableFloatStateOf(preferences.temperature) }
    var maxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var isStream by remember { mutableStateOf(preferences.streamMode) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI 推理超参数调优", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // 1. Temperature 步进微调
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("采样温度 (Temperature)", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                    Text(
                        text = "%.1f".format(temp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2430))
                            .clickable {
                                val next = (temp - 0.1f).coerceIn(0.0f, 1.5f)
                                temp = (next * 10).roundToInt() / 10f
                                preferences.temperature = temp
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("- 0.1", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2430))
                            .clickable {
                                val next = (temp + 0.1f).coerceIn(0.0f, 1.5f)
                                temp = (next * 10).roundToInt() / 10f
                                preferences.temperature = temp
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+ 0.1", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }

        // 2. Max Output Tokens
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("最大输出长度 (Max Output Tokens)", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(150, 300, 600, 1200).forEach { tokens ->
                        val isSel = maxTokens == tokens
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color.White else Color(0xFF1E2430))
                                .clickable {
                                    maxTokens = tokens
                                    preferences.maxOutputTokens = tokens
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$tokens",
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF0F172A) else Color.White
                            )
                        }
                    }
                }
            }
        }

        // 3. 流式传输开关
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .clickable {
                    isStream = !isStream
                    preferences.streamMode = isStream
                }
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("流式输出 (Stream Mode)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("逐字打字机动效与首字极速响应", fontSize = 7.5.sp, color = Color(0xFF94A3B8))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isStream) Color.White else Color(0xFF333D4F))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isStream) "已开启" else "已关闭",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStream) Color(0xFF0F172A) else Color.White
                    )
                }
            }
        }
    }
}

/**
 * 3. 工具集细粒度权限控制与 MCP 协议探针
 */
@Composable
private fun ToolsMcpContent(
    preferences: AgentPreferences,
    isMcpEnabled: Boolean,
    mcpEndpoint: String,
    onToggleMcp: (enabled: Boolean, endpoint: String) -> Unit
) {
    var toolDeviceStatus by remember { mutableStateOf(preferences.toolDeviceStatusEnabled) }
    var toolVibrate by remember { mutableStateOf(preferences.toolVibrateEnabled) }
    var toolMatrixDensity by remember { mutableStateOf(preferences.toolMatrixDensityEnabled) }
    var toolPowerMode by remember { mutableStateOf(preferences.toolPowerModeEnabled) }
    var toolWeather by remember { mutableStateOf(preferences.toolWeatherEnabled) }
    var toolWebSearch by remember { mutableStateOf(preferences.toolWebSearchEnabled) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("原生工具细粒度放行控制", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        val toolItems = listOf(
            Triple("get_device_status", "电量与温控感知", toolDeviceStatus) to {
                toolDeviceStatus = !toolDeviceStatus
                preferences.toolDeviceStatusEnabled = toolDeviceStatus
            },
            Triple("vibrate_device", "线性马达触觉反馈", toolVibrate) to {
                toolVibrate = !toolVibrate
                preferences.toolVibrateEnabled = toolVibrate
            },
            Triple("adjust_matrix_density", "微晶点阵谐振调度", toolMatrixDensity) to {
                toolMatrixDensity = !toolMatrixDensity
                preferences.toolMatrixDensityEnabled = toolMatrixDensity
            },
            Triple("set_system_power_mode", "系统级能耗协同", toolPowerMode) to {
                toolPowerMode = !toolPowerMode
                preferences.toolPowerModeEnabled = toolPowerMode
            },
            Triple("query_time_weather", "高精度时钟与气象", toolWeather) to {
                toolWeather = !toolWeather
                preferences.toolWeatherEnabled = toolWeather
            },
            Triple("web_search", "广域互联网即时搜索", toolWebSearch) to {
                toolWebSearch = !toolWebSearch
                preferences.toolWebSearchEnabled = toolWebSearch
            }
        )

        toolItems.forEach { (meta, toggle) ->
            val (name, label, enabled) = meta
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF141720))
                    .border(0.5.dp, Color(0xFF262D3D), RoundedCornerShape(6.dp))
                    .clickable { toggle() }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(label, fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(name, fontSize = 7.sp, color = Color(0xFF94A3B8))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (enabled) Color.White else Color(0xFF333D4F))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (enabled) "放行" else "拦截",
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) Color(0xFF0F172A) else Color.White
                        )
                    }
                }
            }
        }

        // MCP 协议控制项
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF141720))
                .border(0.5.dp, Color(0xFF262D3D), RoundedCornerShape(6.dp))
                .clickable { onToggleMcp(!isMcpEnabled, mcpEndpoint) }
                .padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("MCP 协议探针", fontSize = 8.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(mcpEndpoint, fontSize = 7.sp, color = Color(0xFF94A3B8))
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isMcpEnabled) Color(0xFF10B981) else Color(0xFF333D4F))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isMcpEnabled) "已连接" else "未启用",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * 3. 伴生系统提示词查看与编辑
 */
@Composable
private fun PromptViewerContent(
    customPrompt: String,
    onSavePrompt: (String) -> Unit
) {
    var editMode by remember { mutableStateOf(false) }
    var textValue by remember { mutableStateOf(customPrompt) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("伴生智能体核心提示词", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            if (editMode) {
                BasicTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 8.sp, lineHeight = 10.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = if (customPrompt.isNotBlank()) customPrompt else "【伴生定位】Wear OS 圆形表盘贴身全自主伴生智能体。\n【行动权限】自主多步反思、深度推理、ReAct 调用 6 大原生穿戴工具，直击要害，适配智能手表快速扫视场景。",
                    fontSize = 8.sp,
                    color = Color(0xFFCBD5E1),
                    lineHeight = 10.5.sp
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E2430))
                    .clickable {
                        onSavePrompt("")
                        textValue = ""
                        editMode = false
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("恢复原生", fontSize = 8.5.sp, color = Color(0xFFCBD5E1))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (editMode) Color.White else Color(0xFF1E2430))
                    .clickable {
                        if (editMode) {
                            onSavePrompt(textValue)
                            editMode = false
                        } else {
                            editMode = true
                        }
                    }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (editMode) "保存设定" else "微调自定义",
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (editMode) Color(0xFF0F172A) else Color.White
                )
            }
        }
    }
}

/**
 * 4. 上下文与长记忆管理 (动态轮数调节与持久偏好)
 */
@Composable
private fun MemoryManagerContent(
    preferences: AgentPreferences
) {
    var memoryCleared by remember { mutableStateOf(false) }
    var turns by remember { mutableIntStateOf(preferences.maxContextTurns) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("上下文与长记忆管理", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        // 上下文滑窗轮数单选
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("单会话滑窗轮数限制", fontSize = 8.5.sp, color = Color(0xFF94A3B8))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(4 to "4轮节能", 10 to "10轮标准", 30 to "30轮深度").forEach { (count, label) ->
                        val isSel = turns == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSel) Color.White else Color(0xFF1E2430))
                                .clickable {
                                    turns = count
                                    preferences.maxContextTurns = count
                                }
                                .padding(vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color(0xFF0F172A) else Color.White
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF141720))
                .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(8.dp))
                .padding(6.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("• 记忆策略：超出设定轮数后自动向前滑窗淘汰", fontSize = 8.sp, color = Color(0xFFCBD5E1))
                Text("• 永久记忆：首层提示词持久锁定长期人设与事实", fontSize = 8.sp, color = Color(0xFFCBD5E1))
                Text("• 会话隔离：支持左滑唤出抽屉独立管理与切换", fontSize = 8.sp, color = Color(0xFFCBD5E1))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (memoryCleared) Color(0xFF1E2430) else Color(0xFF261216))
                .border(0.6.dp, if (memoryCleared) Color(0xFF333D4F) else Color(0xFFEF4444), RoundedCornerShape(8.dp))
                .clickable {
                    preferences.resetAll()
                    memoryCleared = true
                }
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (memoryCleared) "已清空长记忆槽" else "一键重置长时持久记忆",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (memoryCleared) Color(0xFF94A3B8) else Color(0xFFF87171)
            )
        }
    }
}

/**
 * 5. 两翼快捷键定义
 */
@Composable
private fun WingShortcutContent(
    currentAction: RightButtonAction,
    onSelect: (RightButtonAction) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("两翼快捷按键绑定", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        val actions = listOf(
            RightButtonAction.MODEL_SWITCH to "切换大模型",
            RightButtonAction.OPEN_SETTINGS to "打开系统设置",
            RightButtonAction.CLEAR_MEMORY to "一键清空长记忆"
        )

        actions.forEach { (act, label) ->
            val isSelected = currentAction == act
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFFF8FAFC) else Color(0xFF141720))
                    .border(0.6.dp, if (isSelected) Color.White else Color(0xFF262D3D), RoundedCornerShape(8.dp))
                    .clickable { onSelect(act) }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color(0xFF0F172A) else Color.White
                    )
                    if (isSelected) {
                        Text("•", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 6. AI 安全护栏模式选择
 */
@Composable
private fun SecurityGuardrailContent(
    currentMode: SecurityMode,
    onSelect: (SecurityMode) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("AI 安全审查护栏模式", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)

        val modes = listOf(
            Triple(SecurityMode.NOVICE, "新手小白模式", "全命令审批 + 大白话后果说明 + 危险操作强制锁定滑块 3 秒"),
            Triple(SecurityMode.STANDARD, "普通人模式", "快速模型预审，无风险静默放行，有风险滑动确认"),
            Triple(SecurityMode.EXPERT, "我是大佬模式", "除致命破坏指令硬拦截外，全部极速自主执行")
        )

        modes.forEach { (mode, label, desc) ->
            val isSelected = currentMode == mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) Color(0xFFF8FAFC) else Color(0xFF141720))
                    .border(0.6.dp, if (isSelected) Color.White else Color(0xFF262D3D), RoundedCornerShape(8.dp))
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF0F172A) else Color.White
                        )
                        if (isSelected) {
                            Text("•", fontSize = 12.sp, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = desc,
                        fontSize = 7.5.sp,
                        color = if (isSelected) Color(0xFF475569) else Color(0xFF94A3B8),
                        lineHeight = 10.sp
                    )
                }
            }
        }
    }
}
