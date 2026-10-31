package com.example.myapplication.presentation

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.draw.shadow

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentExecutionState
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.AgentToolRegistry
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.RiskAssessment
import com.example.myapplication.presentation.ui.AgentSquareBlurBox
import com.example.myapplication.presentation.ui.BottomBlockMode
import com.example.myapplication.presentation.ui.BottomInteractiveBlock
import com.example.myapplication.presentation.ui.MainInputType
import com.example.myapplication.presentation.ui.ConversationGrouping
import com.example.myapplication.presentation.ui.OnboardingScreen
import com.example.myapplication.presentation.ui.RenderableConversationItem
import com.example.myapplication.presentation.ui.SessionDrawer
import com.example.myapplication.presentation.ui.SettingsScreen
import com.example.myapplication.presentation.ui.TopClockBar
import com.example.myapplication.presentation.ui.VoiceListeningOverlay
import com.example.myapplication.presentation.ui.SpringColoredToolPill
import com.example.myapplication.presentation.ui.SpringStreamingText
import com.example.myapplication.presentation.ui.ThoughtPill
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearBanner
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.presentation.ui.UnifiedMatrixCanvas
import com.example.myapplication.presentation.ui.UserChatBubble
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.AudioRecordWavCapture
import com.example.myapplication.voice.VoiceAsrGateway
import com.example.myapplication.voice.VoiceInputManager
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import java.util.UUID
import kotlin.coroutines.resume

class MainActivity : ComponentActivity() {
    private var externalPromptSeq = 0L
    private val externalPromptState = androidx.compose.runtime.mutableStateOf<ExternalPrompt?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // 输入法由 Compose insets 驱动避让：窗口不整体平移，也不二次压缩内容高度
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            WearAgentUnifiedApp(
                externalPrompt = externalPromptState.value,
                onClearExternalPrompt = { externalPromptState.value = null }
            )
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: android.content.Intent?) {
        val apiKey = intent?.getStringExtra("api_key")
        if (!apiKey.isNullOrBlank()) {
            AgentPreferences(this).apiKey = apiKey
        }
        val securityModeStr = intent?.getStringExtra("security_mode")
        if (!securityModeStr.isNullOrBlank()) {
            try {
                AgentPreferences(this).securityMode = com.example.myapplication.agent.contract.SecurityMode.valueOf(securityModeStr)
            } catch (_: Exception) {}
        }
        val prompt = intent?.getStringExtra("prompt")
        if (!prompt.isNullOrBlank()) {
            // 每次 intent 递增序号：同文本的新 intent 也能再触发，重组则不会重复触发
            externalPromptState.value = ExternalPrompt(seq = ++externalPromptSeq, text = prompt)
        }
    }
}


enum class AppScreen {
    ONBOARDING,
    MAIN,
    SETTINGS
}

