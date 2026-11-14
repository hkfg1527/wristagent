package com.example.myapplication.agent

import com.example.myapplication.agent.contract.ConversationBlock
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.ceil

/**
 * UI 展示与出向语音的只读快照：ViewModel 每次需要时现读一次偏好，
 * 因此在设置页拨动开关后立刻生效，不需要重建界面。
 */
data class AgentDisplayPreferences(
    val compactMode: Boolean = false,
    val autoSpeakReplies: Boolean = false,
    val asrLiveTranscript: Boolean = false,
    /** 流式合帧间隔（毫秒）：ChatViewModel 每轮开场套到节流器上（StreamingThrottleState.updateInterval）。 */
    val streamEmitIntervalMs: Int = AgentPreferences.DEFAULT_STREAM_EMIT_INTERVAL_MS,
    /** 播报语速：每次合成前交给 TextToSpeech.setSpeechRate。 */
    val ttsSpeechRate: Float = AgentPreferences.DEFAULT_TTS_SPEECH_RATE,
    /** 播报音量（0..1）：与语速一样，每次合成前都要重设。 */
    val ttsVolume: Float = AgentPreferences.DEFAULT_TTS_VOLUME,
    /** 播报队列语义：打断或排队。 */
    val ttsQueueMode: TtsQueueMode = TtsQueueMode.INTERRUPT
)

/**
 * 语音中间结果的落屏闸门：asrLiveTranscript 打开时用中间结果覆盖草稿（边说边显示），
 * 关闭时草稿一动不动，只等最终结果走 onVoiceResult 提交。
 */
object VoiceDraftGate {
    fun merge(current: String, partial: String, live: Boolean): String {
        if (!live) return current
        val trimmed = partial.trim()
        return if (trimmed.isEmpty()) current else trimmed
    }
}

/**
 * 自动播报的取文闸门：只有开关打开、且最后一段助手正文非空时才朗读；
 * null 表示这一轮不该出声 —— 不建 TTS、不占音频链路。
 */
object ReplySpeech {
    fun spokenText(enabled: Boolean, blocks: List<ConversationBlock>): String? {
        if (!enabled) return null
        for (block in blocks.asReversed()) {
            if (block is ConversationBlock.Text && !block.isUser) {
                val text = block.content.trim()
                if (text.isNotEmpty()) return text
            }
        }
        return null
    }
}

/**
 * defaultThinkingEnabled 的发送闸门：关掉后整条链路不发 reasoning_effort，
 * 即使模型支持推理也不会被要求推理；开着仍只在推理模型上发。
 */
object ThinkingPolicy {
    fun reasoningEffortBody(enabled: Boolean, modelName: String, effort: ReasoningEffort): String? =
        if (!enabled) null else ReasoningEffortPolicy.bodyValue(modelName, effort)
}

/** memoryEnabled 的注入闸门：关闭时连记忆头都不拼，长期笔记自然也不会进上下文。 */
object MemoryInjection {
    fun header(enabled: Boolean, header: String): String = if (enabled) header else ""
}

/**
 * autoCompressContext 的上下文压缩：复用请求侧既有的"滚动修剪"语义，
 * 打开时把落盘历史也压到同一上限（system 头保留 + 最近 N 轮），
 * 关闭时只留请求侧截断，历史原样保留。
 *
 * 修剪边界上若切在工具回执中间，丢掉孤立的 tool 消息即可 ——
 * 缺回执的 tool_calls 由既有的 sanitizeConversationHistory 自愈补齐。
 */
object ContextCompactor {
    fun trim(history: List<ChatMessage>, maxTurns: Int, enabled: Boolean): List<ChatMessage> {
        if (!enabled) return history
        return trimToMessages(history, maxTurns.coerceAtLeast(1) * 2)
    }

