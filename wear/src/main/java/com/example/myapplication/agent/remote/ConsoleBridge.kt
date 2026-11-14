package com.example.myapplication.agent.remote

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.speech.SpeechRecognizer
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.SandboxLevel
import com.example.myapplication.agent.SettingsChangeBus
import com.example.myapplication.agent.ToolApprovalPolicy
import com.example.myapplication.agent.ProviderProfile
import com.example.myapplication.agent.ProviderProfileBook
import com.example.myapplication.agent.ReplyStyle
import com.example.myapplication.appearance.SettingsStyle
import com.example.myapplication.agent.providerProfileId
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.agent.privilege.PrivilegeManager
import com.example.myapplication.voice.AsrResult
import com.example.myapplication.voice.AudioRecordWavCapture
import com.example.myapplication.voice.VoiceAsrGateway
import kotlinx.coroutines.runBlocking

/**
 * 令牌持久化：独立 prefs 文件，避免用户点"清空记忆"（AgentPreferences.resetAll）时把控制台令牌一起清掉。
 */
class ConsoleTokenStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun current(): String = prefs.getString(KEY_TOKEN, null).orEmpty()

    /** 首次启动生成并落盘；已存在且格式合法则原样返回。 */
    fun getOrCreate(): String {
        val existing = current()
        if (ConsoleToken.isValidFormat(existing)) return existing
        val fresh = ConsoleToken.generate()
        prefs.edit().putString(KEY_TOKEN, fresh).commit()
        return fresh
    }

    fun rotate(): String {
        val fresh = ConsoleToken.generate()
        prefs.edit().putString(KEY_TOKEN, fresh).commit()
        return fresh
    }

    companion object {
        const val PREFS_NAME = "console_prefs"
        const val KEY_TOKEN = "console_token"
    }
}

/** settings 的全部读写都落到现有 [AgentPreferences]，键名与其真实属性一一对应。 */
class AgentPreferencesSettingsPort(private val preferences: AgentPreferences) : ConsoleSettingsPort {

    override fun snapshot(): Map<String, Any?> = linkedMapOf(
        "baseUrl" to preferences.baseUrl,
        "apiKey" to preferences.apiKey,
        "modelName" to preferences.modelName,
        "temperature" to preferences.temperature,
        "maxOutputTokens" to preferences.maxOutputTokens,
        "customSystemPrompt" to preferences.customSystemPrompt,
        "securityMode" to preferences.securityMode.name,
        "maxContextTurns" to preferences.maxContextTurns,
        "asrProvider" to preferences.asrProvider,
        "baiduApiKey" to preferences.baiduApiKey,
        "baiduSecretKey" to preferences.baiduSecretKey,
        "asrEnabled" to preferences.asrEnabled,
        "toolDeviceStatusEnabled" to preferences.toolDeviceStatusEnabled,
        "toolVibrateEnabled" to preferences.toolVibrateEnabled,
        "toolMatrixDensityEnabled" to preferences.toolMatrixDensityEnabled,
        "toolPowerModeEnabled" to preferences.toolPowerModeEnabled,
        "toolWeatherEnabled" to preferences.toolWeatherEnabled,
        "toolWebSearchEnabled" to preferences.toolWebSearchEnabled,
        "isMcpEnabled" to preferences.isMcpEnabled,
        "mcpEndpoint" to preferences.mcpEndpoint,
        "providerProfiles" to webProviderProfiles(preferences),
        "matrixSpacingDp" to preferences.matrixSpacingDp,
        "streamMode" to preferences.streamMode,
        "animationsEnabled" to preferences.animationsEnabled,
        "subAgentEnabled" to preferences.subAgentEnabled,
        "compactMode" to preferences.compactMode,
        "providerManagedSearchEnabled" to preferences.providerManagedSearchEnabled,
        "autoSpeakReplies" to preferences.autoSpeakReplies,
        "asrLiveTranscript" to preferences.asrLiveTranscript,
        "defaultThinkingEnabled" to preferences.defaultThinkingEnabled,
        "replyStyle" to preferences.replyStyle,
        "settingsStyle" to preferences.settingsStyle.name,
        "memoryEnabled" to preferences.memoryEnabled,
        "autoCompressContext" to preferences.autoCompressContext,
        "sandboxLevel" to preferences.sandboxLevel.storageKey,
        "toolApprovalPolicy" to preferences.toolApprovalPolicy.storageKey,
        "historyCompressThreshold" to preferences.historyCompressThreshold,
        "requestTimeoutSeconds" to preferences.requestTimeoutSeconds,
        "requestRetries" to preferences.requestRetries,
        "retryBackoffMs" to preferences.retryBackoffMs,
        "wifiOnlyRequests" to preferences.wifiOnlyRequests,
        "hapticsEnabled" to preferences.hapticsEnabled,
        "hasCompletedOnboarding" to preferences.hasCompletedOnboarding,
    )