@Composable
fun WearAgentUnifiedApp(
    externalPrompt: ExternalPrompt? = null,
    onClearExternalPrompt: () -> Unit = {}
) {
    val context = LocalContext.current

    val preferences = remember { AgentPreferences(context) }
    val toolRegistry = remember { AgentToolRegistry(context) }
    val agentDispatcher = remember { AgentDispatcher(context, toolRegistry, preferences) }
    val voiceInputManager = remember { VoiceInputManager(context, VoiceAsrGateway(capture = AudioRecordWavCapture(context))) }

    var currentScreen by remember {
        mutableStateOf(if (preferences.hasCompletedOnboarding) AppScreen.MAIN else AppScreen.ONBOARDING)
    }

    var liveSpacingDp by remember { mutableFloatStateOf(preferences.matrixSpacingDp) }

    DisposableEffect(Unit) {
        toolRegistry.onMatrixDensityChange = { spacing ->
            liveSpacingDp = spacing
            preferences.matrixSpacingDp = spacing
        }
        toolRegistry.onPowerModeChange = { ultra ->
            preferences.ultraSaving = ultra
        }
        onDispose {
            voiceInputManager.destroy()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
    ) {
        AnimatedContent(
            targetState = currentScreen,
            transitionSpec = {
                if (targetState == AppScreen.SETTINGS) {
                    (slideInVertically(tween(240, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(180)))
                        .togetherWith(slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 5 } + fadeOut(tween(180)))
                } else {
                    (slideInVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(180)))
                        .togetherWith(slideOutVertically(tween(240, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(180)))
                }
            },
            label = "screenTransition"
        ) { screen ->
            when (screen) {
                AppScreen.ONBOARDING -> {
                    OnboardingScreen(
                        preferences = preferences,
                        onFinish = {
                            liveSpacingDp = preferences.matrixSpacingDp
                            currentScreen = AppScreen.MAIN
                        }
                    )
                }

                AppScreen.SETTINGS -> {
                    BackHandler {
                        liveSpacingDp = preferences.matrixSpacingDp
                        currentScreen = AppScreen.MAIN
                    }
                    SettingsScreen(
                        preferences = preferences,
                        onBack = {
                            liveSpacingDp = preferences.matrixSpacingDp
                            currentScreen = AppScreen.MAIN
                        },
                        onRestartOnboarding = {
                            preferences.hasCompletedOnboarding = false
                            currentScreen = AppScreen.ONBOARDING
                        }
                    )
                }

                AppScreen.MAIN -> {
                    MainAgentDashboard(
                        preferences = preferences,
                        agentDispatcher = agentDispatcher,
                        voiceInputManager = voiceInputManager,
                        liveSpacingDp = liveSpacingDp,
                        onOpenSettings = { currentScreen = AppScreen.SETTINGS },
                        externalPrompt = externalPrompt,
                        onClearExternalPrompt = onClearExternalPrompt
                    )
                }
            }
        }
    }
}

/**
 * 发送消息与回复卡片弹出物理动效容器 (微晶弹簧微动效)
 */
@Composable
private fun AnimatedMessageCard(
    key: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scale = remember(key) { Animatable(0.90f) }
    val alpha = remember(key) { Animatable(0f) }
    val offsetY = remember(key) { Animatable(16f) }

    LaunchedEffect(key) {
        launch {
            scale.animateTo(1f, spring(0.72f, 280f))
        }
        launch {
            alpha.animateTo(1f, tween(180))
        }
        launch {
            offsetY.animateTo(0f, spring(0.75f, 300f))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
                translationY = offsetY.value
            }
    ) {
        content()
    }
}

/** 对话框快捷指令胶囊：令牌底 + 描边，触达高度不小于 44dp。 */
@Composable
private fun QuickPromptChip(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(Wear.Size.Touch)
            .clip(RoundedCornerShape(Wear.Radius.Chip))
            .background(Wear.Palette.SurfaceAlt)
            .border(1.dp, Wear.Palette.Border, RoundedCornerShape(Wear.Radius.Chip))
            .clickable { onClick() }
            .padding(horizontal = Wear.Space.Xs),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = Wear.TextCaption.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 对话框操作按钮：主操作 Accent 实底，次操作 SurfaceAlt；触达高度不小于 44dp。 */
@Composable
private fun DialogActionButton(
    label: String,
    isPrimary: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(Wear.Size.Touch)
            .clip(RoundedCornerShape(Wear.Radius.Chip))
            .background(if (isPrimary) Wear.Palette.Accent else Wear.Palette.SurfaceAlt)
            .clickable { onClick() }
            .padding(horizontal = Wear.Space.Lg),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = label,
            style = Wear.TextTitle.copy(
                color = if (isPrimary) Wear.Palette.Bg else Wear.Palette.TextSecondary
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun MainAgentDashboard(
    preferences: AgentPreferences,
    agentDispatcher: AgentDispatcher,
    voiceInputManager: VoiceInputManager,
    liveSpacingDp: Float,
    onOpenSettings: () -> Unit,
    externalPrompt: ExternalPrompt? = null,
    onClearExternalPrompt: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRecordingVoice by remember { mutableStateOf(false) }
    val shockwaveAnim = remember { Animatable(-1f) }

    var isThinkingActive by remember { mutableStateOf(false) }
    val thinkingIntensity by animateFloatAsState(
        targetValue = if (isThinkingActive) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "thinkingIntensity"
    )

    var isTaskCompletedTrigger by remember { mutableStateOf(false) }

    // 底部交互模式与主按键形态
    var bottomBlockMode by remember { mutableStateOf(BottomBlockMode.IDLE) }
    var mainInputType by remember {
        mutableStateOf(
            if (preferences.mainInputType == "KEYBOARD") MainInputType.KEYBOARD else MainInputType.VOICE
        )
    }
    var currentRiskAssessment by remember { mutableStateOf<RiskAssessment?>(null) }
    var pendingContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    var delayedStretchJob by remember { mutableStateOf<Job?>(null) }

    val scrollState = rememberScrollState()
    val mainFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        mainFocusRequester.requestFocus()
    }

    // 初始状态与多会话管理
    val defaultInitialPrompt = "主人，今天有什么需要解决的技术或生活问题？"
    val initialSession = remember {
        ChatSession(
            id = "session_default",
            title = "当前对话",
            conversationBlocks = listOf(
                ConversationBlock.Text(id = "init_0", content = defaultInitialPrompt, isUser = false)
            )
        )
    }

    var sessions by remember { mutableStateOf(listOf(initialSession)) }
    var activeSessionId by remember { mutableStateOf("session_default") }
    var isSessionDrawerOpen by remember { mutableStateOf(false) }

    val currentSession = sessions.find { it.id == activeSessionId } ?: sessions.first()

    var agentState by remember {
        mutableStateOf(
            AgentExecutionState(
                prompt = "就绪",
                conversationBlocks = currentSession.conversationBlocks,
                isFinished = true
            )
        )
    }

    fun switchToSession(id: String) {
        val target = sessions.find { it.id == id } ?: return
        activeSessionId = id
        agentDispatcher.loadConversationHistory(target.history)
        agentState = agentState.copy(
            conversationBlocks = target.conversationBlocks,
            isFinished = true,
            isThinking = false
        )
        isSessionDrawerOpen = false
    }

    fun createNewSession() {
        val newS = ChatSession(
            title = "新对话",
            conversationBlocks = listOf(
                ConversationBlock.Text(
                    id = java.util.UUID.randomUUID().toString(),
                    content = defaultInitialPrompt,
                    isUser = false
                )
            )
        )
        sessions = listOf(newS) + sessions
        activeSessionId = newS.id
        agentDispatcher.resetConversation()
        agentState = AgentExecutionState(
            prompt = "新对话就绪",
            conversationBlocks = newS.conversationBlocks,
            isFinished = true
        )
        isSessionDrawerOpen = false
    }

    fun deleteSession(id: String) {
        val remaining = sessions.filter { it.id != id }
        if (remaining.isEmpty()) {
            createNewSession()
        } else {
            sessions = remaining
            if (activeSessionId == id) {
                switchToSession(remaining.first().id)
            }
        }
    }

    var isExecutingAgent by remember { mutableStateOf(false) }
    var inputState by remember { mutableStateOf(TextInputState()) }

    // 瞬时提示：语音错误与模型切换反馈共用一条，短时可见后自动消失
    var noticeText by remember { mutableStateOf<String?>(null) }
    var noticeIsError by remember { mutableStateOf(false) }
    var noticeSeq by remember { mutableStateOf(0) }

    fun showNotice(text: String, isError: Boolean) {
        noticeText = text
        noticeIsError = isError
        noticeSeq += 1
    }

    LaunchedEffect(noticeSeq) {
        if (noticeSeq > 0) {
            delay(2600)
            noticeText = null
        }
    }

    var triggerAgentAction by remember { mutableStateOf<(String) -> Unit>({}) }
    var launchVoiceInputAction by remember { mutableStateOf<() -> Unit>({}) }

    val speechInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                triggerAgentAction(spokenText)
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
    BackHandler(enabled = isSessionDrawerOpen) {
        isSessionDrawerOpen = false
    }
    BackHandler(enabled = bottomBlockMode == BottomBlockMode.ACTION_ALLOW) {
        pendingContinuation?.resume(false)
        pendingContinuation = null
        bottomBlockMode = BottomBlockMode.IDLE
    }

    DisposableEffect(agentDispatcher) {
        agentDispatcher.onRequireConfirmation = { _, risk ->
            currentRiskAssessment = risk
            bottomBlockMode = BottomBlockMode.ACTION_ALLOW
            suspendCancellableCoroutine { cont ->
                pendingContinuation = cont
            }
        }
        onDispose {
            agentDispatcher.onRequireConfirmation = null
        }
    }

    fun triggerAgent(prompt: String) {
        if (isExecutingAgent) return
        delayedStretchJob?.cancel()

        scope.launch {
            // 水波纹自底栏向中心扩散激荡 (纯白微晶光波)
            launch {
                shockwaveAnim.snapTo(0f)
                shockwaveAnim.animateTo(1f, tween(360, easing = LinearEasing))
            }

            isExecutingAgent = true
            isThinkingActive = true
            isTaskCompletedTrigger = false

            delayedStretchJob = launch {
                delay(300)
                if (isExecutingAgent && bottomBlockMode != BottomBlockMode.ACTION_ALLOW) {
                    bottomBlockMode = BottomBlockMode.THINKING_STOP
                }
            }

            val updatedTitle = if (currentSession.title == "新对话" || currentSession.title == "当前对话") {
                prompt.take(12)
            } else {
                currentSession.title
            }

            agentDispatcher.executeUserPrompt(
                prompt = prompt,
                existingBlocks = currentSession.conversationBlocks
            ) { newState ->
                agentState = newState
                isThinkingActive = isExecutingAgent && !newState.isFinished && (newState.isThinking || newState.isExecutingTool)

                sessions = sessions.map { s ->
                    if (s.id == activeSessionId) {
                        s.copy(
                            title = updatedTitle,
                            conversationBlocks = newState.conversationBlocks,
                            history = agentDispatcher.getConversationHistory()
                        )
                    } else s
                }

                if (newState.isFinished) {
                    isExecutingAgent = false
                    isThinkingActive = false
                    delayedStretchJob?.cancel()

                    // 触发 30 度表镜掠影反光
                    isTaskCompletedTrigger = true
                    bottomBlockMode = BottomBlockMode.IDLE
                } else if (!newState.isThinking && !newState.isExecutingTool && bottomBlockMode != BottomBlockMode.ACTION_ALLOW) {
                    bottomBlockMode = BottomBlockMode.IDLE
                }
            }
        }
    }

    DisposableEffect(Unit) {
        triggerAgentAction = { triggerAgent(it) }

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
                putExtra(RecognizerIntent.EXTRA_PROMPT, "请说话，正在倾听...")
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

    LaunchedEffect(agentState.conversationBlocks.size) {
        if (isExecutingAgent) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // 外部 intent prompt：进入主界面且空闲时触发一次，随后回清并记录序号防止重组重放
    var consumedExternalSeq by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(externalPrompt, isExecutingAgent, consumedExternalSeq) {
        val prompt = externalPrompt ?: return@LaunchedEffect
        if (!ExternalPromptGate.shouldConsume(
                prompt = prompt,
                ready = !isExecutingAgent,
                lastConsumedSeq = consumedExternalSeq
            )
        ) return@LaunchedEffect

        consumedExternalSeq = prompt.seq
        onClearExternalPrompt()
        triggerAgent(prompt.text.trim())
    }

    LaunchedEffect(Unit) {
        voiceInputManager.onListeningStateChanged = { listening ->
            isRecordingVoice = listening
            bottomBlockMode = if (listening) BottomBlockMode.RECORDING else BottomBlockMode.IDLE
        }
        voiceInputManager.onSpeechResult = { recognizedText ->
            isRecordingVoice = false
            bottomBlockMode = BottomBlockMode.IDLE
            triggerAgent(recognizedText)
        }
        voiceInputManager.onSpeechError = { rawMessage ->
            isRecordingVoice = false
            bottomBlockMode = BottomBlockMode.IDLE
            SpeechErrorNotice.message(rawMessage)?.let { showNotice(it, true) }
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
            .pointerInput(bottomBlockMode, isExecutingAgent, inputState.isOpen, isSessionDrawerOpen) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startX = down.position.x
                    val startY = down.position.y

                    // 空间严格隔离：仅在中部正文区监听，完全避开顶部 42dp 状态栏与底部 96dp 交互栏
                    val inContentZone = startY > 42.dp.toPx() && startY < (size.height - 96.dp.toPx())
                    // 状态严格隔离：仅在底栏空闲、未在录音、未在执行智能体、未在文本输入时才允许手势
                    val canGesture = inContentZone &&
                            bottomBlockMode == BottomBlockMode.IDLE &&
                            !isExecutingAgent &&
                            !inputState.isOpen

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (canGesture) {
                                val deltaX = change.position.x - startX
                                val deltaY = change.position.y - startY
                                // 仅手势明确向左轻划时唤出抽屉 (从右侧顺手推入，手势与动效完全一致)
                                if (abs(deltaX) > 24.dp.toPx() && abs(deltaX) > abs(deltaY) * 1.2f) {
                                    if (!isSessionDrawerOpen) {
                                        isSessionDrawerOpen = true
                                    }
                                }
                            }
                            break
                        }
                    }
                }
            }
    ) {
        // ================= 1. 全局唯一点阵坐标系 (通体清晰方阵，覆盖全圆屏) =================
        UnifiedMatrixCanvas(
            modifier = Modifier.fillMaxSize(),
            approvalVisualState = agentState.approvalVisualState,
            isTaskCompletedTrigger = isTaskCompletedTrigger,
            thinkingIntensity = thinkingIntensity,
            isRecordingVoice = isRecordingVoice,
            shockwaveProgress = shockwaveAnim.value,
            gridSpacingDp = liveSpacingDp
        )

        // ================= 2. 文字与原生高斯模糊简单框一体联动滚动流 =================
        // 全屏纵向滑动，正文左右内缩 Wear.Space.Edge，顶部时钟带与底部底栏保留区由 DstIn 遮罩完全抹掉
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Wear.Space.Edge)
                .focusRequester(mainFocusRequester)
                .focusable()
                .onRotaryScrollEvent {
                    scope.launch {
                        scrollState.scrollBy(it.verticalScrollPixels)
                        if (kotlin.math.abs(it.verticalScrollPixels) > 8f) {
                            context.getSystemService(Vibrator::class.java)?.let { vib ->
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                                }
                            }
                        }
                    }
                    true
                }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    // DstIn 遮罩：alpha=0 处内容被抹掉，alpha=1 处内容完整保留
                    // 顶部：0~38dp 全抹（时钟带 8~31dp 及上下缓冲），38~50dp 渐入，50dp 以下完整
                    // 底部：距底 104dp 以上完整，104~92dp 渐出，距底 92dp 以下（底栏保留区 + 主键上方 24dp）全抹
                    val topCutoffPx = (Wear.Size.TopContentInset + Wear.Space.Xs).toPx()
                    val topFadePx = (topCutoffPx + Wear.Space.Md.toPx())
                    val bottomFadeEndPx = size.height - Wear.Size.BottomBarReserve.toPx()
                    val bottomFadeStartPx = bottomFadeEndPx - Wear.Space.Md.toPx()

                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                (topCutoffPx / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                (topFadePx / size.height).coerceIn(0f, 1f) to Wear.Palette.Bg,
                                (bottomFadeStartPx / size.height).coerceIn(0f, 1f) to Wear.Palette.Bg,
                                (bottomFadeEndPx / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                1.0f to Color.Transparent
                            )
                        ),
                        blendMode = BlendMode.DstIn
                    )
                }
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 顶部时间留白：正文从顶部渐入结束处（50dp）开始，避免首条被遮罩吃掉
            Spacer(modifier = Modifier.height(Wear.Size.TopContentInset + Wear.Space.Xs + Wear.Space.Md))

            // 依次渲染对话结构流 (用户右对齐气泡 vs Assistant 纯正方形毛玻璃框，工具调用完整内嵌)
            val conversationItems = remember(agentState.conversationBlocks) {
                ConversationGrouping.groupBlocks(agentState.conversationBlocks)
            }

            conversationItems.forEach { item ->
                val itemKey = when (item) {
                    is RenderableConversationItem.UserPrompt -> item.block.id
                    is RenderableConversationItem.AssistantTurn -> item.key
                }
                AnimatedMessageCard(key = itemKey) {
                    when (item) {
                    is RenderableConversationItem.UserPrompt -> {
                        // 2.1 用户指令：允许右对齐聊天气泡样式，随手势一体滚动
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            UserChatBubble {
                                BasicText(
                                    text = item.block.content,
                                    style = Wear.TextBody
                                )
                            }
                        }
                    }

                    is RenderableConversationItem.AssistantTurn -> {
                        // 2.2 Agent 回复：绝不用聊天气泡，Surface 实底卡片 + 1dp 描边
                        // 思考胶囊、工具调用与逐字流式文本 100% 完整包含在卡片内部
                        AgentSquareBlurBox(
                            modifier = Modifier.fillMaxWidth(),
                            approvalVisualState = agentState.approvalVisualState,
                            isTaskCompletedTrigger = isTaskCompletedTrigger,
                            thinkingIntensity = thinkingIntensity,
                            isRecordingVoice = isRecordingVoice,
                            shockwaveProgress = shockwaveAnim.value,
                            gridSpacingDp = liveSpacingDp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                item.blocks.forEach { block ->
                                    when (block) {
                                        is ConversationBlock.Thought -> {
                                            // 思考微型小胶囊 (带弹性收展动画，内嵌在方框内)
                                            ThoughtPill(thought = block)
                                        }

                                        is ConversationBlock.ToolCall -> {
                                            // 彩色工具调用小胶囊 (专属颜色、状态干了什么、弹性展开，内嵌在方框内)
                                            SpringColoredToolPill(toolCall = block)
                                        }

                                        is ConversationBlock.Text -> {
                                            // 弹性逐字生成流式文本 (带弹簧微晶跃动与呼吸跳动光标)
                                            SpringStreamingText(
                                                text = block.content,
                                                isStreaming = block.isStreaming
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }

            // 错误提示 (随手势一体滑动)
            val err = agentState.errorMessage
            if (!err.isNullOrBlank()) {
                WearBanner(text = "[ALERT] $err", tone = Tone.DANGER)
            }

            // 底部留白：留到底部渐出结束处（104dp），保证末尾正文完整可读而不被遮罩抹掉
            Spacer(modifier = Modifier.height(Wear.Size.BottomBarReserve + Wear.Space.Md))
        }

        // ================= 3. 顶部弧形微晶时间 (遵循穿戴纯净规范，不再常驻会话胶囊) =================
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            TopClockBar(timeText = currentTime)
        }

        // ================= 4. 单体演变底栏控制器 =================
        BottomInteractiveBlock(
            mode = bottomBlockMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            mainInputType = mainInputType,
            onMainInputTypeChange = { newType ->
                mainInputType = newType
                preferences.mainInputType = newType.name
            },
            rightButtonAction = preferences.rightButtonAction,
            cooldownSeconds = currentRiskAssessment?.requireCooldownSeconds ?: 0,
            riskExplanation = currentRiskAssessment?.riskSummary ?: "",
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
                inputState = inputState.reduce(TextInputEvent.Open)
            },
            onRightActionClick = {
                when (preferences.rightButtonAction) {
                    RightButtonAction.MODEL_SWITCH -> {
                        // 只在后端已缓存的模型清单内循环，绝不切到与 baseUrl 不匹配的模型
                        val next = ModelCycle.next(preferences.modelName, preferences.cachedRemoteModels)
                        if (next == null) {
                            showNotice("未发现可用模型，请先在设置中拉取模型列表", true)
                        } else {
                            preferences.modelName = next
                            showNotice("模型已切换: $next", false)
                        }
                    }
                    RightButtonAction.CLEAR_MEMORY -> {
                        agentDispatcher.resetConversation()
                        agentState = AgentExecutionState(
                            prompt = "记忆已重置",
                            conversationBlocks = listOf(
                                ConversationBlock.Text(id = UUID.randomUUID().toString(), content = "会话记忆已清空，系统就绪。")
                            ),
                            isFinished = true
                        )
                    }
                    RightButtonAction.OPEN_SETTINGS -> {
                        onOpenSettings()
                    }
                }
            },
            onStopClick = {
                agentDispatcher.cancelCurrentExecution()
                voiceInputManager.cancel()
                pendingContinuation?.resume(false)
                pendingContinuation = null
                bottomBlockMode = BottomBlockMode.IDLE
                isExecutingAgent = false
                isThinkingActive = false
            },
            onAllow = {
                pendingContinuation?.resume(true)
                pendingContinuation = null
                bottomBlockMode = BottomBlockMode.IDLE
            },
            onReject = {
                pendingContinuation?.resume(false)
                pendingContinuation = null
                bottomBlockMode = BottomBlockMode.IDLE
            },
            onSwipeUpSettings = {
                onOpenSettings() // 阻尼跟手上滑进入设置
            }
        )

        // ================= 5. 瞬时提示条 (语音错误 / 模型切换反馈) =================
        val inlineNotice = noticeText
        if (!inlineNotice.isNullOrBlank()) {
            WearBanner(
                text = inlineNotice,
                tone = if (noticeIsError) Tone.DANGER else Tone.ACCENT,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = Wear.Space.Edge,
                        end = Wear.Space.Edge,
                        bottom = Wear.Size.BottomBarReserve
                    )
            )
        }

        // ================= 6. 自定义文本输入弹窗 (避让输入法 / 返回键只关弹窗) =================
        if (inputState.isOpen) {
            val dialogFocusRequester = remember { FocusRequester() }

            // 返回键只关闭弹窗，绝不退出 Activity；未发送文本保留在状态机中
            BackHandler {
                inputState = inputState.reduce(TextInputEvent.Cancel)
            }

            LaunchedEffect(Unit) {
                dialogFocusRequester.requestFocus()
            }

            // 提交：屏幕按钮与输入法 Action 键共用同一条路径（Wear 输入法整屏时按钮够不到）
            val submitInput: () -> Unit = {
                val afterSubmit = inputState.reduce(TextInputEvent.Submit)
                inputState = afterSubmit
                afterSubmit.lastSent?.let { triggerAgent(it) }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    // 外层承接口感遮罩，内容整体抬到输入法上沿之上
                    .imePadding()
                    .padding(
                        start = Wear.Space.Edge,
                        end = Wear.Space.Edge,
                        top = Wear.Space.Sm,
                        bottom = Wear.Space.Sm
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 输入区可滚动：高度不足时只压缩这一块，操作按钮永不被挤出圆屏
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BasicText(text = "输入文字指令", style = Wear.TextTitle)
                        BasicTextField(
                            value = inputState.text,
                            onValueChange = { inputState = inputState.reduce(TextInputEvent.Change(it)) },
                            textStyle = Wear.TextBody,
                            cursorBrush = SolidColor(Wear.Palette.TextPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitInput() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(dialogFocusRequester)
                                .clip(RoundedCornerShape(Wear.Radius.Chip))
                                .background(Wear.Palette.SurfaceAlt)
                                .border(1.dp, Wear.Palette.Border, RoundedCornerShape(Wear.Radius.Chip))
                                .padding(Wear.Space.Md)
                        )
                        // 预置快捷指令胶囊 (极简纯净纯色无 Emoji，纯黑白冷灰体系)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                        ) {
                            QuickPromptChip(
                                label = "时间天气与资讯",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val send = "查询当前本地时间与天气环境，并联网检索最新科技动态。"
                                    inputState = inputState.reduce(TextInputEvent.Cancel)
                                    triggerAgent(send)
                                }
                            )
                            QuickPromptChip(
                                label = "诊断电量与马达",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    val send = "诊断当前手表电量与硬件温度，并触发双击触觉马达。"
                                    inputState = inputState.reduce(TextInputEvent.Cancel)
                                    triggerAgent(send)
                                }
                            )
                        }
                    }

                    // 操作按钮固定在可视区底部：键盘展开时始终可见可点
                    Row(horizontalArrangement = Arrangement.spacedBy(Wear.Space.Md)) {
                        DialogActionButton(
                            label = "取消",
                            isPrimary = false,
                            onClick = { inputState = inputState.reduce(TextInputEvent.Cancel) }
                        )
                        DialogActionButton(
                            label = "发送",
                            isPrimary = true,
                            onClick = { submitInput() }
                        )
                    }
                }
            }
        }

        // ================= 7. Google M3 原生贴边自适应多会话抽屉 (支持快捷调试模型) =================
        SessionDrawer(
            isOpen = isSessionDrawerOpen,
            sessions = sessions,
            activeSessionId = activeSessionId,
            currentModelName = preferences.modelName,
            availableModels = ModelCycle.parse(preferences.cachedRemoteModels),
            onSelectModel = { selectedModel ->
                preferences.modelName = selectedModel
            },
            onSelectSession = { id -> switchToSession(id) },
            onCreateSession = { createNewSession() },
            onDeleteSession = { id -> deleteSession(id) },
            onClose = { isSessionDrawerOpen = false }
        )

        // ================= 8. 应用内语音录制与倾听悬浮层 =================
        if (isRecordingVoice) {
            VoiceListeningOverlay(
                onCancel = {
                    voiceInputManager.stopListening()
                    isRecordingVoice = false
                    bottomBlockMode = BottomBlockMode.IDLE
                }
            )
        }
    }
}


