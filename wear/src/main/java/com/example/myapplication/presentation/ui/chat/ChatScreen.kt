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
import com.example.myapplication.presentation.ExternalPrompt
import com.example.myapplication.presentation.ModelCycle
import com.example.myapplication.presentation.ui.AUTO_COLLAPSE_DELAY_MS
import com.example.myapplication.presentation.ui.WAKE_LATCH_MS
import com.example.myapplication.presentation.ui.BottomBarEvent
import com.example.myapplication.presentation.ui.BottomBarMode
import com.example.myapplication.presentation.ui.BottomBarVisibility
import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.MainInputType
import com.example.myapplication.presentation.ui.BottomInteractiveBlock
import com.example.myapplication.presentation.ui.ReadingCamera
import com.example.myapplication.presentation.ui.ReadingCameraEvent
import com.example.myapplication.presentation.ui.ReadingCameraPhase
import com.example.myapplication.presentation.ui.ReadingPace
import com.example.myapplication.presentation.ui.ConversationGrouping
import com.example.myapplication.presentation.ui.SessionDrawer
import com.example.myapplication.presentation.ui.TopClockBar
import com.example.myapplication.presentation.ui.UnifiedMatrixCanvas
import com.example.myapplication.presentation.ui.VoiceListeningOverlay
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBanner
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.VoiceInputManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
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

    // 底栏激荡波纹：每一轮被接受的新执行播一次 (纯白微晶光波)
    val shockwaveAnim = remember { Animatable(-1f) }

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
        animationSpec = tween(180),
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
    LaunchedEffect(state.isExecuting) {
        if (state.isExecuting) {
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

    // ================= 提词器镜头：唯一的滚动所有者 =================
    // 只做两件事，且全应用只有这一条链路在动列表：
    //   ① 回复开始出现正文的那一刻，把这一回合卡片的顶边**限速**挪到可读区顶部
    //      （TopContentInset 34dp 之下再留 8dp，即 42dp）；
    //   ② 对齐到位后立刻转为按人类阅读速度推导的**恒定慢速下滚**，用户不用动手就能读完。
    // 到末尾 / 用户自己滚动 / 审批·录音·弹窗·抽屉 一律停住，停在原地不回弹；
    // 一轮结束不做任何跳转 —— 这里是"提词器"，不是"结算追到结尾"。
    val density = LocalDensity.current
    // 表冠/拖拽之后的一小段"镜头让路"窗口
    var cameraPauseUntil by remember { mutableLongStateOf(0L) }

    // 只有"紧跟用户提问、且已经吐出正文的助手回合"才接管镜头；null 表示本轮没有接管。
    val cameraFocusTurnKey = remember(conversationItems) { CameraCurve.replyTurnKey(conversationItems) }
    val cameraFocusIndex = conversationItems.lastIndex

    LaunchedEffect(cameraFocusTurnKey) {
        val turnKey = cameraFocusTurnKey ?: return@LaunchedEffect
        // 每个回合只对齐一次：从设置页返回时 ChatScreen 重新组合，这里必须拦住，
        // 否则镜头会重新"对齐到回合开头"，看起来像画面抽搐。
        if (!viewModel.claimCameraTurn(turnKey)) return@LaunchedEffect
        // 注意：这里**不能**用 isExecuting 当门。提词器的核心场景正是"回复已经结束、用户要读全文"，
        // 用 isExecuting 会把 AutoPan 永远挡在门外（回复一结束协程就 return）。
        // 接管条件只看"本轮已经吐出了正文"（由 replyTurnKey 判定），翻历史会话时 replyTurnKey 为 null。
        val densityScale = density.density
        val targetTopPx = with(density) { (Wear.Size.TopContentInset + CameraCurve.FOCUS_TOP_MARGIN_DP.dp).toPx() }
        val deadZonePx = CameraCurve.deadZonePx(densityScale)
        val panSpeedPxPerSec = ReadingPace.panSpeedPxPerSec(
            density = densityScale,
            speedMultiplier = ReadingPace.USER_SPEED_MULTIPLIER
        )
        val camera = ReadingCamera()
        camera.onEvent(ReadingCameraEvent.TurnBodyAppeared)
        var speedPxPerSec = 0f
        var lastFrameMillis = 0L
        // AutoPan 的亚像素残余：6.6dp/s ≈ 0.22px/帧，直接发 dispatchRawDelta 会被取整丢弃，
        // 必须把不足 1px 的部分攒起来，攒够 1px 再滚动一次（平均速度才等于 13.3px/s）。
        var panResidualPx = 0f
        // 自动下滚开始时刻：持续 3 秒且用户全程无操作，就把底栏自己收起来（用户盯着读，按钮该让位）

        while (isActive) {
            // 注意：withFrameMillis 给的是**毫秒**（不是纳秒），除 1e9 会把 dt 恒夹成下限 0.004，
            // 导致 AutoPan 实际速度只有设计的 1/4。
            val frameMillis = withFrameMillis { it }
            val dt = if (lastFrameMillis == 0L) {
                0.016f
            } else {
                ((frameMillis - lastFrameMillis) / 1_000f).coerceIn(0.004f, 0.05f)
            }
            lastFrameMillis = frameMillis
            // 用户接管（手指在拖 / 刚转过表冠）：本轮自动镜头立即作废，绝不把用户推回去
            if (listState.isScrollInProgress || SystemClock.uptimeMillis() < cameraPauseUntil) {
                camera.onEvent(ReadingCameraEvent.UserScrolled)
                return@LaunchedEffect
            }
            // 审批 / 录音 / 输入弹窗 / 抽屉：立即停，本轮不再自启
            val interrupted = state.isApprovalFocus || state.pendingApproval != null ||
                state.isRecordingVoice || state.draft.isOpen || state.isSessionDrawerOpen
            if (interrupted) {
                camera.onEvent(ReadingCameraEvent.Interrupted)
                return@LaunchedEffect
            }
            when (camera.phase) {
                ReadingCameraPhase.AlignToTurnStart -> {
                    val remainingPx = listState.focusRemainingPx(cameraFocusIndex, targetTopPx)
                    if (abs(remainingPx) <= deadZonePx) {
                        speedPxPerSec = 0f
                        camera.onEvent(ReadingCameraEvent.Aligned)
                    } else {
                        speedPxPerSec = CameraCurve.nextSpeed(
                            speedPxPerSec,
                            CameraCurve.targetSpeed(remainingPx, deadZonePx),
                            dt
                        )
                        val stepPx = CameraCurve.stepPx(speedPxPerSec, dt, remainingPx)
                        val consumedPx = if (stepPx == 0f) 0f else listState.dispatchRawDelta(stepPx)
                        if (stepPx != 0f && abs(consumedPx) < 0.5f) {
                            // 已经顶到列表边界，物理上无法更靠近目标：本轮作废，不卡死也不来回蹭
                            camera.onEvent(ReadingCameraEvent.Interrupted)
                            return@LaunchedEffect
                        }
                    }
                }

                ReadingCameraPhase.AutoPan -> {
                    if (!listState.canScrollForward) {
                        camera.onEvent(ReadingCameraEvent.ReachedEnd)
                        // 提词器滚到「输出结尾」：正文下方正好空出位置，按钮自动回来。
                        // 这里绕过唤醒挂起期（挂起只阻止"收起"，不阻止"恢复"），
                        // 因为这是"读完了"的明确信号，用户此刻最可能想继续操作。
                        viewModel.setBottomBarMode(BottomBarMode.Expanded)
                        return@LaunchedEffect
                    }

                    // 提词器：恒定慢速，绝不再按剩余距离提速；亚像素增量要累积，否则每帧都被取整丢弃
                    val wantPx = panSpeedPxPerSec * dt + panResidualPx
                    val appliedPx = listState.dispatchRawDelta(wantPx)
                    panResidualPx = wantPx - appliedPx

                }

                ReadingCameraPhase.Idle -> return@LaunchedEffect
            }
        }
    }

    // ================= 底栏可见性：滚动即收起成小白条，点条才展开 =================
    // 底栏形态存在 ViewModel 里：切到设置再回来时不会重置成展开（否则会弹一下）
    val bottomBarMode by viewModel.bottomBarMode.collectAsState()
    val bottomBarForceExpanded = state.isBottomBarForceExpanded
    // 进入强制态（执行/审批/录音/弹窗/抽屉）立即展开并锁住，离开强制态不自动收起
    LaunchedEffect(bottomBarForceExpanded) {
        if (bottomBarForceExpanded) viewModel.setBottomBarMode(BottomBarMode.Expanded)
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
        viewModel.setBottomBarMode(next)
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
                applyBarEvent(event, allowCollapse = BottomBarVisibility.collapseAllowed(now, wakeLatchUntil))
            }

            BottomBarEvent.AutoPanQuiet -> {
                applyBarEvent(event, allowCollapse = BottomBarVisibility.collapseAllowed(now, wakeLatchUntil))
            }
        }
    }
    // 手指拖拽的"开始滚动"上报：只认用户手势（镜头自己走 dispatchRawDelta 不会置真）
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { it }
            .collect { reportBottomBarEvent(BottomBarEvent.UserScroll) }
    }

    // 新回合到达后 3 秒内无人滚动 → 自动收起（每个回合只计时一次，返回设置页不会重开）
    LaunchedEffect(cameraFocusTurnKey) {
        val key = cameraFocusTurnKey ?: return@LaunchedEffect
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
            viewModel.setBottomBarMode(
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



    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
    ) {
        // ================= 1. 全局唯一点阵坐标系 (通体清晰方阵，覆盖全圆屏) =================
        UnifiedMatrixCanvas(
            modifier = Modifier.fillMaxSize(),
            approvalVisualState = state.agentState.approvalVisualState,
            isTaskCompletedTrigger = state.isTaskCompleted,
            thinkingIntensity = thinkingIntensity,
            isRecordingVoice = state.isRecordingVoice,
            shockwaveProgress = shockwaveAnim.value,
            gridSpacingDp = liveSpacingDp,
            // 审批滑块进度直接交给点阵：越往左推，点本身从冷白基色连续变绿变亮，不叠加任何整屏色层
            approvalProgress = state.approvalProgress
        )

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
            shockwaveProgress = shockwaveAnim.value,
            gridSpacingDp = liveSpacingDp,
            onRotaryScroll = { verticalScrollPixels ->
                // 表冠：dispatchRawDelta 同步吃位移 —— 不排队、不与镜头抢同一把 scroll 互斥锁，
                // 每格刻度都立刻生效（scope.launch + scroll{} 在高频刻度下会被逐帧滚动挤掉，真表上就像没接上）
                cameraPauseUntil = SystemClock.uptimeMillis() + CameraPauseAfterUserScrollMs
                // 表冠刻度 = 用户开始滚动：底栏收起成小白条（强制态下不收）
                if (abs(verticalScrollPixels) > 0f) reportBottomBarEvent(BottomBarEvent.UserScroll)
                listState.dispatchRawDelta(verticalScrollPixels)
                if (abs(verticalScrollPixels) > 8f) {
                    context.getSystemService(Vibrator::class.java)?.let { vib ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                        }
                    }
                }
                true
            }
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
        // ★ 唯一收展进度：1 = 完整主键，0 = 贴底弧条。
        // 按钮的尺寸/圆角、弧条的圆心/半径/线宽/弧长全部由它插值 —— 一条时间线、一个连续形变，
        // 不再出现"下面突然冒出、上面自己收、两边各弹一下"的割裂。
        val collapseProgress = remember { Animatable(1f) }
        LaunchedEffect(bottomBarMode) {
            collapseProgress.animateTo(
                targetValue = if (bottomBarMode == BottomBarMode.Collapsed) 0f else 1f,
                // 阻尼 0.62：只允许一次轻微过冲（有弹性但不来回弹两下）
                animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f)
            )
        }
        val arcP = 1f - collapseProgress.value
        // 注意：弧条与它的热区必须画在 BottomInteractiveBlock **之后**（见下方），
        // 否则会被底栏容器（58dp 高）压在下面，点击事件全被容器吃掉 —— 这就是"点弧条没反应"的真因。

        // ================= 4. 单体演变底栏控制器 =================
        // 文字输入弹窗是模态：打开时底栏整体退场，避免卡片与主键互相压叠
        if (!state.draft.isOpen) BottomInteractiveBlock(
            mode = state.bottomBlockMode,
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
                            start = Wear.Space.Edge,
                            end = Wear.Space.Edge,
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
                onSubmit = { viewModel.submitDraft() }
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
                    voiceInputManager.stopListening()
                    viewModel.onVoiceListeningChanged(false)
                }
            )
        }
    }
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
