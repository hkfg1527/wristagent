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
import android.os.SystemClock
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
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
import com.example.myapplication.agent.contract.RiskLevel
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
import com.example.myapplication.presentation.ui.StreamingThrottleState
import com.example.myapplication.presentation.ui.rememberEdgeOverscroll
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
import androidx.compose.runtime.withFrameMillis
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.abs

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

/** TEMP 测试指令：强制只调用一次 test_noop（空实现），再输出一段废话用于观察流式吐字。 */
private const val TEMP_TOOL_TEST_COMMAND =
    "测试模式请求：请只调用一次 test_noop 工具（note 随便填），然后输出一段约 180 字的中文说明，" +
        "内容是描述流式输出观察用的填充文字，不要调用其它任何工具，不要向我提问。"

/**
 * 审批页强调色与标题：随 AI 风险等级变化 —— 低风险绿、中度黄、重度黄红、致命红。
 * 标题、危险条点亮段与滑块着色共用同一套映射，避免各处重复写等级到颜色的分支。
 */
internal object ApprovalTone {
    fun accentOf(level: RiskLevel): Color = when (level) {
        RiskLevel.LOW -> Wear.Palette.Success
        RiskLevel.MEDIUM -> Wear.Palette.Warning
        RiskLevel.HIGH -> Wear.Palette.Critical
        RiskLevel.CRITICAL -> Wear.Palette.Danger
    }

    fun headlineOf(level: RiskLevel): String = when (level) {
        RiskLevel.LOW -> "低风险"
        RiskLevel.MEDIUM -> "需要确认"
        RiskLevel.HIGH, RiskLevel.CRITICAL -> "高风险操作"
    }
}

/** AI 风险等级 -> 危险条点亮段数：中低风险 1 段、高风险 2 段、致命 3 段。 */
private fun riskBarsLit(level: RiskLevel): Int = when (level) {
    RiskLevel.LOW, RiskLevel.MEDIUM -> 1
    RiskLevel.HIGH -> 2
    RiskLevel.CRITICAL -> 3
}

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

/** 审批文案宽度：240dp 圆屏上固定 190dp，两侧各留 25dp，正文不会碰到圆周。 */
private val ApprovalTextWidth = 190.dp

/**
 * 文案块垂直抬升：屏幕高 240dp，取 48dp 抬升后文案中心落在 (240−48)/2 = 96dp，
 * 整列文案因此压在圆心上方，底边不会挤进 164dp 起的滑轨区。
 */
private val ApprovalTextCenterLift = 48.dp

/** 文案块间距：靠 10dp 间距分段，不用任何线框。 */
private val ApprovalTextBlockGap = 10.dp

/** 审批态只做极轻压暗（0.22）：点阵必须仍然看得见，也没有任何整屏绿/红蒙色层。 */
private const val ApprovalScrimAlpha = 0.22f

/** 文字底下径向暗晕：中心 0.82 的 Bg，150dp 处已经透明，于是没有边界也没有轮廓。 */
private const val ApprovalHaloCenterAlpha = 0.82f
private val ApprovalHaloRadius = 150.dp

/** 文案入场：每块从 +48dp 浮上来并淡入，四块依次错开 60ms（标题 → 调用 → 改动 → 最坏情况）。 */
private val ApprovalTextFloatDistance = 48.dp
private const val ApprovalTextStaggerMs = 60L

/** 危险条：3 段 32×8dp，按等级点亮。 */
private val ApprovalBarWidth = 32.dp
private val ApprovalBarHeight = 8.dp

/** 审批态对话列表的残留不透明度：退到 35%，仍能辨认"这些内容还没走"。 */
private const val ApprovalListAlpha = 0.35f

/** 列表图层的模糊半径（px）：只糊列表、不糊点阵，密度 2.0 下约 4dp 的轻微虚化。 */
private const val ApprovalListBlurRadiusPx = 8f

/** 放行塌缩：卡片缩到工具胶囊尺度、下移淡出，动画结束后才真正退出审批态。 */
private const val ApprovalCollapseScale = 0.18f
private val ApprovalCollapseDrop = 60.dp
private const val ApprovalCollapseDurationMs = 220

