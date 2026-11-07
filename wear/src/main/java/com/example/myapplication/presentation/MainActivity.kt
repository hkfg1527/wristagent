package com.example.myapplication.presentation

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import android.view.WindowManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.AgentRepository
import com.example.myapplication.agent.AgentToolRegistry
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.agent.remote.ConsoleServer
import com.example.myapplication.presentation.theme.MyApplicationTheme
import com.example.myapplication.presentation.ui.chat.WearAgentApp

/**
 * 宿主外壳：只做窗口形态、外部 intent 分发与"ViewModel ↔ 应用根容器"的接线。
 * 聊天界面的一切状态与业务入口都在 [ChatViewModel]，界面结构在 WearAgentApp/ChatScreen。
 */
class MainActivity : ComponentActivity() {

    private var externalPromptSeq = 0L
    private val externalPromptState = mutableStateOf<ExternalPrompt?>(null)

    private val preferences by lazy { AgentPreferences(applicationContext) }
    // 局域网网页控制台：随 Activity 存活（无前台服务），退到后台被回收即断开。
    // create() 是单例工厂，重复调用安全；start()/stop() 幂等，端口被占会自动回退 8081/8082。
    private val consoleServer by lazy { ConsoleServer.create(applicationContext) }
    private val toolRegistry by lazy { AgentToolRegistry(applicationContext) }

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModel.factory(
            application = application,
            gateway = AgentRepository(
                dispatcher = AgentDispatcher(applicationContext, toolRegistry, preferences),
                preferences = preferences
            ),
            initialMainInputType = mainInputTypeFromStored(preferences.mainInputType)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        // Keep-screen-on only while the app is actually working: an always-on screen plus a
        // per-frame wake lock is the single biggest battery cost on a watch. Idle lets the
        // system dim and sleep as usual; running / recording / approval keeps it awake.
        lifecycleScope.launch {
            chatViewModel.uiState
                .map { it.isExecuting || it.isRecordingVoice }
                .distinctUntilChanged()
                .collect { keepOn ->
                    if (keepOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
        }
        // 输入法由 Compose insets 驱动避让：窗口不整体平移，也不二次压缩内容高度
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        // 提前建 ViewModel：引擎初始化时就要装载 system 记忆头与实时遥测
        chatViewModel
        setContent {
            MyApplicationTheme {
                WearAgentApp(
                    viewModel = chatViewModel,
                    preferences = preferences,
                    toolRegistry = toolRegistry,
                    externalPrompt = externalPromptState.value,
                    onClearExternalPrompt = { externalPromptState.value = null }
                )
            }
        }
        handleIntent(intent)
        // 监听失败（端口全占）时 start() 不抛异常、port 保持 -1，App 照常可用
        consoleServer.start()
    }

    override fun onDestroy() {
        consoleServer.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val apiKey = intent?.getStringExtra("api_key")
        if (!apiKey.isNullOrBlank()) {
            preferences.apiKey = apiKey
        }
        // 安全等级不接受外部 intent 注入：本 Activity 是 exported 的 LAUNCHER，
        // 任何应用都能发 -e security_mode EXPERT，而 EXPERT 等于危险操作自动放行，
        // 这条通道等于把审批闸门对外开关，只能由用户在设置页手动切换。
        val prompt = intent?.getStringExtra("prompt")
        if (!prompt.isNullOrBlank()) {
            // 每次 intent 递增序号：同文本的新 intent 也能再触发，重组则不会重复触发
            externalPromptState.value = ExternalPrompt(seq = ++externalPromptSeq, text = prompt)
        }
    }
}
