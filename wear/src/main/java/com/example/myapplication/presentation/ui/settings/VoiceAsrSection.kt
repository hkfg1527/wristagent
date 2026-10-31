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
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
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
        WearSectionHeader(text = stringResource(R.string.settings_asr_toggle_header))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Wear.Space.Sm)
        ) {
            listOf(true to stringResource(R.string.settings_switch_on), false to stringResource(R.string.settings_switch_off)).forEach { (value, label) ->
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

        WearSectionHeader(text = stringResource(R.string.settings_asr_service_header))

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
                stringResource(R.string.settings_asr_baidu_desc)
            } else {
                stringResource(R.string.settings_asr_system_desc)
            },
            style = Wear.TextCaption,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        WearSectionHeader(text = stringResource(R.string.settings_baidu_keys_header))
        WearSectionHeader(text = "API Key (AK)")

        SettingsInput(
            value = apiKey,
            onValueChange = {
                apiKey = it
                persist()
            },
            placeholder = stringResource(R.string.settings_baidu_api_key_hint)
        )

        WearSectionHeader(text = "Secret Key (SK)")

        SettingsInput(
            value = secretKey,
            onValueChange = {
                secretKey = it
                persist()
            },
            placeholder = stringResource(R.string.settings_baidu_secret_key_hint)
        )

        WearSectionHeader(text = stringResource(R.string.settings_asr_language_header))

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

        WearSectionHeader(text = stringResource(R.string.settings_asr_selftest_header))

        SettingsActionButton(
            label = if (isTesting) stringResource(R.string.settings_asr_testing) else stringResource(R.string.settings_asr_test),
            enabled = !isTesting,
            tone = Tone.ACCENT,
            onClick = {
                context.vibrateTick()
                if (isTesting) return@SettingsActionButton
                isTesting = true
                testStatus = context.getString(R.string.settings_asr_recording)
                persist()
                val gateway = VoiceAsrGateway(capture = AudioRecordWavCapture(context))
                scope.launch {
                    val result = gateway.transcribe(
                        currentSettings(),
                        android.speech.SpeechRecognizer.isRecognitionAvailable(context)
                    )
                    isTesting = false
                    testStatus = when (result) {
                        is AsrResult.Success -> context.getString(R.string.settings_asr_success, result.text)
                        is AsrResult.Failure -> context.getString(R.string.settings_asr_failure, result.reason)
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

        SubPageDoneRow()

        Spacer(modifier = Modifier.height(SubPageBottomReserve))
    }
}