// ============================================================================
// 纯逻辑：文本输入弹窗状态机（无 Compose / Android 依赖，可单测）
// ============================================================================
data class TextInputState(
    val isOpen: Boolean = false,
    val text: String = "",
    val lastSent: String? = null
)

sealed class TextInputEvent {
    /** 打开弹窗：不清空未发送文本 */
    object Open : TextInputEvent()

    /** 取消 / 返回键关闭：文本原样保留，重开可继续编辑 */
    object Cancel : TextInputEvent()

    data class Change(val text: String) : TextInputEvent()

    /** 提交：仅非空白文本才关闭并产出一条一次性待发指令 */
    object Submit : TextInputEvent()
}

fun TextInputState.reduce(event: TextInputEvent): TextInputState = when (event) {
    TextInputEvent.Open -> copy(isOpen = true)
    TextInputEvent.Cancel -> copy(isOpen = false)
    is TextInputEvent.Change -> copy(text = event.text)
    TextInputEvent.Submit ->
        if (text.isBlank()) this else copy(isOpen = false, text = "", lastSent = text)
}

// ============================================================================
// 纯逻辑：右副键模型循环（只在 cachedRemoteModels 清单内循环）
// ============================================================================
object ModelCycle {
    fun parse(cached: String?): List<String> = (cached ?: "")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

    fun next(current: String?, cached: String?): String? {
        val models = parse(cached)
        if (models.isEmpty()) return null
        val index = models.indexOf(current?.trim())
        return models[(index + 1) % models.size]
    }
}

// ============================================================================
// 纯逻辑：externalPrompt 一次性消费（seq 标识单次 intent，重组不重放）
// ============================================================================
data class ExternalPrompt(val seq: Long, val text: String)

object ExternalPromptGate {
    fun shouldConsume(prompt: ExternalPrompt?, ready: Boolean, lastConsumedSeq: Long?): Boolean =
        prompt != null && ready && prompt.text.isNotBlank() && prompt.seq != lastConsumedSeq
}

// ============================================================================
// 纯逻辑：语音错误提示文案规整（空白消息不产生提示条）
// ============================================================================
object SpeechErrorNotice {
    fun message(raw: String?): String? = raw?.trim()?.takeIf { it.isNotEmpty() }
}

