package com.example.myapplication.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.GestureBindings
import com.example.myapplication.agent.GestureEvent
import com.example.myapplication.agent.ProviderProfileBook
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.agent.net.NetworkDiagnosisStore
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.agent.remote.ConsoleServer
import com.example.myapplication.presentation.ui.settings.AppearanceContent
import com.example.myapplication.presentation.ui.settings.ConsolePortStatus
import com.example.myapplication.presentation.ui.settings.RemoteConsoleContent
import com.example.myapplication.presentation.ui.settings.VoiceEntryContent
import com.example.myapplication.presentation.ui.settings.SettingsRowGap
import com.example.myapplication.presentation.ui.settings.SettingsRowSlot
import com.example.myapplication.presentation.ui.settings.LocalSettingsRowSlot
import com.example.myapplication.presentation.ui.settings.SettingsRowSidePadding
import com.example.myapplication.presentation.ui.settings.SettingsSectionGap
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
import com.example.myapplication.presentation.ui.settings.SettingsFocusStyle
import com.example.myapplication.presentation.ui.settings.SettingsNavRow
import com.example.myapplication.presentation.ui.settings.SettingsRowStaggerState
import com.example.myapplication.presentation.ui.settings.SettingsEdgeScrim
import com.example.myapplication.presentation.ui.settings.SettingsSectionHeader
import com.example.myapplication.presentation.ui.settings.settingsScalingParams
import com.example.myapplication.presentation.ui.settings.settingsSideInset
import com.example.myapplication.presentation.ui.settings.SettingsStaggeredRow
import com.example.myapplication.presentation.ui.settings.SettingsSubDialog
import com.example.myapplication.presentation.ui.settings.SettingsTopAnchorProbe
import com.example.myapplication.presentation.ui.settings.GestureBindingsContent
import com.example.myapplication.presentation.ui.settings.ProviderProfilesContent
import com.example.myapplication.presentation.ui.settings.RootContentTopPadding
import com.example.myapplication.presentation.ui.settings.RootTitleTopPadding
import com.example.myapplication.presentation.ui.settings.AboutContent
import com.example.myapplication.presentation.ui.settings.PromptContextContent
import com.example.myapplication.presentation.ui.settings.SettingsTransferContent
import com.example.myapplication.presentation.ui.settings.ToolsMcpContent
import com.example.myapplication.presentation.ui.settings.VoiceAsrContent
import com.example.myapplication.presentation.ui.settings.PullDownDismissContainer
import com.example.myapplication.presentation.ui.settings.isAtScrollTop
import com.example.myapplication.presentation.ui.settings.rememberSettingsExitController
import com.example.myapplication.presentation.ui.settings.rememberSettingsRowStaggerState
import com.example.myapplication.presentation.ui.settings.vibrateTick
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.theme.WearMaterialTheme
import com.example.myapplication.voice.AsrProviderId

