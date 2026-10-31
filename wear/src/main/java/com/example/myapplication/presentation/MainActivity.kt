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
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentExecutionState
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.AgentToolRegistry
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.agent.ChatSession
import com.example.myapplication.agent.contract.ConversationBlock
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
import com.example.myapplication.presentation.ui.ToolVisualMeta
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
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

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

    // 主键输入方式提示只在本次启动播报一次：从设置页返回主界面不重复打扰
    var announceInputMode by remember { mutableStateOf(true) }

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
                        announceInputMode = announceInputMode,
                        onInputModeAnnounced = { announceInputMode = false },
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

/** 跟随流式正文到底部用的位移量：远超一屏内容，滚动到末端会被自动截断。 */
private const val BOTTOM_FOLLOW_SCROLL_PX = 48_000f

/** 审批卡片危险等级：只由风险评估的现有字段推导，点亮 1/2/3 段。 */
private fun riskLevelOf(risk: RiskAssessment?): Int = when {
    risk == null -> 1
    risk.isHardBlocked -> 3
    risk.isHighRisk -> 2
    else -> 1
}

/** 审批卡片的工具名：取时间线上最后一个工具调用并解析成中文展示名；取不到返回 null。 */
private fun lastToolDisplayName(blocks: List<ConversationBlock>): String? =
    blocks.lastOrNull { it is ConversationBlock.ToolCall }
        ?.let { (it as ConversationBlock.ToolCall).toolName }
        ?.takeIf { it.isNotBlank() }
        ?.let { ToolVisualMeta.resolve(it).displayName }

/** 列表条目 key：用户气泡用 block id，助手回合用回合 key，保证滚动位置稳定。 */
private fun itemKeyOf(item: RenderableConversationItem): String = when (item) {
    is RenderableConversationItem.UserPrompt -> item.block.id
    is RenderableConversationItem.AssistantTurn -> item.key
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

/**
 * 审批内容卡片：标题 / 工具名 / 风险说明 / 三等分危险条。
 * 危险条用 Accent(常态) 与 Danger(中高风险及硬黑名单) 两种令牌着色，未点亮的段用 Border。
 */
@Composable
private fun ApprovalRequestCard(
    toolDisplayName: String,
    riskExplanation: String,
    riskLevel: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Wear.Radius.Card))
            .background(Wear.Palette.Surface)
            .border(
                width = 1.dp,
                color = Wear.Palette.Danger.copy(alpha = 0.55f),
                shape = RoundedCornerShape(Wear.Radius.Card)
            )
            .padding(horizontal = Wear.Space.Md, vertical = Wear.Space.Sm),
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        BasicText(text = "需要你的许可", style = Wear.TextTitle)
        BasicText(
            text = toolDisplayName,
            style = Wear.TextBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (riskExplanation.isNotBlank()) {
            BasicText(
                text = riskExplanation,
                style = Wear.TextCaption,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Wear.Space.Xs)) {
            repeat(3) { index ->
                val litColor = if (riskLevel >= 2) Wear.Palette.Danger else Wear.Palette.Accent
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < riskLevel) litColor else Wear.Palette.Border)
                )
            }
        }
    }
}

/**
 * 对话卡片宽度上限：240dp 圆屏减去两侧 [Wear.Space.Edge] 后四周仍留 20dp，
 * 长回复不再贴着圆屏边缘被裁；用户气泡同样收在此宽度内保持右对齐。
 */
private val ConversationCardMaxWidth = 200.dp

/** 底栏底距：与 [BottomInteractiveBlock] 调用处的 padding(bottom = 12.dp) 对齐，审批卡片据此换算自身底边。 */
private val BottomBarBottomInset = 12.dp

/** 主键输入方式的中文提示：模式切换与启动播报共用同一文案，避免用户靠小图标猜主键行为。 */
private const val NOTICE_INPUT_KEYBOARD = "已切换到键盘输入，点主键打开输入框"
private const val NOTICE_INPUT_VOICE = "已切换到语音输入，点主键或长按说话"

