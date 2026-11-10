package com.example.myapplication.presentation.ui.chat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.contract.RiskLevel
import com.example.myapplication.presentation.ApprovalCollapseDurationMs
import com.example.myapplication.presentation.ApprovalTone
import com.example.myapplication.presentation.ChatViewModel
import com.example.myapplication.presentation.deriveBottomBlockMode
import com.example.myapplication.presentation.ExternalPrompt
import com.example.myapplication.presentation.ModelCycle
import com.example.myapplication.presentation.ui.AUTO_COLLAPSE_DELAY_MS
import com.example.myapplication.presentation.ui.WAKE_LATCH_MS
import com.example.myapplication.presentation.ui.BottomBarEvent
import com.example.myapplication.presentation.ui.BottomBarMode
import com.example.myapplication.presentation.ui.BottomBarVisibility
import com.example.myapplication.presentation.ui.ApprovalBlockHeight
import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.CameraIdle
import com.example.myapplication.presentation.ui.CollapsedStripHeight
import com.example.myapplication.presentation.ui.IdleBlockHeight
import com.example.myapplication.presentation.ui.MainInputType
import com.example.myapplication.presentation.ui.BottomInteractiveBlock
import com.example.myapplication.presentation.ui.ReadingCamera
import com.example.myapplication.presentation.ui.ReadingCameraEvent
import com.example.myapplication.presentation.ui.ReadingAnchor
import com.example.myapplication.presentation.ui.ConversationGrouping
import com.example.myapplication.presentation.ui.SessionDrawer
import com.example.myapplication.presentation.ui.TopClockBar
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.presentation.ui.UnifiedMatrixCanvas
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.VoiceListeningOverlay
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBanner
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.VoiceInputManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** 用户主动滚动（拖拽/表冠）后，镜头暂停这么久再继续跟随，避免和手指抢。 */
private const val CameraPauseAfterUserScrollMs = 1200L

/** 审批态对话列表的残留不透明度：退到 35%，仍能辨认"这些内容还没走"。 */
private const val ApprovalListAlpha = 0.35f

/** 列表图层的模糊半径（px）：只糊列表、不糊点阵，密度 2.0 下约 4dp 的轻微虚化。 */
private const val ApprovalListBlurRadiusPx = 8f

/** 底栏底距：与 [BottomInteractiveBlock] 调用处的 padding(bottom = 12.dp) 对齐。 */
private val BottomBarBottomInset = 12.dp

