package com.example.myapplication.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
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
import com.example.myapplication.presentation.ui.FrostedPhysicalMaskCard
import com.example.myapplication.presentation.ui.FrostedTextMaskContainer
import com.example.myapplication.presentation.ui.OnboardingScreen
import com.example.myapplication.presentation.ui.RenderableConversationItem
import com.example.myapplication.presentation.ui.SessionDrawer
import com.example.myapplication.presentation.ui.SettingsScreen
import com.example.myapplication.presentation.ui.SpringColoredToolPill
import com.example.myapplication.presentation.ui.SpringStreamingText
import com.example.myapplication.presentation.ui.ThoughtPill
import com.example.myapplication.presentation.ui.UnifiedMatrixCanvas
import com.example.myapplication.presentation.ui.UserChatBubble
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
    companion object {
        var onBackPressedCustom: (() -> Boolean)? = null
        var onPromptTrigger: ((String) -> Unit)? = null
    }

    override fun onBackPressed() {
        if (onBackPressedCustom?.invoke() == true) {
            return
        }
        super.onBackPressed()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            WearAgentUnifiedApp()
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
            onPromptTrigger?.invoke(prompt)
        }
    }
}

/**
 * 顶部弧形微晶时间显示 (完全贴合圆形表盘边缘弧度 · 居中工整 · 绝不倒字)
 */
/**
 * 顶部弧形微晶时间显示 (高精细极坐标逐字弧形贴边排版 · 绝对居中对称 · 严禁重叠倒字)
 */
@Composable
fun TopClockBar(
    timeText: String,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val w = size.width
        val centerX = w / 2f
        val centerY = centerX // 手表圆心在 (centerX, centerX)
        // 贴合圆形表盘顶部弧线半径 (圆盘半径内缩 14dp 作为字符几何中心)
        val arcRadius = centerX - 14.dp.toPx()

        drawIntoCanvas { canvas ->
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#E2E8F0")
                textSize = 13.sp.toPx()
                typeface = android.graphics.Typeface.create(
                    android.graphics.Typeface.MONOSPACE,
                    android.graphics.Typeface.BOLD
                )
                textAlign = android.graphics.Paint.Align.CENTER
            }

            val textLength = timeText.length
            val midIndex = (textLength - 1) / 2f
            val charAngleStepDeg = 4.6f // 每个字符的表盘极坐标弧度夹角 (度)

            for (i in 0 until textLength) {
                val charStr = timeText[i].toString()
                // 12 点钟正顶端对应极坐标 -90 度
                val angleDeg = -90f + (i - midIndex) * charAngleStepDeg
                val angleRad = angleDeg * (Math.PI.toFloat() / 180f)

                val charX = centerX + arcRadius * kotlin.math.cos(angleRad)
                val charY = centerY + arcRadius * kotlin.math.sin(angleRad)
                val rotationDeg = angleDeg + 90f // 字符沿切线自然微倾

                canvas.nativeCanvas.save()
                canvas.nativeCanvas.translate(charX, charY)
                canvas.nativeCanvas.rotate(rotationDeg)
                // 绘制在旋转坐标系原点
                canvas.nativeCanvas.drawText(charStr, 0f, paint.textSize / 3f, paint)
                canvas.nativeCanvas.restore()
            }
        }
    }
}

enum class AppScreen {
    ONBOARDING,
    MAIN,
    SETTINGS
}

@Composable
fun WearAgentUnifiedApp() {
    val context = LocalContext.current

    val preferences = remember { AgentPreferences(context) }
    val toolRegistry = remember { AgentToolRegistry(context) }
    val agentDispatcher = remember { AgentDispatcher(context, toolRegistry, preferences) }
    val voiceInputManager = remember { VoiceInputManager(context) }

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
            .background(Color.Black)
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
                    DisposableEffect(Unit) {
                        MainActivity.onBackPressedCustom = {
                            liveSpacingDp = preferences.matrixSpacingDp
                            currentScreen = AppScreen.MAIN
                            true
                        }
                        onDispose {
                            MainActivity.onBackPressedCustom = null
                        }
                    }
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
                        onOpenSettings = { currentScreen = AppScreen.SETTINGS }
                    )
                }
            }
        }
    }
}

