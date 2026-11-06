package com.example.myapplication.presentation.ui

import com.example.myapplication.agent.contract.ConversationBlock
import kotlin.math.abs
import kotlin.math.min

/**
 * 镜头（列表跟随）的限速曲线，纯逻辑可单测。
 *
 * 只用在一件事上：把"新回复的第一行"**限速**挪到可读区顶部（对齐阶段）。
 *  1. **速度由剩余距离决定**：离目标越远越快，越近越慢（远快近慢）；
 *  2. **加速有上限** [ACCEL]：从静止起步是渐进上去的（ease-in），不会一上来就窜；
 *  3. **减速是自然发生的**：接近目标时剩余距离变小 → 目标速度随之变小（ease-out）；
 *  4. **死区** [DEAD_ZONE_DP]：|剩余| 小于死区就当作到位、速度归零 ——
 *     layoutInfo 反推的位置永远有几像素抖动，没有死区就是永动慢挪。
 *
 * 距离带符号：[targetSpeed] 对负的剩余距离给负速度，于是"目标在上方"也能反向挪回来。
 * 全程没有"最慢速度地板"：很近的距离就该给极小的速度，而不是被抬到某个固定值。
 */
object CameraCurve {

    /** 每 1px 剩余距离对应的目标速度（px/s per px）。 */
    const val SPEED_PER_PX = 9.0f

    /** 最快速度（px/s）：只有非常远的距离才会跑到这个上限。 */
    const val MAX_SPEED = 2600f

    /** 加速度上限（px/s²）：起步渐进的来源；6000 时从静止到 2600px/s 约 0.43s，视觉上是一条加速曲线而不是瞬移。 */
    const val ACCEL = 6000f

    /** 死区（dp）：|剩余距离| 小于它就当作到位。 */
    const val DEAD_ZONE_DP = 8f

    /** 对齐目标：可读区顶部在顶部时钟遮罩（34dp）之下再留 8dp 余量。 */
    const val FOCUS_TOP_MARGIN_DP = 8f

    /** 死区换算成像素（密度由调用方传入，纯逻辑层不读 Density）。 */
    fun deadZonePx(density: Float): Float = DEAD_ZONE_DP * density

    /**
     * 由剩余距离算目标速度：远快近慢、带方向、夹在 ±[MAX_SPEED] 内、死区内为零。
     * [remainingPx] > 0 表示目标在下方（正向滚动，把内容往上推）；< 0 表示目标在上方（反向滚动）。
     */
    fun targetSpeed(remainingPx: Float, deadZonePx: Float = 0f): Float {
        val magnitude = abs(remainingPx)
        if (magnitude <= deadZonePx) return 0f
        val speed = min(magnitude * SPEED_PER_PX, MAX_SPEED)
        return if (remainingPx < 0f) -speed else speed
    }

    /** 当前速度向目标速度靠拢，每帧最多变化 [ACCEL] × dt（这就是 ease-in 与 ease-out 的实现）。 */
    fun nextSpeed(currentSpeed: Float, targetSpeed: Float, dtSeconds: Float): Float {
        if (dtSeconds <= 0f) return currentSpeed
        val maxDelta = ACCEL * dtSeconds
        val delta = targetSpeed - currentSpeed
        return if (abs(delta) <= maxDelta) targetSpeed else currentSpeed + maxDelta * if (delta > 0f) 1f else -1f
    }

    /** 这一帧该走多远：速度 × 时间，且绝不越过剩余距离（越过就会回弹，两个方向都不许）。 */
    fun stepPx(speed: Float, dtSeconds: Float, remainingPx: Float): Float {
        val step = speed * dtSeconds
        return if (remainingPx >= 0f) step.coerceIn(0f, remainingPx) else step.coerceIn(remainingPx, 0f)
    }