/**
 * 聊天主界面：只读 [ChatViewModel.uiState] 并回调 ViewModel，不再自己持有会话/执行/审批状态。
 * 动画、焦点、镜头与弹窗入场留在这一层——它们属于"看上去怎么动"，不属于业务状态。
 */
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    preferences: AgentPreferences,
    voiceInputManager: VoiceInputManager,
    liveSpacingDp: Float,
    onOpenSettings: () -> Unit,
    announceInputMode: Boolean = false,
    onInputModeAnnounced: () -> Unit = {},
    externalPrompt: ExternalPrompt? = null,
    onClearExternalPrompt: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.uiState.collectAsState()

    // 动画特效总开关（唯一真值来自 AgentPreferences，由 WearAgentApp 注入）：
    // 关掉后点阵整块不参与组合、激荡进度恒为 -1（没有波），底栏与审批的弹性由各组件自己降级
    val animationsEnabled = LocalAnimationsEnabled.current

    // 底栏激荡波纹：每一轮被接受的新执行播一次 (纯白微晶光波)
    val shockwaveAnim = remember { Animatable(-1f) }
    val shockwaveProgress = if (animationsEnabled) shockwaveAnim.value else -1f

    val thinkingIntensity by animateFloatAsState(
        targetValue = if (state.isThinkingActive) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "thinkingIntensity"
    )

    // 用 ViewModel 保存的位置初始化：否则从设置页返回时列表会跳回顶部（用户看到"内容错位/抽搐"）
    val listState = rememberScalingLazyListState(
        initialCenterItemIndex = viewModel.savedCenterItemIndex,
        initialCenterItemScrollOffset = viewModel.savedCenterItemScrollOffset
    )
    val mainFocusRequester = remember { FocusRequester() }

    val isApprovalFocus = state.isApprovalFocus

    // 列表退场渐变：审批态 1f -> 35%，与遮罩同一条 180ms 曲线，不做硬切
    val listAlpha by animateFloatAsState(
        targetValue = if (isApprovalFocus) ApprovalListAlpha else 1f,
        animationSpec = Wear.Motion.base(),
        label = "conversationListAlpha"
    )

    // 模糊效果只建一次：图形层每帧都会跑 lambda，不能在里面反复 new RenderEffect
    val listBlurEffect = remember {
        BlurEffect(ApprovalListBlurRadiusPx, ApprovalListBlurRadiusPx, TileMode.Clamp)
    }

    // 放行塌缩：先播 220ms 塌缩动画，动画走完 ViewModel 才把结论交回 Agent
    val approvalCollapse = remember { Animatable(0f) }
    LaunchedEffect(state.pendingApproval) {
        // 新一轮审批从完整态开始：上一轮放行留下的塌缩进度必须归零
        if (state.pendingApproval != null) approvalCollapse.snapTo(0f)
    }
    LaunchedEffect(state.isApprovalCollapsing) {
        if (state.isApprovalCollapsing) {
            approvalCollapse.animateTo(1f, tween(ApprovalCollapseDurationMs))
        }
    }

    // 审批弹出的瞬间给一次触觉反馈：全屏接管需要腕上的一记"咔"
    LaunchedEffect(state.pendingApproval) {
        if (state.pendingApproval != null) {
            context.getSystemService(Vibrator::class.java)?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                }
            }
        }
    }

    // 水波纹自底栏向中心扩散激荡：每一次被接受的执行都在这里起波，入口（键盘/语音/外部 intent）无需各自实现
    LaunchedEffect(state.isExecuting, animationsEnabled) {
        if (state.isExecuting && animationsEnabled) {
            shockwaveAnim.snapTo(0f)
            shockwaveAnim.animateTo(1f, tween(360, easing = LinearEasing))
        }
    }

    // 表冠只有在列表节点持有焦点时才收得到 rotary 事件：首帧、抽屉/输入弹窗/审批浮层关闭、
    // 从设置页返回（整页重组）都要重新抢一次焦点。请求必须排到下一帧 —— 首帧里节点还没挂上树，
    // 直接 requestFocus() 会落到未初始化的 requester 上（真表上表现为表冠彻底没反应）。
    LaunchedEffect(state.isSessionDrawerOpen, state.draft.isOpen, isApprovalFocus) {
        if (state.isSessionDrawerOpen || state.draft.isOpen || isApprovalFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { mainFocusRequester.requestFocus() }
    }

    // 持久化的模式若是键盘，启动时主动说明主键当前会做文本输入（切换是静默保留的）
    LaunchedEffect(Unit) {
        if (announceInputMode) {
            if (state.mainInputType == MainInputType.KEYBOARD) {
                viewModel.showNotice(R.string.wearchat_notice_input_keyboard, isError = false)
            }
            onInputModeAnnounced()
        }
    }

    var launchVoiceInputAction by remember { mutableStateOf<() -> Unit>({}) }

    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                viewModel.triggerAgent(spokenText)
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchVoiceInputAction()
        }
    }

    // 审批挂起、返回拦截与多会话抽屉关闭
    BackHandler(enabled = state.isSessionDrawerOpen) {
        viewModel.closeSessionDrawer()
    }
    BackHandler(enabled = isApprovalFocus) {
        viewModel.rejectPending()
    }

    val recognizerPrompt = stringResource(R.string.wearchat_recognizer_prompt)

    DisposableEffect(Unit) {
        fun launchVoiceInput() {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasPerm) {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }

            val asr = preferences.asrSettings()
            if (asr.enabled && asr.providerId == AsrProviderId.BAIDU && asr.hasBaiduCredentials) {
                voiceInputManager.startThirdPartyListening(asr)
                return
            }

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, recognizerPrompt)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            val activities = context.packageManager.queryIntentActivities(speechIntent, 0)
            if (activities.isNotEmpty()) {
                try {
                    speechInputLauncher.launch(speechIntent)
                    return
                } catch (e: Exception) {
                    // 回退到应用内录音
                }
            }
            voiceInputManager.startListening()
        }
        launchVoiceInputAction = { launchVoiceInput() }

        onDispose { }
    }

    val conversationItems = remember(state.blocks) {
        ConversationGrouping.groupBlocks(state.blocks)
    }

    // ================= 底栏形态与收展进度（镜头要读，必须声明在镜头循环之前）=================
    // 底栏形态存在 ViewModel 里：切到设置再回来时不会重置成展开（否则会弹一下）
    val bottomBarMode by viewModel.bottomBarMode.collectAsState()
    // ★ 唯一收展进度：1 = 完整主键，0 = 贴底弧条。
    // 按钮的尺寸/圆角、弧条的圆心/半径/线宽/弧长全部由它插值 —— 一条时间线、一个连续形变。
    // 镜头也读它：底栏占用高度一变，可读区就变了，正在读的那一行必须重新让出来。
    val collapseProgress = remember { Animatable(1f) }
    LaunchedEffect(bottomBarMode) {
        collapseProgress.animateTo(
            targetValue = if (bottomBarMode == BottomBarMode.Collapsed) 0f else 1f,
            // 阻尼 0.62：只允许一次轻微过冲（有弹性但不来回弹两下）
            animationSpec = Wear.Motion.micro()
        )
    }

    // ================= 阅读镜头：唯一的滚动所有者 =================
    // 每帧只做一件事：把「正在读的那一行」保持在阅读带里。
    //   · 阅读锚点 = 本回合（最后一个紧跟提问的助手回合）正文最后一条的底边，取自 layoutInfo；
    //   · 阅读带   = 42dp … （视口高 − 底栏实际占用 − 8dp）；
    //   · 锚点低于带下沿 → 正文上移；高于带上沿 → 正文下移；带内（含 8dp 死区）→ 不滚；
    //   · 位移一律走 CameraCurve 的限速器（远快近慢 + 加速度上限 + 单帧不越过目标 + 亚像素残余）。
    // 判定输入只有 layoutInfo 与底栏占用，所以「工具胶囊插入 / 思考胶囊折叠 / 底栏形变 / 逐字吐字」
    // 这四类布局变化走的是同一条路径：下一帧按锚点重新判定。旧实现"对齐一次 → 恒速下滚"
    // 只有一次纠错机会，任何一次插入/形变都会让锚点永久离开可读区，这就是"相机不跟随"。
    val density = LocalDensity.current
    // 表冠/拖拽之后的一小段"镜头让路"窗口
    var cameraPauseUntil by remember { mutableLongStateOf(0L) }

    // 只有"紧跟用户提问、且已经有可读内容（正文/思考/工具胶囊）"的助手回合才接管镜头；
    // null 表示本轮没有接管（欢迎语、翻历史会话）。
    val readingTurnKey = remember(conversationItems) { CameraCurve.readingTurnKey(conversationItems) }
    // 条目索引每帧现取：错误提示条会追加在会话条目之后，回合内新增胶囊则不会。
    val liveConversationItems = rememberUpdatedState(conversationItems)

    LaunchedEffect(readingTurnKey) {
        if (readingTurnKey == null) return@LaunchedEffect
        // 本轮是否已经被用户叫停的唯一判据（isStoppedForTurn）：用户滚过之后不再自动拉回。
        // 对齐/匀速这两个旧阶段已被"锚点 vs 阅读带"取代，因此这里只用到两个事件。
        val camera = ReadingCamera()
        camera.onEvent(ReadingCameraEvent.TurnBodyAppeared)

        val bandTopPx = with(density) {
            ReadingAnchor.bandTopPx(
                topInsetPx = Wear.Size.TopContentInset.toPx(),
                marginPx = CameraCurve.FOCUS_TOP_MARGIN_DP.dp.toPx()
            )
        }
        val bandGapPx = with(density) { ReadingAnchor.BAND_BOTTOM_GAP_DP.dp.toPx() }
        val deadZonePx = CameraCurve.deadZonePx(density.density)
        var speedPxPerSec = 0f
        var lastFrameMillis = 0L
        // 亚像素残余：速度小到每帧不足 1px 时直接 dispatchRawDelta 会被取整丢弃，必须攒够再滚
        var residualPx = 0f
        // 连续"无事可做"的帧数：攒够 CameraIdle.SLEEP_AFTER_IDLE_FRAMES 帧就休眠。
        // 镜头是逐帧控制器，但"每帧都要判断"不等于"每帧都要唤醒"：静置时若一直排帧回调，
        // 一个回合在屏上就等于永久 60 次/秒的空转。
        var idleStreak = 0

        while (isActive) {
            if (CameraIdle.shouldSleep(idleStreak)) {
                // ---- 静止休眠：一个帧回调都不排，只等真正的状态变化 ----
                // 唤醒源全部是 snapshot 状态（不是轮询）：布局变化（胶囊插入/展开、条目尺寸）、
                // 底栏形变、会话条目变化（新 token / 新回合）、用户开始或结束滚动、沉浸态开关。
                // drop(1) 丢掉 combine 起播时的"当前快照"，否则会立刻醒来变成空转。
                combine(
                    snapshotFlow { listState.layoutInfo },
                    snapshotFlow { collapseProgress.value },
                    snapshotFlow { liveConversationItems.value },
                    snapshotFlow { listState.isScrollInProgress },
                    snapshotFlow {
                        listOf(
                            state.isApprovalFocus,
                            state.isRecordingVoice,
                            state.draft.isOpen,
                            state.isSessionDrawerOpen
                        )
                    }
                ) { _, _, _, _, _ -> }.drop(1).first()
                // 醒来第一帧必须重新判定：计数、限速器、dt 全部重起算
                idleStreak = 0
                speedPxPerSec = 0f
                residualPx = 0f
                lastFrameMillis = 0L
                continue
            }
            // withFrameMillis 给的是**毫秒**（当纳秒用会把 dt 恒夹成下限，速度掉到设计的 1/4）
            val frameMillis = withFrameMillis { it }
            val dt = if (lastFrameMillis == 0L) {
                0.016f
            } else {
                ((frameMillis - lastFrameMillis) / 1_000f).coerceIn(0.004f, 0.05f)
            }
            lastFrameMillis = frameMillis

            // ---- 让路：用户手指/表冠在动，或刚滚完还在暂停窗口内 ----
            if (ReadingAnchor.shouldYield(listState.isScrollInProgress, SystemClock.uptimeMillis(), cameraPauseUntil)) {
                if (listState.isScrollInProgress) {
                    // 用户主动接管滚动：本轮作废，绝不把人推回去
                    camera.onEvent(ReadingCameraEvent.UserScrolled)
                    return@LaunchedEffect
                }
                // 暂停窗口内只是"这一帧不抢"，窗口一过镜头自己接回来。
                // 这里绝不能计入静止帧：窗口到期是"时间"条件，休眠后没有任何状态变化能叫醒它。
                idleStreak = 0
                continue
            }
            if (camera.isStoppedForTurn) return@LaunchedEffect

            // ---- 沉浸态（审批/录音/输入弹窗/抽屉）：这一帧不抢，但**不作废本轮** ----
            // 审批放行后正文继续吐字、录音结束后回答继续流式，镜头必须能接回来；
            // 旧实现这里直接 return，镜头从此死到下一轮 —— 用户看到的是"好像也坏了"。
            if (state.isApprovalFocus || state.isRecordingVoice || state.draft.isOpen || state.isSessionDrawerOpen) {
                speedPxPerSec = 0f
                residualPx = 0f
                // 沉浸态本来就不动列表；这些标志的变化本身就是唤醒源，所以这里可以计入静止帧
                idleStreak = CameraIdle.nextStreak(idleStreak, isIdle = true)
                continue
            }

            val info = listState.layoutInfo
            val viewportHeightPx = info.viewportSize.height.toFloat()
            if (viewportHeightPx <= 0f) continue
            val turnIndex = liveConversationItems.value.lastIndex
            val focusItem = info.visibleItemsInfo.firstOrNull { it.index == turnIndex }
            // 回合还没进可视区时按"贴着下沿"给距离：它永远排在列表末尾，只可能在下方，
            // 交给同一个限速器逐帧找过去，绝不瞬移。
            val anchorBottomPx = focusItem?.let {
                CameraCurve.itemBottomPx(
                    itemCenterOffsetPx = it.offset.toFloat(),
                    itemSizePx = it.size.toFloat(),
                    viewportHeightPx = viewportHeightPx
                )
            } ?: viewportHeightPx

            // 底栏实际占用：与按钮视觉共用同一条 collapseProgress 时间线，不另算第二条曲线
            val barFootprintPx = with(density) {
                CameraCurve.barFootprintDp(
                    expandedBlockHeightDp = (if (state.isApprovalFocus) ApprovalBlockHeight else IdleBlockHeight).value,
                    bottomInsetDp = BottomBarBottomInset.value,
                    collapsedHeightDp = CollapsedStripHeight.value,
                    collapseProgress = collapseProgress.value
                ).dp.toPx()
            }
            val plan = ReadingAnchor.plan(
                anchorBottomPx = anchorBottomPx,
                bandTopPx = bandTopPx,
                bandBottomPx = ReadingAnchor.bandBottomPx(viewportHeightPx, barFootprintPx, bandGapPx),
                deadZonePx = deadZonePx,
                canScrollForward = listState.canScrollForward,
                canScrollBackward = listState.canScrollBackward
            )
            if (plan.isHold) {
                speedPxPerSec = 0f
                residualPx = 0f
                idleStreak = CameraIdle.nextStreak(idleStreak, isIdle = true)
                continue
            }

            speedPxPerSec = CameraCurve.nextSpeed(
                speedPxPerSec,
                CameraCurve.targetSpeed(plan.deltaPx, deadZonePx),
                dt
            )
            // 单帧位移绝不越过剩余距离，连亚像素残余一起夹住（否则会越过目标再回弹）
            val wantPx = (CameraCurve.stepPx(speedPxPerSec, dt, plan.deltaPx) + residualPx)
                .let { if (plan.deltaPx >= 0f) it.coerceIn(0f, plan.deltaPx) else it.coerceIn(plan.deltaPx, 0f) }
            val appliedPx = if (abs(wantPx) < 0.01f) 0f else listState.dispatchRawDelta(wantPx)
            residualPx = wantPx - appliedPx
            if (abs(appliedPx) < 0.5f && abs(wantPx) >= 0.5f) {
                // 已经顶到列表边界：这段位移物理上走不了，丢掉本帧残余，不卡死也不来回蹭；
                // 同时按"无事可做"计数，免得在边界上空转一整晚
                residualPx = 0f
                speedPxPerSec = 0f
                idleStreak = CameraIdle.nextStreak(idleStreak, isIdle = true)
            } else {
                idleStreak = 0
            }
        }
    }


    // ================= 底栏形态的唯一写入口 =================
    // 形态（闲置 / 录音 / 停止 / 审批）由 state.deriveBottomBlockMode() 这一个纯函数派生
    // （审批 > 录音 > 执行中 > 空闲）；这里只管"收 / 展"这一维。
    // 全文件只有这一个函数能写底栏形态：以前五处各自 set，后写者覆盖先写者，
    // collapseProgress 被来回拉扯 —— 用户看到的就是"点过按钮之后状态混乱"。
    fun applyBarMode(next: BottomBarMode) {
        if (bottomBarMode != next) viewModel.setBottomBarMode(next)
    }

    // ================= 底栏可见性：滚动即收起成小白条，点条才展开 =================
    val bottomBarForceExpanded = state.isBottomBarForceExpanded
    // 进入强制态（执行/审批/录音/弹窗/抽屉）立即展开并锁住，离开强制态不自动收起
    LaunchedEffect(bottomBarForceExpanded) {
        if (bottomBarForceExpanded) applyBarMode(BottomBarMode.Expanded)
    }
    // 唤醒挂起期：点一次弧条把按钮叫回来后，3 秒内不许再因滚动/静默被自动收走
    var wakeLatchUntil by remember { mutableLongStateOf(0L) }
    var lastUserScrollAt by remember { mutableLongStateOf(0L) }

    // 统一的形态迁移入口：挂起期内只允许"展开"方向，禁止自动收起
    fun applyBarEvent(event: BottomBarEvent, allowCollapse: Boolean) {
        val next = BottomBarVisibility.next(
            current = bottomBarMode,
            event = event,
            forceExpanded = state.isBottomBarForceExpanded
        )
        if (next == BottomBarMode.Collapsed && !allowCollapse) return
        applyBarMode(next)
    }

    val reportBottomBarEvent: (BottomBarEvent) -> Unit = { event ->
        val now = SystemClock.uptimeMillis()
        when (event) {
            BottomBarEvent.StripTap -> {
                applyBarEvent(event, allowCollapse = true)
                // 刚被唤醒：挂起 3 秒，避免手一碰又被收走
                wakeLatchUntil = now + WAKE_LATCH_MS
            }

            BottomBarEvent.UserScroll -> {
                lastUserScrollAt = now
                // 已经在底部：不收起（底部是"随时要操作"的位置），并且把按钮恢复回来。
                // 注意判据：底部预留 76dp 会让"最后一行可见"与 canScrollForward==false 不等价
                // （内容到底后列表仍能再滚 152px），所以用"最后一条已进入可视区"来判定。
                val atBottom = isContentEndVisible(listState, conversationItems.size)
                if (BottomBarVisibility.shouldRestoreAtBottom(atBottom)) {
                    applyBarMode(BottomBarMode.Expanded)
                }
                applyBarEvent(
                    event,
                    allowCollapse = BottomBarVisibility.shouldCollapseOnScroll(
                        nowMs = now,
                        wakeLatchUntilMs = wakeLatchUntil,
                        atBottom = atBottom
                    )
                )
            }

            BottomBarEvent.AutoPanQuiet -> {
                applyBarEvent(event, allowCollapse = BottomBarVisibility.collapseAllowed(now, wakeLatchUntil))
            }
        }
    }
    // 用户滚动的唯一上报口：拖拽与表冠同源 —— 官方 RotaryScrollLogic 也在同一把 scroll 互斥锁里驱动，
    // 两者都会把 isScrollInProgress 置真；镜头自己走 dispatchRawDelta 不会置真，所以这里只认用户。
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect {
                // 用户接管滚动：自动镜头让路，本轮不再把人推回去
                cameraPauseUntil = SystemClock.uptimeMillis() + CameraPauseAfterUserScrollMs
                reportBottomBarEvent(BottomBarEvent.UserScroll)
            }
    }

    // 兜底：惯性甩动到底部时，isScrollInProgress 可能已经变 false 才耗尽动量，
    // 上面那次 UserScroll 判断会漏掉 → 这里直接盯"能否继续向前滚"的翻转。
    LaunchedEffect(listState, conversationItems.size) {
        snapshotFlow { isContentEndVisible(listState, conversationItems.size) }
            .filter { visible -> visible }
            .collect {
                if (bottomBarMode == BottomBarMode.Collapsed) {
                    applyBarMode(BottomBarMode.Expanded)
                }
            }
    }

    // 新回合到达后 3 秒内无人滚动 → 自动收起（每个回合只计时一次，返回设置页不会重开）
    LaunchedEffect(readingTurnKey) {
        val key = readingTurnKey ?: return@LaunchedEffect
        if (!viewModel.claimAutoCollapseTurn(key)) return@LaunchedEffect
        val turnStart = SystemClock.uptimeMillis()
        delay(AUTO_COLLAPSE_DELAY_MS)
        val now = SystemClock.uptimeMillis()
        val shouldCollapse = BottomBarVisibility.shouldAutoCollapseAfterTurn(
            nowMs = now,
            turnStartMs = turnStart,
            wakeLatchUntilMs = wakeLatchUntil,
            lastUserScrollMs = lastUserScrollAt,
            forceExpanded = state.isBottomBarForceExpanded
        )
        if (shouldCollapse) {
            applyBarMode(
                BottomBarVisibility.next(
                    current = bottomBarMode,
                    event = BottomBarEvent.AutoPanQuiet,
                    forceExpanded = state.isBottomBarForceExpanded
                )
            )
        }
    }

    // 外部 intent prompt：进入主界面且空闲时触发一次，随后回清并记录序号防止重组重放
    LaunchedEffect(externalPrompt, state.isExecuting) {
        if (viewModel.consumeExternalPrompt(externalPrompt)) {
            onClearExternalPrompt()
        }
    }

    LaunchedEffect(Unit) {
        voiceInputManager.onListeningStateChanged = { listening ->
            viewModel.onVoiceListeningChanged(listening)
        }
        voiceInputManager.onSpeechPartial = { partial -> viewModel.onVoicePartial(partial) }
        voiceInputManager.onSpeechResult = { recognizedText ->
            viewModel.onVoiceResult(recognizedText)
        }
        voiceInputManager.onSpeechError = { rawMessage ->
            viewModel.onVoiceError(rawMessage)
        }
    }

    // 持续把当前居中位置写回 ViewModel：切屏前就有最新值可恢复
    LaunchedEffect(listState) {
        // 用 Long 把 (index, offset) 编成单值：滚动时每帧都会求值，Pair 分配是实打实的垃圾
        snapshotFlow { (listState.centerItemIndex.toLong() shl 32) or (listState.centerItemScrollOffset.toLong() and 0xFFFFFFFFL) }
            .collect { packed ->
                viewModel.saveListPosition(
                    centerItemIndex = (packed shr 32).toInt(),
                    centerItemScrollOffset = packed.toInt()
                )
            }
    }



    // 点阵常驻做空间底（低对比），审批态让位给遮罩（见下方 isApprovalFocus 判断）。
    // 曾经算过一个 matrixActive 想只在活动态显示，但那个变量从未被消费 —— 已删除，
    // 免得注释和行为对不上。

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
    ) {
        // ================= 1. 全局唯一点阵坐标系 (仅活动状态可见) =================
        // 审批弹层的首帧最贵：全屏遮罩组合 + 模糊层 + 点阵切到审批绘制路径（红瀑）三件事同时发生。
        // 审批期间点阵直接让位成纯色底，把首帧的绘制预算全部留给遮罩，弹出不再卡一下。
        // 点阵常驻：滚动期间不再冻结。曾试过 isScrollInProgress 门控，实测 janky 只从
        // 69.8% 降到 66.3%（收益边际），但滚动时背景突然静止再接上，观感比省下的那点帧更差。
        if (animationsEnabled && !isApprovalFocus) {
            UnifiedMatrixCanvas(
                modifier = Modifier.fillMaxSize(),
                approvalVisualState = state.agentState.approvalVisualState,
                // 完工掠影已删除：它和消息卡收尾动画抢同一时刻，两个动效叠加看起来"诡异"。
                // 一个动作只允许一组动效（收尾交给消息卡自己的 160ms 亮度回落）。
                isTaskCompletedTrigger = false,
                thinkingIntensity = thinkingIntensity,
                isRecordingVoice = state.isRecordingVoice,
                shockwaveProgress = shockwaveProgress,
                gridSpacingDp = liveSpacingDp,
                // 审批滑块进度直接交给点阵：越往左推，点本身从冷白基色连续变绿变亮，不叠加任何整屏色层
                approvalProgress = state.approvalProgress
            )
        } else {
            // 关闭动效：呼吸、冲击波、审批红瀑、完工掠影全部随点阵一起消失，
            // 只留底板纯色（不使用静态点阵，避免"最后一个呼吸相位"被当成常驻状态留在屏上）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Wear.Palette.Bg)
            )
        }

        // ================= 2. 文字与卡片一体联动滚动流 (M3 圆屏标准：边缘缩小淡出，不做整块抹除) =================
        ConversationList(
            items = conversationItems,
            listState = listState,
            focusRequester = mainFocusRequester,
            listAlpha = listAlpha,
            isApprovalFocus = isApprovalFocus,
            listBlurEffect = listBlurEffect,
            errorMessage = state.agentState.errorMessage,
            isStreamingBurst = state.isStreamingBurst,
            approvalVisualState = state.agentState.approvalVisualState,
            isTaskCompleted = state.isTaskCompleted,
            thinkingIntensity = thinkingIntensity,
            isRecordingVoice = state.isRecordingVoice,
            shockwaveProgress = shockwaveProgress,
            gridSpacingDp = liveSpacingDp,
            expandedCapsules = state.expandedCapsules,
            compactMode = state.compactMode,
            onToggleCapsule = { key -> viewModel.toggleCapsule(key) }
        )

        // ================= 3. 顶部弧形微晶时间 (遵循穿戴纯净规范，不再常驻会话胶囊) =================
        // 曲线坐标系取整屏圆形；绘制在内容列表之后，正文从时钟下方穿过时只被轻微压暗，不会被抹掉。
        // 审批态由居中文字浮层接管，文字顶边会扫到弧形时钟字形，
        // 因此审批期间隐藏时钟：沉浸要的是干净，而不是留下一条状态栏残影。
        if (!isApprovalFocus) {
            // 时间状态由时钟自己持有：避免每秒让整棵 ChatScreen 重组
            TopClockBar(modifier = Modifier.fillMaxSize())
        }

        // ================= 3.5 审批浮层：没有卡片也没有描边，文字浮在径向暗晕之上 =================
        ApprovalFloatingLayer(
            visible = isApprovalFocus,
            toolName = state.pendingApproval?.toolName.orEmpty(),
            risk = state.pendingApproval?.risk,
            collapse = approvalCollapse.value
        )

        // 收起态：半透明弧线贴着圆屏底缘（Wear M3E 的曲面元素都是沿表盘边缘成弧）。
        // 视觉由这里画，点击热区仍由下面 BottomInteractiveBlock 的 44dp 透明区域承担。
        // 收展进度已提到 ChatScreen 顶部（镜头共用同一条时间线），这里只取弧条进度
        val arcP = 1f - collapseProgress.value
        // 注意：弧条与它的热区必须画在 BottomInteractiveBlock **之后**（见下方），
        // 否则会被底栏容器（58dp 高）压在下面，点击事件全被容器吃掉 —— 这就是"点弧条没反应"的真因。

        // ================= 4. 单体演变底栏控制器 =================
        // 文字输入弹窗是模态：打开时底栏整体退场，避免卡片与主键互相压叠
        if (!state.draft.isOpen) BottomInteractiveBlock(
            // 形态只认派生值：底栏不会再被某个写者单独改出"半套"状态
            mode = state.deriveBottomBlockMode(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomBarBottomInset),
            mainInputType = state.mainInputType,
            onMainInputTypeChange = { newType -> viewModel.changeMainInputType(newType) },
            cooldownSeconds = state.pendingApproval?.risk?.requireCooldownSeconds ?: 0,
            riskExplanation = state.pendingApproval?.risk?.riskSummary ?: "",
            onVoiceStartRecord = {
                launchVoiceInputAction()
            },
            onVoiceStopAndTrigger = {
                scope.launch {
                    shockwaveAnim.snapTo(0f)
                    shockwaveAnim.animateTo(1f, tween(480, easing = LinearEasing))
                }
                voiceInputManager.stopListening()
            },
            onLeftKeyboardClick = {
                viewModel.openTextInput()
            },
            onRightActionClick = {
                // 右翼固定打开会话抽屉：横向手势已被 Wear 系统占用，入口只走显式按钮
                context.getSystemService(Vibrator::class.java)?.let { vib ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    }
                }
                viewModel.openSessionDrawer()
            },
            onStopClick = {
                voiceInputManager.cancel()
                viewModel.stopAgent()
            },
            onAllow = {
                // 放行不立刻退出：先让整页塌缩成工具胶囊尺度，动画走完再交回结论
                viewModel.approvePending()
            },
            onReject = {
                viewModel.rejectPending()
            },
            onSwipeUpSettings = {
                onOpenSettings() // 阻尼跟手上滑进入设置
            },
            onApprovalProgress = { viewModel.onApprovalProgress(it) },
            // 滑块着色跟着审批等级走：低风险绿、中度黄、重度黄红、致命红，滑到底仍是绿
            approvalTone = ApprovalTone.accentOf(
                state.pendingApproval?.risk?.aiRiskLevel ?: RiskLevel.MEDIUM
            ),
            collapsed = bottomBarMode == BottomBarMode.Collapsed,
            collapseProgress = collapseProgress.value,
            onCollapsedStripTap = { reportBottomBarEvent(BottomBarEvent.StripTap) }
        )

        // 弧条绘在底栏之后：保证它在最上层，热区能真正接到点击
        if (arcP > 0.01f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCurvedBottomHandle(arcP)
            }
            // 热区必须跟着弧走：点弧条本身、或从弧条上滑，都能恢复成主键。
            // 之前热区留在旧按钮位置，用户点看得见的弧条没反应 —— 这是真 bug。
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(72.dp)
                    // 单一手势处理器：clickable 与 detectVerticalDragGestures 叠在同一节点时，
                    // 后者会吃掉抬手事件，导致"点弧条没反应"（用户实际遇到的 bug）。
                    // 这里统一为"抬手即恢复"：轻点、上滑、下滑都算，符合直觉。
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) {
                                    reportBottomBarEvent(BottomBarEvent.StripTap)
                                    break
                                }
                            }
                        }
                    }
            )
        }

        // ================= 5. 瞬时提示条 (语音错误 / 模型切换反馈) =================
        val inlineNotice = state.notice
        if (inlineNotice != null) {
            val noticeText = stringResource(inlineNotice.textRes, *inlineNotice.args.toTypedArray())
            if (noticeText.isNotBlank()) {
                WearBanner(
                    text = noticeText,
                    tone = if (inlineNotice.isError) Tone.DANGER else Tone.ACCENT,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = Wear.Space.Lg,
                            end = Wear.Space.Lg,
                            bottom = Wear.Size.BottomBarReserve
                        )
                )
            }
        }

        // ================= 6. 自定义文本输入弹窗 (避让输入法 / 返回键只关弹窗) =================
        if (state.draft.isOpen) {
            TextInputDialog(
                draft = state.draft,
                onChange = { viewModel.changeDraft(it) },
                onCancel = { viewModel.cancelTextInput() },
                onSubmit = { viewModel.submitDraft() },
                visionBetaEnabled = preferences.visionBetaEnabled
            )
        }

        // ================= 7. Google M3 原生贴边自适应多会话抽屉 (支持快捷调试模型) =================
        SessionDrawer(
            isOpen = state.isSessionDrawerOpen,
            sessions = state.sessions,
            activeSessionId = state.activeSessionId,
            currentModelName = preferences.modelName,
            availableModels = ModelCycle.parse(preferences.cachedRemoteModels),
            onSelectModel = { selectedModel -> viewModel.selectModel(selectedModel) },
            onSelectSession = { id -> viewModel.switchToSession(id) },
            onCreateSession = { viewModel.createNewSession() },
            onDeleteSession = { id -> viewModel.deleteSession(id) },
            onOpenSettings = {
                viewModel.closeSessionDrawer()
                onOpenSettings()
            },
            onClose = { viewModel.closeSessionDrawer() }
        )

        // ================= 8. 应用内语音录制与倾听悬浮层 =================
        if (state.isRecordingVoice) {
            VoiceListeningOverlay(
                onCancel = {
                    voiceInputManager.cancel()
                    viewModel.onVoiceListeningChanged(false)
                }
            )
        }
    }
}

