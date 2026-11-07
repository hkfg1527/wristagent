package com.example.myapplication.presentation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameMillis
import com.example.myapplication.presentation.ui.settings.LocalAnimationsEnabled
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.myapplication.agent.ApprovalVisualState
import com.example.myapplication.presentation.ui.theme.Wear
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * 预计算的静态点阵几何：六个平行 FloatArray 逐点平铺存放，绘制期按下标只读访问。
 * 用基本类型数组而不是对象列表，避免逐帧装箱与指针追逐。
 */
private class MatrixGrid(
    val count: Int,
    val widthPx: Float,
    val heightPx: Float,
    val radiusPx: Float,
    val xs: FloatArray,
    val ys: FloatArray,
    val distCenter: FloatArray,
    val edgeFade: FloatArray,
    val distFromButton: FloatArray,
    val projCoord: FloatArray,
)

private val EMPTY_FLOATS = FloatArray(0)

private val EMPTY_GRID = MatrixGrid(
    count = 0,
    widthPx = 0f,
    heightPx = 0f,
    radiusPx = 0f,
    xs = EMPTY_FLOATS,
    ys = EMPTY_FLOATS,
    distCenter = EMPTY_FLOATS,
    edgeFade = EMPTY_FLOATS,
    distFromButton = EMPTY_FLOATS,
    projCoord = EMPTY_FLOATS,
)

/**
 * 逐帧复用的批绘制缓冲：分桶计数、游标、点坐标、Paint 只在网格尺寸变化时分配一次。
 * points 按「每组的连续段」交错存放 (x, y)，直接喂给 Skia 的 drawPoints(FloatArray, offset, count)，
 * 绘制期不产生任何临时对象，也不出现 List/ArrayList。
 */
private class MatrixBatches(val dotCount: Int) {
    val groupOfDot = IntArray(dotCount)
    val groupSize = IntArray(DOT_GROUP_COUNT)
    val groupStart = IntArray(DOT_GROUP_COUNT)
    val groupCursor = IntArray(DOT_GROUP_COUNT)
    val points = FloatArray(dotCount * 2)
    // 组内代表值 = 成员均值：桶只保证成员方差有界，均值让整组像素误差无偏（并让顶部半径档的吸收无损）
    val alphaSum = FloatArray(DOT_GROUP_COUNT)
    val radiusSum = FloatArray(DOT_GROUP_COUNT)
    val categoryRgb = IntArray(DOT_CATEGORY_COUNT)
    val paint = Paint().apply {
        isAntiAlias = true
        strokeCap = StrokeCap.Round
    }
}

private val COLOR_DOT_BASE = Color(0xFFE2E8F0)
private val COLOR_DOT_SHOCK = Color(0xFFFFFFFF) // 纯净亮白微晶光芒，彻底消除廉价土味蓝
private val COLOR_DOT_GREEN = Color(0xFF10B981)
private val COLOR_DOT_CASCADE = Color(0xFFEF4444)
private val COLOR_DOT_WHITE = Color.White

// 组绘制只改 Paint 的 alpha 通道，颜色通道按类别预先取好，绘制期只做一次位或
private val RGB_DOT_GREEN = COLOR_DOT_GREEN.toArgb() and 0x00FFFFFF
private val RGB_DOT_CASCADE = COLOR_DOT_CASCADE.toArgb() and 0x00FFFFFF
private val RGB_DOT_WHITE = COLOR_DOT_WHITE.toArgb() and 0x00FFFFFF
private val RGB_DOT_SHOCK = COLOR_DOT_SHOCK.toArgb() and 0x00FFFFFF

private const val HALF_PI = (Math.PI / 2.0).toFloat()

// ---------------------------------------------------------------------------
// 批绘制分组：单帧内把点按 (颜色类别 × alpha 量化桶 × 半径档位) 归并，
// 每组一次 drawPoints，取代逐点 drawCircle。分组逻辑全部是纯函数，单测直接覆盖。
// ---------------------------------------------------------------------------