    /**
     * 对齐剩余距离（px，带符号）：目标回合卡片**顶边**到"可读区顶部"的像素差。
     *
     * [itemCenterOffsetPx] / [itemSizePx] 直接取 ScalingLazyListItemInfo.offset / size ——
     * Wear 的 offset 是**相对视口中线**的中心偏移（ItemCenter 锚点），
     * 所以卡片顶边在视口里的位置 = 视口高/2 + offset − size/2。
     * 这里不做"(n−1)×条目间距"修正：间距已经逐项累加进 offset（ScalingLazyListState 里
     * 每走一项都会加上 gapBetweenItemsPx），再减一次就是双重扣减；而且用顶边定位本来也不累加条目高度。
     */
    fun focusRemainingPx(
        itemCenterOffsetPx: Float,
        itemSizePx: Float,
        viewportHeightPx: Float,
        targetTopPx: Float
    ): Float {
        val itemTopPx = viewportHeightPx / 2f + itemCenterOffsetPx - itemSizePx / 2f
        return itemTopPx - targetTopPx
    }

    /**
     * 条目**底边**在视口中的位置（px）。
     *
     * 阅读锚点取的就是这条线：一个助手回合整体是同一个列表条目，正文块从卡片顶边依次往下排，
     * 因此"最后一条正文的底边" = 该条目底边。取反方向（顶边）会把"整轮刚出现"误判成
     * "锚点跑到阅读带上面"，镜头就会反向乱拉。
     */
    fun itemBottomPx(
        itemCenterOffsetPx: Float,
        itemSizePx: Float,
        viewportHeightPx: Float
    ): Float = viewportHeightPx / 2f + itemCenterOffsetPx + itemSizePx / 2f

    /**
     * 目标项还没进入可视区时的搜寻距离（带符号）：给到 [MAX_SPEED] 对应的距离，
     * 于是"先限速找过去"不会突然瞬移，进可视区后再按 [focusRemainingPx] 收敛。
     */
    fun seekRemainingPx(viewportHeightPx: Float, targetBelow: Boolean): Float {
        val distance = (MAX_SPEED / SPEED_PER_PX).coerceAtLeast(viewportHeightPx)
        return if (targetBelow) distance else -distance
    }

    // ---------------------------------------------------------------------
    // 底栏形变 → 可读区变化：按钮长成完整主键、扁审批条、还是收成贴底弧条，
    // 它遮住的屏幕底部高度完全不同。镜头必须知道这件事，否则"按钮在变形、
    // 正文却按旧的可读区一直往前推"——用户看到的就是镜头不跟着按钮走。
    // ---------------------------------------------------------------------

    /** 重新对位的触发阈值（dp）：形变超过这个量才算"可读区真的变了"。 */
    const val REALIGN_THRESHOLD_DP = 0.5f

    /**
     * 底栏在 y 方向实际占住的高度（dp）= 视觉块高度 × 收展进度 + 底距。
     *
     * 收展进度 1 = 完整按钮（块高度 [expandedBlockHeightDp]），0 = 贴底弧条（只剩
     * [collapsedHeightDp] 那么薄的一条）；中间按同一条时间线插值 —— 与
     * [com.example.myapplication.presentation.ui.BottomInteractiveBlock] 里主键尺寸
     * 用的就是同一个进度值，两处不允许出现第二条时间线。
     */
    fun barFootprintDp(
        expandedBlockHeightDp: Float,
        bottomInsetDp: Float,
        collapsedHeightDp: Float,
        collapseProgress: Float
    ): Float {
        val p = collapseProgress.coerceIn(0f, 1f)
        val visual = collapsedHeightDp + (expandedBlockHeightDp - collapsedHeightDp) * p
        return visual + bottomInsetDp
    }

