package com.example.myapplication.presentation.ui

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences
import kotlinx.coroutines.delay

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF000000))
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
                    color = Color.White.copy(alpha = alpha),
                    radius = 4.dp.toPx() * (1f + explosionProgress.value * 2f),
                    center = Offset(cx, cy)
                )

                // 物理扩散光环 (唤醒点阵)
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.7f),
                    radius = currentRadius,
                    center = Offset(cx, cy),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        // ================= 3. 阶段 2: 中心变圆，显示中间黑字与继续按钮 =================
        AnimatedVisibility(
            visible = phase == 2,
            enter = fadeIn(tween(260)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 300f)),
            exit = fadeOut(tween(200)) + scaleOut(spring(dampingRatio = 0.8f, stiffness = 400f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // 中间大白圆 (直径 180dp)
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF8FAFC)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "你好",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "欢迎使用穿戴智能助手",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155),
                                textAlign = TextAlign.Center
                            )
                        }

                        // 下方 [继续] 胶囊按键
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0F172A))
                                .clickable { phase = 3 }
                                .padding(horizontal = 20.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "继续",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }

        // ================= 4. 阶段 3: 圆缩小回中心点，浮现“欢迎配置”，下方滑出 URL 配置框 =================
        AnimatedVisibility(
            visible = phase == 3,
            enter = fadeIn(tween(280)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 320f)) { it / 3 },
            exit = fadeOut(tween(200))
        ) {
            OnboardingConfigPhase(
                preferences = preferences,
                onFinish = {
                    preferences.hasCompletedOnboarding = true
                    onFinish()
                }
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

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onRotaryScrollEvent {
                    true
                },
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 中心原点与欢迎徽章
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Text(
                        text = "配置 AI 模型",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF8FAFC)
                    )
                    Text(
                        text = "设置 API 端点与密钥后即可开始使用",
                        fontSize = 8.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            // Base URL 输入框
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF11141C))
                        .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("API Base URL", fontSize = 7.5.sp, color = Color(0xFF94A3B8))
                        BasicTextField(
                            value = inputBaseUrl,
                            onValueChange = {
                                inputBaseUrl = it
                                preferences.baseUrl = it
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }
                }
            }

            // API Key 输入框
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF11141C))
                        .border(0.6.dp, Color(0xFF262D3D), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Column {
                        Text("API Key", fontSize = 7.5.sp, color = Color(0xFF94A3B8))
                        BasicTextField(
                            value = inputApiKey,
                            onValueChange = {
                                inputApiKey = it
                                preferences.apiKey = it
                            },
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            cursorBrush = SolidColor(Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }
                }
            }

            // 完成并进入智能体按键
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable {
                            preferences.baseUrl = inputBaseUrl
                            preferences.apiKey = inputApiKey
                            onFinish()
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "完成并进入主界面",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }
    }
}
