package com.example.myapplication.presentation.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.SettingsChangeBus
import com.example.myapplication.agent.AgentToolRegistry
import com.example.myapplication.presentation.ChatViewModel
import com.example.myapplication.presentation.ExternalPrompt
import com.example.myapplication.presentation.ui.OnboardingScreen
import com.example.myapplication.presentation.ui.SettingsScreen
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import com.example.myapplication.presentation.ui.settings.SettingsScreenVeilMs
import androidx.compose.runtime.CompositionLocalProvider
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.voice.AudioRecordWavCapture
import com.example.myapplication.voice.VoiceAsrGateway
import com.example.myapplication.voice.VoiceInputManager

enum class AppScreen {
    ONBOARDING,
    MAIN,
    SETTINGS
}

/**
 * 应用根容器：三个页面的切换、点阵密度与省电偏好的回写、语音引擎的生命周期都留在这里。
 * 聊天页只做展示与回调，状态全部来自 [ChatViewModel]。
 */
@Composable
fun WearAgentApp(
    viewModel: ChatViewModel,
    preferences: AgentPreferences,
    toolRegistry: AgentToolRegistry,
    externalPrompt: ExternalPrompt? = null,
    onClearExternalPrompt: () -> Unit = {}
) {
    val context = LocalContext.current

    val voiceInputManager = remember {
        VoiceInputManager(context, VoiceAsrGateway(capture = AudioRecordWavCapture(context)))
    }

    var currentScreen by remember {
        mutableStateOf(if (preferences.hasCompletedOnboarding) AppScreen.MAIN else AppScreen.ONBOARDING)
    }

    var liveSpacingDp by remember { mutableFloatStateOf(preferences.matrixSpacingDp) }

    // 网页控制台改设置后，界面必须立刻跟上：版本号一变就重读偏好
    val settingsRevision by SettingsChangeBus.revision.collectAsState()

    // 网页端改设置会 bump 这个 revision：之前只有 animationsEnabled 读了它，
    // 点阵网格间距等字段仍然是 remember 一次的快照，网页改完必须杀进程才生效。
    LaunchedEffect(settingsRevision) {
        if (settingsRevision > 0L) {
            liveSpacingDp = preferences.matrixSpacingDp
            viewModel.syncCompactMode(preferences.compactMode)
        }
    }

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
                when {
                    // 进入设置：容器只做一层极轻淡入盖住切换，逐行浮动由 SettingsScreen 内部按行播放
                    targetState == AppScreen.SETTINGS ->
                        fadeIn(tween(SettingsScreenVeilMs))
                            .togetherWith(fadeOut(tween(SettingsScreenVeilMs)))
                    // 离开设置：设置页已先把各行逐行沉出，这里只需要一层很短的淡出盖住切换瞬间，
                    // 目标屏保持它原有的入场（主界面入场未改）
                    initialState == AppScreen.SETTINGS ->
                        (slideInVertically(tween(240, easing = FastOutSlowInEasing)) { -it / 5 } + fadeIn(tween(180)))
                            .togetherWith(fadeOut(tween(SettingsScreenVeilMs)))
                    else ->
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
                    // 动画开关的唯一注入点：点阵、底栏、审批浮层、输入弹窗都只读这个 local，
                    // 于是"关掉动效"是一件全局的事，不会各页各开关一半生效。
                    CompositionLocalProvider(
                        LocalAnimationsEnabled provides remember(settingsRevision) {
                            preferences.animationsEnabled
                        }
                    ) {
                        ChatScreen(
                            viewModel = viewModel,
                            preferences = preferences,
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
}
