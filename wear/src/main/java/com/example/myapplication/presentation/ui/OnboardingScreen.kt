package com.example.myapplication.presentation.ui

import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.WearCard
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 点阵全动效极客新手引导
 * - 阶段 1: 屏幕中心一个发光白点放大波浪铺满屏幕，唤醒背景微晶阵列
 * - 阶段 2: 阵列向中心凝聚为一个发光大圆，显示黑字欢迎与“继续”按键
 * - 阶段 3: 大圆缩小回中间一个点，浮现“欢迎配置”，下方顺畅滑出 URL 与 Key 输入框
 * - 阶段 4: 完成引导，保存配置平滑落位进入主界面
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    preferences: AgentPreferences,
    onFinish: () -> Unit
) {
    // 引导阶段: 1=中心粒子爆炸铺满, 2=凝聚大圆欢迎, 3=收缩为点并弹出配置框
    var phase by remember { mutableIntStateOf(1) }

    // 阶段1: 粒子爆炸扩散进度 (0f -> 1f)
    val explosionProgress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        explosionProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing)
        )
        delay(150)
        phase = 2
    }

    // 两条出口共用同一个收尾：先写完成标记再交给上层，保证"跳过"与"完成并进入"都不会再回到引导页
    val finishOnboarding: () -> Unit = {
        preferences.hasCompletedOnboarding = true
        onFinish()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Wear.Palette.Bg)
    ) {
        // ================= 1. 背景点阵画布 (随引导阶段呼吸与发散) =================
        UnifiedMatrixCanvas(
            modifier = Modifier.fillMaxSize(),
            shockwaveProgress = if (phase == 1) explosionProgress.value else -1f,
            thinkingIntensity = if (phase == 2) 0.6f else 0.15f,
            isRecordingVoice = false,
            gridSpacingDp = preferences.matrixSpacingDp
        )

        // ================= 2. 阶段 1: 中心粒子爆炸开铺满屏幕 =================
        if (phase == 1) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val currentRadius = size.width * 0.75f * explosionProgress.value
                val alpha = (1f - explosionProgress.value).coerceIn(0f, 1f)

                // 中心发光原点
                drawCircle(
                    color = Wear.Palette.TextPrimary.copy(alpha = alpha),
                    radius = 4.dp.toPx() * (1f + explosionProgress.value * 2f),
                    center = Offset(cx, cy)
                )

                // 物理扩散光环 (唤醒点阵)
                drawCircle(
                    color = Wear.Palette.TextPrimary.copy(alpha = alpha * 0.7f),
                    radius = currentRadius,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // ================= 3. 阶段 2: 中心变圆，显示中间黑字与继续按钮 =================
        AnimatedVisibility(
            visible = phase == 2,
            enter = fadeIn(tween(260)) + scaleIn(Wear.Motion.micro()),
            exit = fadeOut(tween(200)) + scaleOut(Wear.Motion.micro())
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 中间大白圆 (直径 180dp，引导页识别符号)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Wear.Palette.TextPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Wear.Space.Lg, vertical = Wear.Space.Lg),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm, Alignment.CenterVertically)
                    ) {
                        BasicText(
                            text = stringResource(R.string.ui_onboarding_greeting),
                            style = Wear.TextTitle.copy(color = Wear.Palette.Bg),
                            maxLines = 1
                        )
                        BasicText(
                            text = stringResource(R.string.ui_onboarding_welcome),
                            modifier = Modifier.fillMaxWidth(),
                            style = Wear.TextCaption.copy(
                                color = Wear.Palette.TextMuted,
                                textAlign = TextAlign.Center
                            ),
                            maxLines = 2
                        )
                        // 下方 [继续] 胶囊按键与纯文字 [跳过]：两键并排约 100dp 宽、44dp 高，均在 148dp 白圆内容区内
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
                        ) {
                            WearChip(
                                label = stringResource(R.string.ui_common_continue),
                                selected = false,
                                onClick = { phase = 3 }
                            )
                            SkipTextButton(onClick = finishOnboarding)
                        }
                    }
                }
            }
        }

        // ================= 4. 阶段 3: 圆缩小回中心点，浮现“欢迎配置”，下方滑出 URL 配置框 =================
        AnimatedVisibility(
            visible = phase == 3,
            enter = fadeIn(tween(280)) + slideInVertically(Wear.Motion.micro()) { it / 3 },
            exit = fadeOut(tween(200))
        ) {
            // 阶段内的"完成并进入主界面"自行写回输入框后再调用 onFinish；顶部叉号"跳过"直接调用 onFinish
            OnboardingConfigPhase(
                preferences = preferences,
                onFinish = finishOnboarding
            )
        }
    }
}

/**
 * 阶段 3 配置表单: 中心微晶原点 + 欢迎配置 + URL/Key 输入卡片
 */
