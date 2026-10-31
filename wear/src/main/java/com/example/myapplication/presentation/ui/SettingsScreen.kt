package com.example.myapplication.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
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
import com.example.myapplication.presentation.ui.settings.SettingsActionButton
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.SubPageBottomReserve
import com.example.myapplication.presentation.ui.settings.SubSettingsModal
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import com.example.myapplication.presentation.ui.settings.VoiceAsrContent
import com.example.myapplication.presentation.ui.settings.WingShortcutContent
import com.example.myapplication.presentation.ui.settings.PullDownDismissContainer
import com.example.myapplication.presentation.ui.settings.vibrateTick
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.voice.AsrProviderId
import kotlin.math.abs

/** 圆屏内可用内容宽度上限：最宽处仍留出 36dp 边距，chevron 不贴屏幕外缘。 */
private val SettingsContentMaxWidth = 168.dp

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

    // 系统 BACK 三层化：子页打开时只关子页并留在设置列表，不退出整个设置页。
    // 顺序保证：本回调在 SettingsScreen 组合体内注册，MainActivity 的 SETTINGS 分支 BackHandler
    // 早于 SettingsScreen 调用（即更早注册），OnBackPressedDispatcher 后注册者优先，故 BACK 先命中这里。
    BackHandler(enabled = activeDialog != null || showVoiceAsr) {
        if (showVoiceAsr) {
            showVoiceAsr = false
        } else {
            activeDialog = null
        }
    }

    val listState = rememberScalingLazyListState()

    // 到顶继续下滑越界 → tick → 过阈值回主界面；阈值/震动与主界面底部越界共用同一份常量
    val topOverscroll = rememberEdgeOverscroll(
        overflowSign = 1f,
        atEdge = {
            !listState.canScrollBackward ||
                (listState.centerItemIndex == 0 && listState.centerItemScrollOffset == 0)
        },
        onCross = onBack
    )

    // 下滑退出：收手势的外层节点不位移，位移只作用在内层内容节点，避免局部坐标与自身平移互相放大。
    PullDownDismissContainer(
        modifier = modifier,
        enabled = activeDialog == null && !showVoiceAsr,
        // 到顶判定：列表已无法向后滚动（等价于 firstVisibleItemIndex==0 && ScrollOffset==0，
        // ScalingLazyListState 只把这些量暴露为 canScrollBackward），再加首项正落在垂直中心兜底，
        // 避免自动居中初始化时残留像素级偏移导致手势失效。
        atTop = {
            !listState.canScrollBackward ||
                (listState.centerItemIndex == 0 && listState.centerItemScrollOffset == 0)
        },
        onDismiss = onBack
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Wear.Palette.Bg)
        ) {
            val settingsFocusRequester = remember { FocusRequester() }

            // 表冠要焦点：等节点真正挂上树的那一帧再请求，首帧直接请求会落到未初始化的 requester 上
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { settingsFocusRequester.requestFocus() }
            }

            // 模型与服务分区的真实配置状态：以 API Key 是否填写为准
            val apiKeyConfigured = preferences.apiKey.isNotBlank()

            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    // 到顶继续下滑的越界位移不进滚动，只做震动与过阈值返回
                    .nestedScroll(topOverscroll.connection)
                    // 顺序即作用域：处理器必须在 focusable 之外，否则 rotary 事件冒不进来
                    .onRotaryScrollEvent {
                        // 表冠：同步吃位移（dispatchRawDelta 不受 scroll 互斥锁排队影响，每格刻度都生效）
                        val consumed = listState.dispatchRawDelta(it.verticalScrollPixels)
                        if (abs(it.verticalScrollPixels) > 10f) {
                            context.vibrateTick()
                        }
                        // 已到顶还继续向下转：剩余位移交给越界手感累计
                        val leftover = it.verticalScrollPixels - consumed
                        if (leftover != 0f) topOverscroll.push(leftover, rotary = true)
                        true
                    }
                    .focusRequester(settingsFocusRequester)
                    .focusable(),
                state = listState,
                // 关掉库自带的 rotary 与系统拉伸越界：表冠/越界统一由上面的处理器与 nestedScroll 接管
                rotaryScrollableBehavior = null,
                overscrollEffect = null,
                contentPadding = PaddingValues(horizontal = Wear.Space.Edge, vertical = Wear.Space.Sm),
                verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs),
                // 圆屏官方做法：首项默认对齐视口垂直中心，上下边缘随曲率缩小淡出
                autoCentering = AutoCenteringParams(itemIndex = 0)
            ) {
                // 顶部标题：水平居中，不再在圆屏最外圈放叉号（退出走下划 / 系统 BACK / 末尾完成返回）
                item {
                    Box(
                        modifier = Modifier
                            .widthIn(max = SettingsContentMaxWidth)
                            .fillMaxWidth()
                            .padding(top = Wear.Size.TopContentInset, bottom = Wear.Space.Sm)
                            .height(Wear.Size.Touch),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            text = "系统设置",
                            style = Wear.TextTitle.copy(textAlign = TextAlign.Center),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

                // 6. 快捷操作
                item {
                    SettingsEntry(
                        title = "底栏副键",
                        // 右翼已固定为会话抽屉入口，不再由偏好项决定，此处为只读说明
                        value = "右翼固定打开会话抽屉",
                        onClick = {
                            context.vibrateTick()
                            activeDialog = SettingsSubDialog.WING_SHORTCUT
                        }
                    )
                }

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                item { WearDivider(modifier = Modifier.widthIn(max = SettingsContentMaxWidth)) }

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

                // 第三条退出路径：不依赖手势与系统键的显式"完成返回"
                item {
                    SettingsActionButton(
                        label = "完成返回",
                        tone = Tone.ACCENT,
                        modifier = Modifier.widthIn(max = SettingsContentMaxWidth),
                        onClick = onBack
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
            .widthIn(max = SettingsContentMaxWidth)
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