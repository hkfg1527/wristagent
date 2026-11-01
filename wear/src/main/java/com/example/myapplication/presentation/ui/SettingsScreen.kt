package com.example.myapplication.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.presentation.ui.settings.AdvancedPermissionContent
import com.example.myapplication.presentation.ui.settings.HyperParamsContent
import com.example.myapplication.presentation.ui.settings.MemoryManagerContent
import com.example.myapplication.presentation.ui.settings.ModelConfigContent
import com.example.myapplication.presentation.ui.settings.PromptViewerContent
import com.example.myapplication.presentation.ui.settings.SecurityGuardrailContent
import com.example.myapplication.presentation.ui.settings.SettingsActionButton
import com.example.myapplication.presentation.ui.settings.SettingsContentWidth
import com.example.myapplication.presentation.ui.settings.SettingsNavRow
import com.example.myapplication.presentation.ui.settings.SettingsStaggeredRow
import com.example.myapplication.presentation.ui.settings.SettingsStatusTextRes
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import com.example.myapplication.presentation.ui.settings.VoiceAsrContent
import com.example.myapplication.presentation.ui.settings.PullDownDismissContainer
import com.example.myapplication.presentation.ui.settings.WingShortcutContent
import com.example.myapplication.presentation.ui.settings.rememberSettingsExitController
import com.example.myapplication.presentation.ui.settings.rememberSettingsRowStaggerState
import com.example.myapplication.presentation.ui.settings.vibrateTick
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.WearMaterialTheme
import com.example.myapplication.voice.AsrProviderId
import kotlin.math.abs