/** 点的颜色类别，与绘制分支一一对应。 */
internal const val DOT_CAT_BASE = 0        // 基色 / 审批插值色
internal const val DOT_CAT_GREEN = 1       // 审批放行绿
internal const val DOT_CAT_CASCADE = 2     // 待确认红瀑
internal const val DOT_CAT_GLINT = 3       // 完工掠影白
internal const val DOT_CAT_SHOCK = 4       // 底栏冲击波白
internal const val DOT_CATEGORY_COUNT = 5

/** alpha 量化档数：桶代表值取 (bucket+0.5)/6，最大量化误差 1/12 < 1/8。 */
internal const val DOT_ALPHA_BUCKETS = 6

/** 半径档数：0 号为静态半径（无动态放大），其余为水波/掠影/冲击波的放大档。 */
internal const val DOT_RADIUS_BUCKETS = 5

internal const val DOT_GROUPS_PER_CATEGORY = DOT_ALPHA_BUCKETS * DOT_RADIUS_BUCKETS
internal const val DOT_GROUP_COUNT = DOT_CATEGORY_COUNT * DOT_GROUPS_PER_CATEGORY

/** 空闲纯呼吸时呼吸相位的量化步数：1/24 步长，约 2800ms/24 ≈ 117ms 一档。 */
internal const val IDLE_BREATH_STEPS = 24

/** alpha(0~1) → 量化桶号。NaN / 越界一律收敛到合法桶，绝不逸出 [0, DOT_ALPHA_BUCKETS)。 */
internal fun dotAlphaBucket(alpha: Float): Int =
    (alpha.coerceIn(0f, 1f) * DOT_ALPHA_BUCKETS).toInt().coerceIn(0, DOT_ALPHA_BUCKETS - 1)

/** 桶号 → 该桶绘制时统一使用的 alpha（取桶中点，正向与负向量化误差均摊）。 */
internal fun dotBucketAlpha(bucket: Int): Float =
    (bucket.coerceIn(0, DOT_ALPHA_BUCKETS - 1) + 0.5f) / DOT_ALPHA_BUCKETS

/** 半径增生量(px) → 半径档位；四舍五入到最近档，误差 ≤ quantumPx/2。 */
internal fun dotRadiusBucket(extraRadiusPx: Float, quantumPx: Float): Int {
    if (quantumPx <= 0f || extraRadiusPx <= 0f) return 0
    val bucket = ((extraRadiusPx / quantumPx) + 0.5f).toInt()
    return bucket.coerceIn(0, DOT_RADIUS_BUCKETS - 1)
}

/** 点的颜色类别判定：严格等价于历史 when 分支的优先级顺序。 */
internal fun dotCategory(
    approvalT: Float,
    approvedGreen: Boolean,
    inCascade: Boolean,
    glintAlpha: Float,
    shockAlpha: Float,
): Int = when {
    approvalT > 0.01f -> DOT_CAT_BASE
    approvedGreen -> DOT_CAT_GREEN
    inCascade -> DOT_CAT_CASCADE
    glintAlpha > 0.05f -> DOT_CAT_GLINT
    shockAlpha > 0.05f -> DOT_CAT_SHOCK
    else -> DOT_CAT_BASE
}

/** 组内代表 alpha：取成员均值让整组像素误差无偏；空组回退到桶中心（空组不会被绘制，仅防除零）。 */
internal fun dotGroupAlpha(sumAlpha: Float, memberCount: Int, bucket: Int): Float =
    if (memberCount > 0) sumAlpha / memberCount else dotBucketAlpha(bucket)

/** 组内代表半径：同理取成员均值；空组回退到该档的静态代表半径。 */
internal fun dotGroupRadius(sumRadius: Float, memberCount: Int, fallbackRadius: Float): Float =
    if (memberCount > 0) sumRadius / memberCount else fallbackRadius

/** 分桶平面下标：类别 × alpha 桶 × 半径档，取值恒在 [0, DOT_GROUP_COUNT)。 */
internal fun dotGroupIndex(
    category: Int,
    alpha: Float,
    extraRadiusPx: Float,
    quantumPx: Float,
): Int {
    val cat = category.coerceIn(0, DOT_CATEGORY_COUNT - 1)
    val alphaBucket = dotAlphaBucket(alpha)
    val radiusBucket = dotRadiusBucket(extraRadiusPx, quantumPx)
    return (cat * DOT_ALPHA_BUCKETS + alphaBucket) * DOT_RADIUS_BUCKETS + radiusBucket
}