    /**
     * 按"保留多少条消息"修剪：与 [trim] 同一套边界规则（system 头保留、孤立 tool 回执丢弃），
     * 只是预算由 [CompressPolicy.keepMessages] 给出，而不是轮数 × 2。
     */
    fun trimToMessages(history: List<ChatMessage>, keepMessages: Int): List<ChatMessage> {
        val limit = keepMessages.coerceAtLeast(2)
        val head = history.takeWhile { it.role == AgentRole.SYSTEM }
        val dialog = history
            .filterIndexed { index, message -> index >= head.size && message.role != AgentRole.SYSTEM }
        if (dialog.size <= limit) return history

        val windowStart = (dialog.size - limit).coerceAtLeast(0)
        val window = dialog.subList(windowStart, dialog.size)

        // 孤立的 tool 回执 = 它的发起方（带同 id toolCalls 的 assistant）不在这个窗口里。
        // 这样的回执必须丢掉：模型收到一个没有发起方的工具结果，不是被直接拒（HTTP 400），
        // 就是顺着它开始编。旧实现只用 dropWhile 剥掉窗口**开头**的回执，落在窗口末尾的
        // 孤回执会一路留在上下文里 —— 这就是本方法修掉的缺陷。
        val initiatorIds = window
            .filter { message -> message.role == AgentRole.ASSISTANT }
            .flatMap { message -> message.toolCalls?.map { call -> call.id } ?: emptyList() }
            .toSet()
        val kept = window
            .filter { message ->
                message.role != AgentRole.TOOL ||
                    (message.toolCallId != null && message.toolCallId in initiatorIds)
            }
            .toMutableList()

        // 预算不许因为剔除而缩水：从窗口之前更早的消息里补回同等条数。
        // 回执本身不单独补 —— 补进来的回执同样缺发起方，等于把同一个 bug 换个位置。
        var refill = windowStart - 1
        while (kept.size < limit && refill >= 0) {
            val candidate = dialog[refill]
            refill--
            if (candidate.role == AgentRole.TOOL) continue
            kept.add(0, candidate)
        }
        return head + kept
    }
}

/**
 * providerManagedSearchEnabled 的工具清单闸门：
 * 本地检索（web_search）与 Provider 托管检索各自独立，关掉的那个不出现在清单里。
 */
object SearchToolGate {
    const val TOOL_LOCAL_SEARCH = "web_search"
    const val TOOL_PROVIDER_SEARCH = "provider_web_search"

    fun exposedSearches(localEnabled: Boolean, providerManagedEnabled: Boolean): List<String> {
        val names = mutableListOf<String>()
        if (localEnabled) names.add(TOOL_LOCAL_SEARCH)
        if (providerManagedEnabled) names.add(TOOL_PROVIDER_SEARCH)
        return names
    }
}

/**
 * 工具审批策略三档。
 *
 * 与 [SecurityMode] 的分工：SecurityMode 决定"这条命令有多危险"，审批策略决定
 * "危险到什么程度必须停下来问人"。两者组合后的唯一判定仍是 [ApprovalGate]，不新增第二个真源。
 */
enum class ToolApprovalPolicy(val storageKey: String) {
    /** 任何工具调用都停下来问人。 */
    ALWAYS("ALWAYS"),

    /** 只对有风险的工具调用问人（默认，等于历史行为）。 */
    RISKY("RISKY"),

    /** 从不问人，只有致命黑名单仍硬拦截。 */
    NEVER("NEVER");

    companion object {
        fun fromStorage(raw: String?): ToolApprovalPolicy =
            values().firstOrNull { it.storageKey == raw } ?: RISKY
    }
}

/**
 * 工具调用能触碰的范围。
 *
 * 判定对象是命令的破坏性分档（[ShellDanger]），不是安全模式：
 * 于是"沙箱收紧"与"审批放宽"可以同时成立，两者不会互相覆盖。
 */
enum class SandboxLevel(val storageKey: String) {
    /** 只放行只读命令。 */
    READ_ONLY("READ_ONLY"),

    /** 放行只读与风险命令，破坏性命令一律挡下（不问人，直接拒）。 */
    WORKSPACE("WORKSPACE"),