/**
 * Wear OS 端侧 AI 设置中心
 *
 * 版面契约（与 [SettingsDesignMath] 一一对应）：
 * - **块居中、行内左/右两槽**：所有行共用 176dp 内容宽（小于 184dp 安全宽），整块在圆内居中；
 *   行内部标签左对齐到左槽线、取值右对齐到右槽线，两条槽线全页误差 0 —— 既保留可扫读的左边缘，
 *   重心又仍在圆的中心（既不"每行都居中"，也不"整页都左对齐"）；
 * - **颜色只有三个用途**：组标题中性、选中态 Accent、危险选项的选中圆点 Danger；
 *   Success / Warning 只允许出现在 6dp 状态点上，且一页最多一处；
 * - **层级靠位置与字重**：组标题 12sp / Medium，行标签与取值为同一档 15sp，
 *   子项只把左槽线右移 12dp，不降字号；
 * - **唯一的焦点**：顶部当前模型名（18sp / Medium），其余一律普通行；
 * - **线只有一种**：子项组上沿一根 1px、从右槽线左收 24dp 的短线，每页线数 == 子项组数；
 * - 进出场为逐行浮动：每行按自己的内容序号延迟浮起/沉下，容器只做极轻淡入淡出。
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
    var currentGestures by remember { mutableStateOf(preferences.gestureBindings()) }
    var mcpEndpoint by remember { mutableStateOf(preferences.mcpEndpoint) }
    var isMcpEnabled by remember { mutableStateOf(preferences.isMcpEnabled) }
    var customPrompt by remember { mutableStateOf(preferences.customSystemPrompt) }
    var currentTemp by remember { mutableFloatStateOf(preferences.temperature) }
    var currentMaxTokens by remember { mutableIntStateOf(preferences.maxOutputTokens) }
    var currentStreamMode by remember { mutableStateOf(preferences.streamMode) }

    // 动画特效：本页持有唯一真值，改完立刻落盘并投进 LocalAnimationsEnabled
    var animationsEnabled by remember { mutableStateOf(preferences.animationsEnabled) }

    // 供应商 Profile：根页只显示当前选中的那套名字，列表本身在子页里
    var providerBook by remember { mutableStateOf(preferences.providerProfiles()) }

    var asrEnabled by remember { mutableStateOf(preferences.asrEnabled) }
    var asrProviderKey by remember { mutableStateOf(preferences.asrProvider) }
    var asrKeyReady by remember { mutableStateOf(preferences.asrSettings().hasBaiduCredentials) }

    // 二级弹窗状态
    var activeDialog by remember { mutableStateOf<SettingsSubDialog?>(null) }
    var showVoiceAsr by remember { mutableStateOf(false) }

    // 清空记忆（[AgentPreferences.resetAll]）直接 clear() 存储，绕过上面这批 remember 缓存：
    // 缓存不重读，用户下一次点任何一行都会把刚被清掉的旧值写回存储 —— 清空等于没生效。
    // 回读字段清单只在 [rootSnapshot] 一处维护，单测逐个字段钉死。
    fun syncFromPreferences() {
        val snapshot = preferences.rootSnapshot()
        currentModel = snapshot.modelName
        currentBaseUrl = snapshot.baseUrl
        currentSecurityMode = snapshot.securityMode
        currentGestures = snapshot.gestureBindings
        mcpEndpoint = snapshot.mcpEndpoint
        isMcpEnabled = snapshot.isMcpEnabled
        customPrompt = snapshot.customSystemPrompt
        currentTemp = snapshot.temperature
        currentMaxTokens = snapshot.maxOutputTokens
        currentStreamMode = snapshot.streamMode
        animationsEnabled = snapshot.animationsEnabled
        providerBook = snapshot.providerBook
        asrEnabled = snapshot.asrEnabled
        asrProviderKey = snapshot.asrProviderKey
        asrKeyReady = snapshot.asrKeyReady
    }

    // 任何子页 / 语音浮层收起都整体回读一次：清空记忆、远端控制台改设置、外观页写存储等
    // 所有绕过缓存的路径都在这里收口，回到根页时屏上真值与被改写的存储一致。
    LaunchedEffect(activeDialog, showVoiceAsr) {
        syncFromPreferences()
    }

    // 逐行浮动：入场记账本（滚动复用不重播）+ 退场调度器（沉完才切屏）
    val rowStagger = rememberSettingsRowStaggerState()
    val exit = rememberSettingsExitController()

    // 控制台服务器：全进程单例，这里只取回同一实例（根页这一行要报它真实监听的端口）
    val consoleServer = remember { ConsoleServer.create(context.applicationContext) }
    var consolePort by remember { mutableIntStateOf(consoleServer.port) }
    // 真实端口不是 Compose 状态（未运行时是 -1）：每次进出子页回读一次，这一行才不会停在旧端口上
    LaunchedEffect(activeDialog) {
        consolePort = consoleServer.port
    }

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
                    // 曲率缩放关死：edgeScale / edgeAlpha 都传 1，边缘行不再缩到 75% 并淡出，
                    // 同一行在任何滚动位置尺寸一致（真机实测同一分隔线曾有 168.0 / 142.0 / 126.0dp 三种宽度）
                    scalingParams = settingsScalingParams(),
                    contentPadding = PaddingValues(
                        // 左右内缩按圆周弦长推导（真机 32.5dp），不再写死 12dp
                        start = settingsSideInset(),
                        end = settingsSideInset(),
                        // 顶部只让给 24dp 渐变遮罩（本页没有曲面时钟，不必让 34dp）
                        top = RootContentTopPadding,
                        // 设置页没有主键：底部预留取 44dp 而不是聊天页的 76dp
                        bottom = Wear.Size.SettingsBottomInset
                    ),
                    // 项间距 0：同组的行拼成一张卡，分组呼吸只由组标题上方的 24dp 承担
                    verticalArrangement = Arrangement.spacedBy(SettingsRowGap),
                    // 关掉 autoCentering：标题行不再撑 44dp 触摸高度后，itemIndex = 1 会让库
                    // 在首项之上插入约 60dp 的 spacer（233dp 屏），整列被推下半个屏幕。
                    // 传 null 后顶部锚点 = contentPadding.top = 24dp 遮罩下沿，位置只由常量决定。
                    autoCentering = null
                ) {
                    // ===== 全屏唯一的焦点：当前模型 =====
                    // 页面标题不承载信息（滑进来就知道在设置页），焦点让给"现在用的是哪个模型"：
                    // 18sp / Medium 比任何一行都重，眯眼看时页面因此有唯一落点。
                    item {
                        RootContentRow(index = 0, stagger = rowStagger, exit = exit) {
                            SettingsFocusBlock(
                                modelName = currentModel,
                                baseUrl = currentBaseUrl,
                                configured = apiKeyConfigured
                            )
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
                        RootContentRow(index = 2, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.FIRST) {
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
                        RootContentRow(index = 3, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.MIDDLE) {
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
                        RootContentRow(index = 4, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.LAST) {
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
                        RootContentRow(index = 6, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.FIRST) {
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
                        RootContentRow(index = 7, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.LAST) {
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
                        RootContentRow(index = 9, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.FIRST) {
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
                        RootContentRow(index = 10, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.MIDDLE) {
                            val consoleStatus =
                                ConsolePortStatus.of(consoleServer.preferredPort, consolePort)
                            SettingsNavRow(
                                title = stringResource(R.string.settings_remote_console_title),
                                // 没在跑就没有端口可报：显示「未运行」，不显示配置里的首选端口
                                value = when (consoleStatus) {
                                    ConsolePortStatus.NOT_RUNNING ->
                                        stringResource(R.string.settings_remote_console_not_running)
                                    ConsolePortStatus.STABLE -> stringResource(
                                        R.string.settings_remote_console_port_value,
                                        consolePort
                                    )
                                    ConsolePortStatus.DRIFTED -> stringResource(
                                        R.string.settings_remote_console_port_drifted_short,
                                        consoleServer.preferredPort,
                                        consolePort
                                    )
                                },
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.REMOTE_CONSOLE
                                }
                            )
                        }
                    }

                    item {
                        RootContentRow(index = 11, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.MIDDLE) {
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
                        RootContentRow(index = 12, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.MIDDLE) {
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
                        RootContentRow(index = 13, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.MIDDLE) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_about),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.ABOUT
                                }
                            )
                        }
                    }

                    // 配置导入导出：整份可移植设置的 JSON 快照，能复制出来也能粘贴回去
                    item {
                        RootContentRow(index = 14, stagger = rowStagger, exit = exit, slot = SettingsRowSlot.LAST) {
                            SettingsNavRow(
                                title = stringResource(R.string.settings_sub_transfer),
                                onClick = {
                                    context.vibrateTick()
                                    activeDialog = SettingsSubDialog.TRANSFER
                                }
                            )
                        }
                    }

                    // 第三条退出路径：不依赖手势与系统键的显式"完成返回"
                    item {
                        RootContentRow(index = 15, stagger = rowStagger, exit = exit) {
                            SettingsActionButton(
                                label = stringResource(R.string.settings_done_back),
                                tone = Tone.MUTED,
                                // 动作按钮不并进卡片：与上面那张卡之间留一整档组间距
                                modifier = Modifier
                                    .widthIn(max = SettingsContentWidth)
                                    .padding(top = SettingsSectionGap),
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
                            onToggleAnimations = { enabled ->
                                animationsEnabled = enabled
                                preferences.animationsEnabled = enabled
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
                                // 嵌套的「清空记忆」页收起时不换 activeDialog，这里显式回读一次，
                                // 否则父页仍拿着清空前的旧值，它的保存路径会把旧值写回存储。
                                MemoryManagerContent(
                                    preferences = preferences,
                                    onBack = {
                                        syncFromPreferences()
                                        dismiss()
                                    }
                                )
                            }
                        )
                        SettingsSubDialog.TRANSFER -> SettingsTransferContent(
                            preferences = preferences,
                            onApplied = { syncFromPreferences() },
                            onBack = { activeDialog = null }
                        )
                        SettingsSubDialog.ABOUT -> {
                            // 网络体检结论与根页同源：进来时读一次最近结果，子页里再重新体检
                            val netDiagSummary = remember(activeDialog) {
                                NetworkDiagnosisStore(context.applicationContext).lastSummary()
                            }
                            AboutContent(
                                sideKeyValue = gestureSummary(currentGestures),
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
                                    GestureBindingsContent(
                                        preferences = preferences,
                                        onBack = {
                                            currentGestures = preferences.gestureBindings()
                                            dismiss()
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
 * 关于页里「侧键」那一行的取值：直接由四条绑定算出来。
 *
 * 绑定全空时显示"未绑定"，任何一条绑上就列出已绑数量 —— 这一行因此永远反映真实可执行的动作，
 * 而不是一个历史上从没有任何消费点的装饰值（旧 rightButtonAction 就是那种装饰值）。
 */
