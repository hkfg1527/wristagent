package com.example.myapplication.presentation.ui.settings

/**
 * 逐行浮动的时间学：纯整数/浮点运算，不依赖 Compose，便于单测覆盖边界。
 * 延迟必须随行号线性增长，但要在封顶值处停住 —— 否则第 30 行要等 1.2 秒才动。
 */

/** 进场：相邻行错开 40ms。 */
const val SettingsRowEnterStepMs = 40

/** 进场封顶 360ms：360/40 之后的行同批起跑，整体入场不超过半秒。 */
const val SettingsRowEnterCapMs = 360

/** 退场：相邻行只错开 25ms，比进场更急，不让用户等。 */
const val SettingsRowExitStepMs = 25

/** 退场封顶 150ms。 */
const val SettingsRowExitCapMs = 150

/** 单行退场时长 140ms。 */
const val SettingsRowExitDurationMs = 140

/** 一次完整退场的总时长：最后起跑那行的延迟 + 单行时长，用于切换前的兜底等待。 */
const val SettingsRowExitTotalMs = SettingsRowExitCapMs + SettingsRowExitDurationMs

/** 容器级淡入淡出上限 100ms：只盖住切换瞬间，真正的动效交给逐行浮动。 */
const val SettingsScreenVeilMs = 100

/**
 * 第 [index] 行的动画启动延迟（毫秒）。
 *
 * - [index] <= 0 或 [stepMs] <= 0 或 [maxMs] <= 0 时返回 0，调用方无需再判空；
 * - 结果单调不减且被 [maxMs] 封顶，行数再多也不会把等待时间线性拉长。
 */
fun staggerDelayMs(index: Int, stepMs: Int, maxMs: Int = SettingsRowEnterCapMs): Int {
    if (index <= 0 || stepMs <= 0 || maxMs <= 0) return 0
    val raw = index.toLong() * stepMs.toLong()
    return if (raw >= maxMs.toLong()) maxMs else raw.toInt()
}

/**
 * 退场进度采样：[elapsedMs] 从 0 开始的整场退场动画里，本行自己的 0..1 进度。
 * 未到本行延迟时为 0，超过延迟 + [durationMs] 后饱和为 1，供 graphicsLayer 直接读取。
 */
fun exitFraction(elapsedMs: Float, startMs: Int, durationMs: Int): Float {
    if (durationMs <= 0) return if (elapsedMs >= startMs) 1f else 0f
    val local = (elapsedMs - startMs) / durationMs.toFloat()
    return if (local <= 0f) 0f else if (local >= 1f) 1f else local
}