/**
 * 单次构建整个屏幕的点阵几何 (仅在画布尺寸或网格间距变化时执行一次)。
 * 圆心坐标、到圆心距离、边缘衰减、到底栏波源距离、掠影投影坐标全部提前算好。
 */
private fun buildMatrixGrid(
    width: Float,
    height: Float,
    stepPx: Float,
    micOriginX: Float,
    micOriginY: Float,
): MatrixGrid {
    val maxRadius = minOf(width, height) / 2f
    if (width <= 0f || height <= 0f || maxRadius <= 0f || stepPx <= 0f) return EMPTY_GRID

    val centerX = width / 2f
    val centerY = height / 2f
    val halfCount = (maxRadius / stepPx).toInt() + 1
    val side = halfCount * 2 + 1
    val capacity = side * side
    val xs = FloatArray(capacity)
    val ys = FloatArray(capacity)
    val distCenterArr = FloatArray(capacity)
    val edgeFadeArr = FloatArray(capacity)
    val distFromBtnArr = FloatArray(capacity)
    val projCoordArr = FloatArray(capacity)

    var index = 0
    for (kx in -halfCount..halfCount) {
        val x = centerX + kx * stepPx
        for (ky in -halfCount..halfCount) {
            val y = centerY + ky * stepPx
            val dx = x - centerX
            val dy = y - centerY
            val distCenter = sqrt(dx * dx + dy * dy)
            if (distCenter <= maxRadius) {
                val normDist = (distCenter / maxRadius).coerceIn(0f, 1f)
                val bx = x - micOriginX
                val by = y - micOriginY
                xs[index] = x
                ys[index] = y
                distCenterArr[index] = distCenter
                edgeFadeArr[index] = (1.0f - normDist * normDist).coerceIn(0f, 1f)
                distFromBtnArr[index] = sqrt(bx * bx + by * by)
                projCoordArr[index] = x + y * 0.62f
                index++
            }
        }
    }

    return MatrixGrid(
        count = index,
        widthPx = width,
        heightPx = height,
        radiusPx = maxRadius,
        xs = xs,
        ys = ys,
        distCenter = distCenterArr,
        edgeFade = edgeFadeArr,
        distFromButton = distFromBtnArr,
        projCoord = projCoordArr,
    )
}

/**
 * 思考行波的连续相位（纯逻辑，可单测）。
 *
 * 相位只由"累计毫秒"决定，不挂在无限动画上：
 *  - 无限动画（`rememberInfiniteTransition`）的相位起点在组合期是**自由**的，
 *    它的周期（2400ms）与本文件里"波扫满一次可达半径"所需时长并不相等
 *    （旧实现是 `phase * 1.4R % 1.25R`，一个视觉周期只有 2143ms），于是波形
 *    是一串"从圆心诞生 → 扫出去 → 淡死"的脉冲，而不是一条持续的行波。
 *    用户看到的就是"先放一点点，回到初始，再过一遍"。
 *  - 思考开始/结束在本文件里只是一次**可见性**变化（`thinkingIntensity` 交叉淡入淡出）。
 *    相位必须接着原来那根时间轴走，绝不能回到 0 —— 那才是真的"重头再来一遍"。
 */
internal object ThinkingWave {

    /** 一条行波从圆心走到边界并淡出占用的时长（ms）：与视觉周期严格 1:1。 */
    const val CYCLE_MS = 2400L

    /** 行波可达半径（相对表盘半径）：1.25 让波在贴边之前就淡完，不外溢到圆外。 */
    const val REACH_FACTOR = 1.25f

    /** 两端过渡区占可达半径的比例：出生点与抵达边界处 alpha 都必须为 0。 */
    const val RAMP_FACTOR = 0.18f

    /** 累计毫秒 → 本轮相位（0~1）。取模只出现在这里，波内不再二次取模。 */
    fun phaseOf(elapsedMs: Long, cycleMs: Long = CYCLE_MS): Float {
        val cycle = cycleMs.coerceAtLeast(1L)
        val ms = elapsedMs % cycle
        val positive = if (ms < 0L) ms + cycle else ms
        return positive.toFloat() / cycle.toFloat()
    }

