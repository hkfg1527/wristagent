package com.example.myapplication.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.agent.net.NetworkDiagnosisStore
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.agent.remote.ConsoleServer
import com.example.myapplication.presentation.ui.settings.RemoteConsoleContent
import com.example.myapplication.presentation.ui.settings.AdvancedPermissionContent
import com.example.myapplication.presentation.ui.settings.HyperParamsContent
import com.example.myapplication.presentation.ui.settings.MemoryManagerContent
import com.example.myapplication.presentation.ui.settings.ModelConfigContent
import com.example.myapplication.presentation.ui.settings.NetworkDiagnosticsContent
import com.example.myapplication.presentation.ui.settings.PromptViewerContent
import com.example.myapplication.presentation.ui.settings.SecurityGuardrailContent
import com.example.myapplication.presentation.ui.settings.SettingsActionButton
import com.example.myapplication.presentation.ui.settings.SettingsContentWidth
import com.example.myapplication.presentation.ui.settings.SettingsExitController
import com.example.myapplication.presentation.ui.settings.SettingsNavRow
import com.example.myapplication.presentation.ui.settings.SettingsRowDivider
import com.example.myapplication.presentation.ui.settings.SettingsRowStaggerState
import com.example.myapplication.presentation.ui.settings.SettingsStaggeredRow
import com.example.myapplication.presentation.ui.settings.SettingsStatusTextRes
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.SettingsTopAnchorProbe
import com.example.myapplication.presentation.ui.settings.ProviderProfilesContent
import com.example.myapplication.presentation.ui.settings.RootTitleTopPadding
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import com.example.myapplication.presentation.ui.settings.VoiceAsrContent
import com.example.myapplication.presentation.ui.settings.PullDownDismissContainer
import com.example.myapplication.presentation.ui.settings.isAtScrollTop
import com.example.myapplication.presentation.ui.settings.WingShortcutContent
import com.example.myapplication.presentation.ui.settings.rememberSettingsExitController
import com.example.myapplication.presentation.ui.settings.rememberSettingsRowStaggerState
import com.example.myapplication.presentation.ui.settings.vibrateTick
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.WearMaterialTheme
import com.example.myapplication.voice.AsrProviderId