    override fun write(key: String, value: Any): ConsoleWriteResult {
        when (key) {
            "baseUrl" -> preferences.baseUrl = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "apiKey" -> preferences.apiKey = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "modelName" -> preferences.modelName = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "temperature" -> preferences.temperature =
                (value as? Number)?.toFloat() ?: return ConsoleWriteResult.READ_ONLY
            "maxOutputTokens" -> preferences.maxOutputTokens = value as? Int ?: return ConsoleWriteResult.READ_ONLY
            "customSystemPrompt" -> preferences.customSystemPrompt = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "securityMode" -> {
                val name = value as? String ?: return ConsoleWriteResult.READ_ONLY
                val mode = SecurityMode.values().firstOrNull { it.name == name } ?: return ConsoleWriteResult.READ_ONLY
                preferences.securityMode = mode
            }
            "maxContextTurns" -> preferences.maxContextTurns = value as? Int ?: return ConsoleWriteResult.READ_ONLY
            "requestTimeoutSeconds" -> preferences.requestTimeoutSeconds =
                value as? Int ?: return ConsoleWriteResult.READ_ONLY
            "requestRetries" -> preferences.requestRetries =
                value as? Int ?: return ConsoleWriteResult.READ_ONLY
            "retryBackoffMs" -> preferences.retryBackoffMs =
                value as? Int ?: return ConsoleWriteResult.READ_ONLY
            "wifiOnlyRequests" -> preferences.wifiOnlyRequests =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "hapticsEnabled" -> preferences.hapticsEnabled =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "asrProvider" -> preferences.asrProvider = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "baiduApiKey" -> preferences.baiduApiKey = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "baiduSecretKey" -> preferences.baiduSecretKey = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "asrEnabled" -> preferences.asrEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "toolDeviceStatusEnabled" -> preferences.toolDeviceStatusEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "toolVibrateEnabled" -> preferences.toolVibrateEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "toolMatrixDensityEnabled" -> preferences.toolMatrixDensityEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "toolPowerModeEnabled" -> preferences.toolPowerModeEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "toolWeatherEnabled" -> preferences.toolWeatherEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "toolWebSearchEnabled" -> preferences.toolWebSearchEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "isMcpEnabled" -> preferences.isMcpEnabled = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "mcpEndpoint" -> preferences.mcpEndpoint = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "providerProfiles" -> if (!saveWebProviderProfiles(preferences, value)) {
                return ConsoleWriteResult.READ_ONLY
            }
            "matrixSpacingDp" -> preferences.matrixSpacingDp =
                (value as? Number)?.toFloat() ?: return ConsoleWriteResult.READ_ONLY
            "streamMode" -> preferences.streamMode = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "animationsEnabled" -> preferences.animationsEnabled =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "subAgentEnabled" -> preferences.subAgentEnabled =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "compactMode" -> preferences.compactMode =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "providerManagedSearchEnabled" -> preferences.providerManagedSearchEnabled =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "autoSpeakReplies" -> preferences.autoSpeakReplies =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "asrLiveTranscript" -> preferences.asrLiveTranscript =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "defaultThinkingEnabled" -> preferences.defaultThinkingEnabled =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "replyStyle" -> {
                val stored = value as? String ?: return ConsoleWriteResult.READ_ONLY
                val style = ReplyStyle.values().firstOrNull { it.storageKey == stored }
                    ?: return ConsoleWriteResult.READ_ONLY
                preferences.replyStyle = style.storageKey
            }
            "settingsStyle" -> {
                val stored = value as? String ?: return ConsoleWriteResult.READ_ONLY
                val style = SettingsStyle.values().firstOrNull { it.name == stored }
                    ?: return ConsoleWriteResult.READ_ONLY
                preferences.settingsStyle = style
            }
            "memoryEnabled" -> preferences.memoryEnabled =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "autoCompressContext" -> preferences.autoCompressContext =
                value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            "sandboxLevel" -> {
                val storage = value as? String ?: return ConsoleWriteResult.READ_ONLY
                val level = SandboxLevel.values().firstOrNull { it.storageKey == storage }
                    ?: return ConsoleWriteResult.READ_ONLY
                preferences.sandboxLevel = level
            }
            "toolApprovalPolicy" -> {
                val storage = value as? String ?: return ConsoleWriteResult.READ_ONLY
                val policy = ToolApprovalPolicy.values().firstOrNull { it.storageKey == storage }
                    ?: return ConsoleWriteResult.READ_ONLY
                preferences.toolApprovalPolicy = policy
            }
            "historyCompressThreshold" -> preferences.historyCompressThreshold =
                (value as? Number)?.toFloat() ?: return ConsoleWriteResult.READ_ONLY
            else -> return ConsoleWriteResult.READ_ONLY
        }
        // 写入成功后通知界面重读：否则运行中的界面要等重启才看到新值
        SettingsChangeBus.bump()
        return ConsoleWriteResult.OK
    }

