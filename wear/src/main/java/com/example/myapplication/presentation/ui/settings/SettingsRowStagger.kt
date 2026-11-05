package com.example.myapplication.presentation.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.myapplication.presentation.ui.theme.Wear
import kotlinx.coroutines.delay

/** 根设置列表参与逐行动画的内容行数：标题 + 3 个组标题 + 9 个分区入口 + 末尾"完成返回"。 */
const val SettingsStaggeredRowCount = 14

/** 进场位移 24dp：从下方浮上来。 */
private val RowEnterOffset = Wear.Space.Xl

/** 退场位移 16dp：向下沉出。 */
private val RowExitOffset = Wear.Space.Lg

private const val RowEnterDampingRatio = 0.75f
private const val RowEnterStiffness = 320f

/**
 * 「本屏内每行是否已经播过入场」的记账本。
 *
 * ScalingLazyColumn 只保留可见项，滚出再滚回会重新组合；用屏幕级的集合而不是行内 remember，
 * 才能保证同一行在滚动复用后不再重播。集合是普通 MutableSet（不是 snapshot state）：
 * 它在组合期只写不读，不参与快照观察，也不会触发重组。
 */
@Stable
class SettingsRowStaggerState internal constructor() {
    private val playedIndices = mutableSetOf<Int>()

    /** 首次调用返回 true（该行可以播入场），之后恒为 false。 */
    internal fun claimEnter(index: Int): Boolean = playedIndices.add(index)

    /** 诊断用：该行是否已播过入场。 */
    fun hasPlayedEnter(index: Int): Boolean = index in playedIndices
}

@Composable
fun rememberSettingsRowStaggerState(): SettingsRowStaggerState = remember { SettingsRowStaggerState() }

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
 */
@Composable
fun SettingsStaggeredRow(
    index: Int,
    stagger: SettingsRowStaggerState,
    exit: SettingsExitController,
    rowCount: Int = SettingsStaggeredRowCount,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val playsEnter = remember(index) { stagger.claimEnter(index) }
    val enter = remember(index) { Animatable(if (playsEnter) 0f else 1f) }

    LaunchedEffect(index) {
        if (playsEnter) {
            val startMs = staggerDelayMs(index, SettingsRowEnterStepMs, SettingsRowEnterCapMs)
            if (startMs > 0) delay(startMs.toLong())
            enter.animateTo(1f, spring(dampingRatio = RowEnterDampingRatio, stiffness = RowEnterStiffness))
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