/**
 * Wear OS 端侧 AI 设置中心
 * - 扁平单行列表：每行只有标题 / 当前值 / 行尾标记三个元素，行间只有 1dp 分隔线，没有逐行卡片
 * - 全屏只有一个视觉重点：顶部的页面标题；其余行标题统一 secondary，值与箭头统一 muted
 * - 所有取值来自 Wear 设计令牌，字号全部落在官方 M3E 阶梯上，可点区域不小于 44dp，行间距 8dp
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

    // 动画特效与图片识别内测：本页持有唯一真值，改完立刻落盘并投进 LocalAnimationsEnabled
    var animationsEnabled by remember { mutableStateOf(preferences.animationsEnabled) }
    var visionBetaEnabled by remember { mutableStateOf(preferences.visionBetaEnabled) }

    // 供应商 Profile：根页只显示当前选中的那套名字，列表本身在子页里
    var providerBook by remember { mutableStateOf(preferences.providerProfiles()) }

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
    val consoleServer = remember { ConsoleServer.create(context.applicationContext) }
    var consolePort by remember { mutableIntStateOf(consoleServer.port) }
    var consoleRunning by remember { mutableStateOf(consoleServer.isRunning) }

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

    // 顶部对齐诊断：真机看 logcat 的 SettingsTopAnchor 行即可判定"上方有没有空白"
    SettingsTopAnchorProbe(listState)

    WearMaterialTheme {
        // 下滑退出：手势只在真实触摸 + 列表已到顶时累积，表冠不产生指针事件，天然进不来。
        PullDownDismissContainer(
            modifier = modifier,
            // 下拉退出只可能由真实触摸触发（表冠不产生指针事件），且必须已滚到顶。
            // 到顶判据用 isAtScrollTop()：autoCentering 下 canScrollBackward 恒为 true（头部 Spacer 占 lazy index 0）。
            canPullDown = {
                activeDialog == null && !showVoiceAsr && !exit.isExiting && listState.isAtScrollTop()
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
                        // 官方 M3E 表冠：刻度→位移、松手惯性、平台触感（ScrollTick / ScrollLimit）全在 behavior 里。
                        // 它自带 focusTarget，焦点请求仍由 settingsFocusRequester 负责，不要再叠 focusable()。
                        .rotaryScrollable(
                            behavior = RotaryScrollableDefaults.behavior(listState),
                            focusRequester = settingsFocusRequester
                        ),
                    state = listState,
                    // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    // 关掉库自带的 rotary 与系统拉伸越界：表冠由上面的处理器接管，越界不做拉伸
                    rotaryScrollableBehavior = null,
                    overscrollEffect = null,
                    contentPadding = PaddingValues(
                        start = Wear.Space.Md,
                        end = Wear.Space.Md,
                        top = Wear.Space.Sm,
                        bottom = Wear.Size.BottomBarReserve
                    ),
                    verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
                    // itemIndex = 1：自动居中项是第一行设置项，标题不再被居中。
                    // 首项是 ListHeader 时把它留在顶部是官方推荐的用法；居中只影响两端边界，
                    // 于是"滚到最顶"时标题贴住 34dp 时钟带下沿，向上滚动离开顶部时行为与原来一致。
                    autoCentering = AutoCenteringParams(itemIndex = 1)
                ) {
                    // 顶部标题：本屏唯一的视觉重点（主色 + titleLarge 级），水平居中，压在曲面时钟带以下
                    item {
                        SettingsStaggeredRow(index = 0, stagger = rowStagger, exit = exit) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = SettingsContentWidth)
                                    .fillMaxWidth()
                                    .padding(top = RootTitleTopPadding, bottom = Wear.Space.Sm)
                                    .height(Wear.Size.Touch),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = stringResource(R.string.settings_title),
                                    style = Wear.TextDisplayTitle.copy(textAlign = TextAlign.Center),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // 1. 模型与服务：行尾状态色块表示是否已配置
                    item {
                        RootSettingRow(index = 1, stagger = rowStagger, exit = exit, dividerAbove = false) {
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

                    // 2. 供应商：多套 baseUrl / apiKey / 模型的一键套用，套用即写回单值偏好
                    item {
                        RootSettingRow(index = 2, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_provider_profiles),
                                value = providerBook.active()?.name
                                    ?: stringResource(R.string.settings_providers_none_active),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.PROVIDER_PROFILES
                                }
                            )
                        }
                    }

                    // 3. 模型参数
                    item {
                        RootSettingRow(index = 3, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 4. 内置工具与 MCP
                    item {
                        RootSettingRow(index = 4, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 5. 系统提示词
                    item {
                        RootSettingRow(index = 5, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 6. 会话与上下文
                    item {
                        RootSettingRow(index = 6, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 7. 快捷操作
                    item {
                        RootSettingRow(index = 7, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 8. 操作安全防线
                    item {
                        RootSettingRow(index = 8, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 9. 语音转写
                    item {
                        RootSettingRow(index = 9, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 10. 重新观看新手引导
                    item {
                        RootSettingRow(index = 10, stagger = rowStagger, exit = exit, dividerAbove = true) {
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
                        RootSettingRow(index = 11, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 网络体检：值显示最近一次结论，进子页后自动重新体检
                    item {
                        RootSettingRow(index = 12, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            val netDiagSummary = remember(activeDialog) {
                                NetworkDiagnosisStore(context.applicationContext).lastSummary()
                            }
                            SettingsNavRow(
                                title = stringResource(R.string.settings_netdiag_title),
                                value = when {
                                    netDiagSummary == null -> stringResource(R.string.settings_netdiag_value_never)
                                    netDiagSummary.issues == 0 -> stringResource(R.string.settings_netdiag_value_ok)
                                    else -> stringResource(
                                        R.string.settings_netdiag_value_issues,
                                        netDiagSummary.issues
                                    )
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.NETWORK_DIAGNOSTICS
                                }
                            )
                        }
                    }

                    // 第三条退出路径：不依赖手势与系统键的显式"完成返回"
                    item {
                        RootSettingRow(index = 13, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            SettingsActionButton(
                                label = stringResource(R.string.settings_done_back),
                                tone = Tone.MUTED,
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
                        SettingsSubDialog.PROVIDER_PROFILES -> ProviderProfilesContent(
                            preferences = preferences,
                            onBack = { activeDialog = null },
                            onApplied = {
                                // 套用会改写三个单值偏好：根页缓存的值与 Profile 列表都要立刻跟上
                                currentModel = preferences.modelName
                                currentBaseUrl = preferences.baseUrl
                                providerBook = preferences.providerProfiles()
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
                            },
                            animationsEnabled = animationsEnabled,
                            visionBetaEnabled = visionBetaEnabled,
                            onToggleAnimations = { enabled ->
                                animationsEnabled = enabled
                                preferences.animationsEnabled = enabled
                            },
                            onToggleVisionBeta = { enabled ->
                                visionBetaEnabled = enabled
                                preferences.visionBetaEnabled = enabled
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
                        SettingsSubDialog.REMOTE_CONSOLE -> RemoteConsoleContent(
                            onBack = { activeDialog = null }
                        )
                        SettingsSubDialog.NETWORK_DIAGNOSTICS -> NetworkDiagnosticsContent(
                            privilegeManager = privilegeManager,
                            baseUrl = { currentBaseUrl },
                            onBack = { activeDialog = null }
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

/**
 * 根页的一行：与子页骨架同构 —— 可选的 1dp 分隔线 + 行内容，整体参与逐行浮动。
 *
 * 分隔线画在同一列里而不是单独的列表项：单独的列表项会打断 [SettingsStaggeredRow] 的序号，
 * 也会在圆屏上被自动居中单独缩放。
 */
@Composable
private fun RootSettingRow(
    index: Int,
    stagger: SettingsRowStaggerState,
    exit: SettingsExitController,
    dividerAbove: Boolean,
    content: @Composable () -> Unit
) {
    SettingsStaggeredRow(index = index, stagger = stagger, exit = exit) {
        Column(modifier = Modifier.widthIn(max = SettingsContentWidth)) {
            if (dividerAbove) SettingsRowDivider()
            content()
        }
    }
}
