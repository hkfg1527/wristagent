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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.agent.net.NetworkDiagnosisStore
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.agent.remote.ConsoleServer
import com.example.myapplication.presentation.ui.settings.AppearanceContent
import com.example.myapplication.presentation.ui.settings.RemoteConsoleContent
import com.example.myapplication.presentation.ui.settings.VoiceEntryContent
import com.example.myapplication.presentation.ui.settings.SettingsItemGap
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
import com.example.myapplication.presentation.ui.settings.SettingsEdgeScrim
import com.example.myapplication.presentation.ui.settings.SettingsSectionHeader
import com.example.myapplication.presentation.ui.settings.settingsSideInset
import com.example.myapplication.presentation.ui.settings.SettingsStaggeredRow
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.SettingsTopAnchorProbe
import com.example.myapplication.presentation.ui.settings.ProviderProfilesContent
import com.example.myapplication.presentation.ui.settings.RootContentTopPadding
import com.example.myapplication.presentation.ui.settings.RootTitleTopPadding
import com.example.myapplication.presentation.ui.settings.AboutContent
import com.example.myapplication.presentation.ui.settings.PromptContextContent
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

                // 表冠只把事件送给持有焦点的节点，必须显式 requestFocus() 一次。
                // 根页在整个开关过程中一直留在树里：LaunchedEffect(Unit) 只跑一次，从子页返回后
                // 不会重跑，表冠就此失效 —— 所以 key 绑在 activeDialog 上，回到根页时重新取焦。
                // 二级页/语音页盖上来时主动让出焦点，不抢。
                LaunchedEffect(activeDialog, showVoiceAsr) {
                    if (activeDialog != null || showVoiceAsr) return@LaunchedEffect
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
                            // 表冠：同步吃位移，每格刻度都生效
                            listState.dispatchRawDelta(it.verticalScrollPixels)
                            true
                        }
                        .focusRequester(settingsFocusRequester)
                        .focusable(),
                    state = listState,
                    // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
                    flingBehavior = ScrollableDefaults.flingBehavior(),
                    // 关掉库自带的 rotary 与系统拉伸越界：表冠由上面的处理器接管，越界不做拉伸
                    rotaryScrollableBehavior = null,
                    overscrollEffect = null,
                    // 曲率缩放关死：边缘行不再缩到 75% 并淡出。真机实测同一分隔线三种宽度
                    // （168.0 / 142.0 / 126.0dp）、行标题左边缘从 41.0 漂到 52.5dp，唯一根因就是
                    contentPadding = PaddingValues(
                        // 左右内缩按圆周弦长推导（真机 32.5dp），不再写死 12dp
                        start = settingsSideInset(),
                        end = settingsSideInset(),
                        // 顶部只让给 24dp 渐变遮罩（本页没有曲面时钟，不必让 34dp）
                        top = RootContentTopPadding,
                        // 设置页没有主键：底部预留取 44dp 而不是聊天页的 76dp
                        bottom = Wear.Size.SettingsBottomInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(SettingsItemGap),
                    // 关掉 autoCentering：标题行不再撑 44dp 触摸高度后，itemIndex = 1 会让库
                    // 在首项之上插入约 60dp 的 spacer（233dp 屏），整列被推下半个屏幕。
                    // 传 null 后顶部锚点 = contentPadding.top = 24dp 遮罩下沿，位置只由常量决定。
                    autoCentering = null
                ) {
                    // 顶部标题：本屏唯一的视觉重点（主色 + titleLarge 级），左对齐到与所有行同一条轴
                    item {
                        SettingsStaggeredRow(index = 0, stagger = rowStagger, exit = exit) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = SettingsContentWidth)
                                    .fillMaxWidth()
                                    // 不再撑 44dp 触摸高度：标题本身不可点，撑高只会把
                                    // 顶部黑带从 12dp 顶回 41dp（旧版实测 0..41.0dp = 屏高 17.6%）
                                    .padding(top = RootTitleTopPadding, bottom = Wear.Space.Sm),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    text = stringResource(R.string.settings_title),
                                    style = Wear.TextDisplayTitle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // ================= 模型 =================
                    item {
                        RootSectionRow(index = 1, stagger = rowStagger, exit = exit) {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_models))
                        }
                    }

                    // 1. 模型与服务：取值改状态词（模型名进子页），行尾不再挂状态点 ——
                    //    旧版取值是裸模型名，把标题压到折行、自己又被截成 "deepseek-…"
                    item {
                        RootSettingRow(index = 2, stagger = rowStagger, exit = exit, dividerAbove = false) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_model_and_service),
                                value = if (apiKeyConfigured) {
                                    stringResource(R.string.settings_status_configured)
                                } else {
                                    stringResource(R.string.settings_status_unconfigured)
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
                        RootSettingRow(index = 3, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 3. 模型参数：温度文案走资源，en/zh 同步
                    item {
                        RootSettingRow(index = 4, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_model_params),
                                value = stringResource(R.string.settings_temperature_value, currentTemp),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.HYPER_PARAMS
                                }
                            )
                        }
                    }

                    // ================= 语音与工具 =================
                    item {
                        RootSectionRow(index = 5, stagger = rowStagger, exit = exit) {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_voice_tools))
                        }
                    }

                    // 4. 语音：语音转写与"语音与唤醒"合并成一行，转写状态做取值，唤醒入口在子页里
                    item {
                        RootSettingRow(index = 6, stagger = rowStagger, exit = exit, dividerAbove = false) {
                            val asrProvider = AsrProviderId.fromStorage(asrProviderKey)
                            SettingsNavRow(
                                title = stringResource(R.string.settings_voice_entry),
                                value = when {
                                    !asrEnabled -> stringResource(R.string.settings_asr_off)
                                    asrProvider == AsrProviderId.BAIDU && !asrKeyReady ->
                                        stringResource(R.string.settings_asr_key_missing)
                                    asrProvider == AsrProviderId.BAIDU -> stringResource(R.string.settings_asr_baidu)
                                    else -> stringResource(R.string.settings_asr_system)
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.VOICE_ENTRY
                                }
                            )
                        }
                    }

                    // 5. 内置工具与 MCP：取值改"全部启用"，不再暴露 6/6 这种内部计数
                    item {
                        RootSettingRow(index = 7, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            val enabledCount = listOf(
                                preferences.toolDeviceStatusEnabled,
                                preferences.toolVibrateEnabled,
                                preferences.toolMatrixDensityEnabled,
                                preferences.toolPowerModeEnabled,
                                preferences.toolWeatherEnabled,
                                preferences.toolWebSearchEnabled
                            ).count { it }
                            SettingsNavRow(
                                title = stringResource(R.string.settings_tools_and_mcp),
                                value = if (enabledCount == 6) {
                                    stringResource(R.string.settings_tools_all_enabled)
                                } else {
                                    stringResource(R.string.settings_tools_enabled_count, enabledCount)
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.TOOLS_MCP
                                }
                            )
                        }
                    }

                    // ================= 系统 =================
                    item {
                        RootSectionRow(index = 8, stagger = rowStagger, exit = exit) {
                            SettingsSectionHeader(text = stringResource(R.string.settings_section_system))
                        }
                    }

                    // 6. 提示词：系统提示词与会话上下文合并成一行，两者都是同一层的"模型行为"配置
                    item {
                        RootSettingRow(index = 9, stagger = rowStagger, exit = exit, dividerAbove = false) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_prompt_context),
                                value = if (customPrompt.isBlank()) {
                                    stringResource(R.string.settings_prompt_default)
                                } else {
                                    stringResource(R.string.settings_prompt_custom)
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.PROMPT_CONTEXT
                                }
                            )
                        }
                    }

                    // 8. 操作安全防线
                    item {
                        RootSettingRow(index = 13, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_remote_console_title),
                                value = if (consoleServer.isRunning) ":" + consoleServer.port else null,
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.REMOTE_CONSOLE
                                }
                            )
                        }
                    }

                    item {
                        RootSettingRow(index = 10, stagger = rowStagger, exit = exit, dividerAbove = true) {
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

                    // 7. 外观与点阵：取值原先是"matrixSpacingDp"这种内部实现量，已删除
                    item {
                        RootSettingRow(index = 11, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_appearance),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.APPEARANCE
                                }
                            )
                        }
                    }

                    // 8. 关于：快捷操作、重看引导、特权执行、网络体检四条低频项收进一层
                    item {
                        RootSettingRow(index = 12, stagger = rowStagger, exit = exit, dividerAbove = true) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_about),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.ABOUT
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

                // 顶部 24dp / 底部 28dp 渐变遮罩：内容滚到圆屏两端时淡出，而不是硬切或被圆周裁掉
                SettingsEdgeScrim()

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
                        SettingsSubDialog.VOICE_ENTRY -> VoiceEntryContent(
                            preferences = preferences,
                            onBack = { activeDialog = null },
                            onOpenAsr = {
                                // 语音转写页是独立浮层：先把子弹窗收掉，避免两层同时挂树
                                activeDialog = null
                                showVoiceAsr = true
                            }
                        )
                        SettingsSubDialog.APPEARANCE -> AppearanceContent(
                            preferences = preferences,
                            onBack = { activeDialog = null }
                        )
                        SettingsSubDialog.PROMPT_CONTEXT -> PromptContextContent(
                            promptValue = if (customPrompt.isBlank()) {
                                stringResource(R.string.settings_prompt_default)
                            } else {
                                stringResource(R.string.settings_prompt_custom)
                            },
                            turnsValue = stringResource(
                                R.string.settings_memory_turns,
                                preferences.maxContextTurns
                            ),
                            onBack = { activeDialog = null },
                            promptPage = { dismiss ->
                                PromptViewerContent(
                                    customPrompt = customPrompt,
                                    onBack = dismiss,
                                    onSavePrompt = { prompt ->
                                        customPrompt = prompt
                                        preferences.customSystemPrompt = prompt
                                    }
                                )
                            },
                            memoryPage = { dismiss ->
                                MemoryManagerContent(preferences = preferences, onBack = dismiss)
                            }
                        )
                        SettingsSubDialog.ABOUT -> {
                            // 网络体检结论与根页同源：进来时读一次最近结果，子页里再重新体检
                            val netDiagSummary = remember(activeDialog) {
                                NetworkDiagnosisStore(context.applicationContext).lastSummary()
                            }
                            AboutContent(
                                sideKeyValue = stringResource(R.string.settings_side_key_value),
                                privilegeValue = privilegeStatus.activeBackend.name,
                                netDiagValue = when {
                                    netDiagSummary == null ->
                                        stringResource(R.string.settings_netdiag_value_never)
                                    netDiagSummary.issues == 0 ->
                                        stringResource(R.string.settings_netdiag_value_ok)
                                    else -> stringResource(
                                        R.string.settings_netdiag_value_issues,
                                        netDiagSummary.issues
                                    )
                                },
                                onBack = { activeDialog = null },
                                onReplayOnboarding = { requestExit(onRestartOnboarding) },
                                sideKeyPage = { dismiss ->
                                    WingShortcutContent(
                                        currentAction = currentRightAction,
                                        onBack = dismiss,
                                        onSelect = { action ->
                                            currentRightAction = action
                                            preferences.rightButtonAction = action
                                        }
                                    )
                                },
                                privilegePage = { dismiss ->
                                    AdvancedPermissionContent(manager = privilegeManager, onBack = dismiss)
                                },
                                netDiagPage = { dismiss ->
                                    NetworkDiagnosticsContent(
                                        privilegeManager = privilegeManager,
                                        baseUrl = { currentBaseUrl },
                                        onBack = dismiss
                                    )
                                }
                            )
                        }
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

/** 根页的组标题行：与子页 [SettingsSectionHeader] 同一档，整行参与逐行浮动。 */
@Composable
private fun RootSectionRow(
    index: Int,
    stagger: SettingsRowStaggerState,
    exit: SettingsExitController,
    content: @Composable () -> Unit
) {
    SettingsStaggeredRow(index = index, stagger = stagger, exit = exit) {
        content()
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
