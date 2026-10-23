package com.example.myapplication.presentation.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences

/**
 * 类似 OPPO / ColorOS Watch 高级系统向导的新手引导教程 (零打字框 · 纯粹手势教程 · 零 Emoji)
 */
@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    preferences: AgentPreferences,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) } // 1: 长按语音, 2: 左右分裂, 3: 上滑设置

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF040608))
    ) {
        // 纯净点阵物理基底
        UnifiedMatrixCanvas(
            modifier = Modifier.fillMaxSize(),
            shockwaveProgress = -1f,
            thinkingIntensity = if (step == 3) 0.5f else 0f,
            isRecordingVoice = step == 1,
            gridSpacingDp = preferences.matrixSpacingDp
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部分页指示三道微晶点 (OPPO 穿戴式三段指示器)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                for (i in 1..3) {
                    val isCurrent = step == i
                    Box(
                        modifier = Modifier
                            .size(width = if (isCurrent) 16.dp else 5.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (isCurrent) Color(0xFF38BDF8) else Color(0xFF334155))
                    )
                }
            }

            // 中间核心教程动态卡片 (零打字框 · 纯穿戴手势规范)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(280))).togetherWith(fadeOut(animationSpec = tween(220)))
                },
                label = "onboardingCardTransition"
            ) { currentStep ->
                when (currentStep) {
                    1 -> {
                        TutorialGestureCard(
                            tag = "01 / 语音输入",
                            title = "长按语音 · 能量光带",
                            desc = "按住底部白玉按键，中间点阵拉伸融合为波动光带；松开瞬间发出冲击光波并开始思考。"
                        ) {
                            // 动态录音手势图示
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val cy = size.height / 2f
                                    drawLine(
                                        brush = Brush.horizontalGradient(
                                            listOf(Color.Transparent, Color(0xFF38BDF8), Color.White, Color(0xFF38BDF8), Color.Transparent)
                                        ),
                                        start = Offset(size.width * 0.15f, cy),
                                        end = Offset(size.width * 0.85f, cy),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        TutorialGestureCard(
                            tag = "02 / 细胞分裂",
                            title = "左右轻扫 · 展开双翼",
                            desc = "向左或向右横向滑动主按键，左右两端分别分离出重置上下文与键盘输入；点击主球磁吸收拢。"
                        ) {
                            // 双翼分离手势图示
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E2638)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("C", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("<<", fontSize = 8.sp, color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(">>", fontSize = 8.sp, color = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF1E2638)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("T", fontSize = 8.sp, color = Color(0xFF94A3B8))
                                }
                            }
                        }
                    }

                    3 -> {
                        TutorialGestureCard(
                            tag = "03 / 系统掌控",
                            title = "向上滑移 · 直达设置",
                            desc = "按住主按键纵向向上滑动，齿轮图标放大延伸，松开直接呼出模型切换、思考链与点阵调优。"
                        ) {
                            // 向上拉伸图示
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("^", fontSize = 12.sp, color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .size(width = 24.dp, height = 18.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(Color(0xFF38BDF8).copy(alpha = 0.35f))
                                        .border(1.dp, Color(0xFF38BDF8), RoundedCornerShape(9.dp))
                                )
                            }
                        }
                    }
                }
            }

            // 底部操作长胶囊按钮
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .height(38.dp)
                    .shadow(
                        elevation = 6.dp,
                        shape = RoundedCornerShape(19.dp),
                        spotColor = Color.White.copy(alpha = 0.3f)
                    )
                    .clip(RoundedCornerShape(19.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            if (step < 3) {
                                step++
                            } else {
                                preferences.hasCompletedOnboarding = true
                                onFinish()
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (step < 3) "继续教程 >>" else "进入智能伴生盘 >>",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        }
    }
}

@Composable
private fun TutorialGestureCard(
    tag: String,
    title: String,
    desc: String,
    illustration: @Composable () -> Unit
) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 12
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = tag,
                fontSize = 8.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF38BDF8)
            )
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            illustration()
            Text(
                text = desc,
                fontSize = 8.5.sp,
                lineHeight = 12.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}
