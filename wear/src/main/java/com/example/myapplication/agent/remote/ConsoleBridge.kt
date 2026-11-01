package com.example.myapplication.agent.remote

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.speech.SpeechRecognizer
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
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
        "matrixSpacingDp" to preferences.matrixSpacingDp,
        "mainInputType" to preferences.mainInputType,
        "streamMode" to preferences.streamMode,
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
            "matrixSpacingDp" -> preferences.matrixSpacingDp =
                (value as? Number)?.toFloat() ?: return ConsoleWriteResult.READ_ONLY
            "mainInputType" -> preferences.mainInputType = value as? String ?: return ConsoleWriteResult.READ_ONLY
            "streamMode" -> preferences.streamMode = value as? Boolean ?: return ConsoleWriteResult.READ_ONLY
            else -> return ConsoleWriteResult.READ_ONLY
        }
        return ConsoleWriteResult.OK
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
        snapshot["apiKey"] = ConsoleToken.mask(snapshot["apiKey"] as? String)
        snapshot["baiduApiKey"] = ConsoleToken.mask(snapshot["baiduApiKey"] as? String)
        snapshot["baiduSecretKey"] = ConsoleToken.mask(snapshot["baiduSecretKey"] as? String)
        return ConsoleJson.write(snapshot)
    }

    private fun readAsset(context: Context, name: String): ByteArray? = runCatching {
        context.assets.open("console/" + name).use { it.readBytes() }
    }.getOrNull()
}