/**
 * 审批文案的行内排版：12sp [Wear.Palette.TextMuted] 标签做引子，正文接在同一段落里。
 * 竖排标签每块要多占一行（四块合计 64dp），而滑轨上方只有 164dp，
 * 行内引子把这行高度让给正文，四块信息一条不少。
 */
private fun approvalParagraph(label: String, body: String, bodyStyle: TextStyle): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(fontSize = Wear.TextCaption.fontSize, color = Wear.Palette.TextMuted)) {
            append(label)
            append("  ")
        }
        withStyle(
            SpanStyle(
                fontSize = bodyStyle.fontSize,
                color = bodyStyle.color,
                fontWeight = bodyStyle.fontWeight
            )
        ) {
            append(body)
        }
    }

/**
 * 审批浮层：故意不带任何框 —— 没有 Surface 底、没有 1dp 描边、没有圆角、没有内边距盒，
 * 文字直接浮在屏幕中央。文案正下方铺一层 [ApprovalHaloRadius] 的径向暗晕
 * （中心 [ApprovalHaloCenterAlpha] 的 Bg → 半径处透明），暗晕没有边界也没有轮廓，
 * 于是文字是从背景里浮出来的，而不是被框住；暗晕之外的点阵依旧清晰可见。
 * 内容自上而下：等级标题 + 3 段危险条 / 调用 / 改动 / 最坏情况（空文案整块不占位），
 * 每块从 +48dp 浮上来并错开 60ms 启动；放行时整层按 [collapse] 塌缩成工具胶囊尺度。
 */