@Composable
private fun OnboardingConfigPhase(
    preferences: AgentPreferences,
    onFinish: () -> Unit
) {
    var inputBaseUrl by remember { mutableStateOf(preferences.baseUrl) }
    var inputApiKey by remember { mutableStateOf(preferences.apiKey) }

    val listState = rememberScalingLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = listState,
            // 显式钉死连续衰减惯性，不吃库默认值（默认随时可能随版本改成吸附）
            flingBehavior = ScrollableDefaults.flingBehavior(),
            modifier = Modifier
                .fillMaxSize()
                // 官方 M3E 表冠：刻度→位移与松手惯性都由 behavior 提供（自带 focusTarget，
                // 焦点请求仍由 focusRequester 负责，不要再叠 focusable()）
                .rotaryScrollable(
                    behavior = RotaryScrollableDefaults.behavior(listState),
                    focusRequester = focusRequester
                ),
            // 顶部让出叉号行高度，滚动内容不会爬到叉号下面
            contentPadding = PaddingValues(
                start = Wear.Space.Lg,
                end = Wear.Space.Lg,
                top = Wear.Size.TopContentInset + Wear.Size.Touch,
                bottom = Wear.Space.Lg
            ),
            verticalArrangement = Arrangement.spacedBy(Wear.Space.Sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 中心原点与欢迎徽章
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs),
                    modifier = Modifier.padding(top = Wear.Space.Xs, bottom = Wear.Space.Xs)
                ) {
                    Box(
                        modifier = Modifier
                            .size(Wear.Space.Sm)
                            .clip(CircleShape)
                            .background(Wear.Palette.TextPrimary)
                    )
                    BasicText(
                        text = stringResource(R.string.ui_onboarding_config_title),
                        style = Wear.TextTitle,
                        maxLines = 1
                    )
                    BasicText(
                        text = stringResource(R.string.ui_onboarding_config_subtitle),
                        modifier = Modifier.fillMaxWidth(),
                        style = Wear.TextCaption.copy(textAlign = TextAlign.Center),
                        maxLines = 2
                    )
                }
            }

            // Base URL 输入框
            item {
                val baseUrlFocus = remember { FocusRequester() }
                WearCard(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "API Base URL",
                        style = Wear.TextCaption,
                        maxLines = 1
                    )
                    // 单行输入 + 12sp 等宽令牌：整串域名一行放下，超长时横向滚动而非折行
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Wear.Size.Touch)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { baseUrlFocus.requestFocus() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = inputBaseUrl,
                            onValueChange = {
                                inputBaseUrl = it
                                preferences.baseUrl = it
                            },
                            textStyle = Wear.TextMono.copy(color = Wear.Palette.TextPrimary),
                            cursorBrush = SolidColor(Wear.Palette.TextPrimary),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(baseUrlFocus)
                        )
                    }
                }
            }

            // API Key 输入框
            item {
                val apiKeyFocus = remember { FocusRequester() }
                WearCard(modifier = Modifier.fillMaxWidth()) {
                    BasicText(
                        text = "API Key",
                        style = Wear.TextCaption,
                        maxLines = 1
                    )
                    // 同 Base URL：单行 + 12sp 等宽令牌，密钥永不折行
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = Wear.Size.Touch)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { apiKeyFocus.requestFocus() },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = inputApiKey,
                            onValueChange = {
                                inputApiKey = it
                                preferences.apiKey = it
                            },
                            textStyle = Wear.TextMono.copy(color = Wear.Palette.TextPrimary),
                            cursorBrush = SolidColor(Wear.Palette.TextPrimary),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(apiKeyFocus)
                        )
                    }
                }
            }

            // 完成并进入智能体按键
            item {
                WearCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        preferences.baseUrl = inputBaseUrl
                        preferences.apiKey = inputApiKey
                        onFinish()
                    }
                ) {
                    BasicText(
                        text = stringResource(R.string.ui_onboarding_finish),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = Wear.TextBody.copy(color = Wear.Palette.Accent),
                        maxLines = 1
                    )
                }
            }
        }

        // 顶部右侧 44dp 叉号跳过键：宽度取该行上沿处的圆弦长（与设置子页同算法），整块落在圆周内
        val radiusDp = min(maxWidth.value, maxHeight.value) / 2f
        val centerYDp = maxHeight.value / 2f
        val headerTopDp = Wear.Size.TopContentInset.value
        val farEdgeDp = max(
            abs(headerTopDp - centerYDp),
            abs(headerTopDp + Wear.Size.Touch.value - centerYDp)
        )
        val chordHalfDp = sqrt((radiusDp * radiusDp - farEdgeDp * farEdgeDp).coerceAtLeast(0f)) -
            Wear.Space.Sm.value
        val headerWidth = (chordHalfDp.coerceAtLeast(0f) * 2f).dp.coerceAtLeast(Wear.Size.Touch)

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(headerWidth)
                .padding(top = Wear.Size.TopContentInset)
                .height(Wear.Size.Touch),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SkipCrossButton(onClick = onFinish)
        }
    }
}

/** 阶段 2 白圆内的纯文字"跳过"：12sp，用 TextMuted 保证白底对比度 5.05:1（TextSecondary 只有 2.82:1）。 */
@Composable
private fun SkipTextButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = stringResource(R.string.ui_common_skip),
            style = Wear.TextCaption.copy(color = Wear.Palette.TextMuted),
            maxLines = 1
        )
    }
}

/** 阶段 3 顶部的 44dp Canvas 叉号跳过键，画法与设置子页叉号一致。 */
@Composable
private fun SkipCrossButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(Wear.Size.Touch)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(Wear.Space.Md)) {
            val inset = size.minDimension * 0.25f
            val stroke = Wear.Size.BadgeHeight.toPx() * 0.1f
            drawLine(
                color = Wear.Palette.TextSecondary,
                start = Offset(inset, inset),
                end = Offset(size.width - inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Wear.Palette.TextSecondary,
                start = Offset(size.width - inset, inset),
                end = Offset(inset, size.height - inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
    }
}