    /** 网页端的档案清单：字段名与 app.js 的 providerProfiles 行一致（name / baseUrl / model）。 */
    private fun webProviderProfiles(preferences: AgentPreferences): List<Map<String, Any?>> =
        preferences.providerProfiles().profiles.map { profile ->
            linkedMapOf<String, Any?>(
                "name" to profile.name,
                "baseUrl" to profile.baseUrl,
                "model" to profile.modelName,
            )
        }

    /**
     * 网页提交的是整个档案数组，落点仍是 [AgentPreferences.saveProviderProfiles] 这一个键。
     *
     * 按名称复用既有 id 与 apiKey：网页看不到凭据，整块替换不能把它们抹掉；新条目沿用当前单值
     * apiKey，与「以当前配置新建档案」同义。超过 [ProviderProfileBook.MaxProfiles] 套的尾巴由账本截断。
     */
    private fun saveWebProviderProfiles(preferences: AgentPreferences, value: Any): Boolean {
        val entries = value as? List<*> ?: return false
        val existing = preferences.providerProfiles()
        var book = ProviderProfileBook()
        for (entry in entries) {
            val row = entry as? Map<*, *> ?: return false
            val name = (row["name"] as? String)?.trim().orEmpty()
            if (name.isEmpty()) return false
            val previous = existing.profiles.firstOrNull { it.name == name }
            book = book.add(
                ProviderProfile(
                    id = previous?.id ?: providerProfileId(),
                    name = name,
                    baseUrl = (row["baseUrl"] as? String).orEmpty(),
                    apiKey = previous?.apiKey ?: preferences.apiKey,
                    modelName = (row["model"] as? String).orEmpty(),
                )
            )
        }
        val activeId = existing.activeProfileId?.takeIf { id -> book.profiles.any { it.id == id } }
            ?: book.profiles.firstOrNull()?.id
        preferences.saveProviderProfiles(if (activeId == null) book else book.select(activeId))
        return true
    }
}

/** 把控制台接线到 App 现有的业务实现：偏好、特权层、ASR 网关、模型发现、assets。 */
internal object ConsoleBridge {