@Composable
private fun ApprovalFloatingLayer(
    visible: Boolean,
    toolName: String,
    risk: RiskAssessment?,
    collapse: Float,
    modifier: Modifier = Modifier
) {
    // 只压暗 0.22：点阵保持可见，绿/红一律交给点阵自身的颜色插值，不再有整屏蒙色层
    val scrimAlpha by animateFloatAsState(
        targetValue = if (visible) ApprovalScrimAlpha else 0f,
        animationSpec = tween(140),
        label = "approvalScrimAlpha"
    )

    val level = risk?.aiRiskLevel ?: RiskLevel.MEDIUM
    val accent = ApprovalTone.accentOf(level)
    val toolDisplayName = ToolVisualMeta.resolve(toolName).displayName
    val whatItDoes = risk?.whatItDoes.orEmpty()
    val worstCase = risk?.worstCase.orEmpty()
    // 12sp / 16sp 行高：两行正文只占 32dp，四块文案加标题也压得住 190dp 宽
    val bodyStyle = Wear.TextBody.copy(
        fontSize = Wear.TextCaption.fontSize,
        lineHeight = Wear.TextCaption.lineHeight
    )
    val centeredBody = bodyStyle.copy(textAlign = TextAlign.Center)
    val worstCaseStyle = bodyStyle.copy(color = Wear.Palette.Danger)
    val centeredWorstCase = worstCaseStyle.copy(textAlign = TextAlign.Center)

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(140)),
        exit = fadeOut(tween(120)),
        modifier = modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // 放行塌缩：压暗层与文案一起淡出
                .graphicsLayer { alpha = 1f - collapse }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Wear.Palette.Bg.copy(alpha = scrimAlpha))
            )
            // 文案居中偏上：底部留出 [ApprovalTextCenterLift]，文案中心才落在 96dp 而不是屏心
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = ApprovalTextCenterLift),
                contentAlignment = Alignment.Center
            ) {
                // 径向暗晕：中心浓、150dp 处已透明，没有边界没有轮廓，文字像从背景里浮出来
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Wear.Palette.Bg.copy(alpha = ApprovalHaloCenterAlpha),
                                Color.Transparent
                            ),
                            center = center,
                            radius = ApprovalHaloRadius.toPx()
                        )
                    )
                }
                Column(
                    modifier = Modifier
                        .width(ApprovalTextWidth)
                        // 塌缩只作用于文案：缩到工具胶囊尺度并下移，动画走完才退出审批态
                        .graphicsLayer {
                            scaleX = 1f - (1f - ApprovalCollapseScale) * collapse
                            scaleY = 1f - (1f - ApprovalCollapseScale) * collapse
                            translationY = ApprovalCollapseDrop.toPx() * collapse
                        },
                    verticalArrangement = Arrangement.spacedBy(ApprovalTextBlockGap),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 块一：等级标题 + 3 段危险条（标题定调，危险条按等级点亮几段）
                    FloatingApprovalText(slot = 0) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                        ) {
                            BasicText(
                                text = ApprovalTone.headlineOf(level),
                                style = Wear.TextTitle.copy(color = accent),
                                maxLines = 1
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(3) { index ->
                                    Box(
                                        modifier = Modifier
                                            .size(ApprovalBarWidth, ApprovalBarHeight)
                                            .clip(RoundedCornerShape(Wear.Space.Xs))
                                            .background(
                                                if (index < riskBarsLit(level)) accent
                                                else Wear.Palette.Border.copy(alpha = 0.45f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // 块二：调用了什么（工具中文显示名，无 emoji）
                    FloatingApprovalText(slot = 1) {
                        BasicText(
                            text = approvalParagraph("调用", toolDisplayName, centeredBody),
                            style = centeredBody,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 块三：改了什么的直白说明（空文案整块不占位）
                    if (whatItDoes.isNotBlank()) {
                        FloatingApprovalText(slot = 2) {
                            BasicText(
                                text = approvalParagraph("改动", whatItDoes, centeredBody),
                                style = centeredBody,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 块四：最坏情况（空文案整块不占位）
                    if (worstCase.isNotBlank()) {
                        FloatingApprovalText(slot = 3) {
                            BasicText(
                                text = approvalParagraph(
                                    "最坏情况",
                                    worstCase,
                                    centeredWorstCase
                                ),
                                style = centeredWorstCase,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 一块审批文案的入场：同一个 [Animatable] 同时驱动 translationY（+48dp → 0）与 alpha（0 → 1），
 * 走 spring(阻尼 0.82 / 刚度 260)；第 [slot] 块再延后 slot×60ms 启动，于是文字依次从底下浮上来排到上面。
 */
@Composable
private fun FloatingApprovalText(
    slot: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(enter) {
        delay(ApprovalTextStaggerMs * slot)
        enter.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 260f))
    }
    Box(
        modifier = modifier.graphicsLayer {
            alpha = enter.value
            translationY = ApprovalTextFloatDistance.toPx() * (1f - enter.value)
        }
    ) {
        content()
    }
}

/**
 * 对话卡片宽度上限：240dp 圆屏减去两侧 [Wear.Space.Edge] 后四周仍留 20dp，
 * 长回复不再贴着圆屏边缘被裁；用户气泡同样收在此宽度内保持右对齐。
 */
private val ConversationCardMaxWidth = 200.dp

/** 底栏底距：与 [BottomInteractiveBlock] 调用处的 padding(bottom = 12.dp) 对齐。 */
private val BottomBarBottomInset = 12.dp

/**
 * 正在流式输出的那一段文本：突发判定的基准是"上一帧屏幕上的文字 → 本帧文字"，
 * 而不是模型 token 之间的增量（token 增量被节流窗口合并后就失去了可比性）。
 */
private fun streamingTailText(blocks: List<ConversationBlock>): String {
    val tail = blocks.lastOrNull { it is ConversationBlock.Text && it.isStreaming } as? ConversationBlock.Text
    return tail?.content ?: ""
}

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
    // 待审批的工具名：审批页"调用"块用它解析中文显示名
    var pendingToolName by remember { mutableStateOf("") }
    var pendingContinuation by remember { mutableStateOf<CancellableContinuation<Boolean>?>(null) }
    var delayedStretchJob by remember { mutableStateOf<Job?>(null) }

    val listState = rememberScalingLazyListState()
    val mainFocusRequester = remember { FocusRequester() }

    // 到底继续上滑 → tick → 过阈值切设置页；越界期间列表最多拉开 24dp×0.4，松手回弹
    val edgeOverscroll = rememberEdgeOverscroll(
        overflowSign = -1f,
        atEdge = { !listState.canScrollForward },
        onCross = { onOpenSettings() }
    )

    // 流式刷新节流：70ms 一帧（≈12~16fps）+ 突发增量时关掉逐字光标动画
    val streamingThrottle = remember { StreamingThrottleState() }
    var isStreamingBurst by remember { mutableStateOf(false) }

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

    // 审批滑块进度：0=拒绝（点阵保持冷白基色），1=放行（点阵本身变绿变亮），由底栏回调驱动
    var approvalProgress by remember { mutableFloatStateOf(0f) }

    // 放行塌缩：先播 220ms 塌缩动画，动画走完才把结论交回 Agent
    var approvalCollapsing by remember { mutableStateOf(false) }
    val approvalCollapse = remember { Animatable(0f) }

    // 审批沉浸态：居中文字浮层接管，列表退到 35% 继续透出、点阵保持清晰
    val isApprovalFocus = bottomBlockMode == BottomBlockMode.ACTION_ALLOW

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

    // 表冠只有在列表节点持有焦点时才收得到 rotary 事件：首帧、抽屉/输入弹窗/审批浮层关闭、
    // 从设置页返回（整页重组）都要重新抢一次焦点。请求必须排到下一帧 —— 首帧里节点还没挂上树，
    // 直接 requestFocus() 会落到未初始化的 requester 上（真表上表现为表冠彻底没反应）。
    LaunchedEffect(isSessionDrawerOpen, inputState.isOpen, isApprovalFocus) {
        if (isSessionDrawerOpen || inputState.isOpen || isApprovalFocus) return@LaunchedEffect
        withFrameNanos { }
        runCatching { mainFocusRequester.requestFocus() }
    }

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
        agentDispatcher.onRequireConfirmation = { toolName, _, risk ->
            pendingToolName = toolName
            currentRiskAssessment = risk
            // 新一轮审批从完整态开始：上一轮放行留下的塌缩进度必须归零
            // 回调来自 IO 调度（executeUserPrompt 的 withContext），状态改动交回主线程
            scope.launch {
                approvalCollapse.snapTo(0f)
                approvalCollapsing = false
            }
            // 审批弹出的瞬间给一次触觉反馈：全屏接管需要腕上的一记"咔"
            context.getSystemService(Vibrator::class.java)?.let { vib ->
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                }
            }
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

            // 新一轮从空基线起算：首帧立即可见，突发判定不看上一轮的残留文本
            streamingThrottle.reset()
            isStreamingBurst = false

            agentDispatcher.executeUserPrompt(
                prompt = prompt,
                existingBlocks = currentSession.conversationBlocks
            ) { newState ->
                // 逐 token 提交等于每来一个字就重排整列正文：只按 70ms 节拍提交。
                // 终态（一轮结束 / 报错）强制落地，绝不能被节流窗口挡掉。
                val emitNow = streamingThrottle.accept(
                    newText = streamingTailText(newState.conversationBlocks),
                    nowMs = SystemClock.uptimeMillis(),
                    force = newState.isFinished
                )
                isThinkingActive = isExecutingAgent && !newState.isFinished && (newState.isThinking || newState.isExecutingTool)

                if (emitNow) {
                    isStreamingBurst = streamingThrottle.isBurst
                    agentState = newState

                    sessions = sessions.map { s ->
                        if (s.id == activeSessionId) {
                            s.copy(
                                title = updatedTitle,
                                conversationBlocks = newState.conversationBlocks,
                                history = agentDispatcher.getConversationHistory()
                            )
                        } else s
                    }
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

    // ================= 唯一滚动所有者 =================
    // 之前这里有三条 LaunchedEffect 各自驱动同一个 ScalingLazyListState：
    //   ① 流式阶段瞬时 scrollBy 跟随  ② 条目变化时 animateScrollToItem（动画）
    //   ③ 一轮结束 springScrollToEnd（还是逐帧 scroll 的动画）
    // 三者互相取消、又与"内容边涨边滚"叠加，于是发完消息列表会持续弹来弹去。
    // 现在收敛成一条链路：同一时刻只有一个滚动协程，且两种状态互斥。
    val hasUserTurn = conversationItems.any { it is RenderableConversationItem.UserPrompt }
    LaunchedEffect(isExecutingAgent, hasUserTurn, conversationItems.size) {
        // 空会话（只有问候语）不滚动：否则首条会被顶到屏幕最上面，开屏像"往下弹了一下又回去"
        if (!hasUserTurn) return@LaunchedEffect
        if (isExecutingAgent) {
            // 流式阶段：逐帧贴底，瞬时位移、单调前进、被末端夹住 —— 不做任何动画，避免和内容增长互相追
            while (isActive) {
                withFrameMillis { }
                listState.scroll { scrollBy(BOTTOM_FOLLOW_SCROLL_PX) }
            }
        } else {
            // 一轮结束：只做一次动画归位，然后再瞬时钉住末端（两步串行，不再有第二个动画抢锁）
            listState.animateScrollToItem(conversationItems.lastIndex)
            listState.scroll { scrollBy(BOTTOM_FOLLOW_SCROLL_PX) }
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
            gridSpacingDp = liveSpacingDp,
            // 审批滑块进度直接交给点阵：越往左推，点本身从冷白基色连续变绿变亮，不叠加任何整屏色层
            approvalProgress = approvalProgress
        )

        // ================= 2. 文字与卡片一体联动滚动流 (M3 圆屏标准：边缘缩小淡出，不做整块抹除) =================
        // 全屏纵向滑动，正文左右内缩 Wear.Space.Edge；靠近上下边缘时按位置自动缩小淡出，正文可与时钟/主键重叠
        ScalingLazyColumn(
            state = listState,
            // autoCentering = null：默认 AutoCenteringParams 会为 itemIndex 预留顶部空间，
            // 本项目曾写死 itemIndex = 0，于是"第 0 项能滚到圆心"变成滚动锚点 —— 每轮新消息到达后
            // 视图被拉回列表开头，新内容落在视口外。关掉后位置控制完全交给上面的"最后一条居中"逻辑。
            autoCentering = null,
            // 库自带的 rotary（ScalingLazyColumn 默认装 requestFocusOnHierarchyActive + rotaryScrollable）
            // 会自己抢焦点并把刻度全部吃掉，本文件的处理器就永远收不到事件 —— 关掉它，表冠只由下方
            // onRotaryScrollEvent 独家接管；同理关掉系统拉伸越界，越界手感统一走 nestedScroll + graphicsLayer。
            rotaryScrollableBehavior = null,
            overscrollEffect = null,
            // top 必须让开顶部曲面时钟：14dp 时首条卡片会直接压到时钟上（真表 192dp 屏上尤其明显）；
            // 取 TopContentInset(34dp) + Space.Md(12dp) = 46dp，既在时钟下方、又只让掉一点点高度。
            // bottom 让最后一项能滚到圆屏中心（见 Wear.Size.ListCenterReach）
            contentPadding = PaddingValues(
                top = Wear.Size.TopContentInset + Wear.Space.Md,
                bottom = Wear.Size.ListCenterReach
            ),
            modifier = Modifier
                .fillMaxSize()
                // 越界"拉住"：列表没吃下的位移不进滚动，只换成最多 24dp×0.4 的弹性位移，松手回弹
                .nestedScroll(edgeOverscroll.connection)
                // 审批沉浸：整列正文退到 35% 并轻度模糊（只糊列表图层，点阵画布保持清晰）；
                // 图层被缓存，模糊只在列表内容真正变化时重算，不是每帧全屏后处理
                .graphicsLayer {
                    alpha = listAlpha
                    renderEffect = if (isApprovalFocus) listBlurEffect else null
                    translationY = edgeOverscroll.pullOffsetPx
                }
                // 边缘淡出：DstIn 必须落在离屏图层里，否则会把下方已画的点阵一起抹掉。
                // 顶部渐隐必须盖住整个曲面时钟带（TopContentInset=34dp），
                // 否则长回复向上滚动时正文会从时钟底下穿出来、和 "22:57" 叠字（真表 192dp 屏上尤其明显）；
                // 底部只需让开一点点，给底栏留白的边缘收口。
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val clockBandPx = Wear.Size.TopContentInset.toPx()   // 34dp：曲面时钟占的高度
                    val rampPx = 8.dp.toPx()                              // 收口用得很短，避免"半透明半可见"
                    val bottomEdgePx = 14.dp.toPx()
                    val heightPx = size.height
                    if (heightPx > clockBandPx + bottomEdgePx) {
                        // 关键：不是从 0 线性拉到 34dp（那样这段里只是半透明、字仍可见），
                        // 而是"先整段抹干净，最后 8dp 快速收口"，这样正文绝不会压在时钟上。
                        val solidUntilPx = (clockBandPx - rampPx).coerceAtLeast(0f)
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Transparent,
                                (solidUntilPx / heightPx).coerceIn(0f, 1f) to Color.Transparent,
                                (clockBandPx / heightPx).coerceIn(0f, 1f) to Wear.Palette.Bg,
                                ((heightPx - bottomEdgePx) / heightPx).coerceIn(0f, 1f) to Wear.Palette.Bg,
                                1f to Color.Transparent
                            ),
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
                .padding(horizontal = Wear.Space.Edge)
                // 顺序即作用域：rotary 事件从聚焦节点向外冒泡，处理器必须挂在 focusable 之外，
                // 写成 focusable().onRotaryScrollEvent{} 会让处理器落在焦点节点内侧，永远收不到事件
                // （wear 自己的 Modifier.rotaryScrollable 也是 rotaryHandler(...).focusRequester(...).focusable 这个顺序）。
                .onRotaryScrollEvent { event ->
                    // 表冠：dispatchRawDelta 同步吃位移 —— 不排队、不与"贴底跟随"抢同一把 scroll 互斥锁，
                    // 每格刻度都立刻生效（scope.launch + scroll{} 在高频刻度下会被逐帧滚动挤掉，真表上就像没接上）
                    val consumed = listState.dispatchRawDelta(event.verticalScrollPixels)
                    // 已到边界还继续转：剩余位移交给越界手感累计（震动 / 过阈值切设置页）
                    val leftover = event.verticalScrollPixels - consumed
                    if (leftover != 0f) edgeOverscroll.push(leftover, rotary = true)
                    if (kotlin.math.abs(event.verticalScrollPixels) > 8f) {
                        context.getSystemService(Vibrator::class.java)?.let { vib ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                            }
                        }
                    }
                    true
                }
                .focusRequester(mainFocusRequester)
                .focusable(),
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
                                                    isStreaming = block.isStreaming,
                                                    // 单次刷新吐出十几字以上就是"过快"：不做逐字动画，只直接刷新文本
                                                    animateCursor = !isStreamingBurst
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
            toolName = pendingToolName,
            risk = currentRiskAssessment,
            collapse = approvalCollapse.value
        )

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
                // TEMP：左翼临时改为"点一下直接发测试指令"，用于验证单次工具调用 + 流式输出。
                // 恢复时改回 inputState = inputState.reduce(TextInputEvent.Open) 即可。
                triggerAgent(TEMP_TOOL_TEST_COMMAND)
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
                // 放行不立刻退出：先让整页塌缩成工具胶囊尺度，动画走完再交回结论
                if (!approvalCollapsing) {
                    approvalCollapsing = true
                    scope.launch {
                        approvalCollapse.animateTo(1f, tween(ApprovalCollapseDurationMs))
                        approvalCollapsing = false
                        pendingContinuation?.resume(true)
                        pendingContinuation = null
                        bottomBlockMode = BottomBlockMode.IDLE
                    }
                }
            },
            onReject = {
                pendingContinuation?.resume(false)
                pendingContinuation = null
                bottomBlockMode = BottomBlockMode.IDLE
            },
            onSwipeUpSettings = {
                onOpenSettings() // 阻尼跟手上滑进入设置
            },
            onApprovalProgress = { approvalProgress = it },
            // 滑块着色跟着审批等级走：低风险绿、中度黄、重度黄红、致命红，滑到底仍是绿
            approvalTone = ApprovalTone.accentOf(
                currentRiskAssessment?.aiRiskLevel ?: RiskLevel.MEDIUM
            )
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