    /** 不设额外限制，完全交给审批闸门（默认，等于历史行为）。 */
    FULL("FULL");

    companion object {
        fun fromStorage(raw: String?): SandboxLevel =
            values().firstOrNull { it.storageKey == raw } ?: FULL
    }
}

/** 沙箱判定结果。 */
enum class SandboxDecision { ALLOW, BLOCK }

/** 沙箱闸门：纯函数，输入等级与命令分档，输出放行或挡下。 */
object SandboxGate {

    fun decide(level: SandboxLevel, danger: ShellDanger): SandboxDecision = when (level) {
        SandboxLevel.FULL -> SandboxDecision.ALLOW
        SandboxLevel.WORKSPACE ->
            if (danger == ShellDanger.DESTRUCTIVE) SandboxDecision.BLOCK else SandboxDecision.ALLOW
        SandboxLevel.READ_ONLY ->
            if (danger == ShellDanger.SAFE) SandboxDecision.ALLOW else SandboxDecision.BLOCK
    }

    /** 被挡时给人看的一句话；放行返回 null，调用方据此决定是"挡下"还是"继续走审批"。 */
    fun blockMessage(level: SandboxLevel, danger: ShellDanger): String? =
        if (decide(level, danger) == SandboxDecision.ALLOW) {
            null
        } else {
            "沙箱等级 [" + level.storageKey + "] 不允许执行 " + danger.name + " 级命令"
        }
}

/**
 * 上下文压缩的触发与保留预算。
 *
 * 预算 = 轮数 × 2 条消息（一问一答）。阈值只是"多满才开始压"，压缩目标固定回落到预算的
 * [KeepPercent]%，于是同一条历史不会被每轮反复修剪成碎片。
 */
object CompressPolicy {
    const val MinThreshold = 0.3f
    const val MaxThreshold = 0.95f
    const val DefaultThreshold = 0.8f

    /** 压缩后保留预算的百分比：60% 留出回旋空间，避免每轮都修剪成碎片。 */
    const val KeepPercent = 60

    /** 至少保留两条（一问一答），否则压缩会把对话直接切成空。 */
    const val MinKeptMessages = 2

    fun clampThreshold(raw: Float): Float =
        if (raw.isNaN()) DefaultThreshold else raw.coerceIn(MinThreshold, MaxThreshold)

    /** 上下文预算（消息条数）：轮数 × 2。 */
    fun messageBudget(maxTurns: Int): Int = maxTurns.coerceAtLeast(1) * 2

    /**
     * 触发压缩的历史条数：预算 × 阈值，向上取整，至少 2 条。
     *
     * 减去 1e-6 再取整：Float 的 0.8f 转 Double 是 0.8000000119...，直接 ceil 会把
     * "正好 80%" 算成下一条 —— 于是 60 条预算下阈值 0.8 的触发点会从 48 漂到 49。
     */
    fun triggerAt(maxTurns: Int, threshold: Float): Int {
        val budget = messageBudget(maxTurns)
        return ceil(budget * clampThreshold(threshold).toDouble() - 1e-6).toInt()
            .coerceAtLeast(MinKeptMessages)
    }

    /** 压缩后保留的条数：预算的 [KeepPercent]%，整数运算，不受浮点误差影响。 */
    fun keepMessages(maxTurns: Int): Int {
        val budget = messageBudget(maxTurns)
        return ((budget * KeepPercent + 99) / 100).coerceAtLeast(MinKeptMessages)
    }

    /** 是否该压缩：严格大于触发点才算满，边界值不压。 */
    fun shouldCompress(historySize: Int, maxTurns: Int, threshold: Float): Boolean =
        historySize > triggerAt(maxTurns, threshold)
}

/** 一次手势 / 侧键事件。 */
enum class GestureEvent { LONG_PRESS, DOUBLE_TAP, SIDE_KEY_SHORT, SIDE_KEY_LONG }

/** 手势 / 侧键能绑定的动作。 */
enum class GestureAction(val storageKey: String) {
    NONE("NONE"),