/**
 * Wear OS 端侧 AI 设置中心
 * - 扁平单行列表：每个分区一行（标题 + 当前值 + chevron），副标题与徽章同行且徽章宽度封顶
 * - 所有取值来自 Wear 设计令牌，字号最小 12sp，可点区域不小于 44dp，行间距 8dp
 * - ScalingLazyColumn 圆屏曲率自适应与物理表冠联动
 * - 进出场为逐行浮动：每行按自己的内容序号延迟浮起/沉下，容器只做极轻淡入淡出
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

    // 逐行浮动：入场记账本（滚动复用不重播）+ 退场调度器（沉完才切屏）
    val rowStagger = rememberSettingsRowStaggerState()
    val exit = rememberSettingsExitController()

    // 特权层：随设置页存活（进入时探测一次、离开时释放监听），
    // 状态用 StateFlow 收集，列表行只显示当前后端名，不参与任何系统调用。
    val privilegeManager = remember { PrivilegeManager(context) }
    val privilegeStatus by privilegeManager.status.collectAsState()
    DisposableEffect(privilegeManager) {
        privilegeManager.start()
        onDispose { privilegeManager.release() }
    }

    // 四条退出路径（下滑、系统 BACK、末尾完成返回、重看引导）统一走这里：先播沉出再执行返回动作
    val requestExit: (() -> Unit) -> Unit = { action -> exit.begin(action) }

    // 系统 BACK 三层化：子页打开时只关子页并留在设置列表，不退出整个设置页。
    BackHandler(enabled = activeDialog != null || showVoiceAsr) {
        if (showVoiceAsr) {
            showVoiceAsr = false
        } else {
            activeDialog = null
        }
    }

    // 根页 BACK 也走逐行沉出。退场中本处理器仍然拦截（begin 忽略重复请求），
    // 否则连按会落到外层把动画打断成硬切。
    BackHandler(enabled = activeDialog == null && !showVoiceAsr) {
        requestExit(onBack)
    }

    val listState = rememberScalingLazyListState()

    WearMaterialTheme {
        // 下滑退出：收手势的外层节点不位移，位移只作用在内层内容节点，避免局部坐标与自身平移互相放大。
        PullDownDismissContainer(
            modifier = modifier,
            enabled = activeDialog == null && !showVoiceAsr && !exit.isExiting,
            atTop = {
                !listState.canScrollBackward ||
                    (listState.centerItemIndex == 0 && listState.centerItemScrollOffset == 0)
            },
            onDismiss = { requestExit(onBack) }
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
                        // 顺序即作用域：处理器必须在 focusable 之外，否则 rotary 事件冒不进来
                        .onRotaryScrollEvent {
                            // 表冠：同步吃位移（dispatchRawDelta 不受 scroll 互斥锁排队影响，每格刻度都生效）
                            listState.dispatchRawDelta(it.verticalScrollPixels)
                            if (abs(it.verticalScrollPixels) > 10f) {
                                context.vibrateTick()
                            }
                            true
                        }
                        .focusRequester(settingsFocusRequester)
                        .focusable(),
                    state = listState,
                    // 关掉库自带的 rotary 与系统拉伸越界：表冠/越界统一由上面的处理器与 nestedScroll 接管
                    rotaryScrollableBehavior = null,
                    overscrollEffect = null,
                    contentPadding = PaddingValues(
                        start = Wear.Space.Md,
                        end = Wear.Space.Md,
                        top = Wear.Space.Sm,
                        bottom = Wear.Size.BottomBarReserve
                    ),
                    verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
                    // 圆屏官方做法：首项默认对齐视口垂直中心，上下边缘随曲率缩小淡出
                    autoCentering = AutoCenteringParams(itemIndex = 0)
                ) {
                    // 顶部标题：水平居中，整块压在曲面时钟带以下
                    item {
                        SettingsStaggeredRow(index = 0, stagger = rowStagger, exit = exit) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = SettingsContentWidth)
                                    .fillMaxWidth()
                                    .padding(top = Wear.Size.TopContentInset, bottom = Wear.Space.Sm)
                                    .height(Wear.Size.Touch),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = stringResource(R.string.settings_title),
                                    style = Wear.TextTitle.copy(textAlign = TextAlign.Center),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 1. 模型与服务：标题独占首行，副标题与徽章同行，徽章宽度封顶后副标题恒 >= 96dp
                    item {
                        SettingsStaggeredRow(index = 1, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_model_and_service),
                                value = currentModel,
                                badge = SettingsStatusTextRes.modelServiceBadge(apiKeyConfigured).let { (res, tone) ->
                                    stringResource(res) to tone
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.MODEL_CONFIG
                                }
                            )
                        }
                    }

                    // 2. 模型参数
                    item {
                        SettingsStaggeredRow(index = 2, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_model_params),
                                value = "Temp %.1f".format(currentTemp),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.HYPER_PARAMS
                                }
                            )
                        }
                    }

                    // 3. 内置工具与 MCP
                    item {
                        SettingsStaggeredRow(index = 3, stagger = rowStagger, exit = exit) {
                            val activeCount = listOf(
                                preferences.toolDeviceStatusEnabled,
                                preferences.toolVibrateEnabled,
                                preferences.toolMatrixDensityEnabled,
                                preferences.toolPowerModeEnabled,
                                preferences.toolWeatherEnabled,
                                preferences.toolWebSearchEnabled
                            ).count { it }
                            SettingsNavRow(
                                title = stringResource(R.string.settings_tools_and_mcp),
                                value = stringResource(R.string.settings_tools_enabled_count, activeCount),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.TOOLS_MCP
                                }
                            )
                        }
                    }

                    // 4. 系统提示词
                    item {
                        SettingsStaggeredRow(index = 4, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_system_prompt),
                                value = if (customPrompt.isBlank()) {
                                    stringResource(R.string.settings_prompt_default)
                                } else {
                                    stringResource(R.string.settings_prompt_custom)
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.PROMPT_VIEWER
                                }
                            )
                        }
                    }

                    // 5. 会话与上下文
                    item {
                        SettingsStaggeredRow(index = 5, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_memory_management),
                                value = stringResource(R.string.settings_memory_turns, preferences.maxContextTurns),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.MEMORY_MANAGER
                                }
                            )
                        }
                    }

                    // 6. 快捷操作
                    item {
                        SettingsStaggeredRow(index = 6, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_side_key),
                                // 右翼已固定为会话抽屉入口，不再由偏好项决定，此处为只读说明
                                value = stringResource(R.string.settings_side_key_value),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.WING_SHORTCUT
                                }
                            )
                        }
                    }

                    // 7. 操作安全防线
                    item {
                        SettingsStaggeredRow(index = 7, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_security_policy),
                                value = when (currentSecurityMode) {
                                    SecurityMode.NOVICE -> stringResource(R.string.settings_mode_strict)
                                    SecurityMode.STANDARD -> stringResource(R.string.settings_mode_standard)
                                    SecurityMode.EXPERT -> stringResource(R.string.settings_mode_auto)
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.SECURITY_GUARDRAIL
                                }
                            )
                        }
                    }

                    // 8. 语音转写
                    item {
                        SettingsStaggeredRow(index = 8, stagger = rowStagger, exit = exit) {
                            val asrProvider = AsrProviderId.fromStorage(asrProviderKey)
                            SettingsNavRow(
                                title = stringResource(R.string.settings_asr),
                                value = when {
                                    !asrEnabled -> stringResource(R.string.settings_asr_off)
                                    asrProvider == AsrProviderId.BAIDU && !asrKeyReady -> stringResource(R.string.settings_asr_key_missing)
                                    asrProvider == AsrProviderId.BAIDU -> stringResource(R.string.settings_asr_baidu)
                                    else -> stringResource(R.string.settings_asr_system)
                                },
                                onClick = {
                                    context.vibrateTick()
                                    showVoiceAsr = true
                                }
                            )
                        }
                    }

                    // 9. 重新观看新手引导
                    item {
                        SettingsStaggeredRow(index = 9, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_replay_onboarding),
                                onClick = {
                                    context.vibrateTick()
                                    requestExit(onRestartOnboarding)
                                }
                            )
                        }
                    }

                    // 高级权限：Root / Shizuku 双后端的状态与授权入口
                    item {
                        SettingsStaggeredRow(index = 10, stagger = rowStagger, exit = exit) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_privilege_title),
                                value = privilegeStatus.activeBackend.name,
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.PRIVILEGE
                                }
                            )
                        }
                    }

                    // 第三条退出路径：不依赖手势与系统键的显式"完成返回"
                    item {
                        SettingsStaggeredRow(index = 11, stagger = rowStagger, exit = exit) {
                            SettingsActionButton(
                                label = stringResource(R.string.settings_done_back),
                                tone = Tone.ACCENT,
                                modifier = Modifier.widthIn(max = SettingsContentWidth),
                                onClick = { requestExit(onBack) }
                            )
                        }
                    }
                }

                // ================= 二级配置页 =================
                activeDialog?.let { dialog ->
                    when (dialog) {
                        SettingsSubDialog.MODEL_CONFIG -> ModelConfigContent(
                            preferences = preferences,
                            currentModel = currentModel,
                            currentBaseUrl = currentBaseUrl,
                            onBack = { activeDialog = null },
                            onSelect = { model, base ->
                                currentModel = model
                                currentBaseUrl = base
                                preferences.modelName = model
                                preferences.baseUrl = base
                            }
                        )
                        SettingsSubDialog.HYPER_PARAMS -> HyperParamsContent(
                            preferences = preferences,
                            onBack = { activeDialog = null },
                            onParamsChanged = { temp, tokens, stream ->
                                currentTemp = temp
                                currentMaxTokens = tokens
                                currentStreamMode = stream
                            }
                        )
                        SettingsSubDialog.TOOLS_MCP -> ToolsMcpContent(
                            preferences = preferences,
                            isMcpEnabled = isMcpEnabled,
                            mcpEndpoint = mcpEndpoint,
                            onBack = { activeDialog = null },
                            onToggleMcp = { enabled, ep ->
                                isMcpEnabled = enabled
                                mcpEndpoint = ep
                                preferences.isMcpEnabled = enabled
                                preferences.mcpEndpoint = ep
                            }
                        )
                        SettingsSubDialog.PROMPT_VIEWER -> PromptViewerContent(
                            customPrompt = customPrompt,
                            onBack = { activeDialog = null },
                            onSavePrompt = { prompt ->
                                customPrompt = prompt
                                preferences.customSystemPrompt = prompt
                            }
                        )
                        SettingsSubDialog.MEMORY_MANAGER -> MemoryManagerContent(
                            preferences = preferences,
                            onBack = { activeDialog = null }
                        )
                        SettingsSubDialog.WING_SHORTCUT -> WingShortcutContent(
                            currentAction = currentRightAction,
                            onBack = { activeDialog = null },
                            onSelect = { action ->
                                currentRightAction = action
                                preferences.rightButtonAction = action
                            }
                        )
                        SettingsSubDialog.PRIVILEGE -> AdvancedPermissionContent(
                            manager = privilegeManager,
                            onBack = { activeDialog = null }
                        )
                        SettingsSubDialog.SECURITY_GUARDRAIL -> SecurityGuardrailContent(
                            currentMode = currentSecurityMode,
                            onBack = { activeDialog = null },
                            onSelect = { mode ->
                                currentSecurityMode = mode
                                preferences.securityMode = mode
                            }
                        )
                    }
                }

                if (showVoiceAsr) {
                    VoiceAsrContent(
                        preferences = preferences,
                        onBack = { showVoiceAsr = false },
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