@Composable
private fun MainAgentDashboard(
    preferences: AgentPreferences,
    agentDispatcher: AgentDispatcher,
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

    val listState = rememberScalingLazyListState()
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

    // 审批滑块进度：0=拒绝红屏，1=放行绿屏，由底栏回调驱动全屏绿光与卡片
    var approvalProgress by remember { mutableFloatStateOf(0f) }

    // 审批卡片实测位置：底边贴保留区上沿向上生长，宽度取离圆心更远的横边所在弦长
    var approvalCardTopPx by remember { mutableFloatStateOf(0f) }
    var approvalCardHeightPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val windowSize = LocalWindowInfo.current.containerSize
    val approvalCardWidth = remember(approvalCardTopPx, approvalCardHeightPx, windowSize) {
        val radiusPx = min(windowSize.width, windowSize.height) / 2f
        val centerY = windowSize.height / 2f
        val farEdge = max(
            abs(approvalCardTopPx - centerY),
            abs(approvalCardTopPx + approvalCardHeightPx - centerY)
        )
        if (radiusPx <= 0f || farEdge >= radiusPx) {
            // 首帧尚无实测位置：先给圆屏中段的安全宽度，第二帧再按弦长收窄
            200.dp
        } else {
            val halfChord = sqrt(radiusPx * radiusPx - farEdge * farEdge)
            with(density) { (2f * halfChord).toDp() }
                .minus(Wear.Space.Sm * 2f)
                .coerceAtLeast(Wear.Size.Orb * 3f)
        }
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

    // 持久化的模式若是键盘，启动时主动说明主键当前会做文本输入（切换是静默保留的）
    LaunchedEffect(Unit) {
        if (announceInputMode) {
            if (mainInputType == MainInputType.KEYBOARD) {
                showNotice(NOTICE_INPUT_KEYBOARD, false)
            }
            onInputModeAnnounced()
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

    val conversationItems = remember(agentState.conversationBlocks) {
        ConversationGrouping.groupBlocks(agentState.conversationBlocks)
    }

    // 执行中每有新块到达，跟随到列表末端，保证流式正文的最新一句始终可见
    LaunchedEffect(agentState.conversationBlocks.size) {
        if (isExecutingAgent) {
            listState.scroll { scrollBy(BOTTOM_FOLLOW_SCROLL_PX) }
        }
    }

    // 审批待确认：助手卡片（含工具胶囊）若被滑块挤出可视区，就把它滚回可视区顶部。
    // 以 bottomBlockMode 为 key，进入审批态只触发一次；已在可视区时不动，用户手动滚动也不会被拉回。
    LaunchedEffect(bottomBlockMode) {
        if (bottomBlockMode != BottomBlockMode.ACTION_ALLOW || conversationItems.isEmpty()) {
            return@LaunchedEffect
        }
        val lastIndex = conversationItems.lastIndex
        val layoutInfo = listState.layoutInfo
        val alreadyVisible = layoutInfo.visibleItemsInfo.any { item ->
            item.index == lastIndex && item.offset + item.size <= layoutInfo.viewportEndOffset
        }
        if (!alreadyVisible) {
            listState.animateScrollToItem(lastIndex)
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

        // ================= 1.5 审批绿光：点阵之上、内容之下，随滑块进度淡入，0 时完全不可见 =================
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Wear.Palette.Success.copy(alpha = 0.10f * approvalProgress))
        )

        // ================= 2. 文字与卡片一体联动滚动流 (M3 圆屏标准：边缘缩小淡出，不做整块抹除) =================
        // 全屏纵向滑动，正文左右内缩 Wear.Space.Edge；靠近上下边缘时按位置自动缩小淡出，正文可与时钟/主键重叠
        ScalingLazyColumn(
            state = listState,
            autoCentering = AutoCenteringParams(itemIndex = 0),
            // top 与 T1 的 14dp 遮罩同宽；bottom 让最后一项能滚到圆屏中心（见 Wear.Size.ListCenterReach）
            contentPadding = PaddingValues(top = 14.dp, bottom = Wear.Size.ListCenterReach),
            modifier = Modifier
                .fillMaxSize()
                // 最外圈 14dp 轻度淡出：DstIn 必须落在离屏图层里，否则会把下方已画的点阵一起抹掉
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val edgePx = 14.dp.toPx()
                    val heightPx = size.height
                    if (heightPx > edgePx * 2f) {
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                edgePx / heightPx to Wear.Palette.Bg,
                                (heightPx - edgePx) / heightPx to Wear.Palette.Bg,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                .padding(horizontal = Wear.Space.Edge)
                .focusRequester(mainFocusRequester)
                .focusable()
                .onRotaryScrollEvent { event ->
                    // 圆屏列表自带"靠近边缘缩小淡出"，滚轮只负责位移，不再有任何整块抹除遮罩
                    scope.launch { listState.scroll { scrollBy(event.verticalScrollPixels) } }
                    if (kotlin.math.abs(event.verticalScrollPixels) > 8f) {
                        context.getSystemService(Vibrator::class.java)?.let { vib ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                            }
                        }
                    }
                    true
                },
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            // 顶部不再保留大留白：正文允许与弧形时钟重叠，只由列表自身的边缘缩放淡出收敛
            // 依次渲染对话结构流 (用户右对齐气泡 vs Assistant 纯正方形框，工具调用完整内嵌)
            items(
                count = conversationItems.size,
                key = { index -> itemKeyOf(conversationItems[index]) }
            ) { index ->
                val item = conversationItems[index]
                val itemKey = itemKeyOf(item)
                AnimatedMessageCard(key = itemKey) {
                    when (item) {
                    is RenderableConversationItem.UserPrompt -> {
                        // 2.1 用户指令：允许右对齐聊天气泡样式，随手势一体滚动
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            UserChatBubble(modifier = Modifier.widthIn(max = ConversationCardMaxWidth)) {
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
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            AgentSquareBlurBox(
                                modifier = Modifier.widthIn(max = ConversationCardMaxWidth),
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
            }

            // 错误提示 (随列表一体滚动)
            val err = agentState.errorMessage
            if (!err.isNullOrBlank()) {
                item { WearBanner(text = "[ALERT] $err", tone = Tone.DANGER) }
            }
        }

        // ================= 3. 顶部弧形微晶时间 (遵循穿戴纯净规范，不再常驻会话胶囊) =================
        // 曲线坐标系取整屏圆形；绘制在内容列表之后，正文从时钟下方穿过时只被轻微压暗，不会被抹掉
        TopClockBar(
            timeText = currentTime,
            modifier = Modifier.fillMaxSize()
        )

        // ================= 3.5 审批内容卡片：底栏上方浮出，宽度按该 y 处圆弦长收窄 =================
        // 画在底栏之前，滑块永远压在卡片之上；出现时允许覆盖列表内容
        AnimatedVisibility(
            visible = bottomBlockMode == BottomBlockMode.ACTION_ALLOW,
            enter = fadeIn(tween(140)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // 底边 = 屏幕高度 - (保留区 + 底栏底距 + 间距)：底栏审批行顶边贴在保留区上沿，
                // 因此卡片底边正好落在胶囊顶部之上 [Wear.Space.Sm]，绝不重叠，三段式危险条完整露出
                .padding(bottom = Wear.Size.BottomBarReserve + BottomBarBottomInset + Wear.Space.Sm)
        ) {
            ApprovalRequestCard(
                toolDisplayName = lastToolDisplayName(agentState.conversationBlocks) ?: "执行高风险操作",
                riskExplanation = currentRiskAssessment?.riskSummary ?: "",
                riskLevel = riskLevelOf(currentRiskAssessment),
                modifier = Modifier
                    .width(approvalCardWidth)
                    .onGloballyPositioned { coords ->
                        approvalCardTopPx = coords.positionInRoot().y
                        approvalCardHeightPx = coords.size.height.toFloat()
                    }
            )
        }

        // ================= 4. 单体演变底栏控制器 =================
        BottomInteractiveBlock(
            mode = bottomBlockMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = BottomBarBottomInset),
            mainInputType = mainInputType,
            onMainInputTypeChange = { newType ->
                mainInputType = newType
                preferences.mainInputType = newType.name
                showNotice(
                    when (newType) {
                        MainInputType.KEYBOARD -> NOTICE_INPUT_KEYBOARD
                        MainInputType.VOICE -> NOTICE_INPUT_VOICE
                    },
                    false
                )
            },
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
                // 右翼固定打开会话抽屉：横向手势已被 Wear 系统占用，入口只走显式按钮
                context.getSystemService(Vibrator::class.java)?.let { vib ->
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                    }
                }
                isSessionDrawerOpen = true
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
            },
            onApprovalProgress = { approvalProgress = it }
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
            onOpenSettings = {
                isSessionDrawerOpen = false
                onOpenSettings()
            },
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

