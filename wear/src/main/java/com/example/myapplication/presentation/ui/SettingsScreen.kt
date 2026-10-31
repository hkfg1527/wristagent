package com.example.myapplication.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBadge
import com.example.myapplication.presentation.ui.components.WearDivider
import com.example.myapplication.presentation.ui.settings.HyperParamsContent
import com.example.myapplication.presentation.ui.settings.MemoryManagerContent
import com.example.myapplication.presentation.ui.settings.ModelConfigContent
import com.example.myapplication.presentation.ui.settings.PromptViewerContent
import com.example.myapplication.presentation.ui.settings.SecurityGuardrailContent
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.SubPageBottomReserve
import com.example.myapplication.presentation.ui.settings.SubSettingsModal
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import com.example.myapplication.presentation.ui.settings.VoiceAsrContent
import com.example.myapplication.presentation.ui.settings.WingShortcutContent
import com.example.myapplication.presentation.ui.settings.vibrateTick
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.voice.AsrProviderId
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Wear OS 端侧 AI 设置中心
 * - 扁平单行列表：每个分区一行（标题 + 当前值 + chevron），分区之间以分隔线切分
 * - 所有取值来自 Wear 设计令牌，字号最小 12sp，可点区域不小于 44dp
 * - ScalingLazyColumn 圆屏曲率自适应与物理表冠联动
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

    var asrEnabled by remember { mutableStateOf(preferences.asrEnabled) }
    var asrProviderKey by remember { mutableStateOf(preferences.asrProvider) }
    var asrKeyReady by remember { mutableStateOf(preferences.asrSettings().hasBaiduCredentials) }

    // 二级弹窗状态
    var activeDialog by remember { mutableStateOf<SettingsSubDialog?>(null) }
    var showVoiceAsr by remember { mutableStateOf(false) }

    // 右滑返回手势 (基于增量物理位移防抖，停止滑动绝对无闪烁)
    val dragOffsetX = remember { Animatable(0f) }
    var screenWidthPx by remember { mutableFloatStateOf(480f) }
    val dismissThresholdPx = with(density) { 50.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
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

        // 模型与服务分区的真实配置状态：以 API Key 是否填写为准
        val apiKeyConfigured = preferences.apiKey.isNotBlank()

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
            contentPadding = PaddingValues(horizontal = Wear.Space.Edge, vertical = Wear.Space.Sm),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
        ) {
            // 顶部标题与返回键
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Wear.Size.TopContentInset, bottom = Wear.Space.Sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                ) {
                    BasicText(
                        text = "系统设置",
                        modifier = Modifier.weight(1f),
                        style = Wear.TextTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    CloseButton(onClick = onBack)
                }
            }

            // 1. 模型与服务：标题独占首行，值 + 徽章置于次行，徽章按真实 API Key 配置状态渲染
            item {
                SettingsEntry(
                    title = "模型与服务",
                    value = currentModel,
                    badge = if (apiKeyConfigured) "已配置" to Tone.ACCENT else "未配置" to Tone.DANGER,
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.MODEL_CONFIG
                    }
                )
            }

            item { WearDivider() }

            // 2. 模型参数
            item {
                SettingsEntry(
                    title = "模型参数",
                    value = "Temp %.1f".format(currentTemp),
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.HYPER_PARAMS
                    }
                )
            }

            item { WearDivider() }

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
                SettingsEntry(
                    title = "端侧工具与 MCP",
                    value = "$activeCount/6 已启用",
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.TOOLS_MCP
                    }
                )
            }

            item { WearDivider() }

            // 4. 系统提示词
            item {
                SettingsEntry(
                    title = "系统提示词",
                    value = if (customPrompt.isBlank()) "预置默认" else "自定义",
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.PROMPT_VIEWER
                    }
                )
            }

            item { WearDivider() }

            // 5. 会话与上下文
            item {
                SettingsEntry(
                    title = "会话记忆管理",
                    value = "${preferences.maxContextTurns} 轮",
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.MEMORY_MANAGER
                    }
                )
            }

            item { WearDivider() }

            // 6. 快捷操作
            item {
                SettingsEntry(
                    title = "底栏副键绑定",
                    value = when (currentRightAction) {
                        RightButtonAction.MODEL_SWITCH -> "切换大模型"
                        RightButtonAction.OPEN_SETTINGS -> "打开设置"
                        RightButtonAction.CLEAR_MEMORY -> "清空记忆"
                    },
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.WING_SHORTCUT
                    }
                )
            }

            item { WearDivider() }

            // 7. 操作安全防线
            item {
                SettingsEntry(
                    title = "操作安全策略",
                    value = when (currentSecurityMode) {
                        SecurityMode.NOVICE -> "严格确认"
                        SecurityMode.STANDARD -> "标准提示"
                        SecurityMode.EXPERT -> "自动执行"
                    },
                    onClick = {
                        context.vibrateTick()
                        activeDialog = SettingsSubDialog.SECURITY_GUARDRAIL
                    }
                )
            }

            item { WearDivider() }

            // 8. 语音转写
            item {
                val asrProvider = AsrProviderId.fromStorage(asrProviderKey)
                SettingsEntry(
                    title = "语音转写",
                    value = when {
                        !asrEnabled -> "已关闭"
                        asrProvider == AsrProviderId.BAIDU && !asrKeyReady -> "密钥未填写"
                        asrProvider == AsrProviderId.BAIDU -> "百度短语音"
                        else -> "系统识别"
                    },
                    onClick = {
                        context.vibrateTick()
                        showVoiceAsr = true
                    }
                )
            }

            item { WearDivider() }

            // 9. 重新观看新手引导
            item {
                SettingsEntry(
                    title = "重新观看操作引导",
                    onClick = {
                        context.vibrateTick()
                        onRestartOnboarding()
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(SubPageBottomReserve))
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

        if (showVoiceAsr) {
            SubSettingsModal(
                title = "语音转写",
                onDismiss = { showVoiceAsr = false }
            ) {
                VoiceAsrContent(
                    preferences = preferences,
                    onSettingsChanged = { settings ->
                        asrEnabled = settings.enabled
                        asrProviderKey = settings.providerId.storageKey
                        asrKeyReady = settings.hasBaiduCredentials
                    }
                )
            }
        }
    }
}

