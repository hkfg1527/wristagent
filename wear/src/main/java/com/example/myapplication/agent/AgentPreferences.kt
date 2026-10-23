package com.example.myapplication.agent

import android.content.Context
import android.content.SharedPreferences
import com.example.myapplication.agent.contract.RightButtonAction
import com.example.myapplication.agent.contract.SecurityMode

/**
 * Agent 与表盘偏好设置持久化仓库
 */
class AgentPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    var securityMode: SecurityMode
        get() = try {
            SecurityMode.valueOf(prefs.getString("security_mode", SecurityMode.STANDARD.name) ?: SecurityMode.STANDARD.name)
        } catch (_: Exception) {
            SecurityMode.STANDARD
        }
        set(value) = prefs.edit().putString("security_mode", value.name).apply()

    var rightButtonAction: RightButtonAction
        get() = try {
            RightButtonAction.valueOf(prefs.getString("right_button_action", RightButtonAction.MODEL_SWITCH.name) ?: RightButtonAction.MODEL_SWITCH.name)
        } catch (_: Exception) {
            RightButtonAction.MODEL_SWITCH
        }
        set(value) = prefs.edit().putString("right_button_action", value.name).apply()

    var apiKey: String
        get() {
            val saved = prefs.getString("api_key", "") ?: ""
            if (saved.isNotBlank()) return saved
            return com.example.myapplication.BuildConfig.DEEPSEEK_API_KEY
        }
        set(value) = prefs.edit().putString("api_key", value).apply()

    var baseUrl: String
        get() = prefs.getString("base_url", "https://api.deepseek.com") ?: "https://api.deepseek.com"
        set(value) = prefs.edit().putString("base_url", value).apply()

    var modelName: String
        get() = prefs.getString("model_name", "deepseek-chat") ?: "deepseek-chat"
        set(value) = prefs.edit().putString("model_name", value).apply()

    var matrixSpacingDp: Float
        get() = prefs.getFloat("matrix_spacing_dp", 16f)
        set(value) = prefs.edit().putFloat("matrix_spacing_dp", value).apply()

    var showThoughtChain: Boolean
        get() = prefs.getBoolean("show_thought_chain", true)
        set(value) = prefs.edit().putBoolean("show_thought_chain", value).apply()

    var ultraSaving: Boolean
        get() = prefs.getBoolean("ultra_saving", false)
        set(value) = prefs.edit().putBoolean("ultra_saving", value).apply()

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("has_completed_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_completed_onboarding", value).apply()

    var temperature: Float
        get() = prefs.getFloat("temperature", 0.7f)
        set(value) = prefs.edit().putFloat("temperature", value).apply()

    var mcpEndpoint: String
        get() = prefs.getString("mcp_endpoint", "http://127.0.0.1:8000/sse") ?: "http://127.0.0.1:8000/sse"
        set(value) = prefs.edit().putString("mcp_endpoint", value).apply()

    var isMcpEnabled: Boolean
        get() = prefs.getBoolean("is_mcp_enabled", true)
        set(value) = prefs.edit().putBoolean("is_mcp_enabled", value).apply()

    var customSystemPrompt: String
        get() = prefs.getString("custom_system_prompt", "") ?: ""
        set(value) = prefs.edit().putString("custom_system_prompt", value).apply()

    var maxContextTurns: Int
        get() = prefs.getInt("max_context_turns", 30)
        set(value) = prefs.edit().putInt("max_context_turns", value).apply()

    var mainInputType: String
        get() = prefs.getString("main_input_type", "VOICE") ?: "VOICE"
        set(value) = prefs.edit().putString("main_input_type", value).apply()

    var maxOutputTokens: Int
        get() = prefs.getInt("max_output_tokens", 300)
        set(value) = prefs.edit().putInt("max_output_tokens", value).apply()

    var streamMode: Boolean
        get() = prefs.getBoolean("stream_mode", true)
        set(value) = prefs.edit().putBoolean("stream_mode", value).apply()

    var cachedRemoteModels: String
        get() = prefs.getString("cached_remote_models", "deepseek-chat,deepseek-reasoner") ?: "deepseek-chat,deepseek-reasoner"
        set(value) = prefs.edit().putString("cached_remote_models", value).apply()

    var toolDeviceStatusEnabled: Boolean
        get() = prefs.getBoolean("tool_device_status_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_device_status_enabled", value).apply()

    var toolVibrateEnabled: Boolean
        get() = prefs.getBoolean("tool_vibrate_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_vibrate_enabled", value).apply()

    var toolMatrixDensityEnabled: Boolean
        get() = prefs.getBoolean("tool_matrix_density_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_matrix_density_enabled", value).apply()

    var toolPowerModeEnabled: Boolean
        get() = prefs.getBoolean("tool_power_mode_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_power_mode_enabled", value).apply()

    var toolWeatherEnabled: Boolean
        get() = prefs.getBoolean("tool_weather_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_weather_enabled", value).apply()

    var toolWebSearchEnabled: Boolean
        get() = prefs.getBoolean("tool_web_search_enabled", true)
        set(value) = prefs.edit().putBoolean("tool_web_search_enabled", value).apply()

    fun resetAll() {
        prefs.edit().clear().apply()
    }
}