    /** 行波可达半径（px）。 */
    fun reachPx(maxRadiusPx: Float): Float = maxRadiusPx * REACH_FACTOR

    /** 两端过渡区宽度（px）。 */
    fun rampPx(maxRadiusPx: Float): Float = reachPx(maxRadiusPx) * RAMP_FACTOR

    /** 累计毫秒 → 行波半径（px）。一个周期恰好扫满一次 [reachPx]，不会额外回绕一次。 */
    fun travelPx(
        elapsedMs: Long,
        maxRadiusPx: Float,
        cycleMs: Long = CYCLE_MS
    ): Float = phaseOf(elapsedMs, cycleMs) * reachPx(maxRadiusPx)

    /**
     * 行波两端包络：出生时从 0 淡入、抵达边界时淡回 0。
     * 没有它的话，`travel≈0` 时 `|dist-travel| < band` 覆盖的是半径 band 的实心圆盘，
     * 而能量项在圆心最大，于是每次波从圆心诞生都会"啪"地闪一下。
     */
    fun envelope(travelPx: Float, maxRadiusPx: Float): Float {
        val ramp = rampPx(maxRadiusPx)
        if (ramp <= 0f) return 0f
        val rise = (travelPx / ramp).coerceIn(0f, 1f)
        val fall = ((reachPx(maxRadiusPx) - travelPx) / ramp).coerceIn(0f, 1f)
        return rise * fall
    }

    /**
     * 红瀑是否已经停在静止值（0 = 无红瀑）。
     *
     * 只有**静止**时才有资格重置：还在退场途中就 `snapTo(0f)`，等于把一段淡出剪成一次闪。
     */
    fun cascadeAtRest(value: Float, tolerance: Float = 0.02f): Boolean =
        kotlin.math.abs(value) <= tolerance

    /** 完工掠影是否已经扫完（不在可见带内）：只有扫完了才允许重新起一次，否则会"闪一下又重来"。 */
    fun glintAtRest(value: Float): Boolean = value !in -0.3f..1.3f

    /**
     * 冲击波是否仍在波中：只有 `[0,1)` 才算在途。
     *
     * 1f 是 `animateTo(1f)` 走完后的**终值**，不是"正在波中"。旧判据 `in 0f..1.2f`
     * 会让第一轮回答结束后 `hasShock` 永久为真 —— 点阵的空闲降频通道从此再也进不去，
     * 每一帧都重录一遍绘制指令（真表上就是持续的重绘与掉电）。
     */
    fun shockInFlight(progress: Float): Boolean = progress >= 0f && progress < 1f
}

/**
 * 纯圆表盘高亮大微晶方形点阵渲染引擎 (浮于遮罩之上 · 加大高质感光点 · 强烈水波纹扩散 · 四态光相变)
 * 1. 方形阵列：严格基于直角坐标系 (x, y) 均匀栅格排布，以圆心为基准对称排布；
 * 2. 覆盖全圆：完全铺满圆形表盘的所有区域（包括四周圆弧月半弧），无任何方形裁剪框；
 * 3. 几何预缓存：所有点位与距离在组合期一次性算入 FloatArray，绘制期不再分配对象、不再求平方根；
 * 4. 批绘制：逐帧把点按 (颜色类别 × alpha 量化桶 × 半径档位) 归并成若干组，每组一次
 *    drawPoints(PointMode.Points, StrokeCap.Round, strokeWidth = 2 × 半径) 取代逐点 drawCircle；
 *    分组用 remember 的 IntArray/FloatArray 原地做计数排序，绘制的 alpha 与半径取组内成员均值，
 *    绘制期零分配、无 List；
 * 5. 空闲降频：纯呼吸待机时把呼吸相位量化到 1/24，量化值不变则不产生绘制失效，
 *    上一帧的绘制指令被整帧复用；思考波相位只在真有行波时才被读取，不污染空闲帧依赖；
 * 6. 审批进度着色：approvalProgress 让点阵自身从基色插值到放行绿，不额外叠加任何整屏色层。
 */
