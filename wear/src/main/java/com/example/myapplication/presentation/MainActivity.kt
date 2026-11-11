package com.example.myapplication.presentation

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import android.view.WindowManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import com.example.myapplication.agent.AgentDispatcher
import com.example.myapplication.agent.AgentPreferences
import com.example.myapplication.agent.AgentRepository
import com.example.myapplication.agent.AgentToolRegistry
import com.example.myapplication.agent.GestureAction
import com.example.myapplication.agent.GestureBindingGate
import com.example.myapplication.agent.GestureBindings
import com.example.myapplication.agent.GestureEvent
import com.example.myapplication.agent.GestureRequestQueue
import com.example.myapplication.agent.contract.SecurityMode
import com.example.myapplication.agent.remote.ConsoleServer
import com.example.myapplication.agent.remote.ConsoleService
import com.example.myapplication.presentation.theme.MyApplicationTheme
import com.example.myapplication.presentation.ui.chat.WearAgentApp

/**
 * 本模块能接管的物理按键。
 *
 * 键码到这里的映射只出现在 [MainActivity.gestureKeySourceOf] 一处：判据侧不碰 android.view，
 * 才能在 JVM 单测里直接跑。
 */
internal enum class GestureKeySource {
    /** 表冠按下（KEYCODE_STEM_PRIMARY）。 */
    STEM_PRIMARY,

    /** 侧键 / Sport 键（KEYCODE_STEM_1）；没有这颗键的设备自然不会收到它的按键。 */
    STEM_1,

    /** 第二个侧键（KEYCODE_STEM_2）；同上，设备没有就是没有，不伪造。 */
    STEM_2,

    /** 系统返回键（KEYCODE_BACK）：只有长按归我们，短按必须留给系统。 */
    BACK;

    /**
     * 这颗键的短按是不是我们的活儿。
     *
     * 表冠短按 = 回表盘、返回键短按 = 返回上一页，都是系统动作，拿走等于把系统搞坏；
     * 只有侧键的短按可以交给用户绑定的 [GestureEvent.SIDE_KEY_SHORT]。
     */
    val ownsShortPress: Boolean
        get() = this == STEM_1 || this == STEM_2

    /** 按住超过阈值后产出的手势：侧键是 SIDE_KEY_LONG，表冠与返回键是 LONG_PRESS。 */
    val longPressEvent: GestureEvent
        get() = if (ownsShortPress) GestureEvent.SIDE_KEY_LONG else GestureEvent.LONG_PRESS
}

/** 一次按键回调的阶段：首次按下 / 系统重复下发 / 抬起。 */
internal enum class GestureKeyPhase { DOWN, REPEAT, UP }

/**
 * 一次按键处理的结论。
 *
 * [event] 非空表示要投递这个手势，调用方再经 [GestureBindingGate] 解析成动作入队；
 * [consumed] 表示这次按键事件不再往下传 —— 只有确实由我们处理时才为真。
 */
internal data class GestureKeyDecision(
    val event: GestureEvent?,
    val consumed: Boolean,
    /**
     * 短按已抬起、双击还没定：调用方在 [GestureDeferral.delayMs] 之后回调
     * [GestureKeyResolver.onWindowExpired] 结算。判据自己不等、不阻塞、不排定时器。
     */
    val deferral: GestureDeferral? = null
)

/**
 * 双击窗口的欠账：哪个键的短按、什么时候抬起的、还要等多少毫秒回来结算。
 *
 * 时间戳来自正在判定那一刻的按键回调，[delayMs] 只用来告诉调用方"什么时候回来问"，
 * 判定本身仍然只按时间戳算 —— 定时器抖动、回调迟到都不会把双击判成单击或反过来。
 */
internal data class GestureDeferral(
    val source: GestureKeySource,
    val releasedAtMs: Long,
    val delayMs: Long
)

/**
 * 按键到手势的纯判据：不碰 android.view、不碰队列、不读时钟（时间由调用方传进来）。
 *
 * 三条不变量：
 * 1. 短按不吞系统键：表冠短按（回表盘）与返回键短按（返回上一页）一律交还系统；
 * 2. 长按只投递一次：按住不放会让系统连续下发重复事件，判据只在跨过阈值那一拍打上
 *    已投递标记，之后每次重复与最后的抬起都不再投递；
 * 3. 未绑定的手势不投递也不吞：设备没有侧键、或用户没绑动作时，行为与没接这段代码一致。
 */