    fun buildApi(context: Context, serverPort: () -> Int): ConsoleApi {
        val appContext = context.applicationContext ?: context
        val preferences = AgentPreferences(appContext)
        val privilege = PrivilegeManager(appContext)
        val tokens = ConsoleTokenStore(appContext)
        val startedAtMs = SystemClock.elapsedRealtime()
        return ConsoleApi(
            settings = AgentPreferencesSettingsPort(preferences),
            capabilities = { capabilitiesOf(appContext, privilege) },
            actions = { name -> runAction(appContext, preferences, privilege, name) },
            assets = { name -> readAsset(appContext, name) },
            token = { tokens.getOrCreate() },
            serverPort = serverPort,
            uptime = { SystemClock.elapsedRealtime() - startedAtMs },
            throttle = ConsoleAuthThrottle(maxFailures = preferences.consoleMaxFailures),
        )
    }

    private fun capabilitiesOf(context: Context, privilege: PrivilegeManager): Map<String, Any?> {
        val status = runCatching { runBlocking { privilege.refresh() } }.getOrNull() ?: privilege.status.value
        return mapOf(
            "root" to status.rootAvailable,
            "shizukuInstalled" to status.shizukuInstalled,
            "shizukuReady" to status.shizukuReady,
            "activeBackend" to status.activeBackend.name,
            "internet" to hasInternet(context),
        )
    }

    private fun hasInternet(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = manager?.activeNetwork
        val caps = network?.let { manager.getNetworkCapabilities(it) }
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    private fun runAction(
        context: Context,
        preferences: AgentPreferences,
        privilege: PrivilegeManager,
        name: String,
    ): String = when (name) {
        "probe-privilege" -> probePrivilege(privilege)
        "test-asr" -> testAsr(context, preferences)
        "clear-memory" -> clearMemory(preferences)
        "refresh-models" -> refreshModels(preferences)
        "export-settings" -> exportSettings(preferences)
        else -> ""
    }

    private fun probePrivilege(privilege: PrivilegeManager): String = runBlocking {
        val status = runCatching { privilege.refresh() }.getOrNull() ?: privilege.status.value
        val output = runCatching { privilege.probe() }.getOrNull()?.trim().orEmpty()
        val backend = status.activeBackend.name
        if (output.isEmpty()) backend + ": 未取得 id 输出（root=" + status.rootAvailable + "）"
        else backend + ": " + output.lineSequence().first()
    }

    private fun testAsr(context: Context, preferences: AgentPreferences): String = runBlocking {
        val gateway = VoiceAsrGateway(capture = AudioRecordWavCapture(context))
        val recognized = SpeechRecognizer.isRecognitionAvailable(context)
        when (val result = gateway.transcribe(preferences.asrSettings(), recognized)) {
            is AsrResult.Success -> "识别成功：" + result.text
            is AsrResult.Failure -> "识别失败：" + result.reason
        }
    }

    private fun clearMemory(preferences: AgentPreferences): String {
        preferences.resetAll()
        return "已清空全部设置与记忆（与手表「清空记忆」同一实现 AgentPreferences.resetAll）"
    }

    private fun refreshModels(preferences: AgentPreferences): String = runBlocking {
        val (costMs, models) = AgentDispatcher.fetchRemoteModels(preferences.baseUrl, preferences.apiKey)
        when {
            costMs < 0 -> "刷新失败：网络不可达或鉴权失败"
            models.isEmpty() -> "连接成功（" + costMs + "ms），但服务端未返回模型列表"
            else -> {
                preferences.cachedRemoteModels = models.joinToString(",")
                "已刷新 " + models.size + " 个模型（" + costMs + "ms）：" + models.joinToString(", ")
            }
        }
    }

    private fun exportSettings(preferences: AgentPreferences): String {
        val snapshot = LinkedHashMap(AgentPreferencesSettingsPort(preferences).snapshot())
        for (secret in ConsoleSecrets.KEYS) {
            snapshot[secret] = ConsoleSecrets.mask(snapshot[secret] as? String)
        }
        return ConsoleJson.write(snapshot)
    }

    private fun readAsset(context: Context, name: String): ByteArray? = runCatching {
        context.assets.open("console/" + name).use { it.readBytes() }
    }.getOrNull()
}