/**
 * 扁平列表项：标题独占首行（右手侧只有 chevron），值文本与徽章统一放到次行，
 * 标题可用宽度恒为「行内宽 − chevron − 间距」，不会被值挤压成省略号。
 */
@Composable
private fun SettingsEntry(
    title: String,
    onClick: () -> Unit,
    value: String? = null,
    badge: Pair<String, Tone>? = null,
) {
    val shape = RoundedCornerShape(Wear.Radius.Chip)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Wear.Size.Touch)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            BasicText(
                text = title,
                modifier = Modifier.weight(1f),
                style = Wear.TextBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ChevronMark()
        }
        if (value != null || badge != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
            ) {
                if (value != null) {
                    BasicText(
                        text = value,
                        modifier = Modifier.weight(1f),
                        style = Wear.TextCaption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (badge != null) {
                    WearBadge(text = badge.first, tone = badge.second)
                }
            }
        }
    }
}

/** 44dp Canvas 叉号关闭键。 */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(Wear.Space.Md)) {
            val inset = size.minDimension * 0.25f
            val stroke = Wear.Size.BadgeHeight.toPx() * 0.1f
            drawLine(
                color = Wear.Palette.TextSecondary,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Wear.Palette.TextSecondary,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}

/** 右向折线箭头，与列表行一致。 */
@Composable
private fun ChevronMark() {
    Canvas(modifier = Modifier.size(Wear.Space.Md)) {
        val stroke = Wear.Size.BadgeHeight.toPx() * 0.1f
        drawLine(
            color = Wear.Palette.TextMuted,
            start = Offset(size.width * 0.3f, size.height * 0.2f),
            end = Offset(size.width * 0.7f, size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Wear.Palette.TextMuted,
            start = Offset(size.width * 0.7f, size.height * 0.5f),
            end = Offset(size.width * 0.3f, size.height * 0.8f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}