@Composable
fun UnifiedMatrixCanvas(
    modifier: Modifier = Modifier,
    approvalVisualState: ApprovalVisualState = ApprovalVisualState.NONE,
    isTaskCompletedTrigger: Boolean = false, // 触发完工掠影反光
    thinkingIntensity: Float = 0f,           // 0f ~ 1f 思考引擎强度
    isRecordingVoice: Boolean = false,       // 录音状态
    shockwaveProgress: Float = -1f,          // 底栏激荡冲击波 (0f ~ 1f)
    gridSpacingDp: Float = 16f,              // 网格物理间距 (dp)
    approvalProgress: Float = 0f             // 审批滑块进度 (0f 拒绝 ~ 1f 放行)：点阵本身由基色渐变到放行绿
) {
    // 3.0s 沉稳深邃呼吸过渡 (加大呼吸振幅)。动效总开关关闭时连无限动画都不创建：
    // 相位钉在中位，量化档位随之恒定，点阵彻底静止
    val animationsEnabled = LocalAnimationsEnabled.current
    val breathCycle by if (animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "matrixBreathTransition")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "breathCycle"
        )
    } else {
        remember { mutableStateOf(0.5f) }
    }

    // 思考同心行波相位：累计真实毫秒，不挂在无限动画上。
    // 无限动画的相位起点是自由的，且 2400ms 周期与"扫满一次可达半径"不同步（见 ThinkingWave），
    // 于是它只能靠包络把诞生/回绕两处藏起来，看起来仍是一串脉冲；换成累计时钟后，
    // 思考开始/中断/恢复只是同一条时间轴的裁剪 —— 叠加，不重启。
    val waveElapsedMs = remember { mutableLongStateOf(0L) }
    val waveRunning = thinkingIntensity > 0.01f
    LaunchedEffect(waveRunning) {
        if (!waveRunning) return@LaunchedEffect
        var previousFrame = withFrameMillis { it }
        while (true) {
            val frame = withFrameMillis { it }
            // 停走期间流逝的时间不补进相位：恢复时接着原来的相位继续，而不是瞬移一大段
            waveElapsedMs.longValue += (frame - previousFrame).coerceIn(0L, 64L)
            previousFrame = frame
        }
    }

    // 空闲降频的相位量化：量化值不变时读端不失效，Compose 直接复用上一帧已录制的绘制指令
    val idleBreathStep by remember {
        derivedStateOf { (breathCycle * IDLE_BREATH_STEPS + 0.5f).toInt() }
    }

    // 红瀑下倾进度动画 (0f ~ 1f)
    val cascadeAnim = remember { Animatable(0f) }
    // 完工表镜掠影动画 (-0.4f ~ 1.4f)
    val glintAnim = remember { Animatable(-0.5f) }

    LaunchedEffect(approvalVisualState) {
        when (approvalVisualState) {
            ApprovalVisualState.PENDING_CASCADE -> {
                // 新一次红瀑必须从顶点起算：这是**真的需要重放**，可以重置
                cascadeAnim.snapTo(0f)
                cascadeAnim.animateTo(1f, tween(360, easing = LinearEasing))
            }
            ApprovalVisualState.REJECTED_EBB -> {
                cascadeAnim.animateTo(0f, tween(260, easing = LinearEasing))
            }
            ApprovalVisualState.APPROVED_GREEN -> {
                cascadeAnim.animateTo(0f, tween(400, easing = LinearEasing))
            }
            ApprovalVisualState.NONE -> {
                // 无审批态不是"重放"，只是一次退场：还在退就让它走完同一段淡出，
                // 已经停了就什么都不做。旧实现无条件 snapTo(0f)，把没走完的淡出剪成一次闪。
                if (!ThinkingWave.cascadeAtRest(cascadeAnim.value)) {
                    cascadeAnim.animateTo(0f, tween(260, easing = LinearEasing))
                }
            }
        }
    }

    LaunchedEffect(isTaskCompletedTrigger) {
        if (!isTaskCompletedTrigger) return@LaunchedEffect
        // 掠影已经在外扫就不重置：重置会把扫到一半的白光拍回起点，
        // 真机上就是"闪一下，然后又从头扫一遍"。
        if (!ThinkingWave.glintAtRest(glintAnim.value)) return@LaunchedEffect
        glintAnim.snapTo(-0.4f)
        glintAnim.animateTo(1.4f, tween(380, easing = LinearEasing))
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val stepPx = with(density) { gridSpacingDp.dp.toPx() }.coerceAtLeast(10f)
        val micOriginOffsetPx = with(density) { 34.dp.toPx() }
        val bounded = constraints.hasBoundedWidth && constraints.hasBoundedHeight
        val boxWidthPx = if (bounded) constraints.maxWidth.toFloat() else 0f
        val boxHeightPx = if (bounded) constraints.maxHeight.toFloat() else 0f

        // 几何只在 (画布尺寸, 网格间距) 变化时重建一次，其余帧直接复用
        val grid = remember(boxWidthPx, boxHeightPx, stepPx, micOriginOffsetPx) {
            buildMatrixGrid(
                width = boxWidthPx,
                height = boxHeightPx,
                stepPx = stepPx,
                micOriginX = boxWidthPx / 2f,
                micOriginY = boxHeightPx - micOriginOffsetPx,
            )
        }

        // 分桶缓冲同样只随网格点数分配一次
        val batches = remember(grid.count) { MatrixBatches(grid.count) }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = grid.count
            if (count == 0) return@Canvas

            val w = size.width
            val h = size.height
            val maxRadius = minOf(w, h) / 2f
            val scaleX = if (grid.widthPx > 0f) w / grid.widthPx else 1f
            val scaleY = if (grid.heightPx > 0f) h / grid.heightPx else 1f

            val xs = grid.xs
            val ys = grid.ys
            val distCenterArr = grid.distCenter
            val edgeFadeArr = grid.edgeFade
            val distFromButtonArr = grid.distFromButton
            val projCoordArr = grid.projCoord

            val baseRadius = 2.3.dp.toPx()
            val shockBand = 54.dp.toPx()
            val rippleBandWidth = 50.dp.toPx()
            val cascadeBand = 60.dp.toPx()
            val glintBandWidth = 48.dp.toPx()
            val dynamicUnit = 0.8.dp.toPx()
            // 半径量化档宽：一档 0.6dp，再取组内均值后残差 ≈ 0.17dp（基半径 2.3dp 的 7%，不可辨）
            val radiusQuantum = 0.6.dp.toPx()

            // ---- 逐帧不变量的行波参数，一次性算好 ----
            val glintPosition = glintAnim.value * (w + h)
            val cascadeCurrentY = cascadeAnim.value * h
            // 1f 是冲击波走完后的终值，不是"正在波中"：把它算在途会让空闲降频永久失效
            val hasShock = ThinkingWave.shockInFlight(shockwaveProgress)
            val shockTravel = shockwaveProgress * maxRadius * 2.3f
            val shockEnergy = (1f - shockwaveProgress * 0.35f).coerceIn(0f, 1f)
            val hasRipple = thinkingIntensity > 0.01f
            val rippleReach = ThinkingWave.reachPx(maxRadius)
            // 思考波相位只在真有行波时才读取：空闲待机不建立这条依赖，避免每帧无谓失效。
            // 相位来自累计时钟，一个周期恰好扫满一次 reach，波内不再二次取模。
            val rippleTravel = if (hasRipple) ThinkingWave.travelPx(waveElapsedMs.longValue, maxRadius) else 0f
            val rippleEnvelope = if (hasRipple) ThinkingWave.envelope(rippleTravel, maxRadius) else 0f
            val hasGlint = glintAnim.value in -0.3f..1.3f
            val hasCascade = cascadeAnim.value > 0.01f
            val approvedGreen = approvalVisualState == ApprovalVisualState.APPROVED_GREEN

            // 审批进度：每个点自己的颜色从冷白基色连续插值到放行绿，而不是在整屏上盖一层绿膜；
            // 亮度与半径随进度轻微增强，滑块越往左推，整屏点阵越绿越亮。
            val approvalT = approvalProgress.coerceIn(0f, 1f)
            val baseDotColor = lerp(COLOR_DOT_BASE, Wear.Palette.Success, approvalT)
            val approvalRadiusScale = 1f + 0.35f * approvalT
            // 审批态下点阵必须"看得见的绿"：除了按比例提亮，再给一条随进度上升的最低亮度，
            // 否则基色点本来就暗（alpha≈0.25），插值成绿也肉眼无感。
            val approvalAlphaBoost = 0.9f * approvalT
            val approvalAlphaFloor = 0.22f * approvalT

            // ---- 空闲降频：纯呼吸待机时把呼吸相位量化到 1/24 ----
            // 量化值不变时这里不会去读 breathCycle 原始值，本帧也就不产生绘制失效，
            // 上一帧录制的绘制指令被整帧复用（与"在 draw 内提前 return"等效，
            // 但不会把节点重录成空指令——那会让点阵闪一帧空白）。
            val idleBreathOnly = !isRecordingVoice && !hasRipple && !hasShock && !hasGlint &&
                !hasCascade && approvalT < 0.01f && !approvedGreen
            val breath = if (idleBreathOnly) idleBreathStep / IDLE_BREATH_STEPS.toFloat() else breathCycle
            val breathBase = 0.24f + 0.28f * breath

            val staticRadius = baseRadius * approvalRadiusScale
            val groupOfDot = batches.groupOfDot
            val groupSize = batches.groupSize
            val groupStart = batches.groupStart
            val groupCursor = batches.groupCursor
            val points = batches.points
            val alphaSum = batches.alphaSum
            val radiusSum = batches.radiusSum
            val categoryRgb = batches.categoryRgb

            categoryRgb[DOT_CAT_BASE] = baseDotColor.toArgb() and 0x00FFFFFF
            categoryRgb[DOT_CAT_GREEN] = RGB_DOT_GREEN
            categoryRgb[DOT_CAT_CASCADE] = RGB_DOT_CASCADE
            categoryRgb[DOT_CAT_GLINT] = RGB_DOT_WHITE
            categoryRgb[DOT_CAT_SHOCK] = RGB_DOT_SHOCK

            // ---- pass 1：逐点分类 + 分桶计数 + 组内累加（本帧唯一需要逐点算三角函数的地方）----
            groupSize.fill(0)
            alphaSum.fill(0f)
            radiusSum.fill(0f)
            var i = 0
            while (i < count) {
                val edgeFade = edgeFadeArr[i]

                // 1. 基础待机与呼吸明暗 (提升基础对比度与明亮度)
                val baseAlpha = if (isRecordingVoice) 0.18f * edgeFade else breathBase * edgeFade

                // 2. 从底部按钮向全屏扩散的强烈水波纹 (Button Ripple)
                var shockAlpha = 0f
                var shockScale = 0f
                if (hasShock) {
                    val distDiff = abs(distFromButtonArr[i] - shockTravel)
                    if (distDiff < shockBand) {
                        val falloff = cos((distDiff / shockBand) * HALF_PI).let { it * it }
                        shockAlpha = falloff * shockEnergy * 1.1f * (0.35f + 0.65f * edgeFade)
                        shockScale = falloff * shockEnergy * 2.2f
                    }
                }

                // 3. 思考状态下的同心持续呼吸波
                var rippleAlpha = 0f
                var rippleScale = 0f
                if (hasRipple) {
                    val distDiff = abs(distCenterArr[i] - rippleTravel)
                    if (distDiff < rippleBandWidth) {
                        val falloff = cos((distDiff / rippleBandWidth) * HALF_PI).let { it * it }
                        val energy = (1f - distCenterArr[i] / rippleReach).coerceIn(0.1f, 1f)
                        rippleAlpha = falloff * energy * 0.85f * thinkingIntensity * edgeFade * rippleEnvelope
                        rippleScale = falloff * energy * 1.6f * thinkingIntensity * rippleEnvelope
                    }
                }

                // 4. 完工掠影反光 (Glint: 30 度冷白反光扫过)
                var glintAlpha = 0f
                var glintScale = 0f
                if (hasGlint) {
                    val distGlint = abs(projCoordArr[i] - glintPosition)
                    if (distGlint < glintBandWidth) {
                        val falloff = cos((distGlint / glintBandWidth) * HALF_PI).let { it * it }
                        glintAlpha = falloff * 0.95f * edgeFade
                        glintScale = falloff * 2.0f
                    }
                }

                // 5. 红瀑 / 翡翠绿光相变
                var isParticleInCascade = false
                var cascadeRatio = 0f
                if (hasCascade && ys[i] <= cascadeCurrentY) {
                    isParticleInCascade = true
                    val distFromFront = abs(cascadeCurrentY - ys[i])
                    cascadeRatio = (1f - (distFromFront / cascadeBand).coerceIn(0f, 1f))
                }

                // 6. 颜色类别 + alpha（与历史 when 分支逐条等价，仅把逐点 Color.copy 换成裸 alpha）
                val category =
                    dotCategory(approvalT, approvedGreen, isParticleInCascade, glintAlpha, shockAlpha)
                val alpha = when (category) {
                    DOT_CAT_GREEN -> (0.85f * edgeFade).coerceIn(0f, 1f)
                    DOT_CAT_CASCADE -> ((0.46f + 0.54f * cascadeRatio) * edgeFade).coerceIn(0f, 1f)
                    DOT_CAT_GLINT -> (baseAlpha + glintAlpha).coerceIn(0f, 1f)
                    DOT_CAT_SHOCK -> (baseAlpha + shockAlpha).coerceIn(0f, 1f)
                    else -> (baseAlpha * (1f + approvalAlphaBoost) + rippleAlpha + approvalAlphaFloor)
                        .coerceIn(0f, 1f)
                }

                // 7. 半径增生量：动态放大单独占档；无动态的帧里 0 档成员全是静态半径，组代表值即精确半径
                val extraRadius = (rippleScale + glintScale * 1.2f + shockScale * 1.5f) * dynamicUnit

                val group = dotGroupIndex(category, alpha, extraRadius, radiusQuantum)
                groupOfDot[i] = group
                groupSize[group]++
                alphaSum[group] += alpha
                radiusSum[group] += staticRadius + extraRadius
                i++
            }

            // ---- 前缀和：把每个桶的计数换算成 points 里的连续区间 ----
            var cursor = 0
            var group = 0
            while (group < DOT_GROUP_COUNT) {
                groupStart[group] = cursor
                groupCursor[group] = cursor
                cursor += groupSize[group]
                group++
            }

            // ---- pass 2：把坐标按组写进连续段（只做两次数组写入，不再重算任何三角函数）----
            i = 0
            while (i < count) {
                val slot = groupCursor[groupOfDot[i]]
                groupCursor[groupOfDot[i]] = slot + 1
                points[slot * 2] = xs[i] * scaleX
                points[slot * 2 + 1] = ys[i] * scaleY
                i++
            }

            // ---- 批绘制：每满组一次 drawPoints(PointMode.Points, 圆头, strokeWidth = 2 × 半径) ----
            drawIntoCanvas { canvas ->
                val nativeCanvas = canvas.nativeCanvas
                val nativePaint = batches.paint.asFrameworkPaint()
                var g = 0
                while (g < DOT_GROUP_COUNT) {
                    val dotCount = groupSize[g]
                    if (dotCount > 0) {
                        val category = g / DOT_GROUPS_PER_CATEGORY
                        val withinCategory = g - category * DOT_GROUPS_PER_CATEGORY
                        val alphaBucket = withinCategory / DOT_RADIUS_BUCKETS
                        val radiusBucket = withinCategory - alphaBucket * DOT_RADIUS_BUCKETS
                        val groupAlpha = dotGroupAlpha(alphaSum[g], dotCount, alphaBucket)
                        val groupRadius = dotGroupRadius(
                            radiusSum[g],
                            dotCount,
                            staticRadius + radiusBucket * radiusQuantum,
                        )
                        val alphaByte = (groupAlpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                        nativePaint.color = categoryRgb[category] or (alphaByte shl 24)
                        nativePaint.strokeWidth = 2f * groupRadius
                        nativeCanvas.drawPoints(points, groupStart[g] * 2, dotCount * 2, nativePaint)
                    }
                    g++
                }
            }
        }
    }
}