    /**
     * 按钮形变后镜头需要额外补的正向滚动量（px）。
     *
     * 只在"占得更多"（展开）方向补：多出来的 Δ 会把原本可读的底部几行压到按钮下面，
     * 正向滚 Δ 才能把它们重新抬到按钮上沿之上。
     * 收起方向补 0 —— 底部空出来的地方只是把更多内容露出来，此时再滚就是推着人走。
     */
    fun realignCompensationPx(previousFootprintPx: Float, currentFootprintPx: Float): Float =
        (currentFootprintPx - previousFootprintPx).coerceAtLeast(0f)

    /** 形变是否已经大到值得重新对位：亚像素抖动不该反复抢镜头。 */
    fun shouldRealign(
        previousFootprintPx: Float,
        currentFootprintPx: Float,
        thresholdPx: Float
    ): Boolean = abs(currentFootprintPx - previousFootprintPx) >= thresholdPx.coerceAtLeast(0f)

    /**
     * 旧版"对齐到回合顶边"的准入口径（镜头已改用 [readingTurnKey] + 阅读锚点，本函数只作历史契约保留）。
     *
     * 什么时刻该做对齐：最后一项是**紧跟用户提问的助手回合**，且这一回合已经有正文。
     * 只有欢迎语、还没吐字（只有思考/工具胶囊）、正文还是空白，都不算"回复开始出现"。
     */
    fun replyTurnKey(items: List<RenderableConversationItem>): String? {
        if (items.size < 2) return null
        val last = items.last()
        if (last !is RenderableConversationItem.AssistantTurn) return null
        if (items[items.size - 2] !is RenderableConversationItem.UserPrompt) return null
        val hasBody = last.blocks.any { it is ConversationBlock.Text && it.content.isNotBlank() }
        return if (hasBody) last.key else null
    }

    /**
     * 镜头跟随口径：本回合是否已经有**可读内容**。思考胶囊、工具胶囊与正文都算一行正文 ——
     * 只认正文会让"先出思考/先调工具"的回合整段没有镜头（用户看到的是一条不动的死屏）。
     */
    fun readingTurnKey(items: List<RenderableConversationItem>): String? {
        if (items.size < 2) return null
        val last = items.last()
        if (last !is RenderableConversationItem.AssistantTurn) return null
        if (items[items.size - 2] !is RenderableConversationItem.UserPrompt) return null
        return if (hasReadableContent(last.blocks)) last.key else null
    }

    /** 回合里是否已经有可读内容：一条非空正文、一段非空思考、或任意一个工具胶囊。 */
    fun hasReadableContent(blocks: List<ConversationBlock>): Boolean = blocks.any { block ->
        when (block) {
            is ConversationBlock.Text -> block.content.isNotBlank()
            is ConversationBlock.Thought -> block.reasoning.isNotBlank()
            is ConversationBlock.ToolCall -> true
        }
    }
}

/**
 * 提词器阅读节奏：极慢下滚速度由"人类默读下限"反推，禁止把这几个数字散落在 UI 里。
 *
 * 《义务教育语文课程标准》默读 ≥300 字/分钟为下限，成年人快读 500~800 字/分钟；
 * 取可舒适快读 500 字/分钟 × 0.7 ≈ 350 字/分钟 ≈ 5.8 字/秒；
 * 版面 12sp 正文、阅读宽约 190dp 时每行约 14 字、行高约 16dp：
 * 每行耗时 = 14 / 5.8 ≈ 2.41s，单行 16dp ⇒ 16 / 2.41 ≈ 6.6dp/s（≈13.3px/s @2.0）。
 */
object ReadingPace {

    /** 可舒适快读的基准字速（字/分钟）。 */
    const val MAX_READ_CHARS_PER_MIN = 500f

    /** 相对基准的降速系数：用户要求按 0.7 走，宁可慢一点也不要催。 */
    const val PACE_FACTOR = 0.7f

    /**
     * 界面实际使用的倍率：真机上手感 0.7 倍读速太慢（一屏要走 14 秒），用户要求再快一倍。
     * 保留 [PACE_FACTOR] 作为“人类读速模型”的基准，这里只做产品级提速，两者语义分开。
     */
    const val USER_SPEED_MULTIPLIER = 5.2f