@Composable
private fun gestureSummary(bindings: GestureBindings): String {
    val bound = GestureEvent.values().count {
        bindings.bindingFor(it) != com.example.myapplication.agent.GestureAction.NONE
    }
    return if (bound == 0) {
        stringResource(R.string.settings_gesture_none)
    } else {
        stringResource(R.string.settings_gesture_bound_count, bound)
    }
}

/**
 * 根页的组标题行：与子页 [SettingsSectionHeader] 完全同构（12sp / TextMuted / Medium，左对齐）。
 */
@Composable
private fun RootSectionRow(
    index: Int,
    stagger: SettingsRowStaggerState,
    exit: SettingsExitController,
    content: @Composable () -> Unit
) {
    SettingsStaggeredRow(index = index, rowCount = RootRowCount, stagger = stagger, exit = exit) {
        content()
    }
}

/**
 * 根页的内容行：一块 176dp 宽的行内容，整行参与逐行浮动。
 *
 * [slot] 是这一行在所属卡片里的位置（组首 / 组中 / 组尾 / 单行），由调用点按字面顺序写死：
 * 根页的 item 是逐条手写的，没有可推导的注册表，明写比另存一份"哪一行是组首"的布尔表更不容易漂。
 * 圆角、14dp 内边距与组内那根 1px 线都由 [SettingsRowSlot] 一路传下去。
 */