/**
 * 是否已经"看到内容结尾"：最后一条已经出现在可视区，或已经无法继续向前滚。
 *
 * 为什么不能只看 canScrollForward：列表底部有 76dp 预留，内容到底之后仍可再滚 152px，
 * 于是"最后一行已经显示出来"时 canScrollForward 依然是 true —— 用户视角早就到底了，
 * 而按 canScrollForward 判断会永远不成立。
 */
private fun isContentEndVisible(
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    itemCount: Int
): Boolean {
    if (itemCount <= 0) return true
    if (!listState.canScrollForward) return true
    val info = listState.layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return false
    if (last.index < itemCount - 1) return false
    // 注意：Wear 的 ScalingLazyListItemInfo.offset 是**相对视口中线**的中心偏移（不是顶边），
    // 所以条的底边在视口里的坐标 = 视口中心 + offset + size/2。
    // 另外整段回复是"同一个 item"，光看"最后一条可见"永远为真（大卡片几乎总在屏上），
    // 必须判断它的**底边是否已经进入屏幕**。
    val viewportHeightPx = info.viewportSize.height.toFloat()
    val lastItemBottom = viewportHeightPx / 2f + last.offset + last.size / 2f
    return lastItemBottom <= viewportHeightPx
}

/**
 * 收起态贴底把手：沿圆屏底缘画一段半透明圆弧。
 *
 * 为什么是弧：Wear 的界面元素都以表盘圆心为基准成弧（曲面文字/曲面列表同理），
 * 直角矩形小条贴在圆屏底部会啃进圆周、像贴纸；弧线与机身外缘同心，才像是长在表盘上。
 * Canvas 角度约定 0° = 3 点、90° = 6 点，因此 42° ~ 138° 正好覆盖底部约 96° 的一段。
 */