    /** 每行字数：12sp 正文、约 190dp 阅读宽的实测值。 */
    const val CHARS_PER_LINE = 14

    /** 行高（dp）：正文行距扣掉行内留白后的视觉行高。 */
    const val LINE_HEIGHT_DP = 16f

    /** 每秒可读字数。 */
    fun charsPerSec(paceFactor: Float = PACE_FACTOR): Float =
        MAX_READ_CHARS_PER_MIN * paceFactor / 60f

    /** 读完一行的耗时（秒）。 */
    fun secondsPerLine(paceFactor: Float = PACE_FACTOR): Float = CHARS_PER_LINE / charsPerSec(paceFactor)

    /** 恒定下滚速度（dp/s）：一行的高度除以读一行的时间。 */
    fun panSpeedDpPerSec(
        paceFactor: Float = PACE_FACTOR,
        speedMultiplier: Float = 1f
    ): Float = LINE_HEIGHT_DP / secondsPerLine(paceFactor) * speedMultiplier

    /** 恒定下滚速度（px/s）。 */
    fun panSpeedPxPerSec(
        density: Float,
        paceFactor: Float = PACE_FACTOR,
        speedMultiplier: Float = 1f
    ): Float = panSpeedDpPerSec(paceFactor, speedMultiplier) * density
}

/** 提词器镜头阶段：空闲 / 对齐到本回合开头 / 恒定慢速下滚。 */
enum class ReadingCameraPhase { Idle, AlignToTurnStart, AutoPan }

/** 提词器镜头事件。 */
enum class ReadingCameraEvent {
    /** 回合首次出现正文：新一轮开始，重新对齐。 */
    TurnBodyAppeared,

    /** 对齐已经进入死区（本帧位移为 0）。 */
    Aligned,

    /** 到达内容末尾（无法再向前滚动）。 */
    ReachedEnd,

    /** 用户手动滚动（表冠或手指，任一方向）。 */
    UserScrolled,

    /** 强制态打断：审批、停止、录音、输入弹窗、抽屉。 */
    Interrupted
}

/**
 * 提词器镜头状态机（纯逻辑，可单测）。
 *
 * 规则：
 *  - 回合首次出现正文 → AlignToTurnStart；
 *  - 对齐完成 → AutoPan，以恒定慢速一路下滚，**不再计算剩余距离提速**；
 *  - 到末尾 / 用户滚动 / 强制态 → 回到 Idle，并且**本轮不再自启**（isStoppedForTurn，
 *    只有新一轮的 TurnBodyAppeared 才会解除）；
 *  - 一轮结束不回弹、不追尾。
 */
class ReadingCamera {

    var phase: ReadingCameraPhase = ReadingCameraPhase.Idle
        private set

    /** 本轮是否已经被用户或强制态叫停（叫停后不再自动恢复）。 */
    var isStoppedForTurn: Boolean = false
        private set

    fun onEvent(event: ReadingCameraEvent): ReadingCameraPhase {
        when (event) {
            ReadingCameraEvent.TurnBodyAppeared -> {
                isStoppedForTurn = false
                phase = ReadingCameraPhase.AlignToTurnStart
            }

            ReadingCameraEvent.Aligned ->
                phase = if (!isStoppedForTurn && phase == ReadingCameraPhase.AlignToTurnStart) {
                    ReadingCameraPhase.AutoPan
                } else {
                    ReadingCameraPhase.Idle
                }

            ReadingCameraEvent.ReachedEnd -> phase = ReadingCameraPhase.Idle

            ReadingCameraEvent.UserScrolled, ReadingCameraEvent.Interrupted -> {
                isStoppedForTurn = true
                phase = ReadingCameraPhase.Idle
            }
        }
        return phase
    }
}
