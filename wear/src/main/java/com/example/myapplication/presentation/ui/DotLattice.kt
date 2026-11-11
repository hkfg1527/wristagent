package com.example.myapplication.presentation.ui

/**
 * 主键按压 → 点阵骨架的纯数学层。
 *
 * 只回答两个问题："这一点被推开多远" 与 "这一点被放大多少倍"，不引用任何 Compose 类型、
 * 不持有状态、不分配临时容器，因此可以直接被 JVM 单测覆盖（见 DotLatticeTest）。
 *
 * 单位约定（两个 amplitude 语义不同，故意分开命名以免误用）：
 *  - [displacement] / [displacementX] / [displacementY] 的 amplitudePx 是**像素**位移；
 *  - [scale] 的 amplitude 是**倍率增量**（0.35f 表示最多放大成 1.35 倍）。
 * 两者共用同一个平滑衰减权重 (1-t)²，t = distancePx / falloffPx 夹在 0..1：
 * 原点处权重 1、falloffPx 及以外权重 0，中间无台阶。
 *
 * 绘制热路径不调用返回 Pair 的 [displacement]（每点每帧会分配一个对象），
 * 而是取 [displacementX] / [displacementY] 两个标量函数，数学完全相同。
 */
object DotLattice {

    /**
     * 按压影响半径（单位：一个网格间距）。
     *
     * 3.5 格的依据：主键宽 52dp ≈ 3.25 格，影响区至少要盖住键自身的投影再向外多留一格，
     * 邻点才看得出"被挤开"；再放大就会连屏幕中部一起平移，观感变成整盘抖动而不是局部顶起。
     */
    const val FALLOFF_GRID_STEPS = 3.5f

    /**
     * 原点处最大位移（单位：一个网格间距）。
     *
     * 0.9 格 ≈ 一格点阵被顶开一道缝：小于 0.5 格时点只是"稍微挪了一下"，
     * 接近 1 格时相邻两点的间距会翻倍甚至重叠，所以封在 0.9。
     */
    const val PEAK_DISPLACEMENT_GRID_STEPS = 0.9f

    /** 原点处最大半径倍率增量：基半径 2.3dp → 3.1dp，肉眼可见"顶起来"而不炸成光斑。 */
    const val PEAK_RADIUS_GAIN = 0.35f

    /**
     * 平滑衰减权重 (1-t)²，t 夹在 0..1。距离 <= 0 视为原点（权重 1）；
     * falloffPx 非正或 NaN 时退化为 0（不除零）。
     */
    private fun weightOf(distancePx: Float, falloffPx: Float): Float {
        if (!(falloffPx > 0f)) return 0f
        if (!(distancePx > 0f)) return if (distancePx.isNaN()) 0f else 1f
        val t = (distancePx / falloffPx).coerceIn(0f, 1f)
        val rest = 1f - t
        return rest * rest
    }

    /**
     * 该点应被推开的位移向量 [Pair]（契约面 / 单测入口）。
     *
     * [dirX] / [dirY] 是"原点到该点"的**单位**方向向量，由调用方按格点坐标算好传入。
     * 方向未定义（distancePx <= 0）时返回 (0, 0) 而不是 NaN。
     */
    fun displacement(
        dirX: Float,
        dirY: Float,
        distancePx: Float,
        amplitudePx: Float,
        falloffPx: Float,
    ): Pair<Float, Float> = Pair(
        displacementX(dirX, distancePx, amplitudePx, falloffPx),
        displacementY(dirY, distancePx, amplitudePx, falloffPx),
    )

    /** 位移的 x 分量（标量版，绘制热路径用它逐点求值，零分配）。 */
    fun displacementX(dirX: Float, distancePx: Float, amplitudePx: Float, falloffPx: Float): Float {
        if (!(distancePx > 0f)) return 0f
        if (!(amplitudePx > 0f)) return 0f
        val weight = weightOf(distancePx, falloffPx)
        if (weight <= 0f) return 0f
        val value = dirX * amplitudePx * weight
        return if (value.isNaN()) 0f else value
    }

    /** 位移的 y 分量（标量版）。 */
    fun displacementY(dirY: Float, distancePx: Float, amplitudePx: Float, falloffPx: Float): Float {
        if (!(distancePx > 0f)) return 0f
        if (!(amplitudePx > 0f)) return 0f
        val weight = weightOf(distancePx, falloffPx)
        if (weight <= 0f) return 0f
        val value = dirY * amplitudePx * weight
        return if (value.isNaN()) 0f else value
    }

    /**
     * 半径放大倍数：离原点越近放大越多，恒 >= 1。
     *
     * amplitude 为 0（或负 / NaN）时恒等 1，即"没有按压时的半径"; 原点处为 1 + amplitude。
     */
    fun scale(distancePx: Float, amplitude: Float, falloffPx: Float): Float {
        if (!(amplitude > 0f)) return 1f
        return 1f + amplitude * weightOf(distancePx, falloffPx)
    }
}