    /** 终止正在跑的 agent 任务。 */
    STOP_TURN("STOP_TURN"),

    /** 在已缓存的模型清单里切到下一个。 */
    SWITCH_MODEL("SWITCH_MODEL"),

/**
     * 忘掉本会话"记住的命令"清单，之后每条命令重新走审批。
     *
     * 名字即动作，不藏在别的语义下面：曾经把它挂在"新建会话"上，结果是用户按一个手势
     * 想开新会话，却顺手丢掉了自己的审批放行记录 —— 隐式破坏性副作用，已改为独立动作。
     */
    FORGET_REMEMBERED("FORGET_REMEMBERED");

    companion object {
        fun fromStorage(raw: String?): GestureAction =
            values().firstOrNull { it.storageKey == raw } ?: NONE
    }
}

/** 四条绑定的不可变快照。 */
data class GestureBindings(
    val longPress: GestureAction = GestureAction.NONE,
    val doubleTap: GestureAction = GestureAction.NONE,
    val sideKeyShort: GestureAction = GestureAction.NONE,
    val sideKeyLong: GestureAction = GestureAction.NONE
) {
    fun bindingFor(event: GestureEvent): GestureAction = when (event) {
        GestureEvent.LONG_PRESS -> longPress
        GestureEvent.DOUBLE_TAP -> doubleTap
        GestureEvent.SIDE_KEY_SHORT -> sideKeyShort
        GestureEvent.SIDE_KEY_LONG -> sideKeyLong
    }
}

/** 手势闸门：把事件解析成动作，并给出"这个动作是不是终止"的唯一判据。 */
object GestureBindingGate {

    fun actionFor(bindings: GestureBindings, event: GestureEvent): GestureAction =
        bindings.bindingFor(event)

    fun stopsCurrentTurn(action: GestureAction): Boolean = action == GestureAction.STOP_TURN

    /** 该绑定表里是否存在"终止当前任务"的落点：审批期只需要这一条判定。 */
    fun anyStopsCurrentTurn(bindings: GestureBindings): Boolean =
        GestureEvent.values().any { stopsCurrentTurn(bindings.bindingFor(it)) }
}

/**
 * 手势动作的一次性投递队列：输入层投递，[com.example.myapplication.agent.AgentDispatcher]
 * 在每个工具循环开头消费。用队列而不是布尔量，是因为"终止"必须只生效一次 ——
 * 布尔量会让下一个任务在开头就被上一次的长按打断。
 */
object GestureRequestQueue {
    private val pending = ConcurrentLinkedQueue<GestureAction>()

    fun post(action: GestureAction) {
        if (action != GestureAction.NONE) pending.add(action)
    }

    /** 取出并清空全部待处理动作。 */
    fun drain(): List<GestureAction> {
        val out = mutableListOf<GestureAction>()
        while (true) {
            val next = pending.poll() ?: break
            out.add(next)
        }
        return out
    }

    /**
     * 只问"这一批里有没有终止"，同批其余动作会被一并丢弃。
     *
     * 调度方不许用它当消费点 —— 那正是上一版的缺口：切模型 / 忘掉清单被 drain 出来直接扔了。
     * 消费整批请走 [GestureRuntime.applyDrained]。
     */
    fun consumeStopTurn(): Boolean = drain().any { GestureBindingGate.stopsCurrentTurn(it) }

    fun clear() {
        pending.clear()
    }
}

/** 一次手势动作的执行结果：模型切换会带上切换后的模型名，其余为 null。 */
data class GestureOutcome(val action: GestureAction, val modelName: String? = null) {
    /** 这次动作是否真的改变了什么 —— NONE 永远是 false。 */
    val effective: Boolean get() = action != GestureAction.NONE
}

/**
 * 一批 drain 的结算结果。
 *
 * [stopTurn] 是"结束当前 turn"的唯一信号，[outcomes] 是同一批里其余动作的真实执行结果。
 * 队列是取走语义，同一批每个动作只会被执行一次，调用方不需要自己去重。
 */
