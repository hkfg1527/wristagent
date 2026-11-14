package com.example.myapplication.presentation.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay

/** 根设置列表参与逐行动画的内容行数：标题 + 3 个组标题 + 9 个分区入口 + 末尾"完成返回"。 */
const val SettingsStaggeredRowCount = 14

/** 进场只上移 4dp：保留连续性，不产生飞入感。 */
private val RowEnterOffset = Wear.Space.Xs

/** 退场位移 16dp：向下沉出。 */
private val RowExitOffset = Wear.Space.Lg

/**
 * 入场记账本 + 入场窗口。
 *
 * 入场只属于「页面刚进来时首屏看得见的那批行」。ScalingLazyColumn 只保留可见项，屏幕外的行是
 * **滚动把它带进视口时**才第一次组合的：只要第一次组合就播入场，手指一滑就会看到一串行同时从
 * +24dp 处淡入浮起（表冠一格一行慢慢过，症状不明显），这正是「手指滑动时组件全部飞起来」。
 *
 * 所以窗口在页面首帧之后立刻关闭（[closeEntry]）：此后第一次组合的行直接落在终态，不播位移也不播淡入。
 * 集合是普通 MutableSet（不是 snapshot state）：它在组合期只写不读，不参与快照观察，也不触发重组。
 */
@Stable
class SettingsRowStaggerState internal constructor() {
    private val playedIndices = mutableSetOf<Int>()

    private var entryOpen = true

    /**
     * 该行能不能播入场：返回 true 当且仅当入场窗口还开着，且这个 index 是第一次组合。
     *
     * 首个条件把「滚动带进视口的行」全部挡在外面；第二个条件保证同一行滚出再滚回不重播。
     */
    internal fun claimEnter(index: Int): Boolean {
        val firstComposition = playedIndices.add(index)
        return entryOpen && firstComposition
    }

    /** 关闭入场窗口：页面首屏已经组合完成，之后进来的行都是滚动带来的，不应该再有入场位移。 */
    internal fun closeEntry() {
        entryOpen = false
    }

    /** 诊断用：入场窗口是否还开着。 */
    internal fun isEntryOpen(): Boolean = entryOpen

    /** 诊断用：该行是否已经组合过（不代表它播过入场）。 */
    fun hasPlayedEnter(index: Int): Boolean = index in playedIndices
}

/**
 * 页面级入场窗口：两帧之后关闭。
 *
 * 首屏可见项在首帧的 measure 里就组合完了（lazy 项的组合发生在 measure 阶段），
 * 因此等满两帧再关窗，既不会切掉首屏那批行的入场，又保证之后才组合的行（一定是滚动进来的）
 * 不再播入场。
 */
@Composable
fun rememberSettingsRowStaggerState(): SettingsRowStaggerState {
    val state = remember { SettingsRowStaggerState() }
    LaunchedEffect(state) {
        withFrameNanos { }
        withFrameNanos { }
        state.closeEntry()
    }
    return state
}

/**
 * 退场调度器：把"离开设置页"拆成两段 —— 先让各行沉下去，动画跑完才真正切屏。
 *
 * [elapsedMs] 是一条 0 → [SettingsRowExitTotalMs] 的线性时间轴，每行按自己的延迟从这条轴上
 * 采样 [exitFraction]。它只被 graphicsLayer 读取，因此退场不触发任何重组。
 */
@Stable
class SettingsExitController internal constructor() {
    private var exiting by mutableStateOf(false)

    /** 退场是否正在进行：用于禁用下滑手势、吞掉重复 BACK。 */
    val isExiting: Boolean get() = exiting

    private val elapsed = Animatable(0f)

    /** 退场时间轴当前位置（毫秒），供各行在绘制阶段采样。 */
    val elapsedMs: Float get() = elapsed.value

    private var pendingAction: (() -> Unit)? = null

    /** 请求退场：已在退场中则忽略；返回本次是否真的启动了退场。 */
    internal fun begin(action: () -> Unit): Boolean {
        if (exiting) return false
        pendingAction = action
        exiting = true
        return true
    }

    internal suspend fun run() {
        // 上一次退场跑完时 elapsed 停在总时长上：不归零就直接 animateTo(同一个值)
        // 会立刻返回，第二次退场既没有动画，页面也会停在 alpha=0 的透明态。
        elapsed.snapTo(0f)
        elapsed.animateTo(
            targetValue = SettingsRowExitTotalMs.toFloat(),
            animationSpec = tween(durationMillis = SettingsRowExitTotalMs, easing = LinearEasing)
        )
        val action = pendingAction
        pendingAction = null
        exiting = false
        action?.invoke()
    }
}

@Composable
fun rememberSettingsExitController(): SettingsExitController {
    val controller = remember { SettingsExitController() }
    LaunchedEffect(controller.isExiting) {
        if (controller.isExiting) controller.run()
    }
    return controller
}

/**
 * 单行动画外壳：位移与透明度全部走 [Modifier.graphicsLayer]，只在绘制阶段读写，
 * 不进组合、不进布局，滚动时也不会有额外开销。
 *
 * [index] 是内容行序号，用于算进场/退场延迟；分隔线传入其上一行的序号，跟着上一行一起浮。
 * 入场是否播放由 [SettingsRowStaggerState] 的窗口决定，滚动带进来的行一律直接落位。
 */
@Composable
fun SettingsStaggeredRow(
    index: Int,
    stagger: SettingsRowStaggerState,
    exit: SettingsExitController,
    rowCount: Int = SettingsStaggeredRowCount,
    /**
     * 是否给这一行挂逐行浮动/淡入的图层。
     *
     * 卡片档里「分区行」那一整块高 3000+ px：给它挂 graphicsLayer 意味着每次入场动画
     * 都要绘制一块 432×3113 的位图（atrace 里实测到 `drawLayer [graphicsLayer] 432.0 x 3113.0`），
     * 这是目前定位到的最大离群帧来源。这一块因此不参与逐行动画，只在需要时关掉。
     */
    animate: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!animate) {
        Box(modifier = modifier) { content() }
        return
    }
    val playsEnter = remember(index) { stagger.claimEnter(index) }
    val enter = remember(index) { Animatable(if (playsEnter) 0f else 1f) }

    LaunchedEffect(index) {
        if (playsEnter) {
            val startMs = staggerDelayMs(index, SettingsRowEnterStepMs, SettingsRowEnterCapMs)
            if (startMs > 0) delay(startMs.toLong())
            enter.animateTo(1f, Wear.Motion.base())
        }
    }

    // 退场反向起跑：末行先走，标题最后走。行数由调用方传入，子页行数与根页不同也能正确反向。
    val exitStartMs = remember(index, rowCount) {
        staggerDelayMs(rowCount - 1 - index, SettingsRowExitStepMs, SettingsRowExitCapMs)
    }

    val density = LocalDensity.current
    val enterOffsetPx = with(density) { RowEnterOffset.toPx() }
    val exitOffsetPx = with(density) { RowExitOffset.toPx() }

    Box(
        modifier = modifier.graphicsLayer {
            val enterValue = enter.value
            val exitValue = exitFraction(exit.elapsedMs, exitStartMs, SettingsRowExitDurationMs)
            translationY = (1f - enterValue) * enterOffsetPx + exitValue * exitOffsetPx
            alpha = enterValue * (1f - exitValue)
        }
    ) {
        content()
    }
}