@Composable
private fun RootContentRow(
    index: Int,
    stagger: SettingsRowStaggerState,
    exit: SettingsExitController,
    slot: SettingsRowSlot? = null,
    content: @Composable () -> Unit
) {
    SettingsStaggeredRow(index = index, rowCount = RootRowCount, stagger = stagger, exit = exit) {
        // slot = null 的两种是焦点块与「完成返回」：它们不是卡片行，保持独立
        CompositionLocalProvider(LocalSettingsRowSlot provides slot) {
            Box(modifier = Modifier.widthIn(max = SettingsContentWidth)) { content() }
        }
    }
}

/**
 * 根页唯一的视觉焦点：当前模型名 + 下面一行 baseUrl（无 baseUrl 时退回配置状态）。
 *
 * 18sp / Medium / TextPrimary 比页面上任何一行都重，且只此一处 ——
 * 一屏一个焦点的做法替代了"每行等权、眯眼看是一整片灰块"。
 * 焦点块不可点：它只是把"现在在用哪个模型"抬出来，改动路径仍是下面「模型与服务」那一行。
 */
@Composable
private fun SettingsFocusBlock(modelName: String, baseUrl: String, configured: Boolean) {
    val title = if (modelName.isBlank()) {
        stringResource(R.string.settings_status_unconfigured)
    } else {
        modelName
    }
    val subtitle = if (baseUrl.isBlank()) {
        stringResource(
            if (configured) R.string.settings_status_configured else R.string.settings_status_unconfigured
        )
    } else {
        baseUrl
    }
    Column(
        modifier = Modifier
            .widthIn(max = SettingsContentWidth)
            .fillMaxWidth()
            // 起始内边距与行标签同一条左槽线：卡片行内的标签离卡片左缘 14dp，焦点块因此也内缩 14dp
            .padding(start = SettingsRowSidePadding, top = Wear.Space.Sm, bottom = Wear.Space.Xs)
    ) {
        BasicText(
            text = title,
            style = SettingsFocusStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Wear.Space.Xs))
        // 小字回到 12sp 那一档：焦点下面的一切都必须明显更轻
        BasicText(
            text = subtitle,
            style = Wear.TextCaption.copy(color = Wear.Palette.TextMuted),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 根页参与逐行动画的行数：焦点块 1 + 组标题 3 + 分区入口 11 + 末尾「完成返回」1。 */
private const val RootRowCount = 16

/**
 * 根页 remember 缓存的全部偏好：初始化与「清空记忆后回读」共用同一份快照。
 *
 * 为什么需要它：[AgentPreferences.resetAll] 是 `prefs.clear()`，它绕过根页这批缓存直接重写存储。
 * 缓存不重读，屏上就还是清空前的值，而根页每一行的点击回调都是「读缓存 + 写存储」——
 * 用户随手再点一下，就把刚被清掉的旧值原样写了回去，清空等于失效。
 */
internal data class SettingsRootSnapshot(
    val modelName: String,
    val baseUrl: String,
    val securityMode: SecurityMode,
    val gestureBindings: GestureBindings,
    val mcpEndpoint: String,
    val isMcpEnabled: Boolean,
    val customSystemPrompt: String,
    val temperature: Float,
    val maxOutputTokens: Int,
    val streamMode: Boolean,
    val animationsEnabled: Boolean,
    val asrEnabled: Boolean,
    val asrProviderKey: String,
    val asrKeyReady: Boolean,
    val providerBook: ProviderProfileBook
)

/**
 * 一次性读出根页缓存需要的全部偏好；纯读，不写存储。
 *
 * 字段清单必须与 SettingsScreen 里那批 `by remember { mutableStateOf(...) }` 缓存一一对应：
 * 单测扫源码钉住「每个缓存初始化用到的 preferences.X 都必须在这里回读」，
 * 否则以后新增一列缓存又会重新踩回「清空记忆失效」。
 */
internal fun AgentPreferences.rootSnapshot(): SettingsRootSnapshot {
    val asr = asrSettings()
    return SettingsRootSnapshot(
        modelName = modelName,
        baseUrl = baseUrl,
        securityMode = securityMode,
        gestureBindings = gestureBindings(),
        mcpEndpoint = mcpEndpoint,
        isMcpEnabled = isMcpEnabled,
        customSystemPrompt = customSystemPrompt,
        temperature = temperature,
        maxOutputTokens = maxOutputTokens,
        streamMode = streamMode,
        animationsEnabled = animationsEnabled,
        asrEnabled = asr.enabled,
        asrProviderKey = asrProvider,
        asrKeyReady = asr.hasBaiduCredentials,
        providerBook = providerProfiles()
    )
}