private fun DrawScope.drawCurvedBottomHandle(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.001f) return
    fun mix(from: Float, to: Float) = from + (to - from) * p

    // ★ 硬约束：这条弧**永远是一小段细线**，绝不参与"从球插值"。
    // 之前让线宽 52dp→4.5dp、弧长 360°→48° 一起插值，中间态就是一块半屏粗弧（用户看到"两个巨大圆弧闪一下"）。
    // 现在：线宽恒 5dp、弧长最大 48°、圆心固定在屏幕中心（只让半径长大），几何上不可能再糊住屏幕。
    val strokePx = 5.dp.toPx()
    val sweep = mix(18f, 48f)
    // 半径从主键附近（48dp ⇒ 弧顶 y≈144dp）长到贴底（84dp ⇒ y≈180dp）：
    // 视觉上是"从按钮位置往下弯过去贴边"，全程停留在屏幕下半部。
    val startRadius = 48.dp.toPx()
    val endRadius = size.minDimension / 2f - 12.dp.toPx()
    val radius = mix(startRadius, endRadius)
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    // 主键先把体积收掉，弧条再淡入：两者不同时出现，避免叠色
    // 弧条必须等主键基本收完再出现（p>0.55），否则会和正在缩小的按钮叠成两个形状
    val alpha = ((p - 0.55f) / 0.30f).coerceIn(0f, 1f) * 0.62f

    drawArc(
        color = Wear.Palette.Orb.copy(alpha = alpha),
        startAngle = 90f - sweep / 2f,
        sweepAngle = sweep,
        useCenter = false,
        topLeft = Offset(centerX - radius, centerY - radius),
        size = Size(radius * 2f, radius * 2f),
        style = Stroke(width = strokePx, cap = StrokeCap.Round)
    )
}
