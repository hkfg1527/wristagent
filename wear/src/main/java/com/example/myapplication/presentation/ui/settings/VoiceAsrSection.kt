package com.example.myapplication.presentation.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.presentation.ui.components.WearChip
import com.example.myapplication.presentation.ui.components.WearSectionHeader
import com.example.myapplication.presentation.ui.theme.Wear
import com.example.myapplication.voice.AsrLanguage
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.AsrResult
import com.example.myapplication.voice.AsrSettings
import com.example.myapplication.voice.AudioRecordWavCapture
import com.example.myapplication.voice.VoiceAsrGateway
import kotlinx.coroutines.launch

/**
 * 语音转写配置分区：厂商切换、百度 AK/SK、识别语言、总开关与真实测试转写。
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
        verticalArrangement = Arrangement.spacedBy(Wear.Space.Xs)
    ) {
        WearSectionHeader(text = "语音转写开关")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(true to "开启", false to "关闭").forEach { (value, label) ->
                WearChip(
                    label = label,
                    selected = enabled == value,
                    onClick = {
                        context.vibrateTick()
                        enabled = value
                        persist()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        WearSectionHeader(text = "识别服务")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(AsrProviderId.SYSTEM, AsrProviderId.BAIDU).forEach { candidate ->
                WearChip(
                    label = candidate.displayName,
                    selected = providerId == candidate,
                    onClick = {
                        context.vibrateTick()
                        providerId = candidate
                        testStatus = ""
                        persist()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        BasicText(
            text = if (providerId == AsrProviderId.BAIDU) {
                "百度短语音：密钥缺失时自动回退系统识别"
            } else {
                "系统识别：依赖手表现有语音服务"
            },
            style = Wear.TextCaption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        WearSectionHeader(text = "百度语音密钥")
        WearSectionHeader(text = "API Key (AK)")

        SettingsInput(
            value = apiKey,
            onValueChange = {
                apiKey = it
                persist()
            },
            placeholder = "请输入 API Key"
        )

        WearSectionHeader(text = "Secret Key (SK)")

        SettingsInput(
            value = secretKey,
            onValueChange = {
                secretKey = it
                persist()
            },
            placeholder = "请输入 Secret Key"
        )

        WearSectionHeader(text = "识别语言")

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            AsrLanguage.values().forEach { candidate ->
                WearChip(
                    label = candidate.displayName,
                    selected = language == candidate,
                    onClick = {
                        context.vibrateTick()
                        language = candidate
                        persist()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        WearSectionHeader(text = "连通性自检")

        SettingsActionButton(
            label = if (isTesting) "正在测试…" else "测试转写",
            enabled = !isTesting,
            tone = Tone.ACCENT,
            onClick = {
                context.vibrateTick()
                if (isTesting) return@SettingsActionButton
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
            }
        )

        if (testStatus.isNotBlank()) {
            BasicText(
                text = testStatus,
                style = Wear.TextCaption,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
