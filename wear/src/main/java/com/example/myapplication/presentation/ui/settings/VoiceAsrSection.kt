package com.example.myapplication.presentation.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.example.myapplication.R
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.presentation.ui.components.Tone
import com.example.myapplication.voice.AsrLanguage
import com.example.myapplication.voice.AsrProviderId
import com.example.myapplication.voice.AsrResult
import com.example.myapplication.voice.AsrSettings
import com.example.myapplication.voice.AudioRecordWavCapture
import com.example.myapplication.voice.VoiceAsrGateway
import kotlinx.coroutines.launch

/** 该页可编辑的两个密钥项。 */
private enum class AsrKeyField { API_KEY, SECRET_KEY }

/**
 * 语音转写配置分区：总开关、厂商单选、百度 AK/SK 对话框、识别语言单选与真实自检。
 * 每次改动都走同一 [AsrSettings] 快照落盘，写键与副作用与旧版完全一致。
 */
@Composable
fun VoiceAsrContent(
    preferences: AgentPreferences,
    onBack: () -> Unit,
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
    var editingField by remember { mutableStateOf<AsrKeyField?>(null) }

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

    SettingsSubPage(
        title = stringResource(R.string.settings_asr),
        onBack = onBack,
        overlay = {
            when (editingField) {
                AsrKeyField.API_KEY -> SettingsTextDialog(
                    title = stringResource(R.string.settings_baidu_api_key),
                    initial = apiKey,
                    placeholder = stringResource(R.string.settings_baidu_api_key_hint),
                    onValueChange = { value ->
                        apiKey = value
                        persist()
                    },
                    onDismiss = { editingField = null }
                )
                AsrKeyField.SECRET_KEY -> SettingsTextDialog(
                    title = stringResource(R.string.settings_baidu_secret_key),
                    initial = secretKey,
                    placeholder = stringResource(R.string.settings_baidu_secret_key_hint),
                    onValueChange = { value ->
                        secretKey = value
                        persist()
                    },
                    onDismiss = { editingField = null }
                )
                null -> Unit
            }
        }
    ) {
        section(stringResource(R.string.settings_asr_toggle_header))

        row {
            SettingsSwitchRow(
                label = stringResource(R.string.settings_asr_toggle_header),
                checked = enabled,
                onCheckedChange = { value ->
                    enabled = value
                    persist()
                }
            )
        }

        section(stringResource(R.string.settings_asr_service_header))

        listOf(AsrProviderId.SYSTEM, AsrProviderId.BAIDU).forEach { candidate ->
            val descriptionRes = if (candidate == AsrProviderId.BAIDU) {
                R.string.settings_asr_baidu_desc
            } else {
                R.string.settings_asr_system_desc
            }
            row {
                SettingsRadioRow(
                    label = candidate.displayName,
                    subtitle = stringResource(descriptionRes),
                    selected = providerId == candidate,
                    onSelect = {
                        providerId = candidate
                        testStatus = ""
                        persist()
                    }
                )
            }
        }

        section(stringResource(R.string.settings_baidu_keys_header))

        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_baidu_api_key),
                value = SettingsValueMath.maskSecret(apiKey),
                onClick = {
                    editingField = AsrKeyField.API_KEY
                }
            )
        }

        row {
            SettingsTextEntryRow(
                label = stringResource(R.string.settings_baidu_secret_key),
                value = SettingsValueMath.maskSecret(secretKey),
                onClick = {
                    editingField = AsrKeyField.SECRET_KEY
                }
            )
        }

        section(stringResource(R.string.settings_asr_language_header))

        AsrLanguage.values().forEach { candidate ->
            row {
                SettingsRadioRow(
                    label = candidate.displayName,
                    selected = language == candidate,
                    onSelect = {
                        language = candidate
                        persist()
                    }
                )
            }
        }

        section(stringResource(R.string.settings_asr_selftest_header))

        standaloneRow {
            SettingsActionButton(
                label = if (isTesting) {
                    stringResource(R.string.settings_asr_testing)
                } else {
                    stringResource(R.string.settings_asr_test)
                },
                enabled = !isTesting,
                tone = Tone.ACCENT,
                onClick = {
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
        }

        if (testStatus.isNotBlank()) {
            row { SettingsCaptionRow(text = testStatus) }
        }
    }
}