internal class GestureKeyResolver(
    private val longPressThresholdMs: Long = DEFAULT_LONG_PRESS_MS,
    private val doubleTapWindowMs: Long = DEFAULT_DOUBLE_TAP_WINDOW_MS
) {
    /** 正在按住的一拍；[firstTap] 非空表示它是双击的第二拍。 */
    private class Press(val startedAtMs: Long, val firstTap: PendingShort? = null) {
        var fired = false
        var firedBound = false
    }

    /** 已抬起、等窗口结算的一拍短按。 */
    private class PendingShort(val releasedAtMs: Long)

    private val presses = HashMap<GestureKeySource, Press>()

    /** 挂起等窗口的短按：同一个键同时只可能有一次。 */
    private val pendingShorts = HashMap<GestureKeySource, PendingShort>()

    fun onKey(
        source: GestureKeySource,
        phase: GestureKeyPhase,
        atMs: Long,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision = when (phase) {
        GestureKeyPhase.DOWN -> onDown(source, atMs, isBound)
        GestureKeyPhase.REPEAT -> onRepeat(source, atMs, isBound)
        GestureKeyPhase.UP -> onUp(source, atMs, isBound)
    }

    /**
     * 按下的起点必须来自最近一次按下：丢了抬起事件时重新计时，不拿上一轮的起点算这次的长按。
     *
     * 双击在这里接上第一拍：窗口内的这次按下不给上一次短按结算，而是给它挂上"第二拍"标记；
     * 窗口已过（定时回调迟到或压根没排上）就先结算它，再把这次按下当新的一拍。
     */
    private fun onDown(
        source: GestureKeySource,
        atMs: Long,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision {
        val pending = pendingShorts[source]
        val inWindow = pending != null && atMs - pending.releasedAtMs in 0L..doubleTapWindowMs
        // 过窗的第一拍照常投递：结算不能只依赖定时回调，回调迟到也得有手势产出
        val flushed = if (pending != null && !inWindow) settlePendingShort(source, isBound) else null
        presses[source] = Press(atMs, firstTap = if (inWindow) pending else null)
        return GestureKeyDecision(flushed?.event, ownsSequence(source, isBound))
    }

    private fun onRepeat(
        source: GestureKeySource,
        atMs: Long,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision {
        val press = presses[source] ?: return GestureKeyDecision(null, false)
        // 重复事件既不参与双击判定，也不改动挂起的窗口：它只在这一拍按住够久时补判长按
        if (press.fired) return GestureKeyDecision(null, press.firedBound)
        if (atMs - press.startedAtMs < longPressThresholdMs) return GestureKeyDecision(null, false)
        return fire(source, press, isBound)
    }

    private fun onUp(
        source: GestureKeySource,
        atMs: Long,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision {
        val press = presses.remove(source) ?: return GestureKeyDecision(null, false)
        // 长按已经投递过：抬起只负责收尾，不能再投递第二次
        if (press.fired) return GestureKeyDecision(null, press.firedBound)
        // 设备不送重复事件（或漏掉了）时，松手这一拍按同一阈值补判长按；
        // 第二拍按成长按同样走这里：双击作废，欠着的第一拍一并放弃（一次序列只产出一个手势）
        if (atMs - press.startedAtMs >= longPressThresholdMs) return fire(source, press, isBound)
        if (!source.ownsShortPress) return GestureKeyDecision(null, false)
        val firstTap = press.firstTap
        if (firstTap != null) {
            pendingShorts.remove(source)
            val inWindow = press.startedAtMs - firstTap.releasedAtMs in 0L..doubleTapWindowMs
            // 两拍都短于长按阈值（这里的这一拍已判定）+ 间隔在窗口内，才算双击
            if (inWindow && isBound(GestureEvent.DOUBLE_TAP)) {
                return publish(GestureEvent.DOUBLE_TAP, isBound)
            }
            // 窗口对不上、或双击刚被解绑：这一拍退回普通短按，第一拍不再单独投递
            return publish(GestureEvent.SIDE_KEY_SHORT, isBound)
        }
        // 第一拍短按：绑了双击就先挂着等窗口，没绑就照旧立刻投递（未绑定 = 行为不变）
        if (!isBound(GestureEvent.DOUBLE_TAP)) return publish(GestureEvent.SIDE_KEY_SHORT, isBound)
        pendingShorts[source] = PendingShort(releasedAtMs = atMs)
        return GestureKeyDecision(
            event = null,
            // 这一串按键整段归我们：按下已吞，抬起也不能漏给系统，否则第一拍会被当成系统动作
            consumed = true,
            deferral = GestureDeferral(source, atMs, doubleTapWindowMs)
        )
    }

    /**
     * 双击窗口到期：由调用方（[MainActivity] 的 Handler）在窗口之后回调进来结算。
     *
     * 判据不排定时器、不读时钟、不阻塞：回来早了（定时器抖动）就再欠一次账，
     * 由调用方接着等；回来晚了也按时间戳算，不会把单击误判成双击。
     */
    fun onWindowExpired(
        source: GestureKeySource,
        atMs: Long,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision {
        val pending = pendingShorts[source] ?: return GestureKeyDecision(null, false)
        val waited = atMs - pending.releasedAtMs
        if (waited < doubleTapWindowMs) {
            return GestureKeyDecision(
                event = null,
                consumed = false,
                deferral = GestureDeferral(source, pending.releasedAtMs, doubleTapWindowMs - waited)
            )
        }
        return settlePendingShort(source, isBound)
    }

    /** 结算挂起的短按：它没等到第二拍，就是一次普通短按。 */
    private fun settlePendingShort(
        source: GestureKeySource,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision {
        if (pendingShorts.remove(source) == null) return GestureKeyDecision(null, false)
        return publish(GestureEvent.SIDE_KEY_SHORT, isBound)
    }

    private fun fire(
        source: GestureKeySource,
        press: Press,
        isBound: (GestureEvent) -> Boolean
    ): GestureKeyDecision {
        // 长按抢走这一串按键：双击欠账作废
        pendingShorts.remove(source)
        val decision = publish(source.longPressEvent, isBound)
        press.fired = true
        press.firedBound = decision.consumed
        return decision
    }

    /**
     * 只有"短按也算我们的"且这组动作里至少绑了一个，才吞按下；
     * 否则侧键的按下要留给设备自己（例如厂商预设的运动键快捷方式）。
     * 只绑了双击同样要吞：第一拍被设备抢走就凑不出双击。
     */
    private fun ownsSequence(source: GestureKeySource, isBound: (GestureEvent) -> Boolean): Boolean =
        source.ownsShortPress && (
            isBound(GestureEvent.SIDE_KEY_SHORT) ||
                isBound(GestureEvent.DOUBLE_TAP) ||
                isBound(source.longPressEvent)
            )

    /** 绑过的动作才投递、才吞；没绑定一律原样放行。 */
    private fun publish(event: GestureEvent, isBound: (GestureEvent) -> Boolean): GestureKeyDecision =
        if (isBound(event)) GestureKeyDecision(event, true) else GestureKeyDecision(null, false)

    companion object {
        /**
         * 长按阈值 450ms。
         *
         * 与底栏按住说话（340ms）同一量级但不是同一个值：两个阈值撞在一起时，一次按住会在两处
         * 同时被认成长按。系统按键重复的第一拍约在 500ms，跨过阈值即判长按；万一设备根本不送
         * 重复事件，抬起那一拍按同一阈值补判。
         */
        const val DEFAULT_LONG_PRESS_MS = 450L

        /**
         * 双击窗口 300ms：两次短按之间的间隔（第一次抬起 → 第二次按下）。
         *
         * 必须同时满足两条才算双击：两拍各自都短于长按阈值 450ms、间隔落在这个窗口里。
         * 窗口比长按阈值短，所以一次按住不会被同时判成长按与双击的第一拍。
         */
        const val DEFAULT_DOUBLE_TAP_WINDOW_MS = 300L
    }
}

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
    // 按键判据的持有者：按下计时与"已投递"标记只活在这里，语义见 GestureKeyResolver 的三条不变量。
    private val gestureKeyResolver = GestureKeyResolver()
    // 双击窗口的定时器：只负责"什么时候回来结算"，不参与判定，也不阻塞主线程（sleep 会把按键序列自己拖死）。
    private val gestureWindowHandler = Handler(Looper.getMainLooper())

    private val chatViewModel: ChatViewModel by viewModels {
        ChatViewModel.factory(
            application = application,
            gateway = AgentRepository(
                dispatcher = AgentDispatcher(applicationContext, toolRegistry, preferences),
                preferences = preferences
            )
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
        // 走前台服务：Activity 销毁不再带走控制台
        ConsoleService.start(this)
    }

    /**
     * 物理按键事件源：表冠按下、侧键（Sport 键）、返回键长按。
     *
     * 在 [dispatchKeyEvent] 这一层接管，因为只有它同时看得到按下、重复与抬起三拍 ——
     * [onKeyDown] 只有按下，判不出"按住了多久"，也和 [onKeyUp] 的状态对不上。
     * 这里只做三件事：把按键翻译成手势、把绑定出来的动作投进 [GestureRequestQueue]、
     * 给双击窗口排一个到期回调（[scheduleWindowSettlement]，不阻塞主线程）；
     * 真正执行仍在 AgentDispatcher 的工具循环里，UI 层不直接执行业务动作。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val source = gestureKeySourceOf(event.keyCode) ?: return super.dispatchKeyEvent(event)
        val phase = when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) GestureKeyPhase.DOWN else GestureKeyPhase.REPEAT
            }
            KeyEvent.ACTION_UP -> GestureKeyPhase.UP
            else -> return super.dispatchKeyEvent(event)
        }
        // 绑定每次现读：设置页拨完之后下一次按键就生效，不需要重建 Activity
        val bindings = preferences.gestureBindings()
        val decision = gestureKeyResolver.onKey(source, phase, SystemClock.uptimeMillis()) { gesture ->
            GestureBindingGate.actionFor(bindings, gesture) != GestureAction.NONE
        }
        deliverGesture(decision, bindings)
        // 没绑定的手势不吞按键：功能不生效时行为与没接这段代码完全一致
        return if (decision.consumed) true else super.dispatchKeyEvent(event)
    }

    /** 判据的结论落地：要投递的入队，欠着双击窗口的排一个到期回调。 */
    private fun deliverGesture(decision: GestureKeyDecision, bindings: GestureBindings) {
        decision.event?.let { GestureRequestQueue.post(GestureBindingGate.actionFor(bindings, it)) }
        decision.deferral?.let { scheduleWindowSettlement(it) }
    }

    /**
     * 双击窗口到期后回来结算：Handler 只决定"什么时候问"，判定仍由 [GestureKeyResolver] 按时间戳做。
     *
     * 绑定到期时现读（与按下时一致），所以设置页拨完之后不需要重建 Activity；
     * 回调早到会带回新的欠账，这里接着排下一次，全程不阻塞主线程。
     */
    private fun scheduleWindowSettlement(deferral: GestureDeferral) {
        gestureWindowHandler.postDelayed({
            val bindings = preferences.gestureBindings()
            val decision = gestureKeyResolver.onWindowExpired(
                deferral.source,
                SystemClock.uptimeMillis()
            ) { gesture -> GestureBindingGate.actionFor(bindings, gesture) != GestureAction.NONE }
            deliverGesture(decision, bindings)
        }, deferral.delayMs)
    }

    /** 键码到按键的唯一映射表：android.view 的常量只在这个文件这一处出现。 */
    private fun gestureKeySourceOf(keyCode: Int): GestureKeySource? = when (keyCode) {
        KeyEvent.KEYCODE_STEM_PRIMARY -> GestureKeySource.STEM_PRIMARY
        KeyEvent.KEYCODE_STEM_1 -> GestureKeySource.STEM_1
        KeyEvent.KEYCODE_STEM_2 -> GestureKeySource.STEM_2
        KeyEvent.KEYCODE_BACK -> GestureKeySource.BACK
        else -> null
    }

    override fun onDestroy() {
        // 待结算的双击窗口跟着 Activity 一起作废，不留给已经没人收的 Handler
        gestureWindowHandler.removeCallbacksAndMessages(null)
        // 不在这里 stop：前台服务自理，用户从设置页关闭开关时才停
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