data class GestureDrainOutcome(
    val stopTurn: Boolean,
    val outcomes: List<GestureOutcome>
) {
    /** 这一批里除终止之外，是否真的执行成了什么。 */
    val appliedOthers: Boolean get() = outcomes.any { it.effective }
}

/**
 * 手势动作的唯一执行点。
 *
 * 三个动作都必须落到一处真实状态上，否则就是新一批假开关：
 * - [GestureAction.STOP_TURN] 投进 [GestureRequestQueue]，由 AgentDispatcher 消费
 *   （任务起步时结算一次、每个工具循环再结算一次）；
 * - [GestureAction.SWITCH_MODEL] 改写 [AgentPreferences.modelName]，下一次请求就用新模型；
 * - [GestureAction.FORGET_REMEMBERED] 清空会话级记住命令清单（[AgentPreferences.forgetRememberedShellCommands]），
 *   动作名与副作用一一对应，不做隐式破坏。
 */
class GestureRuntime(private val preferences: AgentPreferences) {

    fun perform(action: GestureAction): GestureOutcome = when (action) {
        GestureAction.NONE -> GestureOutcome(GestureAction.NONE)

        GestureAction.STOP_TURN -> {
            GestureRequestQueue.post(GestureAction.STOP_TURN)
            GestureOutcome(GestureAction.STOP_TURN)
        }

        GestureAction.SWITCH_MODEL -> {
            val next = nextModel(preferences.cachedRemoteModels, preferences.modelName)
            if (next != null) preferences.modelName = next
            GestureOutcome(GestureAction.SWITCH_MODEL, next)
        }

        GestureAction.FORGET_REMEMBERED -> {
            preferences.forgetRememberedShellCommands()
            GestureOutcome(GestureAction.FORGET_REMEMBERED)
        }
    }

    /** 按事件解析绑定再执行：事件 → 动作的映射只走 [GestureBindingGate] 一处。 */
    fun perform(bindings: GestureBindings, event: GestureEvent): GestureOutcome =
        perform(GestureBindingGate.actionFor(bindings, event))

    /**
     * 消费侧：把 [GestureRequestQueue.drain] 出来的整批动作真正执行掉。
     *
     * 顺序固定为「先执行其余动作，再判终止」：
     * - [GestureAction.STOP_TURN] 的副作用是调度方结束当前 turn。一旦先结束，同一批里的切模型 /
     *   忘掉清单就再没有执行机会 —— 那与"按了键没反应"的假开关是同一类问题；
     * - 先执行其余动作不削弱终止语义：返回的 [GestureDrainOutcome.stopTurn] 为 true 时调用方仍然
     *   立刻结束 turn，与旧行为（consumeStopTurn 命中即返回）逐字一致。
     *
     * STOP_TURN 不走 [perform]：那里是输入侧的"投递进队列"，消费侧再投一次等于自己把队列填满，
     * 终止就成了永不消失的开关，会连带打断下一个任务。
     */
    fun applyDrained(actions: List<GestureAction>): GestureDrainOutcome {
        val outcomes = mutableListOf<GestureOutcome>()
        var stopTurn = false
        for (action in actions) {
            when (action) {
                // 入队时已滤掉 NONE；真漏进来也必须是零副作用
                GestureAction.NONE -> Unit
                GestureAction.STOP_TURN -> stopTurn = true
                GestureAction.SWITCH_MODEL, GestureAction.FORGET_REMEMBERED -> outcomes.add(perform(action))
            }
        }
        return GestureDrainOutcome(stopTurn, outcomes)
    }

    companion object {
        /** 在缓存模型清单里取当前模型的下一个；只有一个模型或清单为空时返回 null（无动作可做）。 */
        fun nextModel(cachedModels: String, current: String): String? {
            val models = cachedModels.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (models.size < 2) return null
            val index = models.indexOf(current)
            if (index < 0) return models.first()
            return models[(index + 1) % models.size]
        }
    }
}

