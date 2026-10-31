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
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.BlurEffect
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
import com.example.myapplication.presentation.ui.CameraCurve
import com.example.myapplication.presentation.ui.MainInputType
import com.example.myapplication.presentation.ui.BottomInteractiveBlock
import com.example.myapplication.presentation.ui.ConversationGrouping
import com.example.myapplication.presentation.ui.RenderableConversationItem
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

    val listState = rememberScalingLazyListState()
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

    // ================= 唯一滚动所有者 =================
    // 之前这里有三条 LaunchedEffect 各自驱动同一个 ScalingLazyListState：
    //   ① 流式阶段瞬时 scrollBy 跟随  ② 条目变化时 animateScrollToItem（动画）
    //   ③ 一轮结束 springScrollToEnd（还是逐帧 scroll 的动画）
    // 三者互相取消、又与"内容边涨边滚"叠加，于是发完消息列表会持续弹来弹去。
    // 现在收敛成一条链路：同一时刻只有一个滚动协程，且两种状态互斥。
    val hasUserTurn = conversationItems.any { it is RenderableConversationItem.UserPrompt }
    val density = LocalDensity.current
    // 表冠/拖拽之后的一小段"镜头让路"窗口
    var cameraPauseUntil by remember { mutableLongStateOf(0L) }

    // 唯一镜头引擎：一条加速曲线，没有第二套瞬移机制。
    LaunchedEffect(state.isExecuting, hasUserTurn, conversationItems.size) {
        if (!hasUserTurn) return@LaunchedEffect
        val bottomPaddingPx = with(density) { Wear.Size.ListCenterReach.toPx() }
        var speedPxPerSec = 0f
        var lastFrameNanos = 0L
        while (isActive) {
            val frameNanos = withFrameMillis { it }
            val dt = if (lastFrameNanos == 0L) {
                0.016f
            } else {
                ((frameNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.004f, 0.05f)
            }
            lastFrameNanos = frameNanos
            // 用户在拖/刚转过表冠：这一帧不推进，避免和手指抢位置
            val userBusy = listState.isScrollInProgress || SystemClock.uptimeMillis() < cameraPauseUntil
            if (userBusy) {
                speedPxPerSec = 0f
                continue
            }
            val remainingPx = listState.remainingToEndPx(bottomPaddingPx)
            if (remainingPx <= 0.5f) {
                speedPxPerSec = 0f
                // 已经贴到末端：流式阶段等新内容；一轮结束就收工（全程没有瞬移）
                if (!state.isExecuting) break
                continue
            }
            // 速度由剩余距离决定（远快近慢），再由加速度上限决定"多久能加到那么快"（ease-in）；
            // 越接近末端目标速度越小，于是减速是自然发生的（ease-out）。
            val targetSpeed = CameraCurve.targetSpeed(remainingPx)
            speedPxPerSec = CameraCurve.nextSpeed(speedPxPerSec, targetSpeed, dt)
            val stepPx = CameraCurve.stepPx(speedPxPerSec, dt, remainingPx)
            if (stepPx > 0f) listState.dispatchRawDelta(stepPx)
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

    var currentTime by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(1000)
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
            TopClockBar(
                timeText = currentTime,
                modifier = Modifier.fillMaxSize()
            )
        }

        // ================= 3.5 审批浮层：没有卡片也没有描边，文字浮在径向暗晕之上 =================
        ApprovalFloatingLayer(
            visible = isApprovalFocus,
            toolName = state.pendingApproval?.toolName.orEmpty(),
            risk = state.pendingApproval?.risk,
            collapse = approvalCollapse.value
        )

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
            )
        )

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