@Composable
private fun MainAgentDashboard(
    preferences: AgentPreferences,
    agentDispatcher: AgentDispatcher,
    voiceInputManager: VoiceInputManager,
    liveSpacingDp: Float,
    onOpenSettings: () -> Unit
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
                prompt = "伴生就绪",
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
    var showCustomInputDialog by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceInputManager.startListening()
        }
    }

    // 审批挂起、返回拦截与多会话抽屉关闭
    DisposableEffect(bottomBlockMode, isSessionDrawerOpen) {
        if (isSessionDrawerOpen) {
            MainActivity.onBackPressedCustom = {
                isSessionDrawerOpen = false
                true
            }
        } else if (bottomBlockMode == BottomBlockMode.ACTION_ALLOW) {
            MainActivity.onBackPressedCustom = {
                pendingContinuation?.resume(false)
                pendingContinuation = null
                bottomBlockMode = BottomBlockMode.IDLE
                true
            }
        } else {
            MainActivity.onBackPressedCustom = null
        }
        onDispose {
            MainActivity.onBackPressedCustom = null
        }
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
        MainActivity.onPromptTrigger = { prompt ->
            triggerAgent(prompt)
        }
        onDispose {
            MainActivity.onPromptTrigger = null
        }
    }

    LaunchedEffect(agentState.conversationBlocks.size) {
        if (isExecutingAgent) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
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
        voiceInputManager.onSpeechError = { _ ->
            isRecordingVoice = false
            bottomBlockMode = BottomBlockMode.IDLE
            showCustomInputDialog = true
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
            .background(Color(0xFF000000))
            .pointerInput(bottomBlockMode, isExecutingAgent, showCustomInputDialog, isSessionDrawerOpen) {
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
                            !showCustomInputDialog

                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break

                        if (!change.pressed) {
                            if (canGesture) {
                                val deltaX = change.position.x - startX
                                val deltaY = change.position.y - startY
                                // 仅手势明确向左轻划时唤出抽屉 (从右侧顺手推入，手势与动效完全一致)
                                if (deltaX < -24.dp.toPx() && abs(deltaX) > abs(deltaY) * 1.2f) {
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
        // 全屏纵向滑动，正文卡片微内缩 (24dp)，顶部与底部微自然遮罩，绝不遮挡底层点阵
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 28.dp, end = 28.dp)
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
                    // 遮罩只要给极微弱的一点点，只遮住时间下边缘一丁点地方，绝不遮盖底层背景点阵
                    val topCutoffPx = 16.dp.toPx()
                    val topFadePx = 24.dp.toPx()
                    val bottomFadeStartPx = size.height - 76.dp.toPx()
                    val bottomFadeEndPx = size.height - 14.dp.toPx()

                    drawRect(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                (topCutoffPx / size.height).coerceIn(0f, 1f) to Color.Transparent,
                                (topFadePx / size.height).coerceIn(0f, 1f) to Color.Black,
                                (bottomFadeStartPx / size.height).coerceIn(0f, 1f) to Color.Black,
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
            // 顶部时间与会话胶囊留白
            Spacer(modifier = Modifier.height(58.dp))

            // 依次渲染对话结构流 (用户右对齐气泡 vs Assistant 纯正方形毛玻璃框，工具调用完整内嵌)
            val conversationItems = remember(agentState.conversationBlocks) {
                ConversationGrouping.groupBlocks(agentState.conversationBlocks)
            }

            conversationItems.forEach { item ->
                when (item) {
                    is RenderableConversationItem.UserPrompt -> {
                        // 2.1 用户指令：允许右对齐聊天气泡样式，随手势一体滚动
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            UserChatBubble {
                                Text(
                                    text = item.block.content,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFE0F2FE),
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    is RenderableConversationItem.AssistantTurn -> {
                        // 2.2 Agent 回复：绝不用聊天气泡，纯正方形框 + API 33 原生硬件 RenderEffect 高斯模糊
                        // 思考胶囊、彩色工具调用与逐字流式文本 100% 完整包含在方框内部！
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

            // 错误提示 (随手势一体滑动)
            val err = agentState.errorMessage
            if (!err.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF261216).copy(alpha = 0.92f))
                        .border(0.8.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = "[ALERT] $err",
                        fontSize = 10.sp,
                        color = Color(0xFFF87171),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // 底部留白，保证文字能完全滑过底栏
            Spacer(modifier = Modifier.height(78.dp))
        }

        // ================= 3. 顶部弧形微晶时间与会话胶囊 (Google M3 规范，点击或左滑快速切换) =================
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopClockBar(timeText = currentTime)

            // 贴边微型会话标签胶囊 (M3 意纯圆药丸，展示当前对话，点击快速呼出会话抽屉)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF141720).copy(alpha = 0.88f))
                    .border(0.6.dp, Color(0xFF333D4F), RoundedCornerShape(10.dp))
                    .clickable { isSessionDrawerOpen = true }
                    .padding(horizontal = 9.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Text(
                        text = currentSession.title,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFE2E8F0),
                        maxLines = 1
                    )
                    Text(
                        text = "▾",
                        fontSize = 7.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
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
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasPerm) {
                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else if (!voiceInputManager.isRecognitionAvailable()) {
                    showCustomInputDialog = true
                } else {
                    voiceInputManager.startListening()
                }
            },
            onVoiceStopAndTrigger = {
                scope.launch {
                    shockwaveAnim.snapTo(0f)
                    shockwaveAnim.animateTo(1f, tween(480, easing = LinearEasing))
                }
                voiceInputManager.stopListening()
            },
            onLeftKeyboardClick = {
                showCustomInputDialog = true
            },
            onRightActionClick = {
                when (preferences.rightButtonAction) {
                    RightButtonAction.MODEL_SWITCH -> {
                        // 切换模型 (Gemini 2.5 Flash / Pro)
                        val next = if (preferences.modelName == "gemini-2.5-flash") "gemini-2.5-pro" else "gemini-2.5-flash"
                        preferences.modelName = next
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
                bottomBlockMode = BottomBlockMode.IDLE
                isExecutingAgent = false
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

        // ================= 6. 自定义文本输入弹窗 =================
        if (showCustomInputDialog) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("输入文字指令", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    BasicTextField(
                        value = customInputText,
                        onValueChange = { customInputText = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(Color.White),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF141720))
                            .border(0.8.dp, Color(0xFF333D4F), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    )
                    // 预置快捷指令胶囊 (极简纯净纯色无 Emoji，纯黑白冷灰体系)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "时间天气与资讯",
                            fontSize = 9.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF181D26))
                                .border(0.6.dp, Color(0xFF333D4F), RoundedCornerShape(6.dp))
                                .clickable {
                                    val send = "查询当前本地时间与天气环境，并联网检索最新科技动态。"
                                    showCustomInputDialog = false
                                    triggerAgent(send)
                                }
                                .padding(vertical = 5.dp, horizontal = 4.dp)
                        )
                        Text(
                            text = "诊断电量与马达",
                            fontSize = 9.sp,
                            color = Color(0xFFE2E8F0),
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF181D26))
                                .border(0.6.dp, Color(0xFF333D4F), RoundedCornerShape(6.dp))
                                .clickable {
                                    val send = "诊断当前手表电量与硬件温度，并触发双击触觉马达。"
                                    showCustomInputDialog = false
                                    triggerAgent(send)
                                }
                                .padding(vertical = 5.dp, horizontal = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "取消",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E2430))
                                .clickable { showCustomInputDialog = false }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                        Text(
                            text = "发送",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFF8FAFC))
                                .clickable {
                                    if (customInputText.isNotBlank()) {
                                        val send = customInputText
                                        customInputText = ""
                                        showCustomInputDialog = false
                                        triggerAgent(send)
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // ================= 7. Google M3 原生贴边自适应多会话抽屉 =================
        SessionDrawer(
            isOpen = isSessionDrawerOpen,
            sessions = sessions,
            activeSessionId = activeSessionId,
            onSelectSession = { id -> switchToSession(id) },
            onCreateSession = { createNewSession() },
            onDeleteSession = { id -> deleteSession(id) },
            onClose = { isSessionDrawerOpen = false }
        )
    }
}
