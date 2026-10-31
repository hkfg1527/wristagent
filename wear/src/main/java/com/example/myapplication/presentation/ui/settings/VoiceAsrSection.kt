package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.voice.AsrLanguage
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.AsrResult
import com.example.myapplication.voice.AsrSettings
import com.example.myapplication.voice.AudioRecordWavCapture
import com.example.myapplication.voice.VoiceAsrGateway
import kotlinx.coroutines.launch

/**
 * 语音转写配置分区：厂商切换、百度 AK/SK、识别语言、总开关与真实测试转写。
 * 视觉完全复用 SettingsShared 的卡片/分组/配色，不引入新的设计语言。
 */
@Composable
fun VoiceAsrContent(
    preferences: AgentPreferences,
    onSettingsChanged: ((AsrSettings) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val initial = remember { preferences.asrSettings() }
    var enabled by remember { mutableStateOf(initial.enabled) }
    var providerId by remember { mutableStateOf(initial.providerId) }
    var language by remember { mutableStateOf(initial.language) }
    var apiKey by remember { mutableStateOf(initial.baiduApiKey) }
    var secretKey by remember { mutableStateOf(initial.baiduSecretKey) }

    var isTesting by remember { mutableStateOf(false) }
    var testStatus by remember { mutableStateOf("") }

    fun currentSettings(): AsrSettings = AsrSettings(
        enabled = enabled,
        providerId = providerId,
        baiduApiKey = apiKey,
        baiduSecretKey = secretKey,
        language = language
    )

    fun persist() {
        val settings = currentSettings()
        preferences.asrEnabled = settings.enabled
        preferences.asrProvider = settings.providerId.storageKey
        preferences.baiduApiKey = settings.baiduApiKey
        preferences.baiduSecretKey = settings.baiduSecretKey
        preferences.asrDevPid = settings.language.devPid
        onSettingsChanged?.invoke(settings)
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. 总开关
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("语音转写开关", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(true to "开启", false to "关闭").forEach { (value, label) ->
                    val isSel = enabled == value
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Color(0xFF0284C7) else Color(0xFF222B3C))
                            .border(
                                width = if (isSel) 1.2.dp else 0.6.dp,
                                color = if (isSel) AccentCyan else Color(0xFF384661),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                enabled = value
                                persist()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // 2. 厂商选择
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("识别服务", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(AsrProviderId.SYSTEM, AsrProviderId.BAIDU).forEach { candidate ->
                    val isSel = providerId == candidate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) Color(0xFF0284C7) else Color(0xFF222B3C))
                            .border(
                                width = if (isSel) 1.2.dp else 0.6.dp,
                                color = if (isSel) AccentCyan else Color(0xFF384661),
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                providerId = candidate
                                testStatus = ""
                                persist()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = candidate.displayName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
            Text(
                text = if (providerId == AsrProviderId.BAIDU) {
                    "百度短语音：密钥缺失时自动回退系统识别"
                } else {
                    "系统识别：依赖手表现有语音服务"
                },
                fontSize = 11.sp,
                color = TextSecondaryColor
            )
        }

        // 3. 百度密钥
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("百度语音密钥", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)

            Text("API Key (AK)", fontSize = 11.sp, color = TextSecondaryColor)
            BasicTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    persist()
                },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                cursorBrush = SolidColor(AccentCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1219))
                    .border(0.6.dp, Color(0xFF333E54), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            )

            Text("Secret Key (SK)", fontSize = 11.sp, color = TextSecondaryColor)
            BasicTextField(
                value = secretKey,
                onValueChange = {
                    secretKey = it
                    persist()
                },
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp),
                cursorBrush = SolidColor(AccentCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1219))
                    .border(0.6.dp, Color(0xFF333E54), RoundedCornerShape(6.dp))
                    .padding(6.dp)
            )
        }

        // 4. 识别语言
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("识别语言", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AsrLanguage.values().forEach { candidate ->
                    val isSel = language == candidate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) Color(0xFF0284C7) else Color(0xFF1E2536))
                            .border(
                                0.6.dp,
                                if (isSel) AccentCyan else Color(0xFF333E54),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                context.vibrateTick()
                                language = candidate
                                persist()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = candidate.displayName,
                            fontSize = 11.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSel) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }
        }

        // 5. 测试转写
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF161A24))
                .border(0.8.dp, Color(0xFF2B3448), RoundedCornerShape(14.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("连通性自检", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentCyan)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isTesting) Color(0xFF1E2536) else Color(0xFF263143))
                    .border(0.8.dp, Color(0xFF3F4F6B), RoundedCornerShape(10.dp))
                    .clickable {
                        context.vibrateTick()
                        if (isTesting) return@clickable
                        isTesting = true
                        testStatus = "录音中，请说话…"
                        persist()
                        val gateway = VoiceAsrGateway(capture = AudioRecordWavCapture(context))
                        scope.launch {
                            val result = gateway.transcribe(
                                currentSettings(),
                                android.speech.SpeechRecognizer.isRecognitionAvailable(context)
                            )
                            isTesting = false
                            testStatus = when (result) {
                                is AsrResult.Success -> "转写成功：" + result.text
                                is AsrResult.Failure -> "转写失败：" + result.reason
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isTesting) "正在测试…" else "测试转写",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryColor
                )
            }

            if (testStatus.isNotBlank()) {
                Text(
                    text = testStatus,
                    fontSize = 11.sp,
                    color = TextSecondaryColor,
                    lineHeight = 14.sp
                )
            }
        }
    }
}
